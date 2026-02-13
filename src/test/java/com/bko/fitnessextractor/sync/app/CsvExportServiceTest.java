package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.GarminSettings;
import com.bko.fitnessextractor.shared.GoogleSettings;
import com.bko.fitnessextractor.shared.StravaSettings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CsvExportServiceTest {

    @Test
    void exportAllCsvZipBuildsZipWithCsvs() throws Exception {
        SpreadsheetPort spreadsheetPort = mock(SpreadsheetPort.class);
        AppSettings settings = new AppSettings(
                new StravaSettings(null, null, null),
                new GarminSettings(null, null, null, null, null, null),
                new GoogleSettings("sheet-id", "key.json")
        );
        CsvExportService service = new CsvExportService(spreadsheetPort, settings);

        List<List<Object>> stravaRows = List.of(
                List.of("Activity ID", "Name"),
                List.of("1", "Morning Run")
        );
        List<List<Object>> garminRows = List.of(
                List.of("Date", "Note"),
                List.of("2024-01-01", "a,b"),
                List.of("2024-01-02", "quote\"me")
        );
        List<List<Object>> stressRows = List.of(
                List.of("Date", "Stress"),
                List.of("2024-01-01", "25")
        );

        when(spreadsheetPort.getExistingValues("Strava Activities")).thenReturn(stravaRows);
        when(spreadsheetPort.getExistingValues("Garmin Metrics")).thenReturn(garminRows);
        when(spreadsheetPort.getExistingValues("Garmin Stress HR")).thenReturn(stressRows);

        byte[] zip = service.exportAllCsvZip();
        Map<String, String> entries = readZipEntries(zip);

        assertEquals(3, entries.size());
        assertEquals("Activity ID,Name\r\n1,Morning Run", entries.get("strava_activities.csv"));
        assertEquals("Date,Note\r\n2024-01-01,\"a,b\"\r\n2024-01-02,\"quote\"\"me\"", entries.get("garmin_metrics.csv"));
        assertEquals("Date,Stress\r\n2024-01-01,25", entries.get("garmin_stress_hr.csv"));
    }

    private Map<String, String> readZipEntries(byte[] zip) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = zipIn.getNextEntry()) != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                int read;
                while ((read = zipIn.read(chunk)) != -1) {
                    buffer.write(chunk, 0, read);
                }
                entries.put(entry.getName(), new String(buffer.toByteArray(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
