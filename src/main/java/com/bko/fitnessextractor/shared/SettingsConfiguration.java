package com.bko.fitnessextractor.shared;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SettingsConfiguration {

    @Bean
    public AppSettings appSettings(EnvConfig envConfig) {
        StravaSettings strava = new StravaSettings(
                envConfig.get("strava.client_id"),
                envConfig.get("strava.client_secret"),
                envConfig.get("strava.refresh_token")
        );
        GarminSettings garmin = new GarminSettings(
                envConfig.get("garmin.username"),
                envConfig.get("garmin.password"),
                envConfig.get("garmin.session_cookie"),
                envConfig.get("garmin.garth_token"),
                envConfig.get("garmin.token_script"),
                envConfig.get("garmin.python_path")
        );
        GoogleSettings google = new GoogleSettings(
                envConfig.get("google.spreadsheet_id"),
                envConfig.get("google.service_account_key_path")
        );
        return new AppSettings(strava, garmin, google);
    }

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
