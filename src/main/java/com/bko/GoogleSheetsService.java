package com.bko;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;

public class GoogleSheetsService {
    private static final String APPLICATION_NAME = "FitnessExtractor";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);

    private final String spreadsheetId;
    private final Sheets sheetsService;

    public GoogleSheetsService(String spreadsheetId, String keyFilePath) throws GeneralSecurityException, IOException {
        this.spreadsheetId = spreadsheetId;
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        // Load the service account JSON file
        FileInputStream serviceAccountStream = new FileInputStream(keyFilePath);
        GoogleCredentials credentials = GoogleCredentials.fromStream(serviceAccountStream)
                .createScoped(SCOPES);

        this.sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    public String getFirstSheetName() throws IOException {
        com.google.api.services.sheets.v4.model.Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        if (spreadsheet.getSheets() != null && !spreadsheet.getSheets().isEmpty()) {
            return spreadsheet.getSheets().get(0).getProperties().getTitle();
        }
        return "Sheet1"; // fallback
    }

    public List<List<Object>> getExistingValues(String range) throws IOException {
        try {
            ValueRange response = sheetsService.spreadsheets().values()
                    .get(spreadsheetId, range)
                    .execute();
            return response.getValues();
        } catch (com.google.api.client.googleapis.json.GoogleJsonResponseException e) {
            if (e.getStatusCode() == 400) {
                System.err.println("Error 400: Range not found (" + range + "). Please check if your sheet name is correct.");
                // Try to help by listing available sheet names
                com.google.api.services.sheets.v4.model.Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
                System.out.println("Available sheets in this spreadsheet:");
                spreadsheet.getSheets().forEach(s -> System.out.println("- " + s.getProperties().getTitle()));
            }
            throw e;
        }
    }

    public void appendActivities(String range, List<List<Object>> values) throws IOException {
        ValueRange body = new ValueRange().setValues(values);
        AppendValuesResponse result = sheetsService.spreadsheets().values()
                .append(spreadsheetId, range, body)
                .setValueInputOption("RAW")
                .execute();
        System.out.println("Appended " + result.getUpdates().getUpdatedCells() + " cells.");
    }

    public void insertActivitiesAtTop(String sheetName, List<List<Object>> values) throws IOException {
        if (values.isEmpty()) return;

        Integer sheetId = getSheetId(sheetName);
        int numRows = values.size();

        // 1. Insert empty rows at the top (after the header, which is at index 0)
        com.google.api.services.sheets.v4.model.Request request = new com.google.api.services.sheets.v4.model.Request()
                .setInsertDimension(new com.google.api.services.sheets.v4.model.InsertDimensionRequest()
                        .setRange(new com.google.api.services.sheets.v4.model.DimensionRange()
                                .setSheetId(sheetId)
                                .setDimension("ROWS")
                                .setStartIndex(1) // Row 2
                                .setEndIndex(1 + numRows))
                        .setInheritFromBefore(false));

        com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest batchRequest =
                new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                        .setRequests(Collections.singletonList(request));

        sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

        // 2. Fill the new rows with data
        ValueRange body = new ValueRange().setValues(values);
        sheetsService.spreadsheets().values()
                .update(spreadsheetId, sheetName + "!A2", body)
                .setValueInputOption("RAW")
                .execute();

        System.out.println("Inserted " + numRows + " rows at the top (below header).");
    }

    public void ensureHeaders(String sheetName, List<Object> headers) throws IOException {
        List<List<Object>> firstRow = null;
        try {
            firstRow = getExistingValues(sheetName + "!1:1");
        } catch (IOException e) {
            // Ignore if range not found or other issues, we'll try to insert anyway if it's empty
        }

        if (firstRow == null || firstRow.isEmpty() || !firstRow.get(0).get(0).toString().equalsIgnoreCase("Activity ID")) {
            System.out.println("Headers missing or sheet empty. Ensuring headers at the top...");
            
            if (firstRow == null || firstRow.isEmpty()) {
                // Sheet is empty, just append headers
                appendActivities(sheetName + "!A1", Collections.singletonList(headers));
            } else {
                // Sheet has data but no headers. Insert a row at the top.
                Integer sheetId = getSheetId(sheetName);
                
                com.google.api.services.sheets.v4.model.Request request = new com.google.api.services.sheets.v4.model.Request()
                        .setInsertDimension(new com.google.api.services.sheets.v4.model.InsertDimensionRequest()
                                .setRange(new com.google.api.services.sheets.v4.model.DimensionRange()
                                        .setSheetId(sheetId)
                                        .setDimension("ROWS")
                                        .setStartIndex(0)
                                        .setEndIndex(1))
                                .setInheritFromBefore(false));

                com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest batchRequest = 
                        new com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest()
                                .setRequests(Collections.singletonList(request));

                sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();

                // Update the newly inserted row with headers
                ValueRange body = new ValueRange().setValues(Collections.singletonList(headers));
                sheetsService.spreadsheets().values()
                        .update(spreadsheetId, sheetName + "!1:1", body)
                        .setValueInputOption("RAW")
                        .execute();
                System.out.println("Inserted headers at the top of the sheet.");
            }
        }
    }

    private Integer getSheetId(String sheetName) throws IOException {
        com.google.api.services.sheets.v4.model.Spreadsheet spreadsheet = sheetsService.spreadsheets().get(spreadsheetId).execute();
        for (com.google.api.services.sheets.v4.model.Sheet sheet : spreadsheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(sheetName)) {
                return sheet.getProperties().getSheetId();
            }
        }
        return 0;
    }
}
