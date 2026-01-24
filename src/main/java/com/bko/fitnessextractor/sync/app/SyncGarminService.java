package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.integrations.garmin.GarminClientPort;
import com.bko.fitnessextractor.integrations.garmin.GarminMetrics;
import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.sync.SyncGarminUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.temporal.ChronoUnit.DAYS;

@Service
public class SyncGarminService implements SyncGarminUseCase {
    private static final Logger logger = LoggerFactory.getLogger(SyncGarminService.class);
    private static final String SHEET_NAME = "Garmin Metrics";

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

            spreadsheetPort.createSheet(SHEET_NAME);
            spreadsheetPort.ensureHeaders(SHEET_NAME, GarminMetrics.getHeaders());

            List<List<Object>> existingData = null;
            try {
                existingData = spreadsheetPort.getExistingValues(SHEET_NAME + "!A:H");
            } catch (Exception e) {
                logger.warn("Could not fetch existing Garmin data: {}", e.getMessage());
            }

            LocalDate today = LocalDate.now(clock);
            LocalDate latestDate = null;
            Map<String, Integer> dateToRowIndex = new HashMap<>();

            if (existingData != null) {
                for (int i = 0; i < existingData.size(); i++) {
                    List<Object> row = existingData.get(i);
                    if (!row.isEmpty()) {
                        String dateStr = row.get(0).toString();
                        dateToRowIndex.putIfAbsent(dateStr, i + 1);
                        try {
                            LocalDate parsed = LocalDate.parse(dateStr);
                            if (latestDate == null || parsed.isAfter(latestDate)) {
                                latestDate = parsed;
                            }
                        } catch (Exception e) {
                            logger.debug("Could not parse date from row {}: {}", i + 1, e.getMessage());
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

            report.info("Garmin sync complete.");
        } catch (Exception e) {
            logger.error("Garmin sync failed", e);
            report.error("Garmin sync failed: " + e.getMessage());
        }

        return report;
    }
}
