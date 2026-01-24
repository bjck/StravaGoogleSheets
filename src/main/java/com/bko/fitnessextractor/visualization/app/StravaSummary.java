package com.bko.fitnessextractor.visualization.app;

import java.util.List;
import java.util.Map;

public record StravaSummary(
        int activityCount,
        double totalDistanceKm,
        double totalMovingHours,
        double averageDistanceKm,
        String latestActivityLabel,
        List<String> chartLabels,
        List<Double> chartDistancesKm,
        Map<String, Integer> typeCounts
) {
}
