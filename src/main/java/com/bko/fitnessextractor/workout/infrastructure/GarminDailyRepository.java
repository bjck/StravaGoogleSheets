package com.bko.fitnessextractor.workout.infrastructure;

import com.bko.fitnessextractor.workout.domain.GarminDailyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface GarminDailyRepository extends JpaRepository<GarminDailyEntity, LocalDate> {

    List<GarminDailyEntity> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    List<GarminDailyEntity> findTop7ByOrderByDateDesc();

    List<GarminDailyEntity> findTop30ByOrderByDateDesc();
}
