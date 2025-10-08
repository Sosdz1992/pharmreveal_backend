package com.alibou.security.drug;

import com.alibou.security.drug.DrugExportDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
public class DrugExcelExportService {

    public byte[] exportToExcel(List<DrugExportDto> drugs) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Drugs");

            // Создание заголовков
            createHeader(sheet);

            CreationHelper creationHelper = workbook.getCreationHelper();
            CellStyle dateCellStyle = workbook.createCellStyle();
            dateCellStyle.setDataFormat(creationHelper.createDataFormat().getFormat("yyyy-mm-dd"));

            // Заполнение строк
            int rowIdx = 1;
            for (DrugExportDto dto : drugs) {
                Row row = sheet.createRow(rowIdx++);
                fillRow(row, dto, dateCellStyle);
            }

            // Автоширина всех 25 колонок
            int numberOfColumns = 25;
            for (int i = 0; i < numberOfColumns; i++) {
                sheet.autoSizeColumn(i);
            }

            // Запись в поток
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }


    private void createHeader(Sheet sheet) {
        Row header = sheet.createRow(0);
        String[] columns = {
                "Segment", "Trade Name", "Company", "Form", "Dosage", "Pack Qty", "INN",
                "ATC1", "ATC2", "ATC3", "Import Date", "Year", "License",
                "Interested Stand", "Interested Party", "Rx/OTC", "Mode",
                "SKU", "Volume Units", "Price Lari", "Price USD",
                "Value GEL", "Value USD", "Volume SU", "Price Source"
        };

        Workbook workbook = sheet.getWorkbook();

        // Создание жирного шрифта
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        // Создание стиля с этим шрифтом
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        // Установка заголовков с жирным стилем
        for (int i = 0; i < columns.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(columns[i]);
            cell.setCellStyle(headerStyle);
        }
    }


    private void fillRow(Row row, DrugExportDto d, CellStyle dateCellStyle) {
        int i = 0;
        row.createCell(i++).setCellValue(d.getSegment());
        row.createCell(i++).setCellValue(d.getTradeName());
        row.createCell(i++).setCellValue(d.getManufacturingCompany());
        row.createCell(i++).setCellValue(d.getDrugForm());
        row.createCell(i++).setCellValue(d.getDosage());
        row.createCell(i++).setCellValue(d.getPackQuantity());
        row.createCell(i++).setCellValue(d.getInn());
        row.createCell(i++).setCellValue(d.getAtc1());
        row.createCell(i++).setCellValue(d.getAtc2());
        row.createCell(i++).setCellValue(d.getAtc3());

        Cell dateCell = row.createCell(i++);
        if (d.getImportDate() != null) {
            dateCell.setCellValue(java.util.Date.from(
                    d.getImportDate().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
            dateCell.setCellStyle(dateCellStyle);
        }

//        row.createCell(i++).setCellValue(d.getImportDate() != null ? d.getImportDate().toString() : "");
        row.createCell(i++).setCellValue(d.getYear() != null ? d.getYear() : 0);
        row.createCell(i++).setCellValue(d.getPersonWithTradingLicense());
        row.createCell(i++).setCellValue(d.getPersonInterestedInRegistrationGeorgiaStand());
        row.createCell(i++).setCellValue(d.getInterestedParty());
        row.createCell(i++).setCellValue(d.getRxOtc());
        row.createCell(i++).setCellValue(d.getModeOfRegistration());
        row.createCell(i++).setCellValue(d.getSku());
        row.createCell(i++).setCellValue(getDouble(d.getVolumeInUnits()));
        row.createCell(i++).setCellValue(getDouble(d.getPricePerUnitLari()));
        row.createCell(i++).setCellValue(getDouble(d.getPricePerUnitUsd()));
        row.createCell(i++).setCellValue(getDouble(d.getValueInGel()));
        row.createCell(i++).setCellValue(getDouble(d.getValueInUsd()));
        row.createCell(i++).setCellValue(getDouble(d.getVolumeInSU()));
        row.createCell(i++).setCellValue(d.getPriceSource());
    }

    private double getDouble(java.math.BigDecimal val) {
        return val != null ? val.doubleValue() : 0.0;
    }
}
