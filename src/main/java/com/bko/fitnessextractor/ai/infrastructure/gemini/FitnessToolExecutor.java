package com.bko.fitnessextractor.ai.infrastructure.gemini;

import com.bko.fitnessextractor.workout.WorkoutStorePort;
import com.bko.fitnessextractor.workout.domain.GarminDailyEntity;
import com.bko.fitnessextractor.workout.domain.WorkoutEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class FitnessToolExecutor {

    private final WorkoutStorePort workoutStore;

    public FitnessToolExecutor(WorkoutStorePort workoutStore) {
        this.workoutStore = workoutStore;
    }

    public Map<String, Object> execute(String functionName, Map<String, Object> args) {
        return switch (functionName) {
            case "list_activity_types" -> listActivityTypes();
            case "get_workout_overview" -> getWorkoutOverview();
            case "get_recent_workouts" -> getRecentWorkouts(args);
            case "get_workouts_by_type" -> getWorkoutsByType(args);
            case "get_workouts_by_date_range" -> getWorkoutsByDateRange(args);
            case "get_garmin_summary" -> getGarminSummary(args);
            case "get_garmin_by_date_range" -> getGarminByDateRange(args);
            default -> Map.of("error", "Unknown function: " + functionName);
        };
    }

    private Map<String, Object> listActivityTypes() {
        List<String> types = workoutStore.getActivityTypes();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("types", types);
        result.put("count", types.size());
        return result;
    }

    private Map<String, Object> getWorkoutOverview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalWorkouts", workoutStore.getWorkoutCount());

        Instant earliest = workoutStore.getEarliestWorkoutDate();
        Instant latest = workoutStore.getLatestWorkoutDate();
        result.put("earliestDate", earliest != null ? earliest.toString() : "none");
        result.put("latestDate", latest != null ? latest.toString() : "none");

        List<Object[]> summaries = workoutStore.getWorkoutSummaryByType();
        List<Map<String, Object>> typeSummaries = summaries.stream().map(row -> {
            Map<String, Object> ts = new LinkedHashMap<>();
            ts.put("type", row[0]);
            ts.put("count", row[1]);
            Double totalDist = (Double) row[2];
            if (totalDist != null) ts.put("totalDistanceKm", Math.round(totalDist / 100.0) / 10.0);
            Long totalSec = row[3] != null ? ((Number) row[3]).longValue() : null;
            if (totalSec != null) ts.put("totalHours", Math.round(totalSec / 36.0) / 100.0);
            return ts;
        }).collect(Collectors.toList());
        result.put("byType", typeSummaries);
        return result;
    }

    private Map<String, Object> getRecentWorkouts(Map<String, Object> args) {
        int limit = getInt(args, "limit", 10);
        List<WorkoutEntity> workouts = workoutStore.getRecentWorkouts(limit);
        return Map.of("workouts", workouts.stream().map(WorkoutEntity::toCompactString).collect(Collectors.toList()));
    }

    private Map<String, Object> getWorkoutsByType(Map<String, Object> args) {
        String type = getString(args, "type");
        if (type == null) return Map.of("error", "type parameter is required");
        List<WorkoutEntity> workouts = workoutStore.getWorkoutsByType(type);
        return Map.of(
                "type", type,
                "count", workouts.size(),
                "workouts", workouts.stream().map(WorkoutEntity::toCompactString).collect(Collectors.toList())
        );
    }

    private Map<String, Object> getWorkoutsByDateRange(Map<String, Object> args) {
        String from = getString(args, "from");
        String to = getString(args, "to");
        String type = getString(args, "type");
        if (from == null || to == null) return Map.of("error", "from and to parameters are required");

        Instant fromInstant = LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<WorkoutEntity> workouts;
        if (type != null && !type.isBlank()) {
            workouts = workoutStore.getWorkoutsByTypeAndDateRange(type, fromInstant, toInstant);
        } else {
            workouts = workoutStore.getWorkoutsByDateRange(fromInstant, toInstant);
        }
        return Map.of(
                "from", from,
                "to", to,
                "count", workouts.size(),
                "workouts", workouts.stream().map(WorkoutEntity::toCompactString).collect(Collectors.toList())
        );
    }

    private Map<String, Object> getGarminSummary(Map<String, Object> args) {
        int days = getInt(args, "days", 7);
        List<GarminDailyEntity> data = workoutStore.getRecentGarminData(days);
        return Map.of(
                "days", days,
                "records", data.stream().map(GarminDailyEntity::toCompactString).collect(Collectors.toList())
        );
    }

    private Map<String, Object> getGarminByDateRange(Map<String, Object> args) {
        String from = getString(args, "from");
        String to = getString(args, "to");
        if (from == null || to == null) return Map.of("error", "from and to parameters are required");

        List<GarminDailyEntity> data = workoutStore.getGarminDataByDateRange(
                LocalDate.parse(from), LocalDate.parse(to));
        return Map.of(
                "from", from,
                "to", to,
                "count", data.size(),
                "records", data.stream().map(GarminDailyEntity::toCompactString).collect(Collectors.toList())
        );
    }

    private String getString(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object val = args.get(key);
        return val != null ? val.toString() : null;
    }

    private int getInt(Map<String, Object> args, String key, int defaultValue) {
        if (args == null) return defaultValue;
        Object val = args.get(key);
        if (val == null) return defaultValue;
        if (val instanceof Number n) return n.intValue();
        try { return Integer.parseInt(val.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }
}
