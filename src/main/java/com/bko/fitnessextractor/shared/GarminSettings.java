package com.bko.fitnessextractor.shared;

public record GarminSettings(
        String username,
        String password,
        String sessionCookie,
        String garthToken,
        String tokenScript,
        String pythonPath
) {
    public boolean isConfigured() {
        return hasText(username) && hasText(password);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
