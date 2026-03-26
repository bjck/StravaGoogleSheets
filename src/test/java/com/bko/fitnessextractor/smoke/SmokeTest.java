package com.bko.fitnessextractor.smoke;

import com.bko.fitnessextractor.workout.WorkoutStorePort;
import com.bko.fitnessextractor.workout.domain.GarminDailyEntity;
import com.bko.fitnessextractor.workout.domain.WorkoutEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test that boots the full application context against a real PostgreSQL
 * database (via Testcontainers) and verifies the key endpoints work.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        classes = {com.bko.fitnessextractor.FitnessExtractorApplication.class, TestGeminiConfig.class}
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SmokeTest extends PostgresContainerTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WorkoutStorePort workoutStore;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
        assertThat(workoutStore).isNotNull();
    }

    @Test
    void statsEndpointReturnsEmptyWhenNoData() throws Exception {
        mockMvc.perform(get("/ai/stats").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWorkouts").value(0))
                .andExpect(jsonPath("$.activityTypes").isArray())
                .andExpect(jsonPath("$.activityTypes").isEmpty());
    }

    @Test
    void modelsEndpointReturns200() throws Exception {
        // Models endpoint may return empty list without a real API key, but should not error
        mockMvc.perform(get("/ai/models").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void mcpToolsEndpointListsTools() throws Exception {
        mockMvc.perform(get("/mcp/tools").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void workoutPersistenceRoundTrip() {
        // Verify we can write and read from the real PostgreSQL database
        assertThat(workoutStore.getWorkoutCount()).isZero();

        WorkoutEntity workout = new WorkoutEntity();
        workout.setStravaId(999L);
        workout.setName("Test Run");
        workout.setType("Run");
        workout.setDistanceMeters(5000.0);
        workout.setMovingTimeSeconds(1800);
        workout.setStartDate(Instant.parse("2024-06-15T08:00:00Z"));
        workout.setAverageHeartrate(145.0);

        // Use a StravaActivity to go through the real save path
        var stravaActivity = new com.bko.fitnessextractor.integrations.strava.StravaActivity();
        stravaActivity.setId(999L);
        stravaActivity.setName("Test Run");
        stravaActivity.setType("Run");
        stravaActivity.setDistance(5000.0);
        stravaActivity.setMovingTime(1800);
        stravaActivity.setStartDate("2024-06-15T08:00:00Z");
        stravaActivity.setAverageHeartrate(145.0);

        int saved = workoutStore.saveStravaActivities(List.of(stravaActivity));
        assertThat(saved).isEqualTo(1);
        assertThat(workoutStore.getWorkoutCount()).isEqualTo(1);

        // Verify dedup - saving again should add 0
        int savedAgain = workoutStore.saveStravaActivities(List.of(stravaActivity));
        assertThat(savedAgain).isZero();

        // Verify query methods
        List<String> types = workoutStore.getActivityTypes();
        assertThat(types).containsExactly("Run");

        List<WorkoutEntity> recent = workoutStore.getRecentWorkouts(10);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getName()).isEqualTo("Test Run");
        assertThat(recent.get(0).getDistanceMeters()).isEqualTo(5000.0);

        List<WorkoutEntity> byType = workoutStore.getWorkoutsByType("Run");
        assertThat(byType).hasSize(1);

        List<WorkoutEntity> byTypeWrong = workoutStore.getWorkoutsByType("Ride");
        assertThat(byTypeWrong).isEmpty();
    }

    @Test
    void garminPersistenceRoundTrip() {
        var metrics = new com.bko.fitnessextractor.integrations.garmin.GarminMetrics();
        metrics.setDate("2024-06-15");
        metrics.setBodyBatteryHighest(85);
        metrics.setBodyBatteryLowest(20);
        metrics.setRestingHeartRate(52);
        metrics.setVo2Max(48.0);
        metrics.setSleepScore(82);
        metrics.setSleepDurationHours(7.5);

        int saved = workoutStore.saveGarminMetrics(List.of(metrics));
        assertThat(saved).isEqualTo(1);

        List<GarminDailyEntity> recent = workoutStore.getRecentGarminData(7);
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).getBodyBatteryMax()).isEqualTo(85);
        assertThat(recent.get(0).getRestingHeartRate()).isEqualTo(52);

        List<GarminDailyEntity> byRange = workoutStore.getGarminDataByDateRange(
                LocalDate.of(2024, 6, 1), LocalDate.of(2024, 6, 30));
        assertThat(byRange).hasSize(1);
    }

    @Test
    void statsEndpointReflectsPersistedData() throws Exception {
        // After the persistence tests above, check stats reflects data
        var activity = new com.bko.fitnessextractor.integrations.strava.StravaActivity();
        activity.setId(1001L);
        activity.setName("Stats Test Ride");
        activity.setType("Ride");
        activity.setDistance(25000.0);
        activity.setMovingTime(3600);
        activity.setStartDate("2024-07-01T10:00:00Z");
        workoutStore.saveStravaActivities(List.of(activity));

        mockMvc.perform(get("/ai/stats").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWorkouts").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.activityTypes").isArray());
    }

    @Test
    void syncApiEndpointReturnsReport() throws Exception {
        // Strava sync should return a report (will warn about missing credentials)
        mockMvc.perform(post("/api/sync/strava").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stravaAttempted").value(true));
    }
}
