package com.bko.fitnessextractor.workout.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "workouts")
public class WorkoutEntity {
    @Id
    private Long stravaId;
    private String name;
    private String type;
    private Double distanceMeters;
    private Integer movingTimeSeconds;
    private Integer elapsedTimeSeconds;
    private Double totalElevationGain;
    private Instant startDate;
    private Double averageSpeed;
    private Double maxSpeed;
    private Double averageHeartrate;
    private Double maxHeartrate;
    private Double averageWatts;
    private Double kilojoules;
    private Integer sufferScore;
    @Column(length = 4000)
    private String description;

    public Long getStravaId() { return stravaId; }
    public void setStravaId(Long stravaId) { this.stravaId = stravaId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getDistanceMeters() { return distanceMeters; }
    public void setDistanceMeters(Double distanceMeters) { this.distanceMeters = distanceMeters; }
    public Integer getMovingTimeSeconds() { return movingTimeSeconds; }
    public void setMovingTimeSeconds(Integer movingTimeSeconds) { this.movingTimeSeconds = movingTimeSeconds; }
    public Integer getElapsedTimeSeconds() { return elapsedTimeSeconds; }
    public void setElapsedTimeSeconds(Integer elapsedTimeSeconds) { this.elapsedTimeSeconds = elapsedTimeSeconds; }
    public Double getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(Double totalElevationGain) { this.totalElevationGain = totalElevationGain; }
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }
    public Double getAverageSpeed() { return averageSpeed; }
    public void setAverageSpeed(Double averageSpeed) { this.averageSpeed = averageSpeed; }
    public Double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(Double maxSpeed) { this.maxSpeed = maxSpeed; }
    public Double getAverageHeartrate() { return averageHeartrate; }
    public void setAverageHeartrate(Double averageHeartrate) { this.averageHeartrate = averageHeartrate; }
    public Double getMaxHeartrate() { return maxHeartrate; }
    public void setMaxHeartrate(Double maxHeartrate) { this.maxHeartrate = maxHeartrate; }
    public Double getAverageWatts() { return averageWatts; }
    public void setAverageWatts(Double averageWatts) { this.averageWatts = averageWatts; }
    public Double getKilojoules() { return kilojoules; }
    public void setKilojoules(Double kilojoules) { this.kilojoules = kilojoules; }
    public Integer getSufferScore() { return sufferScore; }
    public void setSufferScore(Integer sufferScore) { this.sufferScore = sufferScore; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name != null ? name : "Untitled");
        sb.append(" | ").append(type != null ? type : "Unknown");
        if (startDate != null) sb.append(" | ").append(startDate);
        if (distanceMeters != null) sb.append(" | ").append(String.format("%.1f km", distanceMeters / 1000.0));
        if (movingTimeSeconds != null) sb.append(" | ").append(formatDuration(movingTimeSeconds));
        if (averageHeartrate != null) sb.append(" | avgHR=").append(averageHeartrate.intValue());
        if (maxHeartrate != null) sb.append(" | maxHR=").append(maxHeartrate.intValue());
        if (totalElevationGain != null) sb.append(" | elev=").append(totalElevationGain.intValue()).append("m");
        if (averageWatts != null) sb.append(" | watts=").append(averageWatts.intValue());
        if (sufferScore != null) sb.append(" | suffer=").append(sufferScore);
        if (description != null && !description.isBlank()) sb.append(" | note: ").append(description);
        return sb.toString();
    }

    private String formatDuration(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        return h > 0 ? h + "h" + m + "m" : m + "m";
    }
}
