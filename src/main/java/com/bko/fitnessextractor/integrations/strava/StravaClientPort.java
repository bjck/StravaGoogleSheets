package com.bko.fitnessextractor.integrations.strava;

import java.io.IOException;
import java.util.List;

public interface StravaClientPort {
    List<StravaActivity> getActivities(int page, int perPage) throws IOException;
    StravaActivity getActivity(Long id) throws IOException;
}
