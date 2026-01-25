package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.integrations.garmin.GarminClientPort;
import com.bko.fitnessextractor.integrations.garmin.GarminMetrics;
import com.bko.fitnessextractor.integrations.garmin.GarminWellnessSample;
import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.sync.SyncGarminUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
public class SyncGarminService implements SyncGarminUseCase {
    private static final Logger logger = LoggerFactory.getLogger(SyncGarminService.class);
    private static final String SHEET_NAME = "Garmin Metrics";
    private static final String WELLNESS_SHEET_NAME = "Garmin Stress HR";
    private static final int WELLNESS_DEFAULT_DAYS = 30;

    private final SpreadsheetPort spreadsheetPort;
    private final GarminClientPort garminClientPort;
    private final AppSettings appSettings;
    private final Clock clock;

    public SyncGarminService(SpreadsheetPort spreadsheetPort,
                             GarminClientPort garminClientPort,
                             AppSettings appSettings,
                             Clock clock) {
        this.spreadsheetPort = spreadsheetPort;
        this.garminClientPort = garminClientPort;
        this.appSettings = appSettings;
        this.clock = clock;
    }

    @Override
    public SyncReport syncGarmin() {
        SyncReport report = new SyncReport();
        report.setGarminAttempted(true);

        if (!appSettings.isGoogleConfigured()) {
            report.error("Missing Google configuration. Check GOOGLE_SPREADSHEET_ID and GOOGLE_SERVICE_ACCOUNT_KEY_PATH.");
            return report;
        }
        if (!appSettings.isGarminConfigured()) {
            report.warn("Garmin credentials missing, skipping Garmin sync.");
            return report;
        }

        try {
            logger.info("Starting Garmin sync...");
            report.info("Starting Garmin sync...");

            garminClientPort.login();

            LocalDate today = LocalDate.now(clock);
            syncGarminMetrics(report, today);
            try {
                syncGarminWellnessSamples(report, today);
            } catch (Exception e) {
                logger.warn("Garmin stress/HR series sync failed", e);
                report.warn("Garmin stress/HR series sync failed: " + e.getMessage());
            }

            report.info("Garmin sync complete.");
        } catch (Exception e) {
            logger.error("Garmin sync failed", e);
            report.error("Garmin sync failed: " + e.getMessage());
        }

        return report;
    }

    private void syncGarminMetrics(SyncReport report, LocalDate today) throws Exception {
        spreadsheetPort.createSheet(SHEET_NAME);
        spreadsheetPort.ensureHeaders(SHEET_NAME, GarminMetrics.getHeaders());

        List<List<Object>> existingData = null;
        try {
            existingData = spreadsheetPort.getExistingValues(SHEET_NAME + "!A:I");
        } catch (Exception e) {
            logger.warn("Could not fetch existing Garmin data: {}", e.getMessage());
        }

        LocalDate latestDate = null;
        Map<String, Integer> dateToRowIndex = new HashMap<>();

        if (existingData != null) {
            for (int i = 0; i < existingData.size(); i++) {
                List<Object> row = existingData.get(i);
                if (!row.isEmpty()) {
                    String dateStr = row.get(0).toString();
                    dateToRowIndex.putIfAbsent(dateStr, i + 1);
                    LocalDate parsed = tryParseDate(dateStr);
                    if (parsed != null && (latestDate == null || parsed.isAfter(latestDate))) {
                        latestDate = parsed;
                    }
                }
            }
        }

        int daysToFetch;
        if (latestDate != null) {
            long diff = DAYS.between(latestDate, today);
            daysToFetch = (int) diff + 1;
            if (daysToFetch < 1) {
                daysToFetch = 1;
            }
            report.info("Latest date in sheet: " + latestDate + ". Fetching " + daysToFetch + " days.");
        } else {
            daysToFetch = 180;
            report.info("No existing data found. Fetching last " + daysToFetch + " days.");
        }

        List<GarminMetrics> allMetrics = garminClientPort.getMetricsForLastDays(daysToFetch);
        List<List<Object>> newMetrics = new ArrayList<>();

        for (GarminMetrics metrics : allMetrics) {
            String date = metrics.getDate();
            if (dateToRowIndex.containsKey(date)) {
                if (date.equals(today.toString())) {
                    int rowIndex = dateToRowIndex.get(date);
                    spreadsheetPort.updateRow(SHEET_NAME + "!A" + rowIndex, metrics.toRow());
                    report.addGarminUpdated(1);
                }
            } else {
                newMetrics.add(metrics.toRow());
            }
        }

        if (!newMetrics.isEmpty()) {
            spreadsheetPort.insertRowsAtTop(SHEET_NAME, newMetrics);
            report.addGarminInserted(newMetrics.size());
        }
    }

