package com.bko.fitnessextractor.shared;

public record ConfigStatus(boolean googleConfigured, boolean stravaConfigured, boolean garminConfigured) {
    public static ConfigStatus from(AppSettings settings) {
        return new ConfigStatus(
                settings.isGoogleConfigured(),
                settings.isStravaConfigured(),
                settings.isGarminConfigured()
        );
    }
}
