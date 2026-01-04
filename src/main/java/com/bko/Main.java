package com.bko;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        Config config = new Config();

        // Strava stays the same
        String stravaClientId = config.get("strava.client_id");
        String stravaClientSecret = config.get("strava.client_secret");
        String stravaRefreshToken = config.get("strava.refresh_token");

        if (args.length > 0) {
            if (args[0].equals("--setup-strava")) {
                setupStrava(stravaClientId, stravaClientSecret);
                return;
            } else if (args[0].equals("--exchange-code") && args.length > 1) {
                exchangeCode(stravaClientId, stravaClientSecret, args[1]);
                return;
            }
        }

        // Google Changes
        String googleSpreadsheetId = config.get("google.spreadsheet_id");
        // This is the path to the JSON file you downloaded from Google Cloud Console
        String serviceAccountKeyPath = config.get("google.service_account_key_path");

        if (stravaClientId == null || stravaClientSecret == null || stravaRefreshToken == null ||
                googleSpreadsheetId == null || serviceAccountKeyPath == null) {
            // Update your error logging to reflect the new requirement
            System.err.println("Missing configuration. Check .env for Strava keys and GOOGLE_SERVICE_ACCOUNT_KEY_PATH");
            return;
        }

        try {
            StravaService stravaService = new StravaService(stravaClientId, stravaClientSecret, stravaRefreshToken);
            GoogleSheetsService googleSheetsService = new GoogleSheetsService(googleSpreadsheetId, serviceAccountKeyPath);

            String sheetName = googleSheetsService.getFirstSheetName();
            System.out.println("Using sheet: " + sheetName);

            System.out.println("Fetching existing IDs from Google Sheets...");
            List<List<Object>> existingData = googleSheetsService.getExistingValues(sheetName + "!A:A");

            List<Object> headers = List.of(
                "Activity ID", "Name", "Type", "Distance (m)", "Moving Time (s)",
                "Start Date", "Avg Speed (m/s)", "Max Speed (m/s)", "Elevation Gain (m)",
                "Avg Heart Rate", "Max Heart Rate", "Avg Watts", "Kilojoules", "Suffer Score"
            );
            googleSheetsService.ensureHeaders(sheetName, headers);

            Set<String> existingIds = new HashSet<>();
            if (existingData != null) {
                for (List<Object> row : existingData) {
                    if (!row.isEmpty()) {
                        String id = row.get(0).toString();
                        // Ignore the header row if it exists
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
                System.out.println("Fetching Strava page " + page + "...");
                List<StravaActivity> pageActivities = stravaService.getActivities(page, 100);
                if (pageActivities.isEmpty()) break;

                int addedInPage = 0;
                for (StravaActivity a : pageActivities) {
                    if (!existingIds.contains(a.getId().toString())) {
                        newActivities.add(a);
                        addedInPage++;
                    }
                }
                
                System.out.println("Found " + addedInPage + " new activities on page " + page);
                
                // If we found NO new activities on this page, and we already had data in the sheet,
                // it means we've caught up with the "newest" activities and there's no gap to fill.
                if (addedInPage == 0 && !existingIds.isEmpty()) {
                    System.out.println("Reached already synced activities. Stopping.");
                    break;
                }

                if (pageActivities.size() < 100) break;
                page++;
                if (page > 50) {
                    System.out.println("Reached 50 pages limit. Stopping.");
                    break;
                }
            }

            System.out.println("Total new activities to sync: " + newActivities.size());

            if (newActivities.isEmpty()) {
                System.out.println("No new activities to sync.");
            } else {
                System.out.println("Syncing " + newActivities.size() + " new activities...");
                
                List<List<Object>> valuesToAppend = new ArrayList<>();
                for (StravaActivity a : newActivities) {
                    List<Object> row = new ArrayList<>();
                    row.add(a.getId().toString());
                    row.add(a.getName());
                    row.add(a.getType());
                    row.add(a.getDistance());
                    row.add(a.getMovingTime());
                    row.add(a.getStartDate());
                    row.add(a.getAverageSpeed());
                    row.add(a.getMaxSpeed());
                    row.add(a.getTotalElevationGain());
                    row.add(a.getAverageHeartrate());
                    row.add(a.getMaxHeartrate());
                    row.add(a.getAverageWatts());
                    row.add(a.getKilojoules());
                    row.add(a.getSufferScore());
                    valuesToAppend.add(row);
                }

                googleSheetsService.appendActivities(sheetName + "!A1", valuesToAppend);
                System.out.println("Sync complete!");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
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
