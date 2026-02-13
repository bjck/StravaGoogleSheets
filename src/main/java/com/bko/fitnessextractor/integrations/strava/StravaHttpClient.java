package com.bko.fitnessextractor.integrations.strava;

import com.bko.fitnessextractor.shared.AppSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
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
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public StravaHttpClient(AppSettings settings) {
        this.clientId = settings.strava().clientId();
        this.clientSecret = settings.strava().clientSecret();
        this.refreshToken = settings.strava().refreshToken();
    }

    @Override
    public List<StravaActivity> getActivities(int page, int perPage, Instant after, Instant before) throws IOException {
        if (accessToken == null) {
            refreshAccessToken();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildActivitiesUrl(page, perPage, after, before)))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling Strava API", ie);
        }
        if (response.statusCode() == 401) {
            logger.warn("Strava 401: Unauthorized. Access token may be invalid or missing scopes.");
        }
        if (response.statusCode() >= 400) {
            throw new IOException("Strava API error: HTTP " + response.statusCode());
        }
        return Arrays.asList(objectMapper.readValue(response.body(), StravaActivity[].class));
    }

    @Override
    public StravaActivity getActivity(Long id) throws IOException {
        if (accessToken == null) {
            refreshAccessToken();
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(STRAVA_API_BASE + "/activities/" + id))
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling Strava API", ie);
        }
        if (response.statusCode() == 401) {
            logger.warn("Strava 401: Unauthorized while fetching activity {}", id);
        }
        if (response.statusCode() >= 400) {
            throw new IOException("Strava API error: HTTP " + response.statusCode());
        }
        return objectMapper.readValue(response.body(), StravaActivity.class);
    }

    private void refreshAccessToken() throws IOException {
        logger.info("Refreshing Strava access token...");
        String form = "client_id=" + enc(clientId)
                + "&client_secret=" + enc(clientSecret)
                + "&refresh_token=" + enc(refreshToken)
                + "&grant_type=refresh_token";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://www.strava.com/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while refreshing Strava token", ie);
        }
        if (response.statusCode() == 400 || response.statusCode() == 401) {
            logger.error("Error refreshing Strava token: HTTP {} - {}", response.statusCode(), response.body());
            logger.error("Check STRAVA_CLIENT_ID, STRAVA_CLIENT_SECRET, and STRAVA_REFRESH_TOKEN.");
            throw new IOException("Strava auth error: HTTP " + response.statusCode());
        }
        if (response.statusCode() >= 400) {
            throw new IOException("Strava auth error: HTTP " + response.statusCode());
        }

        JsonNode node = objectMapper.readTree(response.body());
        this.accessToken = node.get("access_token").asText();

        if (node.has("scope")) {
            String scopes = node.get("scope").asText();
            if (!scopes.contains("activity:read")) {
                logger.warn("Token lacks 'activity:read' scope. Re-authorize with activity:read_all or activity:read.");
            }
        }
    }

    private String buildActivitiesUrl(int page, int perPage, Instant after, Instant before) {
        StringBuilder url = new StringBuilder(STRAVA_API_BASE)
                .append("/athlete/activities?page=")
                .append(page)
                .append("&per_page=")
                .append(perPage);
        if (after != null) {
            url.append("&after=").append(after.getEpochSecond());
        }
        if (before != null) {
            url.append("&before=").append(before.getEpochSecond());
        }
        return url.toString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
