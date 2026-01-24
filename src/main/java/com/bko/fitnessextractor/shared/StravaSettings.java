package com.bko.fitnessextractor.shared;

public record StravaSettings(String clientId, String clientSecret, String refreshToken) {
    public boolean isConfigured() {
        return hasText(clientId) && hasText(clientSecret) && hasText(refreshToken);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
