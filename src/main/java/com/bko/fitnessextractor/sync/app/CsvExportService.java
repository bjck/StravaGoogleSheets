package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.sync.ExportCsvUseCase;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CsvExportService implements ExportCsvUseCase {
    private static final List<SheetExport> SHEETS = List.of(
            new SheetExport("Strava Activities", "strava_activities.csv"),
            new SheetExport("Garmin Metrics", "garmin_metrics.csv"),
            new SheetExport("Garmin Stress HR", "garmin_stress_hr.csv")
    );

    private final SpreadsheetPort spreadsheetPort;
    private final AppSettings settings;

    public CsvExportService(SpreadsheetPort spreadsheetPort, AppSettings settings) {
        this.spreadsheetPort = spreadsheetPort;
        this.settings = settings;
    }

    @Override
    public byte[] exportAllCsvZip() throws IOException {
        if (!settings.isGoogleConfigured()) {
            throw new IllegalStateException("Missing Google configuration. Check GOOGLE_SPREADSHEET_ID and GOOGLE_SERVICE_ACCOUNT_KEY_PATH.");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(buffer)) {
            for (SheetExport sheet : SHEETS) {
                List<List<Object>> rows = spreadsheetPort.getExistingValues(sheet.sheetName());
                String csv = toCsv(rows);
                zipOut.putNextEntry(new ZipEntry(sheet.fileName()));
                zipOut.write(csv.getBytes(StandardCharsets.UTF_8));
                zipOut.closeEntry();
            }
        }
        return buffer.toByteArray();
    }

    private String toCsv(List<List<Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            if (row != null) {
                for (int col = 0; col < row.size(); col++) {
                    if (col > 0) {
                        builder.append(',');
                    }
                    builder.append(escapeCsv(row.get(col)));
                }
            }
            if (i < rows.size() - 1) {
                builder.append("\r\n");
            }
        }
        return builder.toString();
    }

    private String escapeCsv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        boolean needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!needsQuotes) {
            return text;
        }
        String escaped = text.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private record SheetExport(String sheetName, String fileName) {
    }
}
