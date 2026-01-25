package com.bko.fitnessextractor.integrations.garmin;

import java.util.List;

public class GarminWellnessSample {
    private String date;
    private String timestamp;
    private Integer stress;
    private Integer heartRate;

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Integer getStress() { return stress; }
    public void setStress(Integer stress) { this.stress = stress; }

    public Integer getHeartRate() { return heartRate; }
    public void setHeartRate(Integer heartRate) { this.heartRate = heartRate; }

    public List<Object> toRow() {
        return List.of(
                date != null ? date : "",
                timestamp != null ? timestamp : "",
                stress != null ? stress : "",
                heartRate != null ? heartRate : ""
        );
    }

    public static List<Object> getHeaders() {
        return List.of("Date", "Timestamp", "Stress", "Heart Rate");
    }
}
