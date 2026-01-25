package com.bko.fitnessextractor.integrations.sheets;

import java.io.IOException;
import java.util.List;

public interface SpreadsheetPort {
    List<List<Object>> getExistingValues(String range) throws IOException;
    void appendValues(String range, List<List<Object>> values) throws IOException;
    void updateRow(String range, List<Object> values) throws IOException;
    void updateRows(String sheetName, java.util.Map<Integer, List<Object>> rows) throws IOException;
    void insertRowsAtTop(String sheetName, List<List<Object>> values) throws IOException;
    void ensureHeaders(String sheetName, List<Object> headers) throws IOException;
    void createSheet(String sheetName) throws IOException;
}
