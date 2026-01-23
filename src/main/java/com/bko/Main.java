package com.bko;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.time.temporal.ChronoUnit.DAYS;

public class Main {

    public static final String STRAVA_CLIENT_ID = "strava.client_id";
    public static final String STRAVA_CLIENT_SECRET = "strava.client_secret";
    public static final String STRAVA_REFRESH_TOKEN = "strava.refresh_token";
    public static final String GARMIN_USERNAME = "garmin.username";
    public static final String GARMIN_PASSWORD = "garmin.password";
    public static final String GOOGLE_SPREADSHEET_ID = "google.spreadsheet_id";
    public static final String GOOGLE_SERVICE_ACCOUNT_KEY_PATH = "google.service_account_key_path";
    public static final String GARMIN_SESSION_COOKIE = "garmin.session_cookie";
    public static final String GARMIN_GARTH_TOKEN = "garmin.garth_token";
    public static final String GARMIN_TOKEN_SCRIPT = "garmin.token_script";
    public static final String GARMIN_PYTHON_PATH = "garmin.python_path";

    public static void main(String[] args) {
        Config config = new Config();

        String stravaClientId = config.get(STRAVA_CLIENT_ID);
        String stravaClientSecret = config.get(STRAVA_CLIENT_SECRET);
        String stravaRefreshToken = config.get(STRAVA_REFRESH_TOKEN);

        if (args.length > 0) {
            if (args[0].equals("--setup-strava")) {
                setupStrava(stravaClientId, stravaClientSecret);
                return;
            } else if (args[0].equals("--exchange-code") && args.length > 1) {
                exchangeCode(stravaClientId, stravaClientSecret, args[1]);
                return;
            }
        }

        // Garmin
        String garminUsername = config.get(GARMIN_USERNAME);
        String garminPassword = config.get(GARMIN_PASSWORD);

        // Google Changes
        String googleSpreadsheetId = config.get(GOOGLE_SPREADSHEET_ID);
        // This is the path to the JSON file you downloaded from Google Cloud Console
        String serviceAccountKeyPath = config.get(GOOGLE_SERVICE_ACCOUNT_KEY_PATH);

        if (googleSpreadsheetId == null || serviceAccountKeyPath == null) {
            System.err.println("Missing Google configuration. Check .env for GOOGLE_SPREADSHEET_ID and GOOGLE_SERVICE_ACCOUNT_KEY_PATH");
            return;
        }

        try {
            GoogleSheetsService googleSheetsService = new GoogleSheetsService(googleSpreadsheetId, serviceAccountKeyPath);

            // --- Strava Sync ---
            if (areStravaCredentialsValid(stravaClientId, stravaClientSecret, stravaRefreshToken)) {
                syncStrava(googleSheetsService, stravaClientId, stravaClientSecret, stravaRefreshToken);
            } else {
                System.out.println("Strava credentials missing, skipping Strava sync.");
            }

            // --- Garmin Sync ---
            if (areGarmingCredentialsValid(garminUsername, garminPassword)) {
                syncGarmin(googleSheetsService, garminUsername, garminPassword);
            } else {
                System.out.println("Garmin credentials missing, skipping Garmin sync.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean areGarmingCredentialsValid(String garminUsername, String garminPassword) {
        return garminUsername != null && garminPassword != null;
    }

    private static boolean areStravaCredentialsValid(String stravaClientId, String stravaClientSecret, String stravaRefreshToken) {
        return stravaClientId != null && stravaClientSecret != null && stravaRefreshToken != null;
    }

    private static void syncStrava(GoogleSheetsService googleSheetsService, String clientId, String clientSecret, String refreshToken) throws Exception {
        System.out.println("\n--- Starting Strava Sync ---");
        StravaService stravaService = new StravaService(clientId, clientSecret, refreshToken);
        
        String sheetName = "Strava Activities"; // Use a specific name
        googleSheetsService.createSheet(sheetName);
        
        System.out.println("Fetching existing IDs from Google Sheets...");
        List<List<Object>> existingData = null;
        try {
            existingData = googleSheetsService.getExistingValues(sheetName + "!A:A");
        } catch (Exception e) {
            // Sheet might be new
        }

        List<Object> headers = List.of(
            "Activity ID", "Name", "Type", "Distance (m)", "Moving Time (s)", "Elapsed Time (s)",
            "Start Date", "Avg Speed (m/s)", "Max Speed (m/s)", "Elevation Gain (m)",
            "Avg Heart Rate", "Max Heart Rate", "Avg Watts", "Kilojoules", "Suffer Score", "Description"
        );
        googleSheetsService.ensureHeaders(sheetName, headers);

        Set<String> existingIds = new HashSet<>();
        if (existingData != null) {
            for (List<Object> row : existingData) {
                if (!row.isEmpty()) {
                    String id = row.get(0).toString();
                    if (!id.equalsIgnoreCase("Activity ID")) {
                        existingIds.add(id);
                    }
                }
            }
        }

        System.out.println("Fetching activities from Strava...");
        List<StravaActivity> newActivities = new ArrayList<>();
        int page = 1;
        while (true) {
            List<StravaActivity> pageActivities = stravaService.getActivities(page, 100);
            if (pageActivities.isEmpty()) break;

            int addedInPage = 0;
            for (StravaActivity a : pageActivities) {
                if (!existingIds.contains(a.getId().toString())) {
                    System.out.println("Fetching details for activity: " + a.getId() + " - " + a.getName());
                    try {
                        StravaActivity detailed = stravaService.getActivity(a.getId());
                        newActivities.add(detailed);
                    } catch (IOException e) {
                        System.err.println("Could not fetch details for activity " + a.getId() + ": " + e.getMessage());
                        newActivities.add(a); // Fallback to summary if detail fails
                    }
                    addedInPage++;
                }
            }
            
            System.out.println("Found " + addedInPage + " new activities on page " + page);
            if (addedInPage == 0 && !existingIds.isEmpty()) break;
            if (pageActivities.size() < 100) break;
            page++;
            if (page > 50) break;
        }

        if (newActivities.isEmpty()) {
            System.out.println("No new Strava activities to sync.");
        } else {
            List<List<Object>> valuesToAppend = new ArrayList<>();
            for (StravaActivity a : newActivities) {
                List<Object> row = new ArrayList<>();
                row.add(n(a.getId()));
                row.add(n(a.getName()));
                row.add(n(a.getType()));
                row.add(n(a.getDistance()));
                row.add(n(a.getMovingTime()));
                row.add(n(a.getElapsedTime()));
                row.add(n(a.getStartDate()));
                row.add(n(a.getAverageSpeed()));
                row.add(n(a.getMaxSpeed()));
                row.add(n(a.getTotalElevationGain()));
                row.add(n(a.getAverageHeartrate()));
                row.add(n(a.getMaxHeartrate()));
                row.add(n(a.getAverageWatts()));
                row.add(n(a.getKilojoules()));
                row.add(n(a.getSufferScore()));
                row.add(n(a.getDescription()));
                valuesToAppend.add(row);
            }
            googleSheetsService.insertActivitiesAtTop(sheetName, valuesToAppend);
            System.out.println("Strava sync complete!");
        }
    }

    private static Object n(Object o) {
        return o == null ? "" : o;
    }

    private static void syncGarmin(GoogleSheetsService googleSheetsService, String username, String password) throws Exception {
        Config config = new Config();
        String manualCookie = config.get(GARMIN_SESSION_COOKIE);
        String garthToken = config.get(GARMIN_GARTH_TOKEN);
        String tokenScript = config.get(GARMIN_TOKEN_SCRIPT);
        String pythonPath = config.get(GARMIN_PYTHON_PATH);

        System.out.println("\n--- Starting Garmin Sync ---");
        GarminService garminService = new GarminService(username, password, manualCookie, garthToken);
        garminService.setTokenRefreshConfig(tokenScript, pythonPath);
        garminService.login();

        String sheetName = "Garmin Metrics";
        googleSheetsService.createSheet(sheetName);
        googleSheetsService.ensureHeaders(sheetName, GarminMetrics.getHeaders());

        System.out.println("Fetching existing data from Google Sheets...");
        List<List<Object>> existingData = null;
        try {
            existingData = googleSheetsService.getExistingValues(sheetName + "!A:H");
        } catch (Exception e) {
            System.err.println("Could not fetch existing data from Google Sheets: " + e.getMessage());
        }

        String todayStr = LocalDate.now().toString();
        LocalDate latestDate = null;
        java.util.Map<String, Integer> dateToRowIndex = new java.util.HashMap<>();
        
        if (existingData != null) {
            System.out.println("Found " + existingData.size() + " rows in Garmin sheet.");
            for (int i = 0; i < existingData.size(); i++) {
                List<Object> row = existingData.get(i);
                if (!row.isEmpty()) {
                    String dateStr = row.get(0).toString();
                    if (!dateToRowIndex.containsKey(dateStr)) {
                        dateToRowIndex.put(dateStr, i + 1); // 1-based index for Sheets
                    }
                    
                    try {
                        LocalDate d = LocalDate.parse(dateStr);
                        if (latestDate == null || d.isAfter(latestDate)) {
                            latestDate = d;
                        }
                    } catch (Exception e) {
                        System.err.println("Could not parse date from row " + (i + 1) + ": " + e.getMessage());
                    }
                }
            }
        }

        int daysToFetch;
        if (latestDate != null) {
            long diff = DAYS.between(latestDate, LocalDate.now());
            daysToFetch = (int) diff + 1;
            if (daysToFetch < 1) daysToFetch = 1;
            // Always fetch at least today and yesterday to be safe, or just follow the "difference" rule.
            // The user said "only update the difference between the last entry and now".
            System.out.println("Latest date in sheet: " + latestDate + ". Fetching " + daysToFetch + " days.");
        } else {
            System.out.println("No existing data found. Fetching last 30 days.");
            daysToFetch = 180;
        }

        List<GarminMetrics> allMetrics = garminService.getMetricsForLastDays(daysToFetch);
        List<List<Object>> newMetrics = new ArrayList<>();
        
        for (GarminMetrics metrics : allMetrics) {
            if (dateToRowIndex.containsKey(metrics.getDate())) {
                if (metrics.getDate().equals(todayStr)) {
                    int rowIndex = dateToRowIndex.get(metrics.getDate());
                    System.out.println("Updating today's metrics: " + metrics.getDate() + " at row " + rowIndex);
                    googleSheetsService.updateRow(sheetName + "!A" + rowIndex, metrics.toRow());
                } else {
                    System.out.println("Skipping update for existing historical date: " + metrics.getDate());
                }
            } else {
                System.out.println("Adding new metrics for date: " + metrics.getDate());
                newMetrics.add(metrics.toRow());
            }
        }

        if (!newMetrics.isEmpty()) {
            googleSheetsService.insertActivitiesAtTop(sheetName, newMetrics);
        }
        System.out.println("Garmin sync complete!");
    }

    private static void setupStrava(String clientId, String clientSecret) {
        String authUrl = "https://www.strava.com/oauth/authorize?client_id=" + clientId +
                "&response_type=code&redirect_uri=http://localhost:8080&approval_prompt=force&scope=activity:read_all";

        System.out.println("1. Open this URL in your browser:\n" + authUrl);
        System.out.println("\n2. After authorizing, you will be redirected to a page that fails to load.");
        System.out.println("3. Copy the 'code' parameter from the address bar.");
        System.out.println("4. Run the app again with: mvn exec:java -Dexec.args=\"--exchange-code YOUR_CODE_HERE\"");

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String response;
                if (query != null && query.contains("code=")) {
                    String code = query.split("code=")[1].split("&")[0];
                    response = "Code received: " + code + "\n\nYou can close this window and return to the terminal.";
                    System.out.println("\nReceived code: " + code);
                    System.out.println("Run this command to get your refresh token:");
                    System.out.println("mvn exec:java -Dexec.args=\"--exchange-code " + code + "\"");
                } else {
                    response = "No code found in query parameters.";
                }
                exchange.sendResponseHeaders(200, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                // Stop the server after a short delay
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    server.stop(0);
                }).start();
            });
            server.setExecutor(null);
            server.start();
            System.out.println("\nWaiting for redirect on http://localhost:8080...");
        } catch (IOException e) {
            System.err.println("Could not start local server: " + e.getMessage());
        }
    }

    private static void exchangeCode(String clientId, String clientSecret, String code) {
        try {
            StravaService stravaService = new StravaService(clientId, clientSecret, null);
            stravaService.exchangeCodeWithToken(code);
        } catch (Exception e) {
            System.err.println("Failed to exchange code: " + e.getMessage());
        }
    }
}
