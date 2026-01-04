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
        return dotenv.get(envKey);
    }
}
