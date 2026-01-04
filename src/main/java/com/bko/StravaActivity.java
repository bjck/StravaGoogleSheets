package com.bko;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.ZonedDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivity {
    private Long id;
    private String name;
    private String type;
    private double distance; // in meters
    @JsonProperty("moving_time")
    private int movingTime; // in seconds
    @JsonProperty("elapsed_time")
    private int elapsedTime; // in seconds
    @JsonProperty("total_elevation_gain")
    private double totalElevationGain;
    @JsonProperty("start_date")
    private String startDate;
    @JsonProperty("average_speed")
    private double averageSpeed;
    @JsonProperty("max_speed")
    private double maxSpeed;
    @JsonProperty("average_heartrate")
    private double averageHeartrate;
    @JsonProperty("max_heartrate")
    private double maxHeartrate;
    @JsonProperty("average_watts")
    private double averageWatts;
    @JsonProperty("kilojoules")
    private double kilojoules;
    @JsonProperty("suffer_score")
    private Integer sufferScore;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
    public int getMovingTime() { return movingTime; }
    public void setMovingTime(int movingTime) { this.movingTime = movingTime; }
    public int getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(int elapsedTime) { this.elapsedTime = elapsedTime; }
    public double getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(double totalElevationGain) { this.totalElevationGain = totalElevationGain; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public double getAverageSpeed() { return averageSpeed; }
    public void setAverageSpeed(double averageSpeed) { this.averageSpeed = averageSpeed; }
    public double getMaxSpeed() { return maxSpeed; }
    public void setMaxSpeed(double maxSpeed) { this.maxSpeed = maxSpeed; }
    public double getAverageHeartrate() { return averageHeartrate; }
    public void setAverageHeartrate(double averageHeartrate) { this.averageHeartrate = averageHeartrate; }
    public double getMaxHeartrate() { return maxHeartrate; }
    public void setMaxHeartrate(double maxHeartrate) { this.maxHeartrate = maxHeartrate; }
    public double getAverageWatts() { return averageWatts; }
    public void setAverageWatts(double averageWatts) { this.averageWatts = averageWatts; }
    public double getKilojoules() { return kilojoules; }
    public void setKilojoules(double kilojoules) { this.kilojoules = kilojoules; }
    public Integer getSufferScore() { return sufferScore; }
    public void setSufferScore(Integer sufferScore) { this.sufferScore = sufferScore; }
}
