package org.aps.export_data_v2;

import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.formula.functions.T;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/salary")
public class SalaryController {
    private final ExportExcelService exportExcelService;

    @GetMapping("/export")
    public ResponseEntity<?> exportExcel() throws IOException {
        CompletableFuture<byte[]> excelFuture = exportExcelService.exportUserToExcelParallelProcessing();

        byte[] excelData = excelFuture.join();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data.xlsx")
                .body(excelData);
    }
}
