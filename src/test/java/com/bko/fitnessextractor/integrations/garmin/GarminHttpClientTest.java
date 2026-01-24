package com.bko.fitnessextractor.integrations.garmin;

import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.GarminSettings;
import com.bko.fitnessextractor.shared.GoogleSettings;
import com.bko.fitnessextractor.shared.StravaSettings;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GarminHttpClientTest {

    @Test
    void getMetricsForLastDaysParsesResponsesWithFallbacks() throws Exception {
        Executor executor = mock(Executor.class);

        Response bbResponse = responseWith("[{\"bodyBatteryValuesArray\":[[0,20],[1,80]]}]");
        Response summaryResponse = responseWith("{\"vo2Max\":55.2,\"wellnessWeight\":70000}");
        Response weightResponse = responseWith("{\"weightUnitEntries\":[]}");
        Response sleepResponse = responseWith("{\"dailySleepDTO\":{\"sleepTimeSeconds\":28800,\"sleepScores\":{\"overall\":{\"value\":90}}}}");
        Response rhrResponse = responseWith("[{\"value\":48}]");

        when(executor.execute(any())).thenReturn(bbResponse, summaryResponse, weightResponse, sleepResponse, rhrResponse);

        AppSettings settings = new AppSettings(
                new StravaSettings(null, null, null),
                new GarminSettings("user", "pass", null, null, null, null),
                new GoogleSettings(null, null)
        );

        GarminHttpClient client = new GarminHttpClient(settings);
        setField(client, "executor", executor);
        setField(client, "displayName", "test-user");

        List<GarminMetrics> metricsList = client.getMetricsForLastDays(1);
        GarminMetrics metrics = metricsList.get(0);

        assertEquals(LocalDate.now().toString(), metrics.getDate());
        assertEquals(80, metrics.getBodyBatteryHighest());
        assertEquals(20, metrics.getBodyBatteryLowest());
        assertEquals(70.0, metrics.getWeight());
        assertEquals(55.2, metrics.getVo2Max());
        assertEquals(48, metrics.getRestingHeartRate());
        assertEquals(90, metrics.getSleepScore());
        assertEquals(8.0, metrics.getSleepDurationHours());
    }

    private Response responseWith(String body) throws Exception {
        Response response = mock(Response.class);
        Content content = mock(Content.class);
        when(response.returnContent()).thenReturn(content);
        when(content.asString()).thenReturn(body);
        return response;
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
