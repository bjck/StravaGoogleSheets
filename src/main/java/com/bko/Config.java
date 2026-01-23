package com.bko;

import io.github.cdimascio.dotenv.Dotenv;

public class Config {
    private final Dotenv dotenv;

    public Config() {
        dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    public String get(String key) {
        String envKey = key.toUpperCase().replace(".", "_");
        String value = dotenv.get(envKey);
        if (value == null) {
            value = System.getenv(envKey);
        }
        // Special case for Garth token which might be very long and spread across lines in some envs
        // or just to make sure we don't have whitespace issues
        if (value != null && (envKey.equals("GARMIN_GARTH_TOKEN") || envKey.equals("GARMIN_SESSION_COOKIE"))) {
            value = value.trim();
        }
        if (value != null && envKey.equals("GARMIN_GARTH_TOKEN")) {
            value = value.replaceAll("\\s", "");
        }
        return value;
    }
}
