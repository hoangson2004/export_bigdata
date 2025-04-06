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
        Pageable pageable = PageRequest.of(0, 800000);
        List<Salary> allSalaries = salaryRepository.findAll(pageable).getContent();
        ExportExcelUtil.writeUserDataBatch(workbook, allSalaries, 2);
        ExportExcelUtil.autoSizeColumns(workbook);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        workbook.dispose();

        return outputStream.toByteArray();
    }

}
