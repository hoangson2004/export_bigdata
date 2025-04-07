package org.aps.export_data_v2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.aps.export_data_v2.constant.BatchStatus;
import org.aps.export_data_v2.constant.ExportStatus;
import org.aps.export_data_v2.entity.ExportBatch;
import org.aps.export_data_v2.entity.ExportJob;
import org.aps.export_data_v2.entity.Salary;
import org.aps.export_data_v2.repository.ExportBatchRepository;
import org.aps.export_data_v2.repository.ExportJobRepository;
import org.aps.export_data_v2.repository.SalaryRepository;
import org.aps.export_data_v2.ExportExcelUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportExcelService {
    private final SalaryRepository salaryRepository;
    private final ExportJobRepository exportJobRepository;
    private final ExportBatchRepository exportBatchRepository;
    private final Executor asyncExecutor;

    @Value("${app.export.batch-size:100000}")
    private int BATCH_SIZE;

    @Value("${app.export.max-retries:3}")
    private int MAX_RETRIES;

    @Value("${app.storage.base-path:/tmp/exports}")
    private String basePath;

    @Transactional
    public ExportJob createSalaryExportJob() {
        long totalRecords = salaryRepository.count();
        int totalBatches = (int) Math.ceil((double) totalRecords / BATCH_SIZE);

        ExportJob job = new ExportJob();
        job.setJobUniqueId(UUID.randomUUID().toString());
        job.setExportType("SALARY_EXCEL");
        job.setTotalBatches(totalBatches);
        job.setTotalRecords((int) totalRecords);

        job = exportJobRepository.save(job);

        createJobDirectory(job.getJobUniqueId());

        List<ExportBatch> batches = new ArrayList<>();
        for (int i = 0; i < totalBatches; i++) {
            ExportBatch batch = new ExportBatch();
            batch.setBatchUniqueId(UUID.randomUUID().toString());
            batch.setExportJob(job);
            batch.setBatchNumber(i);
            batch.setStartOffset(i * BATCH_SIZE);
            batch.setEndOffset(Math.min((i + 1) * BATCH_SIZE, (int) totalRecords));
            batches.add(batch);
        }

        exportBatchRepository.saveAll(batches);

        job.setStatus(ExportStatus.IN_PROGRESS);
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

    @Transactional
    public void processBatch(ExportBatch batch) {
        try {
            batch.setStatus(BatchStatus.IN_PROGRESS);
            batch.setLastProcessedAt(LocalDateTime.now());
            exportBatchRepository.save(batch);

            SXSSFWorkbook workbook = new SXSSFWorkbook(100);
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

    @Transactional
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
        String finalFileName = job.getJobUniqueId() + "_final.xlsx";
        String finalFilePath = basePath + File.separator + job.getJobUniqueId() + File.separator + finalFileName;
        List<ExportBatch> completedBatches = exportBatchRepository.findByExportJobIdAndStatus(
                job.getId(),
                BatchStatus.COMPLETED
        );
        // Sắp xếp batch theo thứ tự để đảm bảo dữ liệu được ghép theo đúng thứ tự
        completedBatches.sort(Comparator.comparing(ExportBatch::getBatchNumber));

        SXSSFWorkbook finalWorkbook = new SXSSFWorkbook(100);

        final int MAX_ROWS_PER_SHEET = 1000000;

        int currentRow = 0;
        int currentSheetIndex = 0;
        SXSSFSheet currentSheet = null;

        // Tạo sheet đầu tiên và tạo header
        currentSheet = finalWorkbook.createSheet("Sheet " + (currentSheetIndex + 1));
        ExportExcelUtil.createHeaderRow(finalWorkbook, currentSheet);
        currentRow = 1; // Bắt đầu từ dòng 1 vì dòng 0 là header

        for (ExportBatch batch : completedBatches) {
            String partialPath = batch.getPartialFilePath();
            if (partialPath == null) continue;

            try (InputStream inputStream = new FileInputStream(partialPath);
                 Workbook partialWorkbook = WorkbookFactory.create(inputStream)) {

                Sheet partialSheet = partialWorkbook.getSheetAt(0);

                for (int i = 2; i <= partialSheet.getLastRowNum(); i++) {
                    Row sourceRow = partialSheet.getRow(i);
                    if (sourceRow == null) continue;

                    // Kiểm tra xem có cần tạo sheet mới không
                    if (currentRow >= MAX_ROWS_PER_SHEET) {
                        currentSheetIndex++;
                        currentSheet = finalWorkbook.createSheet("Sheet " + (currentSheetIndex + 1));
                        ExportExcelUtil.createHeaderRow(finalWorkbook, currentSheet); // Tạo header cho sheet mới
                        currentRow = 1; // Reset về dòng 1 (sau header)
                    }

                    Row destRow = currentSheet.createRow(currentRow++);

                    for (int j = 0; j < sourceRow.getLastCellNum(); j++) {
                        Cell sourceCell = sourceRow.getCell(j);
                        Cell destCell = destRow.createCell(j);

                        if (sourceCell == null) continue;

                        switch (sourceCell.getCellType()) {
                            case STRING -> destCell.setCellValue(sourceCell.getStringCellValue());
                            case NUMERIC -> destCell.setCellValue(sourceCell.getNumericCellValue());
                            case BOOLEAN -> destCell.setCellValue(sourceCell.getBooleanCellValue());
                            case FORMULA -> destCell.setCellFormula(sourceCell.getCellFormula());
                            default -> destCell.setCellValue(sourceCell.toString());
                        }
                    }
                }

            } catch (Exception e) {
                log.error("Failed to combine batch file: {}", partialPath, e);
            }
        }

        try (FileOutputStream fileOut = new FileOutputStream(finalFilePath)) {
            finalWorkbook.write(fileOut);
        } finally {
            finalWorkbook.dispose();
        }

        return finalFilePath;
    }

    @Async
    @Transactional
    public void retryFailedBatches(String jobUniqueId) {
        ExportJob job = exportJobRepository.findByJobUniqueId(jobUniqueId)
                .orElseThrow(() -> new RuntimeException("Export job not found"));

        List<ExportBatch> failedBatches =
                exportBatchRepository.findBatchesForRetry(job.getId(), BatchStatus.FAILED, MAX_RETRIES);

        for (ExportBatch batch : failedBatches) {
            processBatch(batch);
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