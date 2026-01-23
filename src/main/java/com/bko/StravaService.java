package com.bko;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.fluent.Form;
import org.apache.hc.client5.http.fluent.Request;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class StravaService {
    private static final String STRAVA_API_BASE = "https://www.strava.com/api/v3";
    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private String accessToken;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StravaService(String clientId, String clientSecret, String refreshToken) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
    }

    public void refreshAccessToken() throws IOException {
        System.out.println("Refreshing Strava access token...");
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
                System.out.println("Authorized scopes: " + scopes);
                if (!scopes.contains("activity:read")) {
                    System.err.println("WARNING: Token does not have 'activity:read' scope. " +
                            "You may need to re-authorize with 'activity:read_all' or 'activity:read'.");
                }
            }
        } catch (org.apache.hc.client5.http.HttpResponseException e) {
            System.err.println("Error refreshing Strava token: " + e.getMessage());
            if (e.getStatusCode() == 400 || e.getStatusCode() == 401) {
                System.err.println("Check your STRAVA_CLIENT_ID, STRAVA_CLIENT_SECRET, and STRAVA_REFRESH_TOKEN in .env");
            }
            throw e;
        }
    }

    public void exchangeCodeWithToken(String code) throws IOException {
        System.out.println("Exchanging code for tokens...");
        try {
            String response = Request.post("https://www.strava.com/oauth/token")
                    .bodyForm(Form.form()
                            .add("client_id", clientId)
                            .add("client_secret", clientSecret)
                            .add("code", code)
                            .add("grant_type", "authorization_code")
                            .build())
                    .execute()
                    .returnContent()
                    .asString();

            JsonNode node = objectMapper.readTree(response);
            String newRefreshToken = node.get("refresh_token").asText();
            String newAccessToken = node.get("access_token").asText();
            String scopes = node.has("scope") ? node.get("scope").asText() : "unknown";

            System.out.println("\n--- SUCCESS ---");
            System.out.println("New Refresh Token: " + newRefreshToken);
            System.out.println("New Access Token: " + newAccessToken);
            System.out.println("Scopes: " + scopes);
            System.out.println("----------------\n");
            System.out.println("IMPORTANT: Update your .env file with this STRAVA_REFRESH_TOKEN!");
        } catch (org.apache.hc.client5.http.HttpResponseException e) {
            System.err.println("Error exchanging code: " + e.getStatusCode() + " " + e.getReasonPhrase());
            throw e;
        }
    }

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
                System.err.println("Error 401: Unauthorized. Your access token might be invalid or missing the 'activity:read' scope.");
                System.err.println("Please ensure you authorized the app with the correct scopes.");
            }
            throw e;
        }
    }

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
                System.err.println("Error 401: Unauthorized.");
            }
            throw e;
        }
    }
}
