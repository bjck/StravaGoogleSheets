package com.bko.fitnessextractor.integrations.strava;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

public interface StravaClientPort {
    default List<StravaActivity> getActivities(int page, int perPage) throws IOException {
        return getActivities(page, perPage, null, null);
    }

    List<StravaActivity> getActivities(int page, int perPage, Instant after, Instant before) throws IOException;
    StravaActivity getActivity(Long id) throws IOException;
}
