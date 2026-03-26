package com.bko.fitnessextractor.ai.infrastructure.context;

import com.bko.fitnessextractor.ai.domain.FitnessContext;
import com.bko.fitnessextractor.ai.domain.FitnessContextPort;
import com.bko.fitnessextractor.workout.WorkoutStorePort;
import com.bko.fitnessextractor.workout.domain.GarminDailyEntity;
import com.bko.fitnessextractor.workout.domain.WorkoutEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SheetsFitnessContextProvider implements FitnessContextPort {
    private final WorkoutStorePort workoutStore;

    public SheetsFitnessContextProvider(WorkoutStorePort workoutStore) {
        this.workoutStore = workoutStore;
    }

    @Override
    public FitnessContext loadContext() {
        List<String> messages = new ArrayList<>();
        long count = workoutStore.getWorkoutCount();
        if (count == 0) {
            messages.add("No workout data in database. Run a sync first.");
        } else {
            messages.add(count + " workouts in database");
            List<String> types = workoutStore.getActivityTypes();
            messages.add("Activity types: " + String.join(", ", types));
            List<WorkoutEntity> recent = workoutStore.getRecentWorkouts(3);
            messages.add("Recent: " + recent.stream()
                    .map(WorkoutEntity::toCompactString)
                    .collect(Collectors.joining(" | ")));
            List<GarminDailyEntity> garmin = workoutStore.getRecentGarminData(3);
            if (!garmin.isEmpty()) {
                messages.add("Garmin: " + garmin.stream()
                        .map(GarminDailyEntity::toCompactString)
                        .collect(Collectors.joining(" | ")));
            }
        }
        return new FitnessContext(messages, null, null, null);
    }
}
