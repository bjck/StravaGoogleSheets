package com.bko.fitnessextractor.visualization.app;

import java.util.List;

public record VisualizationSnapshot(
        List<String> messages,
        StravaSummary strava,
        GarminSummary garmin
) {
    public boolean hasMessages() {
        return messages != null && !messages.isEmpty();
    }
}
