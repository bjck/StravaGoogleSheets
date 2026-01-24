package com.bko.fitnessextractor.integrations.strava;

import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.GarminSettings;
import com.bko.fitnessextractor.shared.GoogleSettings;
import com.bko.fitnessextractor.shared.StravaSettings;
import org.apache.hc.client5.http.fluent.Content;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.fluent.Response;
import org.apache.hc.core5.http.NameValuePair;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StravaHttpClientTest {

    @Test
    void getActivitiesRefreshesAndParses() throws Exception {
        Request postRequest = mock(Request.class);
        Response postResponse = mock(Response.class);
        Content postContent = mock(Content.class);
        when(postRequest.bodyForm(Mockito.<Iterable<NameValuePair>>any())).thenReturn(postRequest);
        when(postRequest.execute()).thenReturn(postResponse);
        when(postResponse.returnContent()).thenReturn(postContent);
        when(postContent.asString()).thenReturn("{\"access_token\":\"token123\"}");

        Request getRequest = mock(Request.class);
        Response getResponse = mock(Response.class);
        Content getContent = mock(Content.class);
        when(getRequest.addHeader(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(getResponse);
        when(getResponse.returnContent()).thenReturn(getContent);
        when(getContent.asString()).thenReturn("[{\"id\":1,\"name\":\"Ride\",\"type\":\"Ride\"}]");

        try (MockedStatic<Request> mocked = Mockito.mockStatic(Request.class)) {
            mocked.when(() -> Request.post("https://www.strava.com/oauth/token")).thenReturn(postRequest);
            mocked.when(() -> Request.get(anyString())).thenReturn(getRequest);

            AppSettings settings = new AppSettings(
                    new StravaSettings("id", "secret", "refresh"),
                    new GarminSettings(null, null, null, null, null, null),
                    new GoogleSettings(null, null)
            );
            StravaHttpClient client = new StravaHttpClient(settings);
            List<StravaActivity> activities = client.getActivities(1, 1);

            assertEquals(1, activities.size());
            assertEquals(1L, activities.get(0).getId());
            assertEquals("Ride", activities.get(0).getName());
        }
    }

    @Test
    void getActivityUsesAccessToken() throws Exception {
        Request getRequest = mock(Request.class);
        Response getResponse = mock(Response.class);
        Content getContent = mock(Content.class);
        when(getRequest.addHeader(anyString(), anyString())).thenReturn(getRequest);
        when(getRequest.execute()).thenReturn(getResponse);
        when(getResponse.returnContent()).thenReturn(getContent);
        when(getContent.asString()).thenReturn("{\"id\":42,\"name\":\"Run\",\"type\":\"Run\"}");

        try (MockedStatic<Request> mocked = Mockito.mockStatic(Request.class)) {
            mocked.when(() -> Request.get(anyString())).thenReturn(getRequest);

            AppSettings settings = new AppSettings(
                    new StravaSettings("id", "secret", "refresh"),
                    new GarminSettings(null, null, null, null, null, null),
                    new GoogleSettings(null, null)
            );
            StravaHttpClient client = new StravaHttpClient(settings);
            setAccessToken(client, "token");

            StravaActivity activity = client.getActivity(42L);
            assertEquals(42L, activity.getId());
            assertEquals("Run", activity.getName());
        }
    }

    private void setAccessToken(StravaHttpClient client, String token) throws Exception {
        Field field = StravaHttpClient.class.getDeclaredField("accessToken");
        field.setAccessible(true);
        field.set(client, token);
    }
}
