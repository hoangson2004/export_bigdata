package org.aps.export_data_v2;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ExportExcelService {
    private final SalaryRepository salaryRepository;
    private final Executor asyncExecutor;

    public CompletableFuture<byte[]> exportUserToExcelParallelProcessing() throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(900000);
        ExportExcelUtil.createHeaderRow(workbook);

        int batchSize = 10000;
        long totalRecords = salaryRepository.count();
        int totalBatches = (int) Math.ceil((double) 800000 / batchSize);

        List<CompletableFuture<Void>> futuresList = IntStream.range(0, totalBatches)
                .mapToObj(batchIndex -> CompletableFuture.supplyAsync(() -> {
                            try {
                                Pageable pageable = PageRequest.of(batchIndex, batchSize);
                                return salaryRepository.findAll(pageable).getContent();
                            } catch (Exception e) {
                                List<Salary> errorList = new ArrayList<>();
                                Salary errorSalary = new Salary(-1,0,null,null);
                                errorList.add(errorSalary);
                                return errorList;
                            }
                        }, asyncExecutor)
                        .thenAcceptAsync(batch -> {
                            synchronized (workbook) {
                                ExportExcelUtil.writeUserDataBatch(workbook, batch, batchIndex * batchSize + 2);
                            }
                        }, asyncExecutor))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(futuresList.toArray(new CompletableFuture[0]))
                .thenApplyAsync(v -> {
                    try {
                        ExportExcelUtil.autoSizeColumns(workbook);

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        workbook.write(outputStream);
                        workbook.close();
                        workbook.dispose();

                        return outputStream.toByteArray();
                    } catch (IOException e) {
                        throw new RuntimeException("Export Error", e);
                    }
                }, asyncExecutor);
    }


}
