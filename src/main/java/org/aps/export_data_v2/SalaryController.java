package org.aps.export_data_v2;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/salary")
public class SalaryController {
    private final ExportExcelService exportExcelService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportExcel() throws IOException {
        byte[] excelData = exportExcelService.exportUserToExcelSingleThreaded();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=data.xlsx")
                .body(excelData);
    }
}
