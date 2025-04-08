package org.aps.export_data_v2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.aps.export_data_v2.constant.BatchStatus;
import org.aps.export_data_v2.constant.ExportStatus;
import org.aps.export_data_v2.entity.ExportBatch;
import org.aps.export_data_v2.entity.ExportJob;
import org.aps.export_data_v2.entity.Salary;
import org.aps.export_data_v2.repository.ExportBatchRepository;
import org.aps.export_data_v2.repository.ExportJobRepository;
import org.aps.export_data_v2.repository.SalaryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@EnableAsync
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportExcelService {
    private final SalaryRepository salaryRepository;
    private final ExportJobRepository exportJobRepository;
    private final ExportBatchRepository exportBatchRepository;
    private final Executor asyncExecutor;

    @Value("${app.export.batch-size}")
    private int BATCH_SIZE;

    @Value("${app.export.max-retries}")
    private int MAX_RETRIES;

    @Value("${app.storage.base-path:/tmp/exports}")
    private String basePath;

    public ExportJob createSalaryExportJob() {
        long totalRecords = salaryRepository.count();
        int totalBatches = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        ExportJob job = ExportJob.builder()
                .jobUniqueId(UUID.randomUUID().toString())
                .requestedBy("User")
                .exportType("SALARY_EXCEL")
                .totalBatches(totalBatches)
                .totalRecords((int) totalRecords)
                .build();
        job = exportJobRepository.save(job);

        createJobDirectory(job.getJobUniqueId());

        List<ExportBatch> batches = new ArrayList<>();
        for (int i = 0; i < totalBatches; i++) {
            ExportBatch batch = ExportBatch.builder()
                    .batchUniqueId(UUID.randomUUID().toString())
                    .exportJob(job)
                    .batchNumber(i)
                    .startOffset(i*BATCH_SIZE)
                    .status(BatchStatus.PENDING)
                    .endOffset(Math.min((i + 1) * BATCH_SIZE, (int) totalRecords))
                    .build();

            batches.add(batch);
        }
        exportBatchRepository.saveAll(batches);

        for (ExportBatch batch : batches) {
            CompletableFuture.runAsync(() -> {
                processBatch(batch);
            }, asyncExecutor);
        }

        return exportJobRepository.save(job);
    }

    @Async
    public void processExportJob(String jobUniqueId) {
        ExportJob job = exportJobRepository.findByJobUniqueId(jobUniqueId)
                .orElseThrow(() -> new RuntimeException("Export job not found"));

        try {
            List<ExportBatch> pendingBatches =
                    exportBatchRepository.findByExportJobIdAndStatus(job.getId(), BatchStatus.PENDING);
            for (ExportBatch batch : pendingBatches) {
                processBatch(batch);
            }
            updateJobStatus(job.getId());

        } catch (Exception e) {
            log.error("Error processing export job: {}", jobUniqueId, e);
        }
    }

    public void processBatch(ExportBatch batch) {
        try {
            batch.setStatus(BatchStatus.IN_PROGRESS);
            batch.setLastProcessedAt(LocalDateTime.now());
            exportBatchRepository.save(batch);

            SXSSFWorkbook workbook = new SXSSFWorkbook(100000);
            ExportExcelUtil.createHeaderRow(workbook);

            int offset = batch.getStartOffset();
            int limit = batch.getEndOffset() - batch.getStartOffset();
            List<Salary> salaries = salaryRepository.findAllByOffsetRange(offset, limit);

            ExportExcelUtil.writeUserDataBatch(workbook, salaries, 2);
            String batchFilePath = saveBatchToFile(batch.getExportJob().getJobUniqueId(), batch.getBatchNumber(), workbook);
            batch.setPartialFilePath(batchFilePath);
            batch.setStatus(BatchStatus.COMPLETED);
            batch.setLastProcessedAt(LocalDateTime.now());
            exportBatchRepository.save(batch);

            ExportJob job = batch.getExportJob();
            job.setProcessedBatches(job.getProcessedBatches() + 1);
            exportJobRepository.save(job);

        } catch (Exception e) {
            log.error("Error processing batch: {}", batch.getBatchUniqueId(), e);
            batch.setStatus(BatchStatus.FAILED);
            batch.setErrorMessage(e.getMessage());
            batch.setRetryCount(batch.getRetryCount() + 1);
            batch.setLastProcessedAt(LocalDateTime.now());
            exportBatchRepository.save(batch);
        }
    }

    private String saveBatchToFile(String jobId, int batchNumber, SXSSFWorkbook workbook) throws IOException {
        String batchFileName = jobId + "_batch_" + batchNumber + ".xlsx";
        String batchFilePath = basePath + File.separator + jobId + File.separator + batchFileName;

        try (FileOutputStream fileOut = new FileOutputStream(batchFilePath)) {
            workbook.write(fileOut);
            workbook.dispose();
        }

        return batchFilePath;
    }

    public void updateJobStatus(Long jobId) {
        ExportJob job = exportJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Export job not found"));

        int completedBatches = exportBatchRepository.countBatchesByJobIdAndStatus(jobId, BatchStatus.COMPLETED);
        int failedBatches = exportBatchRepository.countBatchesByJobIdAndStatus(jobId, BatchStatus.FAILED);

        if (completedBatches + failedBatches == job.getTotalBatches()) {
            if (failedBatches == 0) {
                job.setStatus(ExportStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                try {
                    String finalFilePath = combineExcelFiles(job);
                    job.setResultFileUrl(finalFilePath);
                } catch (Exception e) {
                    log.error("Error combining Excel files for job: {}", job.getJobUniqueId(), e);
                    job.setStatus(ExportStatus.FAILED);
                }
            } else if (completedBatches > 0) {
                job.setStatus(ExportStatus.PARTIALLY_COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                try {
                    String finalFilePath = combineExcelFiles(job);
                    job.setResultFileUrl(finalFilePath);
                } catch (Exception e) {
                    log.error("Error combining Excel files for job: {}", job.getJobUniqueId(), e);
                }
            } else {
                job.setStatus(ExportStatus.FAILED);
                job.setCompletedAt(LocalDateTime.now());
            }

            exportJobRepository.save(job);
        }
    }

    private String combineExcelFiles(ExportJob job) throws IOException {
        List<ExportBatch> completedBatches = exportBatchRepository.findByExportJobIdAndStatus(
                job.getId(),
                BatchStatus.COMPLETED
        );

        return ExportExcelUtil.zipExcelFiles(completedBatches, job.getJobUniqueId(), basePath);
    }

    @Async
    public void retryFailedBatches(String jobUniqueId) {
        ExportJob job = exportJobRepository.findByJobUniqueId(jobUniqueId)
                .orElseThrow(() -> new RuntimeException("Export job not found"));

        List<BatchStatus> listStatus = new ArrayList<>();
        listStatus.add(BatchStatus.FAILED);
        listStatus.add(BatchStatus.PENDING);
        listStatus.add(BatchStatus.IN_PROGRESS);

        List<ExportBatch> failedBatches =
                exportBatchRepository.findBatchesForRetry(job.getId(), listStatus, MAX_RETRIES);

        for (ExportBatch batch : failedBatches) {
                CompletableFuture.runAsync(() -> {
                    processBatch(batch);
                }, asyncExecutor);
        }

        updateJobStatus(job.getId());
    }

    public ExportJob getJobStatus(String jobUniqueId) {
        return exportJobRepository.findByJobUniqueId(jobUniqueId)
                .orElseThrow(() -> new RuntimeException("Export job not found"));
    }

    private void createJobDirectory(String jobUniqueId) {
        try {
            Path directory = Paths.get(basePath, jobUniqueId);
            Files.createDirectories(directory);
        } catch (IOException e) {
            log.error("Could not create directory for job: {}", jobUniqueId, e);
            throw new RuntimeException("Could not create export directory", e);
        }
    }
}