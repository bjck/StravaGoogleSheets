package com.bko.fitnessextractor.integrations.garmin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GarminMetricsTest {

    @Test
    void toRowUsesEmptyStringsForNulls() {
        GarminMetrics metrics = new GarminMetrics();
        metrics.setDate("2024-01-02");
        metrics.setBodyBatteryHighest(90);
        metrics.setSleepDurationHours(7.5);

        List<Object> row = metrics.toRow();

        assertEquals("2024-01-02", row.get(0));
        assertEquals(90, row.get(1));
        assertEquals("", row.get(2));
        assertEquals("", row.get(3));
        assertEquals("", row.get(4));
        assertEquals("", row.get(5));
        assertEquals("", row.get(6));
        assertEquals(7.5, row.get(7));
    }

    @Test
    void headersMatchExpectedOrder() {
        List<Object> headers = GarminMetrics.getHeaders();
        assertEquals(List.of(
                "Date", "Body Battery Max", "Body Battery Min", "Weight (kg)",
                "VO2 Max", "Resting HR", "Sleep Score", "Sleep Duration (h)"
        ), headers);
    }
}
