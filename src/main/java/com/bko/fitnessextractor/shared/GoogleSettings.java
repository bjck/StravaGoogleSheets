package com.bko.fitnessextractor.shared;

public record GoogleSettings(String spreadsheetId, String serviceAccountKeyPath) {
    public boolean isConfigured() {
        return hasText(spreadsheetId) && hasText(serviceAccountKeyPath);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