    private void syncGarminWellnessSamples(SyncReport report, LocalDate today) throws Exception {
        spreadsheetPort.createSheet(WELLNESS_SHEET_NAME);
        spreadsheetPort.ensureHeaders(WELLNESS_SHEET_NAME, GarminWellnessSample.getHeaders());

        List<List<Object>> existingData = null;
        try {
            existingData = spreadsheetPort.getExistingValues(WELLNESS_SHEET_NAME + "!A:D");
        } catch (Exception e) {
            logger.warn("Could not fetch existing Garmin stress/HR data: {}", e.getMessage());
        }

        LocalDate latestDate = null;
        Map<String, Integer> timestampToRowIndex = new HashMap<>();

        if (existingData != null) {
            for (int i = 0; i < existingData.size(); i++) {
                List<Object> row = existingData.get(i);
                if (row.isEmpty()) {
                    continue;
                }
                String dateStr = row.size() > 0 ? row.get(0).toString() : "";
                LocalDate parsed = tryParseDate(dateStr);
                if (parsed != null && (latestDate == null || parsed.isAfter(latestDate))) {
                    latestDate = parsed;
                }
                String timestamp = row.size() > 1 ? row.get(1).toString() : "";
                if (!timestamp.isBlank()) {
                    timestampToRowIndex.putIfAbsent(timestamp, i + 1);
                }
            }
        }

        int daysToFetch;
        if (latestDate != null) {
            long diff = DAYS.between(latestDate, today);
            daysToFetch = (int) diff + 1;
            if (daysToFetch < 1) {
                daysToFetch = 1;
            }
            report.info("Latest stress/HR date in sheet: " + latestDate + ". Fetching " + daysToFetch + " days.");
        } else {
            daysToFetch = WELLNESS_DEFAULT_DAYS;
            report.info("No stress/HR data found. Fetching last " + daysToFetch + " days.");
        }

        List<GarminWellnessSample> samples = garminClientPort.getWellnessSamplesForLastDays(daysToFetch);
        if (samples == null || samples.isEmpty()) {
            report.info("No Garmin stress/HR samples returned.");
            return;
        }

        List<GarminWellnessSample> newSamples = new ArrayList<>();
        Map<Integer, List<Object>> updatedRows = new HashMap<>();

        for (GarminWellnessSample sample : samples) {
            String timestamp = sample.getTimestamp();
            if (timestamp == null || timestamp.isBlank()) {
                continue;
            }
            Integer rowIndex = timestampToRowIndex.get(timestamp);
            if (rowIndex != null) {
                if (today.toString().equals(sample.getDate())) {
                    updatedRows.put(rowIndex, sample.toRow());
                }
            } else {
                newSamples.add(sample);
            }
        }

        if (!updatedRows.isEmpty()) {
            // Update before inserting new rows so row indices remain valid.
            spreadsheetPort.updateRows(WELLNESS_SHEET_NAME, updatedRows);
            report.info("Updated " + updatedRows.size() + " stress/HR samples.");
        }

        if (!newSamples.isEmpty()) {
            newSamples.sort(Comparator.comparing(GarminWellnessSample::getTimestamp).reversed());
            List<List<Object>> rows = new ArrayList<>();
            for (GarminWellnessSample sample : newSamples) {
                rows.add(sample.toRow());
            }
            spreadsheetPort.insertRowsAtTop(WELLNESS_SHEET_NAME, rows);
            report.info("Inserted " + newSamples.size() + " stress/HR samples.");
        }
    }

    private LocalDate tryParseDate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (Exception e) {
            return null;
        }
    }
}
