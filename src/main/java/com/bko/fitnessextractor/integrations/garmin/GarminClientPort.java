package com.bko.fitnessextractor.integrations.garmin;

import java.io.IOException;
import java.util.List;

public interface GarminClientPort {
    void login() throws IOException;
    List<GarminMetrics> getMetricsForLastDays(int days) throws IOException;
    List<GarminWellnessSample> getWellnessSamplesForLastDays(int days) throws IOException;
}
