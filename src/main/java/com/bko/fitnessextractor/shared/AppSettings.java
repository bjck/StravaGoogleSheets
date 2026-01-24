package com.bko.fitnessextractor.shared;

public record AppSettings(StravaSettings strava, GarminSettings garmin, GoogleSettings google) {
    public boolean isGoogleConfigured() {
        return google != null && google.isConfigured();
    }

    public boolean isStravaConfigured() {
        return strava != null && strava.isConfigured();
    }

    public boolean isGarminConfigured() {
        return garmin != null && garmin.isConfigured();
    }
}
