package com.bko.fitnessextractor.workout.infrastructure;

import com.bko.fitnessextractor.workout.domain.WorkoutEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface WorkoutRepository extends JpaRepository<WorkoutEntity, Long> {

    List<WorkoutEntity> findByTypeIgnoreCaseOrderByStartDateDesc(String type);

    List<WorkoutEntity> findByStartDateBetweenOrderByStartDateDesc(Instant from, Instant to);

    List<WorkoutEntity> findByTypeIgnoreCaseAndStartDateBetweenOrderByStartDateDesc(
            String type, Instant from, Instant to);

    List<WorkoutEntity> findTop10ByOrderByStartDateDesc();

    @Query("SELECT DISTINCT w.type FROM WorkoutEntity w WHERE w.type IS NOT NULL ORDER BY w.type")
    List<String> findDistinctTypes();

    @Query("SELECT w.type, COUNT(w), SUM(w.distanceMeters), SUM(w.movingTimeSeconds) " +
           "FROM WorkoutEntity w GROUP BY w.type ORDER BY COUNT(w) DESC")
    List<Object[]> summarizeByType();

    long count();

    @Query("SELECT MIN(w.startDate) FROM WorkoutEntity w")
    Instant findEarliestDate();

    @Query("SELECT MAX(w.startDate) FROM WorkoutEntity w")
    Instant findLatestDate();
}
