package com.bko.fitnessextractor.visualization.app;

public record RecoverySummary(
        String workoutLabel,
        String workoutEndTimestamp,
        Integer minutesToRecovery,
        String status,
        String guidance
) {
}
