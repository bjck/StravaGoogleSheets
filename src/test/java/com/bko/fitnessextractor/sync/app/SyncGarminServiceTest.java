package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.integrations.garmin.GarminClientPort;
import com.bko.fitnessextractor.integrations.garmin.GarminMetrics;
import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.GarminSettings;
import com.bko.fitnessextractor.shared.GoogleSettings;
import com.bko.fitnessextractor.shared.StravaSettings;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncGarminServiceTest {

    @Test
    void syncGarminUpdatesTodayAndInsertsNew() throws Exception {
        LocalDate today = LocalDate.of(2024, 1, 5);
        Clock clock = Clock.fixed(Instant.parse("2024-01-05T10:00:00Z"), ZoneOffset.UTC);

        AppSettings settings = new AppSettings(
                new StravaSettings(null, null, null),
                new GarminSettings("user", "pass", null, null, null, null),
                new GoogleSettings("sheet", "key.json")
        );
        SpreadsheetPort spreadsheetPort = mock(SpreadsheetPort.class);
        GarminClientPort garminClientPort = mock(GarminClientPort.class);

        when(spreadsheetPort.getExistingValues("Garmin Metrics!A:H"))
                .thenReturn(List.of(List.of(today.toString())));

        GarminMetrics todayMetrics = new GarminMetrics();
        todayMetrics.setDate(today.toString());
        GarminMetrics olderMetrics = new GarminMetrics();
        olderMetrics.setDate(today.minusDays(1).toString());

        when(garminClientPort.getMetricsForLastDays(1)).thenReturn(List.of(todayMetrics, olderMetrics));

        SyncGarminService service = new SyncGarminService(spreadsheetPort, garminClientPort, settings, clock);

        SyncReport report = service.syncGarmin();

        assertEquals(1, report.getGarminUpdated());
        assertEquals(1, report.getGarminInserted());
        verify(spreadsheetPort).createSheet("Garmin Metrics");
        verify(spreadsheetPort).ensureHeaders(eq("Garmin Metrics"), any());
        verify(spreadsheetPort).updateRow(eq("Garmin Metrics!A1"), any());
        verify(spreadsheetPort).insertRowsAtTop(eq("Garmin Metrics"), any());
        verify(garminClientPort).login();
    }
}
