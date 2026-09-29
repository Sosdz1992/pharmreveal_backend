package com.alibou.security.drug;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Потоковое чтение первого листа xlsx (SAX): лист не загружается в память целиком,
 * поэтому размер файла не упирается в лимиты XSSFWorkbook.
 */
class DrugExcelReader {

    private static final int COLUMNS = 25;
    private static final DateTimeFormatter IMPORT_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    /**
     * Читает строки листа, передаёт каждый распознанный Drug в consumer.
     *
     * @return ошибки разбора по строкам
     */
    static List<String> read(File file, Consumer<Drug> consumer) throws Exception {
        RowHandler handler = new RowHandler(consumer);

        try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();

            Iterator<InputStream> sheets = reader.getSheetsData();
            if (!sheets.hasNext()) {
                throw new IllegalArgumentException("В файле нет листов");
            }

            try (InputStream sheet = sheets.next()) {
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(styles, null, strings, handler, new RawValueFormatter(), false));
                parser.parse(new InputSource(sheet));
            }
        }

        return handler.errors;
    }

    /**
     * Числа отдаёт без форматирования ячейки (без разделителей и округления),
     * даты — в ISO-формате yyyy-MM-dd.
     */
    private static class RawValueFormatter extends DataFormatter {
        @Override
        public String formatRawCellContents(double value, int formatIndex, String formatString, boolean use1904Windowing) {
            if (DateUtil.isADateFormat(formatIndex, formatString) && DateUtil.isValidExcelDate(value)) {
                return DateUtil.getLocalDateTime(value, use1904Windowing).toLocalDate().toString();
            }
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
    }

    private static class RowHandler implements SheetContentsHandler {

        private final Consumer<Drug> consumer;
        private final List<String> errors = new ArrayList<>();
        private final String[] values = new String[COLUMNS];
        private boolean headerSkipped = false;
        private int lastCol;

        RowHandler(Consumer<Drug> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void startRow(int rowNum) {
            Arrays.fill(values, null);
            lastCol = -1;
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int col = cellReference != null ? new CellReference(cellReference).getCol() : lastCol + 1;
            lastCol = col;
            if (col < COLUMNS) {
                values[col] = formattedValue;
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (!headerSkipped) { // пропускаем заголовок
                headerSkipped = true;
                return;
            }

            int excelRow = rowNum + 1;
            Drug drug;
            try {
                drug = toDrug(excelRow);
            } catch (Exception e) {
                errors.add("Ошибка в строке " + excelRow + ": " + e.getMessage());
                return;
            }
            consumer.accept(drug); // ошибки сохранения не глотаем — они должны откатить импорт
        }

        private Drug toDrug(int rowNum) {
            Drug drug = new Drug();

            drug.setYear(getInt(0, rowNum, "year"));
            drug.setSegment(getString(1));
            drug.setTradeName(getString(2));
            drug.setManufacturingCompany(getString(3));
            drug.setPersonWithTradingLicense(getString(4));
            drug.setPersonInterestedInRegistrationGeorgiaStand(getString(5));
            drug.setInterestedParty(getString(6));
            drug.setRxOtc(getString(7));
            drug.setModeOfRegistration(getString(8));
            drug.setSku(getString(9));
            drug.setDrugForm(getString(10));
            drug.setDosage(getString(11));
            drug.setPackQuantity(getString(12));
            drug.setInn(getString(13));
            drug.setAtc1(getString(14));
            drug.setAtc2(getString(15));
            drug.setAtc3(getString(16));
            drug.setVolumeInUnits(getBigDecimal(17, rowNum, "volumeInUnits"));
            drug.setPricePerUnitLari(getBigDecimal(18, rowNum, "pricePerUnitLari"));
            drug.setPricePerUnitUsd(getBigDecimal(19, rowNum, "pricePerUnitUsd"));
            drug.setValueInGel(getBigDecimal(20, rowNum, "valueInGel"));
            drug.setValueInUsd(getBigDecimal(21, rowNum, "valueInUsd"));
            drug.setVolumeInSU(getBigDecimal(22, rowNum, "volumeInSU"));
            drug.setImportDate(getDate(23, rowNum, "importDate"));
            drug.setPriceSource(getString(24));

            return drug;
        }

        private String getString(int index) {
            String value = values[index];
            return value == null ? null : value.trim();
        }

        private int getInt(int index, int rowNum, String field) {
            return getBigDecimal(index, rowNum, field).intValue();
        }

        private BigDecimal getBigDecimal(int index, int rowNum, String field) {
            try {
                return new BigDecimal(values[index].trim());
            } catch (Exception e) {
                throw fieldError(field, rowNum, "не число: " + values[index]);
            }
        }

        private LocalDate getDate(int index, int rowNum, String field) {
            String value = getString(index);
            if (value == null || value.isEmpty()) return null;
            try {
                return LocalDate.parse(value); // дата-ячейка (см. RawValueFormatter)
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(value, IMPORT_DATE_FORMAT); // дата строкой
            } catch (DateTimeParseException e) {
                throw fieldError(field, rowNum, e.getMessage());
            }
        }

        private RuntimeException fieldError(String field, int rowNum, String message) {
            return new RuntimeException("Ошибка в поле [" + field + "] (строка " + rowNum + "): " + message);
        }
    }
}
