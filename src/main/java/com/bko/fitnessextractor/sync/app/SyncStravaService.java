package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.integrations.strava.StravaActivity;
import com.bko.fitnessextractor.integrations.strava.StravaClientPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.sync.SyncStravaUseCase;
import com.bko.fitnessextractor.workout.WorkoutStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SyncStravaService implements SyncStravaUseCase {
    private static final Logger logger = LoggerFactory.getLogger(SyncStravaService.class);
    private static final String SHEET_NAME = "Strava Activities";

    private final SpreadsheetPort spreadsheetPort;
    private final StravaClientPort stravaClientPort;
    private final AppSettings appSettings;
    private final WorkoutStorePort workoutStore;

    public SyncStravaService(SpreadsheetPort spreadsheetPort, StravaClientPort stravaClientPort,
                             AppSettings appSettings, WorkoutStorePort workoutStore) {
        this.spreadsheetPort = spreadsheetPort;
        this.stravaClientPort = stravaClientPort;
        this.appSettings = appSettings;
        this.workoutStore = workoutStore;
    }

    @Override
    public SyncReport syncStrava() {
        SyncReport report = new SyncReport();
        report.setStravaAttempted(true);

        if (!appSettings.isStravaConfigured()) {
            report.warn("Strava credentials missing, skipping Strava sync.");
            return report;
        }

        try {
            logger.info("Starting Strava sync...");
            report.info("Starting Strava sync...");

            List<StravaActivity> allFetched = fetchAllActivities();

            // Persist to local database (dedup happens inside WorkoutStore)
            int dbSaved = workoutStore.saveStravaActivities(allFetched);
            report.addStravaAdded(dbSaved);
            report.info("Saved " + dbSaved + " new activities to local database.");

            // Optionally sync to Google Sheets
            if (appSettings.isGoogleConfigured()) {
                syncToSheets(allFetched, report);
            }

            report.info("Strava sync complete.");
        } catch (Exception e) {
            logger.error("Strava sync failed", e);
            report.error("Strava sync failed: " + e.getMessage());
        }

        return report;
    }

    private List<StravaActivity> fetchAllActivities() throws IOException {
        List<StravaActivity> allActivities = new ArrayList<>();
        int page = 1;
        while (true) {
            List<StravaActivity> pageActivities = stravaClientPort.getActivities(page, 100);
            if (pageActivities.isEmpty()) break;

            for (StravaActivity activity : pageActivities) {
                try {
                    StravaActivity detailed = stravaClientPort.getActivity(activity.getId());
                    allActivities.add(detailed);
                } catch (IOException e) {
                    logger.warn("Could not fetch details for activity {}: {}", activity.getId(), e.getMessage());
                    allActivities.add(activity);
                }
            }

            logger.info("Fetched {} activities on page {}", pageActivities.size(), page);
            if (pageActivities.size() < 100) break;
            page++;
            if (page > 50) break;
        }
        return allActivities;
    }

    private void syncToSheets(List<StravaActivity> activities, SyncReport report) {
        try {
            spreadsheetPort.createSheet(SHEET_NAME);
            List<Object> headers = List.of(
                    "Activity ID", "Name", "Type", "Distance (m)", "Moving Time (s)", "Elapsed Time (s)",
                    "Start Date", "Avg Speed (m/s)", "Max Speed (m/s)", "Elevation Gain (m)",
                    "Avg Heart Rate", "Max Heart Rate", "Avg Watts", "Kilojoules", "Suffer Score", "Description"
            );
            spreadsheetPort.ensureHeaders(SHEET_NAME, headers);

            Set<String> existingIds = new HashSet<>();
            try {
                List<List<Object>> existingData = spreadsheetPort.getExistingValues(SHEET_NAME + "!A:A");
                if (existingData != null) {
                    for (List<Object> row : existingData) {
                        if (!row.isEmpty()) {
                            String id = row.get(0).toString();
                            if (!id.equalsIgnoreCase("Activity ID")) {
                                existingIds.add(id);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.debug("No existing Strava data found in sheet: {}", e.getMessage());
            }

            List<List<Object>> valuesToAppend = new ArrayList<>();
            for (StravaActivity activity : activities) {
                if (existingIds.contains(String.valueOf(activity.getId()))) continue;
                List<Object> row = new ArrayList<>();
                row.add(normalize(activity.getId()));
                row.add(normalize(activity.getName()));
                row.add(normalize(activity.getType()));
                row.add(normalize(activity.getDistance()));
                row.add(normalize(activity.getMovingTime()));
                row.add(normalize(activity.getElapsedTime()));
                row.add(normalize(activity.getStartDate()));
                row.add(normalize(activity.getAverageSpeed()));
                row.add(normalize(activity.getMaxSpeed()));
                row.add(normalize(activity.getTotalElevationGain()));
                row.add(normalize(activity.getAverageHeartrate()));
                row.add(normalize(activity.getMaxHeartrate()));
                row.add(normalize(activity.getAverageWatts()));
                row.add(normalize(activity.getKilojoules()));
                row.add(normalize(activity.getSufferScore()));
                row.add(normalize(activity.getDescription()));
                valuesToAppend.add(row);
            }

            if (!valuesToAppend.isEmpty()) {
                spreadsheetPort.insertRowsAtTop(SHEET_NAME, valuesToAppend);
                report.info("Added " + valuesToAppend.size() + " activities to Google Sheets.");
            }
        } catch (Exception e) {
            logger.warn("Google Sheets sync failed (data saved to local DB)", e);
            report.warn("Google Sheets sync failed: " + e.getMessage());
        }
    }

    private Object normalize(Object value) {
        return value == null ? "" : value;
    }
}
