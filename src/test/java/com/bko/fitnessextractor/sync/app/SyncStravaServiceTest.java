package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.integrations.strava.StravaActivity;
import com.bko.fitnessextractor.integrations.strava.StravaClientPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.GarminSettings;
import com.bko.fitnessextractor.shared.GoogleSettings;
import com.bko.fitnessextractor.shared.StravaSettings;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SyncStravaServiceTest {

    @Test
    void syncStravaSkipsWhenMissingConfig() {
        AppSettings settings = new AppSettings(
                new StravaSettings(null, null, null),
                new GarminSettings(null, null, null, null, null, null),
                new GoogleSettings("sheet", "key.json")
        );
        SpreadsheetPort spreadsheetPort = mock(SpreadsheetPort.class);
        StravaClientPort stravaClientPort = mock(StravaClientPort.class);

        SyncStravaService service = new SyncStravaService(spreadsheetPort, stravaClientPort, settings);

        SyncReport report = service.syncStrava();

        assertTrue(report.getMessages().get(0).contains("Strava credentials missing"));
        verifyNoInteractions(spreadsheetPort);
        verifyNoInteractions(stravaClientPort);
    }

    @Test
    void syncStravaInsertsNewActivities() throws Exception {
        AppSettings settings = new AppSettings(
                new StravaSettings("id", "secret", "token"),
                new GarminSettings(null, null, null, null, null, null),
                new GoogleSettings("sheet", "key.json")
        );
        SpreadsheetPort spreadsheetPort = mock(SpreadsheetPort.class);
        StravaClientPort stravaClientPort = mock(StravaClientPort.class);

        StravaActivity summary = new StravaActivity();
        summary.setId(1L);
        summary.setName("Ride");
        summary.setType("Ride");

        StravaActivity detail = new StravaActivity();
        detail.setId(1L);
        detail.setName("Ride");
        detail.setType("Ride");

        when(stravaClientPort.getActivities(1, 100)).thenReturn(List.of(summary));
        when(stravaClientPort.getActivity(1L)).thenReturn(detail);
        when(spreadsheetPort.getExistingValues("Strava Activities!A:A"))
                .thenReturn(Collections.singletonList(List.of("Activity ID")));

        SyncStravaService service = new SyncStravaService(spreadsheetPort, stravaClientPort, settings);

        SyncReport report = service.syncStrava();

        assertEquals(1, report.getStravaAdded());
        verify(spreadsheetPort).createSheet("Strava Activities");
        verify(spreadsheetPort).ensureHeaders(eq("Strava Activities"), any());
        verify(spreadsheetPort).insertRowsAtTop(eq("Strava Activities"), any());
    }
}
