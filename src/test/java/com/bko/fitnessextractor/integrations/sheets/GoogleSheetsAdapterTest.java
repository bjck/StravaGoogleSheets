package com.bko.fitnessextractor.integrations.sheets;

import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.GarminSettings;
import com.bko.fitnessextractor.shared.GoogleSettings;
import com.bko.fitnessextractor.shared.StravaSettings;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GoogleSheetsAdapterTest {

    @Test
    void getExistingValuesReturnsValues() throws Exception {
        Sheets sheets = mock(Sheets.class);
        Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
        Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
        Sheets.Spreadsheets.Values.Get get = mock(Sheets.Spreadsheets.Values.Get.class);

        when(sheets.spreadsheets()).thenReturn(spreadsheets);
        when(spreadsheets.values()).thenReturn(values);
        when(values.get(eq("sheet-id"), eq("Sheet1!A:A"))).thenReturn(get);
        ValueRange range = new ValueRange().setValues(List.of(List.of("A", "B")));
        when(get.execute()).thenReturn(range);

        GoogleSheetsAdapter adapter = new GoogleSheetsAdapter(settings());
        setField(adapter, "sheetsService", sheets);

        List<List<Object>> result = adapter.getExistingValues("Sheet1!A:A");
        assertEquals("A", result.get(0).get(0));
        assertEquals("B", result.get(0).get(1));
    }

    @Test
    void insertRowsAtTopNoValuesDoesNothing() throws Exception {
        GoogleSheetsAdapter adapter = new GoogleSheetsAdapter(settings());
        setField(adapter, "sheetsService", mock(Sheets.class));

        adapter.insertRowsAtTop("Sheet1", Collections.emptyList());

        verifyNoInteractions(getField(adapter, "sheetsService"));
    }

    @Test
    void insertRowsAtTopInsertsRowsAndUpdates() throws Exception {
        Sheets sheets = mock(Sheets.class);
        Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
        Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
        Sheets.Spreadsheets.Values.Update update = mock(Sheets.Spreadsheets.Values.Update.class);
        Sheets.Spreadsheets.BatchUpdate batchUpdate = mock(Sheets.Spreadsheets.BatchUpdate.class);
        Sheets.Spreadsheets.Get getSpreadsheet = mock(Sheets.Spreadsheets.Get.class);

        when(sheets.spreadsheets()).thenReturn(spreadsheets);
        when(spreadsheets.values()).thenReturn(values);
        when(values.update(eq("sheet-id"), eq("Sheet1!A2"), any(ValueRange.class))).thenReturn(update);
        when(update.setValueInputOption(anyString())).thenReturn(update);
        when(update.execute()).thenReturn(null);
        when(spreadsheets.batchUpdate(eq("sheet-id"), any(BatchUpdateSpreadsheetRequest.class))).thenReturn(batchUpdate);
        when(batchUpdate.execute()).thenReturn(null);

        Spreadsheet spreadsheet = new Spreadsheet().setSheets(List.of(
                new Sheet().setProperties(new SheetProperties().setTitle("Sheet1").setSheetId(123))
        ));
        when(spreadsheets.get(eq("sheet-id"))).thenReturn(getSpreadsheet);
        when(getSpreadsheet.execute()).thenReturn(spreadsheet);

        GoogleSheetsAdapter adapter = new GoogleSheetsAdapter(settings());
        setField(adapter, "sheetsService", sheets);

        adapter.insertRowsAtTop("Sheet1", List.of(List.of("a"), List.of("b")));

        ArgumentCaptor<BatchUpdateSpreadsheetRequest> batchCaptor =
                ArgumentCaptor.forClass(BatchUpdateSpreadsheetRequest.class);
        verify(spreadsheets).batchUpdate(eq("sheet-id"), batchCaptor.capture());
        assertEquals(1, batchCaptor.getValue().getRequests().size());

        verify(values).update(eq("sheet-id"), eq("Sheet1!A2"), any(ValueRange.class));
    }

    @Test
    void ensureHeadersUpdatesWhenMismatch() throws Exception {
        Sheets sheets = mock(Sheets.class);
        Sheets.Spreadsheets spreadsheets = mock(Sheets.Spreadsheets.class);
        Sheets.Spreadsheets.Values values = mock(Sheets.Spreadsheets.Values.class);
        Sheets.Spreadsheets.Values.Get get = mock(Sheets.Spreadsheets.Values.Get.class);
        Sheets.Spreadsheets.Values.Update update = mock(Sheets.Spreadsheets.Values.Update.class);

        when(sheets.spreadsheets()).thenReturn(spreadsheets);
        when(spreadsheets.values()).thenReturn(values);
        when(values.get(eq("sheet-id"), eq("Sheet1!1:1"))).thenReturn(get);
        ValueRange range = new ValueRange().setValues(List.of(List.of("New")));
        when(get.execute()).thenReturn(range);
        when(values.update(eq("sheet-id"), eq("Sheet1!1:1"), any(ValueRange.class))).thenReturn(update);
        when(update.setValueInputOption(anyString())).thenReturn(update);
        when(update.execute()).thenReturn(null);

        GoogleSheetsAdapter adapter = new GoogleSheetsAdapter(settings());
        setField(adapter, "sheetsService", sheets);

        adapter.ensureHeaders("Sheet1", List.of("New", "Extra"));

        verify(values).update(eq("sheet-id"), eq("Sheet1!1:1"), any(ValueRange.class));
    }

    private AppSettings settings() {
        return new AppSettings(
                new StravaSettings(null, null, null),
                new GarminSettings(null, null, null, null, null, null),
                new GoogleSettings("sheet-id", "key.json")
        );
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Sheets getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (Sheets) field.get(target);
    }
}
