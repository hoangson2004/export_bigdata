package org.aps.export_data_v2;

import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportExcelService {
    private final SalaryRepository salaryRepository;

    public byte[] exportUserToExcelSingleThreaded() throws IOException {
        SXSSFWorkbook workbook = new SXSSFWorkbook(900000);
        ExportExcelUtil.createHeaderRow(workbook);

        int batchSize = 10000;
        long totalRecords = salaryRepository.count();
        int totalBatches = (int) Math.ceil((double) totalRecords / batchSize);

        for (int batchIndex = 0; batchIndex < totalBatches; batchIndex++) {
            Pageable pageable = PageRequest.of(batchIndex, batchSize);
            List<Salary> batch = salaryRepository.findAll(pageable).getContent();
            ExportExcelUtil.writeUserDataBatch(workbook, batch, batchIndex * batchSize + 2);
        }

        ExportExcelUtil.autoSizeColumns(workbook);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        workbook.dispose();

        return outputStream.toByteArray();
    }
}
