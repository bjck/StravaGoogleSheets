package com.bko.fitnessextractor.integrations.strava;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StravaActivityTest {

    @Test
    void settersAndGettersRoundTrip() {
        StravaActivity activity = new StravaActivity();
        activity.setId(123L);
        activity.setName("Morning Run");
        activity.setType("Run");
        activity.setDistance(5000.0);
        activity.setMovingTime(1500);
        activity.setElapsedTime(1600);
        activity.setTotalElevationGain(80.5);
        activity.setStartDate("2024-01-03T08:30:00Z");
        activity.setAverageSpeed(3.3);
        activity.setMaxSpeed(5.4);
        activity.setAverageHeartrate(145.2);
        activity.setMaxHeartrate(170.0);
        activity.setAverageWatts(210.0);
        activity.setKilojoules(550.0);
        activity.setSufferScore(75);
        activity.setDescription("Nice run.");

        assertEquals(123L, activity.getId());
        assertEquals("Morning Run", activity.getName());
        assertEquals("Run", activity.getType());
        assertEquals(5000.0, activity.getDistance());
        assertEquals(1500, activity.getMovingTime());
        assertEquals(1600, activity.getElapsedTime());
        assertEquals(80.5, activity.getTotalElevationGain());
        assertEquals("2024-01-03T08:30:00Z", activity.getStartDate());
        assertEquals(3.3, activity.getAverageSpeed());
        assertEquals(5.4, activity.getMaxSpeed());
        assertEquals(145.2, activity.getAverageHeartrate());
        assertEquals(170.0, activity.getMaxHeartrate());
        assertEquals(210.0, activity.getAverageWatts());
        assertEquals(550.0, activity.getKilojoules());
        assertEquals(75, activity.getSufferScore());
        assertEquals("Nice run.", activity.getDescription());
    }
}
