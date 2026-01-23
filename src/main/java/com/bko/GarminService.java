package com.bko;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Form;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GarminService {
    private static final String SSO_URL = "https://sso.garmin.com/sso/signin";
    private static final String CONNECT_URL = "https://connect.garmin.com/modern";
    private static final String CONNECT_API_URL = "https://connectapi.garmin.com";
    
    private final String username;
    private final String password;
    private final String manualSessionCookie;
    private final String manualGarthToken;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BasicCookieStore cookieStore = new BasicCookieStore();
    private final Executor executor;
    private String displayName;
    private String oauth2Token;
    private String tokenScript;
    private String pythonPath;

    public GarminService(String username, String password) {
        this(username, password, null, null);
    }

    public GarminService(String username, String password, String manualSessionCookie) {
        this(username, password, manualSessionCookie, null);
    }

    public GarminService(String username, String password, String manualSessionCookie, String manualGarthToken) {
        this.username = username;
        this.password = password;
        this.manualSessionCookie = manualSessionCookie;
        this.manualGarthToken = manualGarthToken;
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .build();
        this.executor = Executor.newInstance(httpClient);
    }

    public void setTokenRefreshConfig(String tokenScript, String pythonPath) {
        this.tokenScript = tokenScript;
        this.pythonPath = pythonPath;
    }

    private boolean refreshGarthToken() {
        if (tokenScript == null || pythonPath == null) {
            System.err.println("Token refresh script or python path not configured.");
            return false;
        }

        // 1. Try running with credentials to get a fresh session
        System.out.println("Refreshing Garmin token with credentials...");
        String result = runRefreshScript(true);
        
        // 2. If no result, try resume path
        if (result == null) {
            System.out.println("Credentials-based refresh failed, trying to resume existing session...");
            result = runRefreshScript(false);
        }
        
        if (result != null) {
            if (applyManualToken(result)) {
                System.out.println("Successfully applied refreshed token/bundle.");
                updateEnvFile(result);
                return true;
            }
        }
        
        return false;
    }

    private String runRefreshScript(boolean withCredentials) {
        try {
            ProcessBuilder pb = new ProcessBuilder(pythonPath, tokenScript);
            if (withCredentials) {
                if (username != null) {
                    pb.environment().put("GARMIN_EMAIL", username);
                    pb.environment().put("EMAIL", username);
                }
                if (password != null) {
                    pb.environment().put("GARMIN_PASSWORD", password);
                    pb.environment().put("PASSWORD", password);
                }
            } else {
                pb.environment().remove("GARMIN_EMAIL");
                pb.environment().remove("EMAIL");
                pb.environment().remove("GARMIN_PASSWORD");
                pb.environment().remove("PASSWORD");
            }
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()));
            String line;
            String garthBundle = null;
            String oauth2Token = null;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("Garth Bundle:")) {
                    garthBundle = line.split("Garth Bundle:")[1].trim();
                } else if (line.contains("OAuth2 Access Token:")) {
                    oauth2Token = line.split("OAuth2 Access Token:")[1].trim();
                }
            }
            process.waitFor();
            
            // Prefer bundle over direct token
            return (garthBundle != null) ? garthBundle : oauth2Token;
        } catch (Exception e) {
            System.err.println("Error running token refresh script: " + e.getMessage());
            return null;
        }
    }

    private void updateEnvFile(String newToken) {
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (!java.nio.file.Files.exists(envPath)) return;
            
            List<String> lines = java.nio.file.Files.readAllLines(envPath);
            List<String> newLines = new ArrayList<>();
            boolean found = false;
            for (String line : lines) {
                if (line.trim().startsWith("GARMIN_GARTH_TOKEN=")) {
                    newLines.add("GARMIN_GARTH_TOKEN=" + newToken);
                    found = true;
                } else {
                    newLines.add(line);
                }
            }
            if (!found) {
                newLines.add("GARMIN_GARTH_TOKEN=" + newToken);
            }
            java.nio.file.Files.write(envPath, newLines);
            System.out.println("Updated .env file with new GARMIN_GARTH_TOKEN.");
        } catch (Exception e) {
            System.err.println("Could not update .env file: " + e.getMessage());
        }
    }

    private boolean applyManualToken(String token) {
        if (token == null || token.isEmpty()) return false;
        String input = token.trim();

        // 1. Try as direct JWT
        if (input.startsWith("eyJ") && input.contains(".")) {
            this.oauth2Token = input;
            System.out.println("Applying direct OAuth2 JWT...");
            cookieStore.clear();
            fetchDisplayName();
            if (this.displayName != null) return true;
            this.oauth2Token = null;
        }

        // 2. Try as base64 bundle (Garth format)
        try {
            byte[] decodedBytes;
            try {
                decodedBytes = java.util.Base64.getDecoder().decode(input);
            } catch (IllegalArgumentException e) {
                decodedBytes = java.util.Base64.getUrlDecoder().decode(input);
            }
            String decoded = new String(decodedBytes);
            JsonNode root = objectMapper.readTree(decoded);
            
            if (root.isArray()) {
                if (root.size() >= 2) {
                    JsonNode oauth2 = root.get(1);
                    if (oauth2.has("access_token")) {
                        this.oauth2Token = oauth2.get("access_token").asText().trim();
                        System.out.println("Applying OAuth2 token from Garth bundle...");
                        cookieStore.clear();
                        
                        if (root.size() >= 3) {
                            JsonNode domains = root.get(2);
                            applyGarthCookies(domains);
                        }
                        
                        fetchDisplayName();
                        if (this.displayName != null) return true;
                        this.oauth2Token = null;
                    }
                }
            } else if (root.isObject() && root.has("access_token")) {
                this.oauth2Token = root.get("access_token").asText().trim();
                System.out.println("Applying OAuth2 token from JSON object...");
                cookieStore.clear();
                fetchDisplayName();
                if (this.displayName != null) return true;
                this.oauth2Token = null;
            }
        } catch (Exception e) {
            if (!input.startsWith("eyJ")) {
                System.err.println("Error parsing Garth bundle: " + e.getMessage());
            }
        }

        return false;
    }

    private void applyGarthCookies(JsonNode domains) {
        if (domains == null || !domains.isObject()) return;
        
        java.util.Iterator<String> fieldNames = domains.fieldNames();
        while (fieldNames.hasNext()) {
            String domain = fieldNames.next();
            JsonNode cookies = domains.get(domain);
            if (cookies.isObject()) {
                java.util.Iterator<String> cookieNames = cookies.fieldNames();
                while (cookieNames.hasNext()) {
                    String cookieName = cookieNames.next();
                    JsonNode cookieData = cookies.get(cookieName);
                    if (cookieData.has("value")) {
                        String value = cookieData.get("value").asText();
                        addCookie(cookieName, value, domain.startsWith(".") ? domain : "." + domain);
                    }
                }
            }
        }
    }

    private void addCookie(String name, String value) {
        String domain = name.startsWith("GARMIN-SSO") ? ".garmin.com" : ".connect.garmin.com";
        addCookie(name, value, domain);
    }

    private void addCookie(String name, String value, String domain) {
        org.apache.hc.client5.http.impl.cookie.BasicClientCookie cookie = new org.apache.hc.client5.http.impl.cookie.BasicClientCookie(name, value);
        cookie.setDomain(domain);
        cookie.setPath("/");
        cookieStore.addCookie(cookie);
    }

    public void login() throws IOException, URISyntaxException {
        if (manualGarthToken != null && !manualGarthToken.isEmpty()) {
            if (applyManualToken(manualGarthToken)) {
                return;
            }
        }

        if (manualSessionCookie != null && !manualSessionCookie.isEmpty()) {
            System.out.println("Applying manual Garmin cookies...");
            if (manualSessionCookie.contains("=")) {
                String[] cookies = manualSessionCookie.split(";");
                for (String c : cookies) {
                    String[] parts = c.trim().split("=", 2);
                    if (parts.length == 2) {
                        addCookie(parts[0].trim(), parts[1].trim());
                        System.out.println("Applied cookie: " + parts[0].trim());
                    }
                }
            } else {
                addCookie("SESSION", manualSessionCookie);
                addCookie("session", manualSessionCookie);
                System.out.println("Applied session cookie (tried both SESSION and session).");
            }
            fetchDisplayName();
            return;
        }

        System.out.println("Logging in to Garmin Connect...");
        
        if (this.displayName == null && tokenScript != null) {
            System.out.println("No valid profile found yet. Attempting token refresh via script...");
            if (refreshGarthToken()) {
                fetchDisplayName();
            }
        }

        if (this.displayName != null) {
            System.out.println("Garmin login bypassed using valid Garth token.");
            return;
        }
        
        // 0. Visit modern to get initial cookies
        executor.execute(Request.get(CONNECT_URL)).returnContent();
        
        URI ssoUri = new URIBuilder(SSO_URL)
                .addParameter("service", CONNECT_URL)
                .addParameter("webhost", "https://connect.garmin.com")
                .addParameter("source", CONNECT_URL)
                .addParameter("redirectAfterAccountLoginUrl", CONNECT_URL)
                .addParameter("redirectAfterAccountCreationUrl", CONNECT_URL)
                .addParameter("gauthHost", "https://sso.garmin.com/sso")
                .addParameter("locale", "en_US")
                .addParameter("id", "gauth-widget")
                .addParameter("clientId", "GarminConnect")
                .addParameter("rememberMeShown", "true")
                .addParameter("rememberMeChecked", "false")
                .addParameter("createAccountShown", "true")
                .addParameter("openCreateAccount", "false")
                .addParameter("displayNameShown", "false")
                .addParameter("consumeServiceTicket", "false")
                .addParameter("initialFocus", "true")
                .addParameter("embedWidget", "false")
                .addParameter("generateExtraServiceTicket", "true")
                .addParameter("generateTwoFactorTicket", "false")
                .addParameter("generateNoServiceTicket", "false")
                .addParameter("globalOptInShown", "true")
                .addParameter("globalOptInChecked", "false")
                .addParameter("mobile", "false")
                .addParameter("connectLegalTerms", "true")
                .addParameter("locationPromptShown", "true")
                .addParameter("showPassword", "true")
                .build();

        // 1. Initial GET to get SSO cookies
        executor.execute(Request.get(ssoUri)).returnContent();

        // 2. POST credentials
        String loginResponse = executor.execute(Request.post(ssoUri)
                .addHeader("Referer", ssoUri.toString())
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .bodyForm(Form.form()
                        .add("username", username)
                        .add("password", password)
                        .add("embed", "true")
                        .add("_eventId", "submit")
                        .build()))
                .returnContent()
                .asString();

        // 3. Extract ticket from response
        Pattern pattern = Pattern.compile("response_url\\s*=\\s*['\"]([^'\"]+ticket=([^'\"]+))['\"]");
        Matcher matcher = pattern.matcher(loginResponse);
        if (!matcher.find()) {
            if (loginResponse.contains("Invalid user name or password")) {
                throw new IOException("Garmin Login Failed: Invalid user name or password.");
            }
            throw new IOException("Garmin Login Failed: Could not find ticket in response. Possible CAPTCHA or security block.");
        }

        String ticketUrl = matcher.group(1).replace("\\/", "/");
        if (!ticketUrl.startsWith("http")) {
            ticketUrl = "https://connect.garmin.com" + (ticketUrl.startsWith("/") ? "" : "/") + ticketUrl;
        }
        System.out.println("Login successful, ticket URL: " + ticketUrl);

        // 4. Follow ticket URL to establish session on connect.garmin.com
        System.out.println("Exchanging ticket for session...");
        executor.execute(Request.get(ticketUrl)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Referer", "https://sso.garmin.com/")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"))
                .returnContent();
        
        // Sometimes a second visit to the modern URL is needed to finalize session
        executor.execute(Request.get(CONNECT_URL + "/")).returnContent();
        
        System.out.println("Garmin session established.");
        fetchDisplayName();
    }

    private String executeRequest(String path) throws IOException {
        return executeRequest(path, true);
    }

    private String executeRequest(String path, boolean allowRetry) throws IOException {
        String url;
        Request request;

        if (oauth2Token != null) {
            // Use connectapi for OAuth2
            url = path.startsWith("http") ? path : CONNECT_API_URL + path;
            // Remove /modern/proxy if present in path when switching to connectapi
            url = url.replace("https://connect.garmin.com/modern/proxy", CONNECT_API_URL);
            
            request = Request.get(url)
                    .addHeader("Authorization", "Bearer " + oauth2Token.trim())
                    .addHeader("User-Agent", "GCM-iOS-5.7.2.1")
                    .addHeader("Accept", "application/json");
        } else {
            // Use connect.garmin.com for session cookies
            if (path.startsWith("http")) {
                url = path;
            } else {
                // Add /modern/proxy prefix if not present for the session-based URL
                String prefix = path.startsWith("/modern/proxy") ? "" : "/modern/proxy";
                url = "https://connect.garmin.com" + prefix + (path.startsWith("/") ? "" : "/") + path;
            }
            request = Request.get(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                    .addHeader("di-backend", "connectapi.garmin.com")
                    .addHeader("NK", "NT")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Accept", "application/json, text/plain, */*");
        }

        try {
            return executor.execute(request).returnContent().asString();
        } catch (org.apache.hc.client5.http.HttpResponseException e) {
            if (allowRetry && e.getStatusCode() == 401 && tokenScript != null) {
                System.out.println("Request to " + url + " failed with 401, attempting token refresh...");
                if (refreshGarthToken()) {
                    return executeRequest(path, false);
                }
            }
            System.err.println("Error executing request to " + url + ": " + e.getStatusCode() + " " + e.getReasonPhrase());
            throw e;
        }
    }

    private void fetchDisplayName() {
        try {
            System.out.println("Fetching Garmin display name...");
            String path = "/userprofile-service/socialProfile";
            String response;
            try {
                response = executeRequest(path);
            } catch (IOException e) {
                // Try fallback to user-settings
                System.out.println("Social profile failed, trying user-settings fallback...");
                path = "/userprofile-service/userprofile/user-settings";
                response = executeRequest(path);
            }

            JsonNode node = objectMapper.readTree(response);
            if (node.has("displayName")) {
                this.displayName = node.get("displayName").asText();
                System.out.println("Garmin display name (UUID): " + displayName);
            } else if (node.has("userName")) {
                this.displayName = node.get("userName").asText();
                System.out.println("Garmin display name (username fallback): " + displayName);
            } else if (node.has("userData") && node.get("userData").has("displayName")) {
                this.displayName = node.get("userData").get("displayName").asText();
                System.out.println("Garmin display name (from settings UUID): " + displayName);
            } else if (node.has("userData") && node.get("userData").has("userName")) {
                this.displayName = node.get("userData").get("userName").asText();
                System.out.println("Garmin display name (from settings username): " + displayName);
            } else {
                System.err.println("Could not find display name in response.");
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not fetch display name: " + e.getMessage());
        }
    }

    public GarminMetrics getMetricsForDate(LocalDate date) throws IOException {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        GarminMetrics metrics = new GarminMetrics();
        metrics.setDate(dateStr);

        try {
            // 1. Body Battery (startDate, endDate)
            String bbPath = "/wellness-service/wellness/bodyBattery/reports/daily?startDate=" + dateStr + "&endDate=" + dateStr;
            String bbResponse = executeRequest(bbPath);
            JsonNode bbNode = objectMapper.readTree(bbResponse);
            if (bbNode.isArray() && bbNode.size() > 0) {
                JsonNode day = bbNode.get(0);
                if (day.has("min")) {
                    metrics.setBodyBatteryLowest(day.get("min").asInt());
                }
                if (day.has("max")) {
                    metrics.setBodyBatteryHighest(day.get("max").asInt());
                }
                
                // Fallback to bodyBatteryValuesArray if min/max not present
                if ((metrics.getBodyBatteryLowest() == null || metrics.getBodyBatteryHighest() == null) && day.has("bodyBatteryValuesArray")) {
                    int min = 100;
                    int max = 0;
                    boolean found = false;
                    for (JsonNode entry : day.get("bodyBatteryValuesArray")) {
                        if (entry.isArray() && entry.size() >= 2) {
                            int val = entry.get(1).asInt();
                            if (val < min) min = val;
                            if (val > max) max = val;
                            found = true;
                        }
                    }
                    if (found) {
                        metrics.setBodyBatteryLowest(min);
                        metrics.setBodyBatteryHighest(max);
                    }
                }
                System.out.println("  Body Battery Min: " + metrics.getBodyBatteryLowest() + ", Max: " + metrics.getBodyBatteryHighest());
            }

            // 2. User Summary (Requires displayName)
            if (this.displayName != null) {
                String summaryPath = "/usersummary-service/usersummary/daily/" + this.displayName + "?calendarDate=" + dateStr;
                String summaryResponse = executeRequest(summaryPath);
                JsonNode summaryNode = objectMapper.readTree(summaryResponse);
                
                if (summaryNode.has("restingHeartRate") && !summaryNode.get("restingHeartRate").isNull()) {
                    metrics.setRestingHeartRate(summaryNode.get("restingHeartRate").asInt());
                    System.out.println("  Resting HR: " + metrics.getRestingHeartRate());
                }

                if (summaryNode.has("vo2Max") && !summaryNode.get("vo2Max").isNull()) {
                    metrics.setVo2Max(summaryNode.get("vo2Max").asDouble());
                    System.out.println("  VO2 Max: " + metrics.getVo2Max());
                }

                if (metrics.getWeight() == null && summaryNode.has("wellnessWeight") && !summaryNode.get("wellnessWeight").isNull()) {
                    metrics.setWeight(summaryNode.get("wellnessWeight").asDouble() / 1000.0);
                    System.out.println("  Weight (from summary wellnessWeight): " + metrics.getWeight());
                }

                if (metrics.getWeight() == null && summaryNode.has("weight") && !summaryNode.get("weight").isNull()) {
                    metrics.setWeight(summaryNode.get("weight").asDouble() / 1000.0);
                    System.out.println("  Weight (from summary weight): " + metrics.getWeight());
                }
            }

            // 4. Weight
            String weightPath = "/weight-service/weight/dateRange?startDate=" + dateStr + "&endDate=" + dateStr;
            String weightResponse = executeRequest(weightPath);
            JsonNode weightNode = objectMapper.readTree(weightResponse);
            if (weightNode.has("weightUnitEntries") && weightNode.get("weightUnitEntries").isArray() && weightNode.get("weightUnitEntries").size() > 0) {
                metrics.setWeight(weightNode.get("weightUnitEntries").get(0).get("weight").asDouble() / 1000.0); // Weight is in grams
                System.out.println("  Weight: " + metrics.getWeight());
            }

            // 5. Sleep (Requires displayName, uses 'date' and 'nonSleepBufferMinutes')
            if (this.displayName != null) {
                String sleepPath = "/wellness-service/wellness/dailySleepData/" + this.displayName + "?date=" + dateStr + "&nonSleepBufferMinutes=60";
                String sleepResponse = executeRequest(sleepPath);
                JsonNode sleepNode = objectMapper.readTree(sleepResponse);
                if (sleepNode.has("dailySleepDTO")) {
                    JsonNode dto = sleepNode.get("dailySleepDTO");
                    if (dto.has("sleepTimeSeconds")) {
                        metrics.setSleepDurationHours(dto.get("sleepTimeSeconds").asDouble() / 3600.0);
                        System.out.println("  Sleep Duration: " + metrics.getSleepDurationHours());
                    }
                    if (dto.has("sleepScore")) {
                        metrics.setSleepScore(dto.get("sleepScore").asInt());
                    } else if (dto.has("sleepScores") && dto.get("sleepScores").has("overall")) {
                        metrics.setSleepScore(dto.get("sleepScores").get("overall").get("value").asInt());
                    }
                    
                    if (metrics.getSleepScore() != null) {
                        System.out.println("  Sleep Score: " + metrics.getSleepScore());
                    }
                }
            }
            
            // 6. Resting HR Fallback / Alternative (fromDate, untilDate, metricId=60)
            if (this.displayName != null && (metrics.getRestingHeartRate() == null || metrics.getRestingHeartRate() == 0)) {
                try {
                    String rhrPath = "/userstats-service/wellness/daily/" + this.displayName + "?fromDate=" + dateStr + "&untilDate=" + dateStr + "&metricId=60";
                    String rhrResponse = executeRequest(rhrPath);
                    JsonNode rhrNode = objectMapper.readTree(rhrResponse);
                    if (rhrNode.isArray() && rhrNode.size() > 0) {
                        JsonNode entry = rhrNode.get(0);
                        if (entry.has("value")) {
                            metrics.setRestingHeartRate(entry.get("value").asInt());
                        }
                    }
                } catch (Exception e) {
                    // Fallback failed, no big deal
                }
            }

        } catch (Exception e) {
            System.err.println("Error fetching some Garmin metrics for " + dateStr + ": " + e.getMessage());
        }

        return metrics;
    }

    public List<GarminMetrics> getMetricsForLastDays(int days) throws IOException {
        List<GarminMetrics> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            System.out.println("Fetching Garmin metrics for " + date + "...");
            list.add(getMetricsForDate(date));
        }
        return list;
    }
}
