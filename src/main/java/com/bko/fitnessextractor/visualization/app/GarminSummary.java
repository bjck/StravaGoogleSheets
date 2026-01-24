package com.bko.fitnessextractor.visualization.app;

import java.util.List;

public record GarminSummary(
        String latestDate,
        Integer bodyBatteryMax,
        Integer bodyBatteryMin,
        Double weight,
        Double vo2Max,
        Integer restingHeartRate,
        Integer sleepScore,
        Double sleepDurationHours,
        List<String> chartLabels,
        List<Integer> bodyBatteryMaxSeries,
        List<Integer> sleepScoreSeries,
        List<Integer> restingHeartRateSeries
) {
}
