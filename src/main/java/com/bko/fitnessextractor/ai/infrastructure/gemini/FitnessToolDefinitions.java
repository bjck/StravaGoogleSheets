package com.bko.fitnessextractor.ai.infrastructure.gemini;

import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;

import java.util.List;
import java.util.Map;

public final class FitnessToolDefinitions {

    private FitnessToolDefinitions() {}

    public static Tool buildTool() {
        return Tool.builder()
                .setFunctionDeclarations(List.of(
                        listActivityTypes(),
                        getWorkoutOverview(),
                        getRecentWorkouts(),
                        getWorkoutsByType(),
                        getWorkoutsByDateRange(),
                        getGarminSummary(),
                        getGarminByDateRange()
                ))
                .build();
    }

    private static FunctionDeclaration listActivityTypes() {
        return FunctionDeclaration.builder()
                .setName("list_activity_types")
                .setDescription("List all distinct activity types the user has recorded (e.g. Run, Ride, Swim, WeightTraining). " +
                        "Call this first to understand what data is available before querying specific activities.")
                .build();
    }

    private static FunctionDeclaration getWorkoutOverview() {
        return FunctionDeclaration.builder()
                .setName("get_workout_overview")
                .setDescription("Get a high-level overview of the user's workout history: total count, date range, " +
                        "and breakdown by activity type with total distance and time. Good starting point for general fitness questions.")
                .build();
    }

    private static FunctionDeclaration getRecentWorkouts() {
        return FunctionDeclaration.builder()
                .setName("get_recent_workouts")
                .setDescription("Get the 10 most recent workouts with full details (name, type, distance, duration, " +
                        "heart rate, elevation, watts, suffer score). Use for questions about recent training.")
                .setParameters(Schema.builder()
                        .setType("OBJECT")
                        .setProperties(Map.of(
                                "limit", Schema.builder()
                                        .setType("INTEGER")
                                        .setDescription("Number of recent workouts to return (default 10, max 50)")
                                        .build()
                        ))
                        .build())
                .build();
    }

    private static FunctionDeclaration getWorkoutsByType() {
        return FunctionDeclaration.builder()
                .setName("get_workouts_by_type")
                .setDescription("Get workouts filtered by activity type (e.g. 'Run', 'Ride', 'Swim'). " +
                        "Call list_activity_types first to know valid types.")
                .setParameters(Schema.builder()
                        .setType("OBJECT")
                        .setProperties(Map.of(
                                "type", Schema.builder()
                                        .setType("STRING")
                                        .setDescription("Activity type to filter by, e.g. 'Run', 'Ride', 'Swim', 'WeightTraining'")
                                        .build()
                        ))
                        .setRequired(List.of("type"))
                        .build())
                .build();
    }

    private static FunctionDeclaration getWorkoutsByDateRange() {
        return FunctionDeclaration.builder()
                .setName("get_workouts_by_date_range")
                .setDescription("Get workouts within a specific date range. Useful for analyzing training over a period " +
                        "(e.g. last month, this week, a specific training block).")
                .setParameters(Schema.builder()
                        .setType("OBJECT")
                        .setProperties(Map.of(
                                "from", Schema.builder()
                                        .setType("STRING")
                                        .setDescription("Start date in ISO format (e.g. '2024-01-01')")
                                        .build(),
                                "to", Schema.builder()
                                        .setType("STRING")
                                        .setDescription("End date in ISO format (e.g. '2024-12-31')")
                                        .build(),
                                "type", Schema.builder()
                                        .setType("STRING")
                                        .setDescription("Optional: filter by activity type")
                                        .build()
                        ))
                        .setRequired(List.of("from", "to"))
                        .build())
                .build();
    }

    private static FunctionDeclaration getGarminSummary() {
        return FunctionDeclaration.builder()
                .setName("get_garmin_summary")
                .setDescription("Get recent Garmin health metrics: body battery, resting heart rate, VO2 max, " +
                        "sleep score, sleep duration, HRV, weight. Use for recovery and readiness questions.")
                .setParameters(Schema.builder()
                        .setType("OBJECT")
                        .setProperties(Map.of(
                                "days", Schema.builder()
                                        .setType("INTEGER")
                                        .setDescription("Number of recent days to include (default 7, max 30)")
                                        .build()
                        ))
                        .build())
                .build();
    }

    private static FunctionDeclaration getGarminByDateRange() {
        return FunctionDeclaration.builder()
                .setName("get_garmin_by_date_range")
                .setDescription("Get Garmin health metrics for a specific date range.")
                .setParameters(Schema.builder()
                        .setType("OBJECT")
                        .setProperties(Map.of(
                                "from", Schema.builder()
                                        .setType("STRING")
                                        .setDescription("Start date in ISO format (e.g. '2024-01-01')")
                                        .build(),
                                "to", Schema.builder()
                                        .setType("STRING")
                                        .setDescription("End date in ISO format (e.g. '2024-12-31')")
                                        .build()
                        ))
                        .setRequired(List.of("from", "to"))
                        .build())
                .build();
    }
}
