package org.aps.export_data_v2;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.aps.export_data_v2.entity.ExportBatch;
import org.aps.export_data_v2.entity.Salary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ExportExcelUtil {
    private XSSFWorkbook workbook;
    private XSSFSheet sheet;
    private List<Salary> list;

    private static final ReentrantLock lock = new ReentrantLock();

    public ExportExcelUtil(List<Salary> list) {
        this.list = list;
        workbook = new XSSFWorkbook();
    }

    private static void createCell(Row row, int columnCount, Object value, CellStyle style) {
        Cell cell = row.createCell(columnCount);
        if (value instanceof Integer) {
            cell.setCellValue((Integer) value);
        } else if (value instanceof Double) {
            cell.setCellValue((Double) value);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof Long) {
            cell.setCellValue((Long) value);
        } else if (value instanceof Timestamp) {
            Timestamp timestamp = (Timestamp) value;
            String formattedDate = timestamp.toLocalDateTime()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            cell.setCellValue(formattedDate);
        } else {
            cell.setCellValue(String.valueOf(value));
        }
        cell.setCellStyle(style);
    }

    public static void createHeaderRow(SXSSFWorkbook workbook) {
        SXSSFSheet sheet = workbook.createSheet("Salary Information");
        Row row = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setBold(true);
        font.setFontHeight(20);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        createCell(row, 0, "Salary Information", style);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        row = sheet.createRow(1);
        font.setBold(true);
        font.setFontHeight(16);
        style.setFont(font);
        createCell(row, 0, "Employee ID", style);
        createCell(row, 1, "Salary", style);
        createCell(row, 2, "From Date", style);
        createCell(row, 3, "To Date", style);
    }

    public static void writeUserDataBatch(SXSSFWorkbook workbook, List<Salary> salaryBatch, int startRow) {
        SXSSFSheet sheet = workbook.getSheet("Salary Information");
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setFontHeight(14);
        style.setFont(font);

        int rowIndex = startRow;
        for (Salary salary : salaryBatch) {
            Row row = sheet.createRow(rowIndex++);
            int columnCount = 0;
            if (salary.getEmpNo() == -1) {
                createCell(row, columnCount++, "ERROR", style);
                createCell(row, columnCount++, "ERROR", style);
                createCell(row, columnCount++, "ERROR", style);
                createCell(row, columnCount++, "ERROR", style);
            } else {
                createCell(row, columnCount++, salary.getEmpNo(), style);
                createCell(row, columnCount++, salary.getSalary(), style);
                createCell(row, columnCount++, salary.getFromDate(), style);
                createCell(row, columnCount++, salary.getToDate(), style);
            }
        }
    }


    public static void createHeaderRow(SXSSFWorkbook workbook, SXSSFSheet sheet) {
        Row row = sheet.createRow(0);
        CellStyle style = workbook.createCellStyle();
        XSSFFont font = (XSSFFont) workbook.createFont();
        font.setBold(true);
        font.setFontHeight(20);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        createCell(row, 0, "Salary Information", style);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));

        row = sheet.createRow(1);
        font.setBold(true);
        font.setFontHeight(16);
        style.setFont(font);
        createCell(row, 0, "Employee ID", style);
        createCell(row, 1, "Salary", style);
        createCell(row, 2, "From Date", style);
        createCell(row, 3, "To Date", style);
    }


    public static String zipExcelFiles(List<ExportBatch> completedBatches, String jobUniqueId, String basePath) throws IOException {
        String zipFileName = jobUniqueId + "_final.zip";
        String zipFilePath = basePath + File.separator + jobUniqueId + File.separator + zipFileName;

        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            for (ExportBatch batch : completedBatches) {
                String batchFilePath = batch.getPartialFilePath();  // Đường dẫn tới file Excel của batch
                if (batchFilePath != null) {
                    File batchFile = new File(batchFilePath);
                    if (batchFile.exists()) {
                        try (FileInputStream fis = new FileInputStream(batchFile)) {
                            ZipEntry zipEntry = new ZipEntry(batchFile.getName());
                            zos.putNextEntry(zipEntry);

                            byte[] buffer = new byte[1024];
                            int length;
                            while ((length = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, length);
                            }

                            zos.closeEntry();
                        }
                    }
                }
            }
        }

        return zipFilePath;
    }
}