package com.bko.fitnessextractor.integrations.strava;

import com.bko.fitnessextractor.shared.AppSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.fluent.Form;
import org.apache.hc.client5.http.fluent.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class StravaHttpClient implements StravaClientPort {
    private static final Logger logger = LoggerFactory.getLogger(StravaHttpClient.class);
    private static final String STRAVA_API_BASE = "https://www.strava.com/api/v3";

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private String accessToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StravaHttpClient(AppSettings settings) {
        this.clientId = settings.strava().clientId();
        this.clientSecret = settings.strava().clientSecret();
        this.refreshToken = settings.strava().refreshToken();
    }

    @Override
    public List<StravaActivity> getActivities(int page, int perPage) throws IOException {
        if (accessToken == null) {
            refreshAccessToken();
        }

        try {
            String response = Request.get(STRAVA_API_BASE + "/athlete/activities?page=" + page + "&per_page=" + perPage)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .execute()
                    .returnContent()
                    .asString();

            return Arrays.asList(objectMapper.readValue(response, StravaActivity[].class));
        } catch (org.apache.hc.client5.http.HttpResponseException e) {
            if (e.getStatusCode() == 401) {
                logger.warn("Strava 401: Unauthorized. Access token may be invalid or missing scopes.");
            }
            throw e;
        }
    }

    @Override
    public StravaActivity getActivity(Long id) throws IOException {
        if (accessToken == null) {
            refreshAccessToken();
        }

        try {
            String response = Request.get(STRAVA_API_BASE + "/activities/" + id)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .execute()
                    .returnContent()
                    .asString();

            return objectMapper.readValue(response, StravaActivity.class);
        } catch (org.apache.hc.client5.http.HttpResponseException e) {
            if (e.getStatusCode() == 401) {
                logger.warn("Strava 401: Unauthorized while fetching activity {}", id);
            }
            throw e;
        }
    }

    private void refreshAccessToken() throws IOException {
        logger.info("Refreshing Strava access token...");
        try {
            String response = Request.post("https://www.strava.com/oauth/token")
                    .bodyForm(Form.form()
                            .add("client_id", clientId)
                            .add("client_secret", clientSecret)
                            .add("refresh_token", refreshToken)
                            .add("grant_type", "refresh_token")
                            .build())
                    .execute()
                    .returnContent()
                    .asString();

            JsonNode node = objectMapper.readTree(response);
            this.accessToken = node.get("access_token").asText();

            if (node.has("scope")) {
                String scopes = node.get("scope").asText();
                if (!scopes.contains("activity:read")) {
                    logger.warn("Token lacks 'activity:read' scope. Re-authorize with activity:read_all or activity:read.");
                }
            }
        } catch (org.apache.hc.client5.http.HttpResponseException e) {
            logger.error("Error refreshing Strava token: {}", e.getMessage());
            if (e.getStatusCode() == 400 || e.getStatusCode() == 401) {
                logger.error("Check STRAVA_CLIENT_ID, STRAVA_CLIENT_SECRET, and STRAVA_REFRESH_TOKEN.");
            }
            throw e;
        }
    }
}
