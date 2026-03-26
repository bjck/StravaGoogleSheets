package com.bko.fitnessextractor.workout.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "garmin_daily")
public class GarminDailyEntity {
    @Id
    private LocalDate date;
    private Integer bodyBatteryMax;
    private Integer bodyBatteryMin;
    private Double weight;
    private Double vo2Max;
    private Integer restingHeartRate;
    private Integer sleepScore;
    private Double sleepDurationHours;
    private Double hrv;

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Integer getBodyBatteryMax() { return bodyBatteryMax; }
    public void setBodyBatteryMax(Integer bodyBatteryMax) { this.bodyBatteryMax = bodyBatteryMax; }
    public Integer getBodyBatteryMin() { return bodyBatteryMin; }
    public void setBodyBatteryMin(Integer bodyBatteryMin) { this.bodyBatteryMin = bodyBatteryMin; }
    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }
    public Double getVo2Max() { return vo2Max; }
    public void setVo2Max(Double vo2Max) { this.vo2Max = vo2Max; }
    public Integer getRestingHeartRate() { return restingHeartRate; }
    public void setRestingHeartRate(Integer restingHeartRate) { this.restingHeartRate = restingHeartRate; }
    public Integer getSleepScore() { return sleepScore; }
    public void setSleepScore(Integer sleepScore) { this.sleepScore = sleepScore; }
    public Double getSleepDurationHours() { return sleepDurationHours; }
    public void setSleepDurationHours(Double sleepDurationHours) { this.sleepDurationHours = sleepDurationHours; }
    public Double getHrv() { return hrv; }
    public void setHrv(Double hrv) { this.hrv = hrv; }

    public String toCompactString() {
        StringBuilder sb = new StringBuilder();
        sb.append(date);
        if (bodyBatteryMax != null) sb.append(" | BB=").append(bodyBatteryMin).append("-").append(bodyBatteryMax);
        if (restingHeartRate != null) sb.append(" | RHR=").append(restingHeartRate);
        if (vo2Max != null) sb.append(" | VO2=").append(vo2Max);
        if (sleepScore != null) sb.append(" | sleep=").append(sleepScore);
        if (sleepDurationHours != null) sb.append(" | ").append(String.format("%.1fh", sleepDurationHours));
        if (hrv != null) sb.append(" | HRV=").append(hrv);
        if (weight != null) sb.append(" | ").append(String.format("%.1fkg", weight));
        return sb.toString();
    }
}
