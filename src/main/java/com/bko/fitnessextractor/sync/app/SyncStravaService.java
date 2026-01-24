package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.integrations.strava.StravaActivity;
import com.bko.fitnessextractor.integrations.strava.StravaClientPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.sync.SyncStravaUseCase;
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

    public SyncStravaService(SpreadsheetPort spreadsheetPort, StravaClientPort stravaClientPort, AppSettings appSettings) {
        this.spreadsheetPort = spreadsheetPort;
        this.stravaClientPort = stravaClientPort;
        this.appSettings = appSettings;
    }

    @Override
    public SyncReport syncStrava() {
        SyncReport report = new SyncReport();
        report.setStravaAttempted(true);

        if (!appSettings.isGoogleConfigured()) {
            report.error("Missing Google configuration. Check GOOGLE_SPREADSHEET_ID and GOOGLE_SERVICE_ACCOUNT_KEY_PATH.");
            return report;
        }
        if (!appSettings.isStravaConfigured()) {
            report.warn("Strava credentials missing, skipping Strava sync.");
            return report;
        }

        try {
            logger.info("Starting Strava sync...");
            report.info("Starting Strava sync...");
            spreadsheetPort.createSheet(SHEET_NAME);

            List<List<Object>> existingData = null;
            try {
                existingData = spreadsheetPort.getExistingValues(SHEET_NAME + "!A:A");
            } catch (Exception e) {
                logger.debug("No existing Strava data found or sheet new: {}", e.getMessage());
            }

            List<Object> headers = List.of(
                    "Activity ID", "Name", "Type", "Distance (m)", "Moving Time (s)", "Elapsed Time (s)",
                    "Start Date", "Avg Speed (m/s)", "Max Speed (m/s)", "Elevation Gain (m)",
                    "Avg Heart Rate", "Max Heart Rate", "Avg Watts", "Kilojoules", "Suffer Score", "Description"
            );
            spreadsheetPort.ensureHeaders(SHEET_NAME, headers);

            Set<String> existingIds = new HashSet<>();
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

            List<StravaActivity> newActivities = new ArrayList<>();
            int page = 1;
            while (true) {
                List<StravaActivity> pageActivities = stravaClientPort.getActivities(page, 100);
                if (pageActivities.isEmpty()) {
                    break;
                }

                int addedInPage = 0;
                for (StravaActivity activity : pageActivities) {
                    if (!existingIds.contains(String.valueOf(activity.getId()))) {
                        try {
                            StravaActivity detailed = stravaClientPort.getActivity(activity.getId());
                            newActivities.add(detailed);
                        } catch (IOException e) {
                            logger.warn("Could not fetch details for activity {}: {}", activity.getId(), e.getMessage());
                            newActivities.add(activity);
                        }
                        addedInPage++;
                    }
                }

                logger.info("Found {} new activities on page {}", addedInPage, page);
                if (addedInPage == 0 && !existingIds.isEmpty()) {
                    break;
                }
                if (pageActivities.size() < 100) {
                    break;
                }
                page++;
                if (page > 50) {
                    break;
                }
            }

            if (newActivities.isEmpty()) {
                report.info("No new Strava activities to sync.");
                return report;
            }

            List<List<Object>> valuesToAppend = new ArrayList<>();
            for (StravaActivity activity : newActivities) {
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

            spreadsheetPort.insertRowsAtTop(SHEET_NAME, valuesToAppend);
            report.addStravaAdded(newActivities.size());
            report.info("Strava sync complete. Added " + newActivities.size() + " activities.");
        } catch (Exception e) {
            logger.error("Strava sync failed", e);
            report.error("Strava sync failed: " + e.getMessage());
        }

        return report;
    }

    private Object normalize(Object value) {
        return value == null ? "" : value;
    }
}
