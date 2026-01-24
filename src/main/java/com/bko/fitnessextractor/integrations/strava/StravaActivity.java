package com.bko.fitnessextractor.integrations.strava;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class StravaActivity {
    private Long id;
    private String name;
    private String type;
    private Double distance;
    @JsonProperty("moving_time")
    private Integer movingTime;
    @JsonProperty("elapsed_time")
    private Integer elapsedTime;
    @JsonProperty("total_elevation_gain")
    private Double totalElevationGain;
    @JsonProperty("start_date")
    private String startDate;
    @JsonProperty("average_speed")
    private Double averageSpeed;
    @JsonProperty("max_speed")
    private Double maxSpeed;
    @JsonProperty("average_heartrate")
    private Double averageHeartrate;
    @JsonProperty("max_heartrate")
    private Double maxHeartrate;
    @JsonProperty("average_watts")
    private Double averageWatts;
    @JsonProperty("kilojoules")
    private Double kilojoules;
    @JsonProperty("suffer_score")
    private Integer sufferScore;
    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Double getDistance() { return distance; }
    public void setDistance(Double distance) { this.distance = distance; }
    public Integer getMovingTime() { return movingTime; }
    public void setMovingTime(Integer movingTime) { this.movingTime = movingTime; }
    public Integer getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(Integer elapsedTime) { this.elapsedTime = elapsedTime; }
    public Double getTotalElevationGain() { return totalElevationGain; }
    public void setTotalElevationGain(Double totalElevationGain) { this.totalElevationGain = totalElevationGain; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
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
}
