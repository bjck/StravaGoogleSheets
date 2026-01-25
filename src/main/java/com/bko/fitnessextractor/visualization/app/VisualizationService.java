package com.bko.fitnessextractor.visualization.app;

import com.bko.fitnessextractor.integrations.sheets.SpreadsheetPort;
import com.bko.fitnessextractor.shared.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class VisualizationService implements com.bko.fitnessextractor.visualization.VisualizationService {
    private static final Logger logger = LoggerFactory.getLogger(VisualizationService.class);
    private static final String STRAVA_SHEET = "Strava Activities";
    private static final String GARMIN_SHEET = "Garmin Metrics";
    private static final String GARMIN_STRESS_SHEET = "Garmin Stress HR";
    private static final int STRAVA_RECENT_LIMIT = 16;
    private static final int GARMIN_RECENT_LIMIT = 30;
    private static final int RECOVERY_STRESS_THRESHOLD = 25;
    private static final int OVERTRAINING_MINUTES = 120;
    private static final int STILL_STRESSED_MINUTES = 240;

    private final SpreadsheetPort spreadsheetPort;
    private final AppSettings settings;

    public VisualizationService(SpreadsheetPort spreadsheetPort, AppSettings settings) {
        this.spreadsheetPort = spreadsheetPort;
        this.settings = settings;
    }

    @Override
    public VisualizationSnapshot loadVisualization() {
        List<String> messages = new ArrayList<>();
        StravaSummary strava = null;
        GarminSummary garmin = null;
        RecoverySummary recovery = null;
        List<List<Object>> stravaRows = null;
        List<List<Object>> stressRows = null;

        if (!settings.isGoogleConfigured()) {
            messages.add("Missing Google configuration. Check GOOGLE_SPREADSHEET_ID and GOOGLE_SERVICE_ACCOUNT_KEY_PATH.");
            return new VisualizationSnapshot(List.copyOf(messages), null, null, null);
        }

        try {
            stravaRows = spreadsheetPort.getExistingValues(STRAVA_SHEET + "!A:P");
            strava = buildStravaSummary(stravaRows);
            if (strava == null) {
                messages.add("No Strava data found in the spreadsheet.");
            }
        } catch (Exception e) {
            logger.warn("Failed to load Strava data", e);
            messages.add("Could not load Strava data: " + e.getMessage());
        }

        try {
            List<List<Object>> garminRows = spreadsheetPort.getExistingValues(GARMIN_SHEET + "!A:I");
            garmin = buildGarminSummary(garminRows);
            if (garmin == null) {
                messages.add("No Garmin data found in the spreadsheet.");
            }
        } catch (Exception e) {
            logger.warn("Failed to load Garmin data", e);
            messages.add("Could not load Garmin data: " + e.getMessage());
        }

        try {
            stressRows = spreadsheetPort.getExistingValues(GARMIN_STRESS_SHEET + "!A:D");
        } catch (Exception e) {
            logger.warn("Failed to load Garmin stress data", e);
            messages.add("Could not load Garmin stress data: " + e.getMessage());
        }

        try {
            recovery = buildRecoverySummary(stravaRows, stressRows);
        } catch (Exception e) {
            logger.warn("Failed to calculate recovery summary", e);
            messages.add("Could not calculate recovery summary: " + e.getMessage());
        }

        return new VisualizationSnapshot(List.copyOf(messages), strava, garmin, recovery);
    }

    private StravaSummary buildStravaSummary(List<List<Object>> rows) {
        if (rows == null || rows.size() < 2) {
            return null;
        }

        Map<String, Integer> headerIndex = toHeaderIndex(rows.get(0));
        int nameIndex = getIndex(headerIndex, "Name", 1);
        int typeIndex = getIndex(headerIndex, "Type", 2);
        int distanceIndex = getIndex(headerIndex, "Distance (m)", 3);
        int movingTimeIndex = getIndex(headerIndex, "Moving Time (s)", 4);
        int dateIndex = getIndex(headerIndex, "Start Date", 6);

        List<StravaRow> parsed = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            LocalDate date = parseDate(getCell(row, dateIndex));
            String name = parseString(getCell(row, nameIndex));
            String type = parseString(getCell(row, typeIndex));
            Double distance = parseDouble(getCell(row, distanceIndex));
            Integer movingTime = parseInteger(getCell(row, movingTimeIndex));
            if (date == null && distance == null && name.isBlank()) {
                continue;
            }
            parsed.add(new StravaRow(date, name, type, distance, movingTime));
        }

        if (parsed.isEmpty()) {
            return null;
        }

        double totalDistanceMeters = parsed.stream()
                .map(StravaRow::distanceMeters)
                .filter(value -> value != null)
                .mapToDouble(Double::doubleValue)
                .sum();
        double totalMovingSeconds = parsed.stream()
                .map(StravaRow::movingTimeSeconds)
                .filter(value -> value != null)
                .mapToDouble(Integer::doubleValue)
                .sum();

        int activityCount = parsed.size();
        double totalDistanceKm = round(totalDistanceMeters / 1000.0, 1);
        double totalMovingHours = round(totalMovingSeconds / 3600.0, 1);
        double averageDistanceKm = activityCount > 0 ? round(totalDistanceKm / activityCount, 1) : 0.0;

        StravaRow latestRow = parsed.stream()
                .filter(row -> row.date() != null)
                .max(Comparator.comparing(StravaRow::date))
                .orElse(parsed.get(0));
        String latestActivityLabel = buildLatestActivityLabel(latestRow);

        Map<String, Integer> typeCounts = new HashMap<>();
        for (StravaRow row : parsed) {
            if (!row.type().isBlank()) {
                typeCounts.merge(row.type(), 1, Integer::sum);
            }
        }
        Map<String, Integer> sortedTypeCounts = sortByValueDesc(typeCounts);

        List<StravaRow> datedRows = parsed.stream()
                .filter(row -> row.date() != null)
                .sorted(Comparator.comparing(StravaRow::date))
                .toList();
        List<StravaRow> recentRows = tail(datedRows, STRAVA_RECENT_LIMIT);

        List<String> chartLabels = new ArrayList<>();
        List<Double> chartDistancesKm = new ArrayList<>();
        for (StravaRow row : recentRows) {
            chartLabels.add(row.date() != null ? row.date().toString() : "");
            Double distanceKm = row.distanceMeters() == null ? null : round(row.distanceMeters() / 1000.0, 2);
            chartDistancesKm.add(distanceKm);
        }

        return new StravaSummary(
                activityCount,
                totalDistanceKm,
                totalMovingHours,
                averageDistanceKm,
                latestActivityLabel,
                Collections.unmodifiableList(chartLabels),
                Collections.unmodifiableList(chartDistancesKm),
                Collections.unmodifiableMap(sortedTypeCounts)
        );
    }

    private GarminSummary buildGarminSummary(List<List<Object>> rows) {
        if (rows == null || rows.size() < 2) {
            return null;
        }

        Map<String, Integer> headerIndex = toHeaderIndex(rows.get(0));
        int dateIndex = getIndex(headerIndex, "Date", 0);
        int bodyBatteryMaxIndex = getIndex(headerIndex, "Body Battery Max", 1);
        int bodyBatteryMinIndex = getIndex(headerIndex, "Body Battery Min", 2);
        int weightIndex = getIndex(headerIndex, "Weight (kg)", 3);
        int vo2MaxIndex = getIndex(headerIndex, "VO2 Max", 4);
        int restingHrIndex = getIndex(headerIndex, "Resting HR", 5);
        int sleepScoreIndex = getIndex(headerIndex, "Sleep Score", 6);
        int sleepDurationIndex = getIndex(headerIndex, "Sleep Duration (h)", 7);

        List<GarminRow> parsed = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            LocalDate date = parseDate(getCell(row, dateIndex));
            if (date == null) {
                continue;
            }
            Integer bodyBatteryMax = parseInteger(getCell(row, bodyBatteryMaxIndex));
            Integer bodyBatteryMin = parseInteger(getCell(row, bodyBatteryMinIndex));
            Double weight = parseDouble(getCell(row, weightIndex));
            Double vo2Max = parseDouble(getCell(row, vo2MaxIndex));
            Integer restingHr = parseInteger(getCell(row, restingHrIndex));
            Integer sleepScore = parseInteger(getCell(row, sleepScoreIndex));
            Double sleepDuration = parseDouble(getCell(row, sleepDurationIndex));
            parsed.add(new GarminRow(date, bodyBatteryMax, bodyBatteryMin, weight, vo2Max, restingHr, sleepScore, sleepDuration));
        }

        if (parsed.isEmpty()) {
            return null;
        }

        parsed.sort(Comparator.comparing(GarminRow::date));
        GarminRow latest = parsed.get(parsed.size() - 1);

        List<GarminRow> recentRows = tail(parsed, GARMIN_RECENT_LIMIT);
        List<String> chartLabels = new ArrayList<>();
        List<Integer> bodyBatteryMaxSeries = new ArrayList<>();
        List<Integer> sleepScoreSeries = new ArrayList<>();
        List<Integer> restingHrSeries = new ArrayList<>();

        for (GarminRow row : recentRows) {
            chartLabels.add(row.date().toString());
            bodyBatteryMaxSeries.add(row.bodyBatteryMax());
            sleepScoreSeries.add(row.sleepScore());
            restingHrSeries.add(row.restingHeartRate());
        }

        return new GarminSummary(
                latest.date().toString(),
                latest.bodyBatteryMax(),
                latest.bodyBatteryMin(),
                latest.weight(),
                latest.vo2Max(),
                latest.restingHeartRate(),
                latest.sleepScore(),
                latest.sleepDurationHours(),
                Collections.unmodifiableList(chartLabels),
                Collections.unmodifiableList(bodyBatteryMaxSeries),
                Collections.unmodifiableList(sleepScoreSeries),
                Collections.unmodifiableList(restingHrSeries)
        );
    }

    private RecoverySummary buildRecoverySummary(List<List<Object>> stravaRows, List<List<Object>> stressRows) {
        WorkoutSummary workout = findLatestWorkout(stravaRows);
        if (workout == null) {
            return new RecoverySummary("No recent workout", "", null, "No workout", "Sync Strava to compute recovery.");
        }

        if (stressRows == null || stressRows.size() < 2) {
            return new RecoverySummary(workout.label(), formatInstant(workout.end()), null,
                    "No stress data", "Sync Garmin stress data to compute recovery.");
        }

        List<StressSample> samples = parseStressSamples(stressRows);
        if (samples.isEmpty()) {
            return new RecoverySummary(workout.label(), formatInstant(workout.end()), null,
                    "No stress data", "Sync Garmin stress data to compute recovery.");
        }

        List<StressSample> afterWorkout = new ArrayList<>();
        for (StressSample sample : samples) {
            if (!sample.timestamp().isBefore(workout.end())) {
                afterWorkout.add(sample);
            }
        }
        if (afterWorkout.isEmpty()) {
            return new RecoverySummary(workout.label(), formatInstant(workout.end()), null,
                    "No stress data", "No stress samples after the workout end time.");
        }

        StressSample recoveryPoint = null;
        for (StressSample sample : afterWorkout) {
            if (sample.stressLevel() != null
                    && sample.stressLevel() > 0
                    && sample.stressLevel() <= RECOVERY_STRESS_THRESHOLD) {
                recoveryPoint = sample;
                break;
            }
        }

        if (recoveryPoint != null) {
            long minutesToRecovery = Duration.between(workout.end(), recoveryPoint.timestamp()).toMinutes();
            boolean overtraining = minutesToRecovery > OVERTRAINING_MINUTES;
            String status = overtraining ? "Overtraining" : "Recovered";
            String guidance = overtraining
                    ? "Rest tonight."
                    : "Numbers look promising. You are going to make it.";
            return new RecoverySummary(workout.label(), formatInstant(workout.end()),
                    (int) minutesToRecovery, status, guidance);
        }

        StressSample lastSample = afterWorkout.get(afterWorkout.size() - 1);
        long minutesObserved = Duration.between(workout.end(), lastSample.timestamp()).toMinutes();
        String guidance = minutesObserved >= STILL_STRESSED_MINUTES
                ? "Stress stayed above 25 for more than 4 hours."
                : "Still stressed. More data needed.";
        return new RecoverySummary(workout.label(), formatInstant(workout.end()), null, "Still stressed", guidance);
    }

    private Map<String, Integer> toHeaderIndex(List<Object> headerRow) {
        Map<String, Integer> index = new HashMap<>();
        if (headerRow == null) {
            return index;
        }
        for (int i = 0; i < headerRow.size(); i++) {
            Object value = headerRow.get(i);
            String header = value == null ? "" : value.toString();
            if (!header.isBlank()) {
                index.put(normalizeHeader(header), i);
            }
        }
        return index;
    }

    private int getIndex(Map<String, Integer> headerIndex, String headerName, int fallback) {
        if (headerIndex == null || headerName == null) {
            return fallback;
        }
        Integer value = headerIndex.get(normalizeHeader(headerName));
        return value != null ? value : fallback;
    }

    private String normalizeHeader(String value) {
        return value.trim().toLowerCase(Locale.US);
    }

    private Object getCell(List<Object> row, int index) {
        if (row == null || index < 0 || index >= row.size()) {
            return null;
        }
        return row.get(index);
    }

    private String parseString(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(Object value) {
        Double parsed = parseDouble(value);
        return parsed == null ? null : (int) Math.round(parsed);
    }

    private Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        if (text.length() >= 19) {
            try {
                return LocalDateTime.parse(text.substring(0, 19)).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        if (text.length() >= 10) {
            try {
                return LocalDate.parse(text.substring(0, 10)).atStartOfDay(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private LocalDate parseDate(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(text).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(text).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        if (text.length() >= 10) {
            String candidate = text.substring(0, 10);
            try {
                return LocalDate.parse(candidate);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private WorkoutSummary findLatestWorkout(List<List<Object>> rows) {
        if (rows == null || rows.size() < 2) {
            return null;
        }
        Map<String, Integer> headerIndex = toHeaderIndex(rows.get(0));
        int nameIndex = getIndex(headerIndex, "Name", 1);
        int startIndex = getIndex(headerIndex, "Start Date", 6);
        int elapsedIndex = getIndex(headerIndex, "Elapsed Time (s)", 5);
        int movingIndex = getIndex(headerIndex, "Moving Time (s)", 4);

        WorkoutSummary latest = null;
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            Instant start = parseInstant(getCell(row, startIndex));
            if (start == null) {
                continue;
            }
            Integer elapsedSeconds = parseInteger(getCell(row, elapsedIndex));
            if (elapsedSeconds == null || elapsedSeconds <= 0) {
                elapsedSeconds = parseInteger(getCell(row, movingIndex));
            }
            if (elapsedSeconds == null || elapsedSeconds < 0) {
                elapsedSeconds = 0;
            }
            Instant end = start.plusSeconds(elapsedSeconds);
            String name = parseString(getCell(row, nameIndex));
            String labelDate = end.atZone(ZoneId.systemDefault()).toLocalDate().toString();
            String label = name.isBlank() ? labelDate + " - Activity" : labelDate + " - " + name;
            if (latest == null || end.isAfter(latest.end())) {
                latest = new WorkoutSummary(end, label);
            }
        }
        return latest;
    }

    private List<StressSample> parseStressSamples(List<List<Object>> rows) {
        if (rows == null || rows.size() < 2) {
            return Collections.emptyList();
        }
        Map<String, Integer> headerIndex = toHeaderIndex(rows.get(0));
        int timestampIndex = getIndex(headerIndex, "Timestamp", 1);
        int stressIndex = getIndex(headerIndex, "Stress", 2);

        List<StressSample> samples = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<Object> row = rows.get(i);
            Instant timestamp = parseInstant(getCell(row, timestampIndex));
            Integer stress = parseInteger(getCell(row, stressIndex));
            if (timestamp != null && stress != null) {
                samples.add(new StressSample(timestamp, stress));
            }
        }
        samples.sort(Comparator.comparing(StressSample::timestamp));
        return samples;
    }

    private String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private double round(double value, int precision) {
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }

    private String buildLatestActivityLabel(StravaRow latestRow) {
        if (latestRow == null) {
            return "";
        }
        String date = latestRow.date() != null ? latestRow.date().toString() : "";
        String name = latestRow.name().isBlank() ? "Activity" : latestRow.name();
        if (date.isBlank()) {
            return name;
        }
        return date + " - " + name;
    }

    private Map<String, Integer> sortByValueDesc(Map<String, Integer> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(input.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : entries) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    private <T> List<T> tail(List<T> source, int limit) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        if (source.size() <= limit) {
            return new ArrayList<>(source);
        }
        return new ArrayList<>(source.subList(source.size() - limit, source.size()));
    }

    private record StravaRow(LocalDate date, String name, String type, Double distanceMeters, Integer movingTimeSeconds) {
    }

    private record WorkoutSummary(Instant end, String label) {
    }

    private record StressSample(Instant timestamp, Integer stressLevel) {
    }

    private record GarminRow(LocalDate date,
                             Integer bodyBatteryMax,
                             Integer bodyBatteryMin,
                             Double weight,
                             Double vo2Max,
                             Integer restingHeartRate,
                             Integer sleepScore,
                             Double sleepDurationHours) {
    }
}
