package com.bko.fitnessextractor.shared;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.stereotype.Component;

@Component
public class EnvConfig {
    private final Dotenv dotenv;

    public EnvConfig() {
        this.dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
    }

    public String get(String key) {
        String envKey = key.toUpperCase()
                .replace(".", "_")
                .replace("-", "_");
        String value = dotenv.get(envKey);
        if (value == null) {
            value = System.getenv(envKey);
        }
        if (value != null && (envKey.equals("GARMIN_GARTH_TOKEN") || envKey.equals("GARMIN_SESSION_COOKIE"))) {
            value = value.trim();
        }
        if (value != null && envKey.equals("GARMIN_GARTH_TOKEN")) {
            value = value.replaceAll("\\s", "");
        }
        return value;
    }
}
