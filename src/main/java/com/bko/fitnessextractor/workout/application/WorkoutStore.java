package com.bko.fitnessextractor.workout.application;

import com.bko.fitnessextractor.integrations.garmin.GarminMetrics;
import com.bko.fitnessextractor.integrations.strava.StravaActivity;
import com.bko.fitnessextractor.workout.domain.GarminDailyEntity;
import com.bko.fitnessextractor.workout.domain.WorkoutEntity;
import com.bko.fitnessextractor.workout.infrastructure.GarminDailyRepository;
import com.bko.fitnessextractor.workout.infrastructure.WorkoutRepository;
import com.bko.fitnessextractor.workout.WorkoutStorePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
public class WorkoutStore implements WorkoutStorePort {
    private static final Logger logger = LoggerFactory.getLogger(WorkoutStore.class);

    private final WorkoutRepository workoutRepository;
    private final GarminDailyRepository garminDailyRepository;

    public WorkoutStore(WorkoutRepository workoutRepository, GarminDailyRepository garminDailyRepository) {
        this.workoutRepository = workoutRepository;
        this.garminDailyRepository = garminDailyRepository;
    }

    @Transactional
    public int saveStravaActivities(List<StravaActivity> activities) {
        int saved = 0;
        for (StravaActivity activity : activities) {
            if (activity.getId() == null) continue;
            if (workoutRepository.existsById(activity.getId())) continue;

            WorkoutEntity entity = new WorkoutEntity();
            entity.setStravaId(activity.getId());
            entity.setName(activity.getName());
            entity.setType(activity.getType());
            entity.setDistanceMeters(activity.getDistance());
            entity.setMovingTimeSeconds(activity.getMovingTime());
            entity.setElapsedTimeSeconds(activity.getElapsedTime());
            entity.setTotalElevationGain(activity.getTotalElevationGain());
            entity.setStartDate(parseInstant(activity.getStartDate()));
            entity.setAverageSpeed(activity.getAverageSpeed());
            entity.setMaxSpeed(activity.getMaxSpeed());
            entity.setAverageHeartrate(activity.getAverageHeartrate());
            entity.setMaxHeartrate(activity.getMaxHeartrate());
            entity.setAverageWatts(activity.getAverageWatts());
            entity.setKilojoules(activity.getKilojoules());
            entity.setSufferScore(activity.getSufferScore());
            entity.setDescription(activity.getDescription());
            workoutRepository.save(entity);
            saved++;
        }
        logger.info("Saved {} new workouts to database", saved);
        return saved;
    }

    @Transactional
    public int saveGarminMetrics(List<GarminMetrics> metricsList) {
        int saved = 0;
        for (GarminMetrics metrics : metricsList) {
            if (metrics.getDate() == null) continue;
            LocalDate date = tryParseDate(metrics.getDate());
            if (date == null) continue;

            GarminDailyEntity entity = garminDailyRepository.findById(date).orElse(new GarminDailyEntity());
            entity.setDate(date);
            entity.setBodyBatteryMax(metrics.getBodyBatteryHighest());
            entity.setBodyBatteryMin(metrics.getBodyBatteryLowest());
            entity.setWeight(metrics.getWeight());
            entity.setVo2Max(metrics.getVo2Max());
            entity.setRestingHeartRate(metrics.getRestingHeartRate());
            entity.setSleepScore(metrics.getSleepScore());
            entity.setSleepDurationHours(metrics.getSleepDurationHours());
            entity.setHrv(metrics.getHrv());
            garminDailyRepository.save(entity);
            saved++;
        }
        logger.info("Saved/updated {} garmin daily records", saved);
        return saved;
    }

    // --- Query methods used by AI tools ---

    public List<WorkoutEntity> getRecentWorkouts(int limit) {
        return workoutRepository.findTop10ByOrderByStartDateDesc();
    }

    public List<WorkoutEntity> getWorkoutsByType(String type) {
        return workoutRepository.findByTypeIgnoreCaseOrderByStartDateDesc(type);
    }

    public List<WorkoutEntity> getWorkoutsByDateRange(Instant from, Instant to) {
        return workoutRepository.findByStartDateBetweenOrderByStartDateDesc(from, to);
    }

    public List<WorkoutEntity> getWorkoutsByTypeAndDateRange(String type, Instant from, Instant to) {
        return workoutRepository.findByTypeIgnoreCaseAndStartDateBetweenOrderByStartDateDesc(type, from, to);
    }

    public List<String> getActivityTypes() {
        return workoutRepository.findDistinctTypes();
    }

    public List<Object[]> getWorkoutSummaryByType() {
        return workoutRepository.summarizeByType();
    }

    public long getWorkoutCount() {
        return workoutRepository.count();
    }

    public Instant getEarliestWorkoutDate() {
        return workoutRepository.findEarliestDate();
    }

    public Instant getLatestWorkoutDate() {
        return workoutRepository.findLatestDate();
    }

    public List<GarminDailyEntity> getRecentGarminData(int days) {
        if (days <= 7) return garminDailyRepository.findTop7ByOrderByDateDesc();
        return garminDailyRepository.findTop30ByOrderByDateDesc();
    }

    public List<GarminDailyEntity> getGarminDataByDateRange(LocalDate from, LocalDate to) {
        return garminDailyRepository.findByDateBetweenOrderByDateDesc(from, to);
    }

    private Instant parseInstant(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try { return OffsetDateTime.parse(dateStr).toInstant(); } catch (DateTimeParseException ignored) {}
        try { return Instant.parse(dateStr); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toInstant(); } catch (DateTimeParseException ignored) {}
        return null;
    }

    private LocalDate tryParseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDate.parse(value.trim()); } catch (Exception e) { return null; }
    }
}
