package com.bko.fitnessextractor.ai.domain;

import com.bko.fitnessextractor.visualization.app.GarminSummary;
import com.bko.fitnessextractor.visualization.app.RecoverySummary;
import com.bko.fitnessextractor.visualization.app.StravaSummary;
import com.bko.fitnessextractor.visualization.app.VisualizationSnapshot;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Lightweight view of the user's fitness data that can be injected into AI prompts or returned via MCP tools.
 */
public record FitnessContext(
        List<String> messages,
        StravaSummary strava,
        GarminSummary garmin,
        RecoverySummary recovery
) {
    public static FitnessContext fromSnapshot(VisualizationSnapshot snapshot) {
        if (snapshot == null) {
            return new FitnessContext(List.of("No data available"), null, null, null);
        }
        List<String> msgs = snapshot.messages() == null ? List.of() : List.copyOf(snapshot.messages());
        return new FitnessContext(msgs, snapshot.strava(), snapshot.garmin(), snapshot.recovery());
    }

    public boolean hasData() {
        return (strava != null) || (garmin != null) || (recovery != null);
    }

    /**
     * Converts the context into a compact string that is easy for the LLM to consume.
     */
    public String toPromptString() {
        StringBuilder sb = new StringBuilder();
        if (!messages.isEmpty()) {
            sb.append("System notes: ").append(String.join("; ", messages)).append("\n");
        }
        if (strava != null) {
            sb.append("Strava summary: ")
              .append(strava.activityCount()).append(" activities; ")
              .append(strava.totalDistanceKm()).append(" km total; ")
              .append(strava.totalMovingHours()).append(" h moving; ")
              .append("latest=").append(strava.latestActivityLabel()).append("; ");
            sb.append("Type counts: ").append(toInlineMap(strava.typeCounts())).append("\n");
        }
        if (garmin != null) {
            sb.append("Garmin summary: date=").append(garmin.latestDate()).append("; ")
              .append("BB max/min=").append(garmin.bodyBatteryMax()).append("/").append(garmin.bodyBatteryMin()).append("; ")
              .append("RHR=").append(garmin.restingHeartRate()).append("; ")
              .append("VO2max=").append(garmin.vo2Max()).append("; ")
              .append("Sleep score=").append(garmin.sleepScore()).append(" duration=").append(garmin.sleepDurationHours()).append("h")
              .append("\n");
        }
        if (recovery != null) {
            sb.append("Recovery: workout=").append(recovery.workoutLabel())
              .append("; ended=").append(recovery.workoutEndTimestamp())
              .append("; minutesToRecovery=").append(recovery.minutesToRecovery())
              .append("; status=").append(recovery.status())
              .append("; guidance=").append(recovery.guidance())
              .append("\n");
        }
        return sb.toString().trim();
    }

    private String toInlineMap(Map<String, Integer> map) {
        if (map == null || map.isEmpty()) return "{}";
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        map.forEach((k, v) -> joiner.add(k + ":" + v));
        return joiner.toString();
    }
}

