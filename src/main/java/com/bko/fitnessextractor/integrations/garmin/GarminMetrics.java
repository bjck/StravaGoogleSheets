package com.bko.fitnessextractor.integrations.garmin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GarminMetrics {
    private String date;
    private Integer bodyBatteryHighest;
    private Integer bodyBatteryLowest;
    private Double weight;
    private Double vo2Max;
    private Integer restingHeartRate;
    private Double hrv;
    private Integer sleepScore;
    private Double sleepDurationHours;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public Integer getBodyBatteryHighest() { return bodyBatteryHighest; }
    public void setBodyBatteryHighest(Integer bodyBatteryHighest) { this.bodyBatteryHighest = bodyBatteryHighest; }

    public Integer getBodyBatteryLowest() { return bodyBatteryLowest; }
    public void setBodyBatteryLowest(Integer bodyBatteryLowest) { this.bodyBatteryLowest = bodyBatteryLowest; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public Double getVo2Max() { return vo2Max; }
    public void setVo2Max(Double vo2Max) { this.vo2Max = vo2Max; }

    public Integer getRestingHeartRate() { return restingHeartRate; }
    public void setRestingHeartRate(Integer restingHeartRate) { this.restingHeartRate = restingHeartRate; }

    public Double getHrv() { return hrv; }
    public void setHrv(Double hrv) { this.hrv = hrv; }

    public Integer getSleepScore() { return sleepScore; }
    public void setSleepScore(Integer sleepScore) { this.sleepScore = sleepScore; }

    public Double getSleepDurationHours() { return sleepDurationHours; }
    public void setSleepDurationHours(Double sleepDurationHours) { this.sleepDurationHours = sleepDurationHours; }

    public List<Object> toRow() {
        return List.of(
            date != null ? date : "",
            bodyBatteryHighest != null ? bodyBatteryHighest : "",
            bodyBatteryLowest != null ? bodyBatteryLowest : "",
            weight != null ? weight : "",
            vo2Max != null ? vo2Max : "",
            restingHeartRate != null ? restingHeartRate : "",
            sleepScore != null ? sleepScore : "",
            sleepDurationHours != null ? sleepDurationHours : "",
            hrv != null ? hrv : ""
        );
    }

    public static List<Object> getHeaders() {
        return List.of(
            "Date", "Body Battery Max", "Body Battery Min", "Weight (kg)",
            "VO2 Max", "Resting HR", "Sleep Score", "Sleep Duration (h)", "HRV (ms)"
        );
    }
}
