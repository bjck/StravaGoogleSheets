package com.bko.fitnessextractor.workout;

import com.bko.fitnessextractor.integrations.garmin.GarminMetrics;
import com.bko.fitnessextractor.integrations.strava.StravaActivity;
import com.bko.fitnessextractor.workout.domain.GarminDailyEntity;
import com.bko.fitnessextractor.workout.domain.WorkoutEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface WorkoutStorePort {
    int saveStravaActivities(List<StravaActivity> activities);
    int saveGarminMetrics(List<GarminMetrics> metricsList);

    List<WorkoutEntity> getRecentWorkouts(int limit);
    List<WorkoutEntity> getWorkoutsByType(String type);
    List<WorkoutEntity> getWorkoutsByDateRange(Instant from, Instant to);
    List<WorkoutEntity> getWorkoutsByTypeAndDateRange(String type, Instant from, Instant to);
    List<String> getActivityTypes();
    List<Object[]> getWorkoutSummaryByType();
    long getWorkoutCount();
    Instant getEarliestWorkoutDate();
    Instant getLatestWorkoutDate();

    List<GarminDailyEntity> getRecentGarminData(int days);
    List<GarminDailyEntity> getGarminDataByDateRange(LocalDate from, LocalDate to);
}
