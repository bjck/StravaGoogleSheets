package com.bko.fitnessextractor.integrations.garmin;

import com.bko.fitnessextractor.shared.AppSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Form;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GarminHttpClient implements GarminClientPort {
    private static final Logger logger = LoggerFactory.getLogger(GarminHttpClient.class);
    private static final String SSO_URL = "https://sso.garmin.com/sso/signin";
    private static final String CONNECT_URL = "https://connect.garmin.com/modern";
    private static final String CONNECT_API_URL = "https://connectapi.garmin.com";
    private static final int WELLNESS_BUCKET_MINUTES = 5;

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

    public GarminHttpClient(AppSettings settings) {
        this.username = settings.garmin().username();
        this.password = settings.garmin().password();
        this.manualSessionCookie = settings.garmin().sessionCookie();
        this.manualGarthToken = settings.garmin().garthToken();
        this.tokenScript = settings.garmin().tokenScript();
        this.pythonPath = settings.garmin().pythonPath();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultCookieStore(cookieStore)
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                .build();
        this.executor = Executor.newInstance(httpClient);
    }

    @Override
    public void login() throws IOException {
        try {
            loginInternal();
        } catch (URISyntaxException e) {
            throw new IOException("Failed to build Garmin login URI", e);
        }
    }

    @Override
    public List<GarminMetrics> getMetricsForLastDays(int days) throws IOException {
        List<GarminMetrics> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            logger.info("Fetching Garmin metrics for {}...", date);
            list.add(getMetricsForDate(date));
        }
        return list;
    }

    @Override
    public List<GarminWellnessSample> getWellnessSamplesForLastDays(int days) throws IOException {
        List<GarminWellnessSample> list = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 0; i < days; i++) {
            LocalDate date = today.minusDays(i);
            logger.info("Fetching Garmin stress/HR samples for {}...", date);
            list.addAll(getWellnessSamplesForDate(date));
        }
        return list;
    }

    private void loginInternal() throws IOException, URISyntaxException {
        if (manualGarthToken != null && !manualGarthToken.isEmpty()) {
            if (applyManualToken(manualGarthToken)) {
                return;
            }
        }

        if (manualSessionCookie != null && !manualSessionCookie.isEmpty()) {
            logger.info("Applying manual Garmin cookies...");
            if (manualSessionCookie.contains("=")) {
                String[] cookies = manualSessionCookie.split(";");
                for (String c : cookies) {
                    String[] parts = c.trim().split("=", 2);
                    if (parts.length == 2) {
                        addCookie(parts[0].trim(), parts[1].trim());
                        logger.info("Applied cookie: {}", parts[0].trim());
                    }
                }
            } else {
                addCookie("SESSION", manualSessionCookie);
                addCookie("session", manualSessionCookie);
                logger.info("Applied session cookie (tried both SESSION and session)." );
            }
            fetchDisplayName();
            return;
        }

        logger.info("Logging in to Garmin Connect...");

        if (this.displayName == null && tokenScript != null) {
            logger.info("No valid profile found yet. Attempting token refresh via script...");
            if (refreshGarthToken()) {
                fetchDisplayName();
            }
        }

        if (this.displayName != null) {
            logger.info("Garmin login bypassed using valid Garth token.");
            return;
        }

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

        executor.execute(Request.get(ssoUri)).returnContent();

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
        logger.info("Login successful, ticket URL received.");

        logger.info("Exchanging ticket for session...");
        executor.execute(Request.get(ticketUrl)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .addHeader("Referer", "https://sso.garmin.com/")
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"))
                .returnContent();

        executor.execute(Request.get(CONNECT_URL + "/")).returnContent();

        logger.info("Garmin session established.");
        fetchDisplayName();
    }

    private GarminMetrics getMetricsForDate(LocalDate date) throws IOException {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        GarminMetrics metrics = new GarminMetrics();
        metrics.setDate(dateStr);

        try {
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
            }

            if (this.displayName != null) {
                String summaryPath = "/usersummary-service/usersummary/daily/" + this.displayName + "?calendarDate=" + dateStr;
                String summaryResponse = executeRequest(summaryPath);
                JsonNode summaryNode = objectMapper.readTree(summaryResponse);

                if (summaryNode.has("restingHeartRate") && !summaryNode.get("restingHeartRate").isNull()) {
                    metrics.setRestingHeartRate(summaryNode.get("restingHeartRate").asInt());
                }

                if (summaryNode.has("vo2Max") && !summaryNode.get("vo2Max").isNull()) {
                    metrics.setVo2Max(summaryNode.get("vo2Max").asDouble());
                }

                if (metrics.getWeight() == null && summaryNode.has("wellnessWeight") && !summaryNode.get("wellnessWeight").isNull()) {
                    metrics.setWeight(summaryNode.get("wellnessWeight").asDouble() / 1000.0);
                }

                if (metrics.getWeight() == null && summaryNode.has("weight") && !summaryNode.get("weight").isNull()) {
                    metrics.setWeight(summaryNode.get("weight").asDouble() / 1000.0);
                }
            }

            String weightPath = "/weight-service/weight/dateRange?startDate=" + dateStr + "&endDate=" + dateStr;
            String weightResponse = executeRequest(weightPath);
            JsonNode weightNode = objectMapper.readTree(weightResponse);
            if (weightNode.has("weightUnitEntries") && weightNode.get("weightUnitEntries").isArray() && weightNode.get("weightUnitEntries").size() > 0) {
                metrics.setWeight(weightNode.get("weightUnitEntries").get(0).get("weight").asDouble() / 1000.0);
            }

            if (this.displayName != null) {
                String sleepPath = "/wellness-service/wellness/dailySleepData/" + this.displayName + "?date=" + dateStr + "&nonSleepBufferMinutes=60";
                String sleepResponse = executeRequest(sleepPath);
                JsonNode sleepNode = objectMapper.readTree(sleepResponse);
                if (sleepNode.has("dailySleepDTO")) {
                    JsonNode dto = sleepNode.get("dailySleepDTO");
                    if (dto.has("sleepTimeSeconds")) {
                        metrics.setSleepDurationHours(dto.get("sleepTimeSeconds").asDouble() / 3600.0);
                    }
                    if (dto.has("sleepScore")) {
                        metrics.setSleepScore(dto.get("sleepScore").asInt());
                    } else if (dto.has("sleepScores") && dto.get("sleepScores").has("overall")) {
                        metrics.setSleepScore(dto.get("sleepScores").get("overall").get("value").asInt());
                    }
                }
            }

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
                    logger.debug("Resting HR fallback failed: {}", e.getMessage());
                }
            }

            try {
                String hrvPath = "/hrv-service/hrv/" + dateStr;
                String hrvResponse = executeRequest(hrvPath);
                JsonNode hrvNode = objectMapper.readTree(hrvResponse);
                Double hrvValue = extractHrvValue(hrvNode);
                if (hrvValue != null) {
                    metrics.setHrv(hrvValue);
                }
            } catch (Exception e) {
                logger.debug("HRV fetch failed for {}: {}", dateStr, e.getMessage());
            }

        } catch (Exception e) {
            logger.warn("Error fetching some Garmin metrics for {}: {}", dateStr, e.getMessage());
        }

        return metrics;
    }

    private Double extractHrvValue(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return null;
        }

        Double direct = readFirstNumeric(root,
                "lastNightAvg", "overnightAvg", "hrvValue", "dailyAvg", "dailyHrv",
                "avgHrv", "averageHrv", "rmssdAvg", "rmssdAverage", "rmssd", "hrv");
        if (direct != null) {
            return direct;
        }

        String[] nestedKeys = {"hrvSummary", "summary", "data", "hrvStatus", "lastNight"};
        for (String key : nestedKeys) {
            JsonNode nested = root.get(key);
            Double nestedValue = extractHrvValue(nested);
            if (nestedValue != null) {
                return nestedValue;
            }
        }

        Double series = averageFromSeries(root.get("hrvValuesArray"));
        if (series != null) {
            return series;
        }
        series = averageFromSeries(root.get("hrvValues"));
        if (series != null) {
            return series;
        }
        series = averageFromSeries(root.get("rmssdValues"));
        if (series != null) {
            return series;
        }
        series = averageFromSeries(root.get("values"));
        if (series != null) {
            return series;
        }

        if (root.isArray()) {
            return averageFromSeries(root);
        }

        return null;
    }

    private Double readFirstNumeric(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            JsonNode value = node.get(key);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }
        return null;
    }

    private Double averageFromSeries(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        double total = 0.0;
        int count = 0;
        for (JsonNode entry : node) {
            Double value = null;
            if (entry.isArray() && entry.size() > 1 && entry.get(1).isNumber()) {
                value = entry.get(1).asDouble();
            } else if (entry.isObject()) {
                value = readFirstNumeric(entry, "value", "hrvValue", "rmssd", "hrv");
            } else if (entry.isNumber()) {
                value = entry.asDouble();
            }
            if (value != null) {
                total += value;
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        return total / count;
    }

    private List<GarminWellnessSample> getWellnessSamplesForDate(LocalDate date) throws IOException {
        String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        Map<Long, Integer> stressSeries = new HashMap<>();
        Map<Long, Integer> heartRateSeries = new HashMap<>();

        try {
            String stressPath = "/wellness-service/wellness/dailyStress/" + dateStr;
            String stressResponse = executeRequest(stressPath);
            JsonNode stressNode = objectMapper.readTree(stressResponse);
            stressSeries = parseSeries(stressNode, date,
                    "stressValuesArray", "stressValues", "valuesArray", "values", "stress");
        } catch (Exception e) {
            logger.debug("Stress series fetch failed for {}: {}", dateStr, e.getMessage());
        }

        if (this.displayName != null) {
            try {
                String hrPath = "/wellness-service/wellness/dailyHeartRate/" + this.displayName + "?date=" + dateStr;
                String hrResponse = executeRequest(hrPath);
                JsonNode hrNode = objectMapper.readTree(hrResponse);
                heartRateSeries = parseSeries(hrNode, date,
                        "heartRateValuesArray", "heartRateValues", "hrValuesArray", "hrValues", "values");
            } catch (Exception e) {
                logger.debug("Heart rate series fetch failed for {}: {}", dateStr, e.getMessage());
            }
        } else {
            logger.debug("Skipping heart rate series for {}: display name missing.", dateStr);
        }

        if (stressSeries.isEmpty() && heartRateSeries.isEmpty()) {
            return List.of();
        }

        return bucketWellnessSamples(stressSeries, heartRateSeries);
    }

    private Map<Long, Integer> parseSeries(JsonNode root, LocalDate date, String... keys) {
        if (root == null) {
            return Map.of();
        }
        JsonNode seriesNode = findSeriesNode(root, keys);
        if (seriesNode == null) {
            seriesNode = findFirstSeriesNode(root);
        }
        return readSeries(seriesNode, root, date);
    }

    private Map<Long, Integer> readSeries(JsonNode seriesNode, JsonNode root, LocalDate date) {
        if (seriesNode == null || !seriesNode.isArray()) {
            return Map.of();
        }
        long baseEpochMs = resolveBaseEpochMs(root, date);
        Map<Long, Integer> series = new HashMap<>();
        for (JsonNode entry : seriesNode) {
            Long timestamp = extractTimestamp(entry, baseEpochMs);
            Integer value = extractSeriesValue(entry);
            if (timestamp == null || value == null || value < 0) {
                continue;
            }
            series.put(timestamp, value);
        }
        return series;
    }

    private JsonNode findSeriesNode(JsonNode root, String... keys) {
        if (root == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            JsonNode found = findSeriesNode(root, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private JsonNode findSeriesNode(JsonNode root, String key) {
        if (root == null || key == null) {
            return null;
        }
        if (root.isObject()) {
            JsonNode direct = root.get(key);
            if (direct != null && direct.isArray()) {
                return direct;
            }
            java.util.Iterator<JsonNode> elements = root.elements();
            while (elements.hasNext()) {
                JsonNode found = findSeriesNode(elements.next(), key);
                if (found != null) {
                    return found;
                }
            }
        } else if (root.isArray()) {
            for (JsonNode child : root) {
                JsonNode found = findSeriesNode(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JsonNode findFirstSeriesNode(JsonNode root) {
        if (root == null) {
            return null;
        }
        if (root.isArray()) {
            if (looksLikeSeries(root)) {
                return root;
            }
            for (JsonNode child : root) {
                JsonNode found = findFirstSeriesNode(child);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        if (root.isObject()) {
            java.util.Iterator<JsonNode> elements = root.elements();
            while (elements.hasNext()) {
                JsonNode child = elements.next();
                if (child.isArray() && looksLikeSeries(child)) {
                    return child;
                }
                JsonNode found = findFirstSeriesNode(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean looksLikeSeries(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return false;
        }
        for (JsonNode entry : node) {
            if (entry.isArray() && entry.size() > 1 && entry.get(0).isNumber() && entry.get(1).isNumber()) {
                return true;
            }
            if (entry.isObject()) {
                Long timestamp = readFirstLong(entry, "timestamp", "timestampLocal", "timestampGMT", "time", "ts");
                Double value = readFirstNumeric(entry, "value", "stress", "stressLevel", "heartRate", "hr", "bpm");
                if (timestamp != null && value != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private Long extractTimestamp(JsonNode entry, long baseEpochMs) {
        if (entry == null || entry.isNull()) {
            return null;
        }
        if (entry.isArray() && entry.size() > 0) {
            JsonNode tsNode = entry.get(0);
            if (tsNode.isNumber()) {
                return toEpochMillis(tsNode.asLong(), baseEpochMs);
            }
            return null;
        }
        if (entry.isObject()) {
            Long direct = readFirstLong(entry,
                    "timestamp", "timestampLocal", "timestampGMT", "timeOffset",
                    "timeOffsetMillis", "startTimeInSeconds", "startTimeInMillis",
                    "timeInSeconds", "timeInMillis", "time", "ts");
            if (direct != null) {
                return toEpochMillis(direct, baseEpochMs);
            }
        }
        if (entry.isNumber()) {
            return toEpochMillis(entry.asLong(), baseEpochMs);
        }
        return null;
    }

    private Integer extractSeriesValue(JsonNode entry) {
        if (entry == null || entry.isNull()) {
            return null;
        }
        if (entry.isArray() && entry.size() > 1 && entry.get(1).isNumber()) {
            return (int) Math.round(entry.get(1).asDouble());
        }
        if (entry.isObject()) {
            Double value = readFirstNumeric(entry, "value", "stress", "stressLevel", "heartRate", "hr", "bpm", "beatsPerMinute");
            if (value != null) {
                return (int) Math.round(value);
            }
        }
        if (entry.isNumber()) {
            return (int) Math.round(entry.asDouble());
        }
        return null;
    }

    private Long readFirstLong(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            JsonNode value = node.get(key);
            if (value != null && value.isNumber()) {
                return value.asLong();
            }
        }
        return null;
    }

    private String readFirstText(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) {
                continue;
            }
            JsonNode value = node.get(key);
            if (value != null && value.isTextual()) {
                return value.asText();
            }
        }
        return null;
    }

    private long resolveBaseEpochMs(JsonNode root, LocalDate date) {
        Long base = readFirstLong(root, "startTimestampLocal", "startTimestampGMT", "startTimestamp", "startTime");
        if (base != null) {
            Long normalized = normalizeEpochMillis(base);
            if (normalized != null) {
                return normalized;
            }
        }
        String baseText = readFirstText(root, "startTimestampLocal", "startTimestampGMT", "startTime", "calendarDate");
        Long parsed = parseTimestampText(baseText);
        if (parsed != null) {
            return parsed;
        }
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Long normalizeEpochMillis(long value) {
        if (value <= 0) {
            return null;
        }
        if (value >= 1_000_000_000_000L) {
            return value;
        }
        if (value >= 1_000_000_000L) {
            return value * 1000L;
        }
        return null;
    }

    private Long parseTimestampText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(text).toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private Long toEpochMillis(long raw, long baseEpochMs) {
        if (raw <= 0) {
            return null;
        }
        if (raw >= 1_000_000_000_000L) {
            return raw;
        }
        if (raw >= 1_000_000_000L) {
            return raw * 1000L;
        }
        if (raw <= 86_400L) {
            return baseEpochMs + raw * 1000L;
        }
        if (raw <= 86_400_000L) {
            return baseEpochMs + raw;
        }
        return baseEpochMs + raw * 1000L;
    }

    private List<GarminWellnessSample> bucketWellnessSamples(Map<Long, Integer> stressSeries,
                                                             Map<Long, Integer> heartRateSeries) {
        long bucketSizeMs = WELLNESS_BUCKET_MINUTES * 60_000L;
        Map<Long, Bucket> buckets = new TreeMap<>();
        addSeriesToBuckets(buckets, stressSeries, bucketSizeMs, true);
        addSeriesToBuckets(buckets, heartRateSeries, bucketSizeMs, false);

        if (buckets.isEmpty()) {
            return List.of();
        }

        ZoneId zone = ZoneId.systemDefault();
        List<GarminWellnessSample> samples = new ArrayList<>();
        for (Map.Entry<Long, Bucket> entry : buckets.entrySet()) {
            long bucketStart = entry.getKey();
            Bucket bucket = entry.getValue();
            GarminWellnessSample sample = new GarminWellnessSample();
            sample.setDate(Instant.ofEpochMilli(bucketStart).atZone(zone).toLocalDate().toString());
            sample.setTimestamp(formatTimestamp(bucketStart, zone));
            sample.setStress(bucket.getStressAverage());
            sample.setHeartRate(bucket.getHeartRateAverage());
            samples.add(sample);
        }
        return samples;
    }

    private void addSeriesToBuckets(Map<Long, Bucket> buckets,
                                    Map<Long, Integer> series,
                                    long bucketSizeMs,
                                    boolean isStress) {
        if (series == null || series.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, Integer> entry : series.entrySet()) {
            long timestamp = entry.getKey();
            int value = entry.getValue();
            long bucketStart = (timestamp / bucketSizeMs) * bucketSizeMs;
            Bucket bucket = buckets.computeIfAbsent(bucketStart, key -> new Bucket());
            if (isStress) {
                bucket.addStress(value);
            } else {
                bucket.addHeartRate(value);
            }
        }
    }

    private String formatTimestamp(long epochMs, ZoneId zone) {
        return Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static final class Bucket {
        private int stressTotal;
        private int stressCount;
        private int heartRateTotal;
        private int heartRateCount;

        void addStress(int value) {
            stressTotal += value;
            stressCount++;
        }

        void addHeartRate(int value) {
            heartRateTotal += value;
            heartRateCount++;
        }

        Integer getStressAverage() {
            if (stressCount == 0) {
                return null;
            }
            return Math.round((float) stressTotal / stressCount);
        }

        Integer getHeartRateAverage() {
            if (heartRateCount == 0) {
                return null;
            }
            return Math.round((float) heartRateTotal / heartRateCount);
        }
    }

    private String executeRequest(String path) throws IOException {
        return executeRequest(path, true);
    }

    private String executeRequest(String path, boolean allowRetry) throws IOException {
        String url;
        Request request;

        if (oauth2Token != null) {
            url = path.startsWith("http") ? path : CONNECT_API_URL + path;
            url = url.replace("https://connect.garmin.com/modern/proxy", CONNECT_API_URL);

            request = Request.get(url)
                    .addHeader("Authorization", "Bearer " + oauth2Token.trim())
                    .addHeader("User-Agent", "GCM-iOS-5.7.2.1")
                    .addHeader("Accept", "application/json");
        } else {
            if (path.startsWith("http")) {
                url = path;
            } else {
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
                logger.info("Request to {} failed with 401, attempting token refresh...", url);
                if (refreshGarthToken()) {
                    return executeRequest(path, false);
                }
            }
            logger.error("Error executing request to {}: {} {}", url, e.getStatusCode(), e.getReasonPhrase());
            throw e;
        }
    }

    private void fetchDisplayName() {
        try {
            String path = "/userprofile-service/socialProfile";
            String response;
            try {
                response = executeRequest(path);
            } catch (IOException e) {
                logger.info("Social profile failed, trying user-settings fallback...");
                path = "/userprofile-service/userprofile/user-settings";
                response = executeRequest(path);
            }

            JsonNode node = objectMapper.readTree(response);
            if (node.has("displayName")) {
                this.displayName = node.get("displayName").asText();
            } else if (node.has("userName")) {
                this.displayName = node.get("userName").asText();
            } else if (node.has("userData") && node.get("userData").has("displayName")) {
                this.displayName = node.get("userData").get("displayName").asText();
            } else if (node.has("userData") && node.get("userData").has("userName")) {
                this.displayName = node.get("userData").get("userName").asText();
            } else {
                logger.warn("Could not find display name in response.");
            }
        } catch (Exception e) {
            logger.warn("Could not fetch display name: {}", e.getMessage());
        }
    }

    private boolean refreshGarthToken() {
        if (tokenScript == null || pythonPath == null) {
            logger.warn("Token refresh script or python path not configured.");
            return false;
        }

        logger.info("Refreshing Garmin token with credentials...");
        String result = runRefreshScript(true);

        if (result == null) {
            logger.info("Credentials-based refresh failed, trying to resume existing session...");
            result = runRefreshScript(false);
        }

        if (result != null && applyManualToken(result)) {
            updateEnvFile(result);
            logger.info("Successfully applied refreshed token/bundle.");
            return true;
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

            return garthBundle != null ? garthBundle : oauth2Token;
        } catch (Exception e) {
            logger.error("Error running token refresh script: {}", e.getMessage());
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
            logger.info("Updated .env file with new GARMIN_GARTH_TOKEN.");
        } catch (Exception e) {
            logger.warn("Could not update .env file: {}", e.getMessage());
        }
    }

    private boolean applyManualToken(String token) {
        if (token == null || token.isEmpty()) return false;
        String input = token.trim();

        if (input.startsWith("eyJ") && input.contains(".")) {
            this.oauth2Token = input;
            logger.info("Applying direct OAuth2 JWT...");
            cookieStore.clear();
            fetchDisplayName();
            if (this.displayName != null) return true;
            this.oauth2Token = null;
        }

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
                        logger.info("Applying OAuth2 token from Garth bundle...");
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
                logger.info("Applying OAuth2 token from JSON object...");
                cookieStore.clear();
                fetchDisplayName();
                if (this.displayName != null) return true;
                this.oauth2Token = null;
            }
        } catch (Exception e) {
            if (!input.startsWith("eyJ")) {
                logger.error("Error parsing Garth bundle: {}", e.getMessage());
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
}
