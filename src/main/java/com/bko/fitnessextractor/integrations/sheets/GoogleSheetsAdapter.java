package com.bko.fitnessextractor.integrations.sheets;

import com.bko.fitnessextractor.shared.AppSettings;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesResponse;
import com.google.api.services.sheets.v4.model.DimensionRange;
import com.google.api.services.sheets.v4.model.InsertDimensionRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class GoogleSheetsAdapter implements SpreadsheetPort {
    private static final Logger logger = LoggerFactory.getLogger(GoogleSheetsAdapter.class);
    private static final String APPLICATION_NAME = "FitnessExtractor";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String RAW = "RAW";
    private static final String ROWS = "ROWS";
    private static final int START_INDEX = 1;

    private final AppSettings settings;
    private Sheets sheetsService;

    public GoogleSheetsAdapter(AppSettings settings) {
        this.settings = settings;
    }

    @Override
    public List<List<Object>> getExistingValues(String range) throws IOException {
        try {
            ValueRange response = getSheetsService().spreadsheets().values()
                    .get(getSpreadsheetId(), range)
                    .execute();
            return response.getValues();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 400) {
                logger.warn("Error 400: Range not found ({}). Check sheet name.", range);
                Spreadsheet spreadsheet = getSheetsService().spreadsheets().get(getSpreadsheetId()).execute();
                spreadsheet.getSheets().forEach(s -> logger.info("Available sheet: {}", s.getProperties().getTitle()));
            }
            throw e;
        }
    }

    @Override
    public void appendValues(String range, List<List<Object>> values) throws IOException {
        ValueRange body = new ValueRange().setValues(values);
        AppendValuesResponse result = getSheetsService().spreadsheets().values()
                .append(getSpreadsheetId(), range, body)
                .setValueInputOption(RAW)
                .execute();
        logger.info("Appended {} cells.", result.getUpdates().getUpdatedCells());
    }

    @Override
    public void updateRow(String range, List<Object> values) throws IOException {
        ValueRange body = new ValueRange().setValues(Collections.singletonList(values));
        getSheetsService().spreadsheets().values()
                .update(getSpreadsheetId(), range, body)
                .setValueInputOption(RAW)
                .execute();
    }

    @Override
    public void updateRows(String sheetName, Map<Integer, List<Object>> rows) throws IOException {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        List<ValueRange> data = new java.util.ArrayList<>();
        for (Map.Entry<Integer, List<Object>> entry : rows.entrySet()) {
            Integer rowIndex = entry.getKey();
            if (rowIndex == null || rowIndex < 1) {
                continue;
            }
            List<Object> values = entry.getValue();
            if (values == null) {
                continue;
            }
            String range = sheetName + "!A" + rowIndex;
            data.add(new ValueRange().setRange(range).setValues(Collections.singletonList(values)));
        }

        if (data.isEmpty()) {
            return;
        }

        BatchUpdateValuesRequest request = new BatchUpdateValuesRequest()
                .setValueInputOption(RAW)
                .setData(data);

        BatchUpdateValuesResponse response = getSheetsService().spreadsheets().values()
                .batchUpdate(getSpreadsheetId(), request)
                .execute();

        if (response.getTotalUpdatedCells() != null) {
            logger.info("Batch updated {} cells.", response.getTotalUpdatedCells());
        }
    }

    @Override
    public void insertRowsAtTop(String sheetName, List<List<Object>> values) throws IOException {
        if (values.isEmpty()) {
            return;
        }

        Integer sheetId = getSheetId(sheetName);
        int numRows = values.size();

        Request request = new Request()
                .setInsertDimension(new InsertDimensionRequest()
                        .setRange(new DimensionRange()
                                .setSheetId(sheetId)
                                .setDimension(ROWS)
                                .setStartIndex(START_INDEX)
                                .setEndIndex(START_INDEX + numRows))
                        .setInheritFromBefore(false));

        BatchUpdateSpreadsheetRequest batchRequest =
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(Collections.singletonList(request));

        getSheetsService().spreadsheets().batchUpdate(getSpreadsheetId(), batchRequest).execute();

        ValueRange body = new ValueRange().setValues(values);
        getSheetsService().spreadsheets().values()
                .update(getSpreadsheetId(), sheetName + "!A2", body)
                .setValueInputOption(RAW)
                .execute();

        logger.info("Inserted {} rows at the top of {}.", numRows, sheetName);
    }

    @Override
    public void ensureHeaders(String sheetName, List<Object> headers) throws IOException {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        List<List<Object>> firstRowData = null;
        try {
            firstRowData = getExistingValues(sheetName + "!1:1");
        } catch (IOException e) {
            createSheet(sheetName);
        }

        String firstHeader = headers.get(0).toString();

        if (firstRowData == null || firstRowData.isEmpty() || !firstRowData.get(0).get(0).toString().equalsIgnoreCase(firstHeader)) {
            logger.info("Headers missing or sheet empty in {}. Ensuring headers at the top...", sheetName);

            if (firstRowData == null || firstRowData.isEmpty()) {
                appendValues(sheetName + "!A1", Collections.singletonList(headers));
            } else {
                Integer sheetId = getSheetId(sheetName);

                Request request = new Request()
                        .setInsertDimension(new InsertDimensionRequest()
                                .setRange(new DimensionRange()
                                        .setSheetId(sheetId)
                                        .setDimension(ROWS)
                                        .setStartIndex(0)
                                        .setEndIndex(START_INDEX))
                                .setInheritFromBefore(false));

                BatchUpdateSpreadsheetRequest batchRequest =
                        new BatchUpdateSpreadsheetRequest()
                                .setRequests(Collections.singletonList(request));

                getSheetsService().spreadsheets().batchUpdate(getSpreadsheetId(), batchRequest).execute();

                ValueRange body = new ValueRange().setValues(Collections.singletonList(headers));
                getSheetsService().spreadsheets().values()
                        .update(getSpreadsheetId(), sheetName + "!1:1", body)
                        .setValueInputOption(RAW)
                        .execute();
                logger.info("Inserted headers at the top of the sheet: {}", sheetName);
            }
        } else {
            List<Object> currentHeaders = firstRowData.get(0);
            boolean mismatch = false;
            if (currentHeaders.size() < headers.size()) {
                mismatch = true;
            } else {
                for (int i = 0; i < headers.size(); i++) {
                    String expected = headers.get(i).toString();
                    Object current = currentHeaders.get(i);
                    String actual = current != null ? current.toString() : "";
                    if (!expected.equalsIgnoreCase(actual)) {
                        mismatch = true;
                        break;
                    }
                }
            }

            if (mismatch) {
                logger.info("Headers in {} seem outdated or mismatched. Updating headers...", sheetName);
                ValueRange body = new ValueRange().setValues(Collections.singletonList(headers));
                getSheetsService().spreadsheets().values()
                        .update(getSpreadsheetId(), sheetName + "!1:1", body)
                        .setValueInputOption(RAW)
                        .execute();
            }
        }
    }

    @Override
    public void createSheet(String sheetName) throws IOException {
        Request request = new Request()
                .setAddSheet(new AddSheetRequest()
                        .setProperties(new SheetProperties()
                                .setTitle(sheetName)));

        BatchUpdateSpreadsheetRequest batchRequest =
                new BatchUpdateSpreadsheetRequest()
                        .setRequests(Collections.singletonList(request));

        try {
            getSheetsService().spreadsheets().batchUpdate(getSpreadsheetId(), batchRequest).execute();
            logger.info("Created new sheet: {}", sheetName);
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() != 400) {
                throw e;
            }
        }
    }

    private Integer getSheetId(String sheetName) throws IOException {
        Spreadsheet spreadsheet = getSheetsService().spreadsheets().get(getSpreadsheetId()).execute();
        for (com.google.api.services.sheets.v4.model.Sheet sheet : spreadsheet.getSheets()) {
            if (sheet.getProperties().getTitle().equals(sheetName)) {
                return sheet.getProperties().getSheetId();
            }
        }
        return 0;
    }

    private String getSpreadsheetId() {
        if (!settings.isGoogleConfigured()) {
            throw new IllegalStateException("Missing Google configuration.");
        }
        return settings.google().spreadsheetId();
    }

    private Sheets getSheetsService() throws IOException {
        if (sheetsService == null) {
            try {
                sheetsService = buildSheetsService();
            } catch (GeneralSecurityException e) {
                throw new IOException("Failed to initialize Sheets client", e);
            }
        }
        return sheetsService;
    }

    private Sheets buildSheetsService() throws GeneralSecurityException, IOException {
        if (!settings.isGoogleConfigured()) {
            throw new IllegalStateException("Missing Google configuration.");
        }
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleCredentials credentials;
        try (InputStream serviceAccountStream = openServiceAccountStream()) {
            credentials = GoogleCredentials.fromStream(serviceAccountStream)
                    .createScoped(SCOPES);
        }

        return new Sheets.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private InputStream openServiceAccountStream() throws IOException {
        String path = settings.google().serviceAccountKeyPath();
        if (path == null || path.isBlank()) {
            throw new IOException("Service account key path is missing.");
        }

        if (path.startsWith("classpath:")) {
            String resourcePath = path.substring("classpath:".length());
            return openClasspathResource(resourcePath);
        }

        Path filePath = Path.of(path);
        if (Files.exists(filePath)) {
            return new FileInputStream(filePath.toFile());
        }

        InputStream resourceStream = tryClasspathResource(path);
        if (resourceStream != null) {
            return resourceStream;
        }

        String marker = "src/main/resources/";
        int markerIndex = path.lastIndexOf(marker);
        if (markerIndex >= 0) {
            String resourcePath = path.substring(markerIndex + marker.length());
            return openClasspathResource(resourcePath);
        }

        throw new IOException("Service account key not found at path: " + path);
    }

    private InputStream openClasspathResource(String resourcePath) throws IOException {
        InputStream stream = tryClasspathResource(resourcePath);
        if (stream == null) {
            throw new IOException("Service account resource not found: " + resourcePath);
        }
        return stream;
    }

    private InputStream tryClasspathResource(String resourcePath) {
        String normalized = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        return GoogleSheetsAdapter.class.getResourceAsStream(normalized);
    }
}
