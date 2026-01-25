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
import java.time.ZoneId;
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
        Response hrvResponse = responseWith("{\"hrvSummary\":{\"lastNightAvg\":62.5}}");

        when(executor.execute(any())).thenReturn(bbResponse, summaryResponse, weightResponse, sleepResponse, rhrResponse, hrvResponse);

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
        assertEquals(62.5, metrics.getHrv());
    }

    @Test
    void getWellnessSamplesForLastDaysBucketsSeries() throws Exception {
        Executor executor = mock(Executor.class);

        LocalDate today = LocalDate.now();
        long base = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        Response stressResponse = responseWith("{\"stressValuesArray\":[[" + base + ",30],[" + (base + 300000) + ",60]]}");
        Response hrResponse = responseWith("{\"heartRateValues\":[[" + base + ",50],[" + (base + 300000) + ",70]]}");

        when(executor.execute(any())).thenReturn(stressResponse, hrResponse);

        AppSettings settings = new AppSettings(
                new StravaSettings(null, null, null),
                new GarminSettings("user", "pass", null, null, null, null),
                new GoogleSettings(null, null)
        );

        GarminHttpClient client = new GarminHttpClient(settings);
        setField(client, "executor", executor);
        setField(client, "displayName", "test-user");

        List<GarminWellnessSample> samples = client.getWellnessSamplesForLastDays(1);

        assertEquals(2, samples.size());
        GarminWellnessSample first = samples.get(0);
        GarminWellnessSample second = samples.get(1);

        assertEquals(today.toString(), first.getDate());
        assertEquals(30, first.getStress());
        assertEquals(50, first.getHeartRate());

        assertEquals(today.toString(), second.getDate());
        assertEquals(60, second.getStress());
        assertEquals(70, second.getHeartRate());
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
