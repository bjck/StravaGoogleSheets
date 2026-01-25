package com.bko.fitnessextractor.visualization.app;

import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.GarminSettings;
import com.bko.fitnessextractor.shared.GoogleSettings;
import com.bko.fitnessextractor.shared.StravaSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VisualizationServiceTest {

    @Test
    void loadVisualizationSkipsWhenGoogleMissing() {
        AppSettings settings = new AppSettings(
                new StravaSettings("id", "secret", "token"),
                new GarminSettings("user", "pass", null, null, null, null),
                new GoogleSettings(null, null)
        );
        SpreadsheetPort spreadsheetPort = mock(SpreadsheetPort.class);

        VisualizationService service = new VisualizationService(spreadsheetPort, settings);

        VisualizationSnapshot snapshot = service.loadVisualization();

        assertTrue(snapshot.messages().getFirst().contains("Missing Google configuration"));
        verifyNoInteractions(spreadsheetPort);
    }

    @Test
    void loadVisualizationBuildsSummaries() throws Exception {
        AppSettings settings = new AppSettings(
                new StravaSettings("id", "secret", "token"),
                new GarminSettings("user", "pass", null, null, null, null),
                new GoogleSettings("sheet", "key.json")
        );
        SpreadsheetPort spreadsheetPort = mock(SpreadsheetPort.class);

        List<List<Object>> stravaRows = List.of(
                List.of("Activity ID", "Name", "Type", "Distance (m)", "Moving Time (s)", "Elapsed Time (s)", "Start Date"),
                List.of("1", "Morning Ride", "Ride", "10000", "1800", "1900", "2025-01-10T07:00:00Z"),
                List.of("2", "Evening Run", "Run", "5000", "1500", "1600", "2025-01-12")
        );
        List<List<Object>> garminRows = List.of(
                List.of("Date", "Body Battery Max", "Body Battery Min", "Weight (kg)", "VO2 Max", "Resting HR", "Sleep Score", "Sleep Duration (h)", "HRV (ms)"),
                List.of("2025-01-11", "80", "20", "70.5", "52", "48", "75", "7.5", "62"),
                List.of("2025-01-12", "78", "25", "70.4", "52", "47", "80", "8.0", "64")
        );
        List<List<Object>> stressRows = List.of(
                List.of("Date", "Timestamp", "Stress", "Heart Rate"),
                List.of("2025-01-12", "2025-01-12T00:10:00", "35", "60"),
                List.of("2025-01-12", "2025-01-12T00:35:00", "20", "55")
        );

        when(spreadsheetPort.getExistingValues("Strava Activities!A:P")).thenReturn(stravaRows);
        when(spreadsheetPort.getExistingValues("Garmin Metrics!A:I")).thenReturn(garminRows);
        when(spreadsheetPort.getExistingValues("Garmin Stress HR!A:D")).thenReturn(stressRows);

        VisualizationService service = new VisualizationService(spreadsheetPort, settings);

        VisualizationSnapshot snapshot = service.loadVisualization();

        assertTrue(snapshot.messages().isEmpty());
        assertNotNull(snapshot.strava());
        assertEquals(2, snapshot.strava().activityCount());
        assertEquals(15.0, snapshot.strava().totalDistanceKm());
        assertEquals("2025-01-12 - Evening Run", snapshot.strava().latestActivityLabel());
        assertEquals(List.of("2025-01-10", "2025-01-12"), snapshot.strava().chartLabels());
        assertEquals(List.of(10.0, 5.0), snapshot.strava().chartDistancesKm());

        assertNotNull(snapshot.garmin());
        assertEquals("2025-01-12", snapshot.garmin().latestDate());
        assertEquals(78, snapshot.garmin().bodyBatteryMax());
        assertEquals(47, snapshot.garmin().restingHeartRate());
        assertEquals(List.of("2025-01-11", "2025-01-12"), snapshot.garmin().chartLabels());
        assertEquals(List.of(80, 78), snapshot.garmin().bodyBatteryMaxSeries());

        assertNotNull(snapshot.recovery());
        assertEquals("2025-01-12 - Evening Run", snapshot.recovery().workoutLabel());
        assertEquals(8, snapshot.recovery().minutesToRecovery());
        assertEquals("Recovered", snapshot.recovery().status());
    }
}
