package com.bko.fitnessextractor.sync.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SyncReport {
    private final List<String> messages = new ArrayList<>();
    private boolean success = true;
    private boolean stravaAttempted;
    private boolean garminAttempted;
    private int stravaAdded;
    private int garminInserted;
    private int garminUpdated;

    public void info(String message) {
        messages.add(message);
    }

    public void warn(String message) {
        messages.add("WARN: " + message);
    }

    public void error(String message) {
        messages.add("ERROR: " + message);
        success = false;
    }

    public void merge(SyncReport other) {
        if (other == null) {
            return;
        }
        messages.addAll(other.messages);
        success = success && other.success;
        stravaAttempted = stravaAttempted || other.stravaAttempted;
        garminAttempted = garminAttempted || other.garminAttempted;
        stravaAdded += other.stravaAdded;
        garminInserted += other.garminInserted;
        garminUpdated += other.garminUpdated;
    }

    public List<String> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isStravaAttempted() {
        return stravaAttempted;
    }

    public void setStravaAttempted(boolean stravaAttempted) {
        this.stravaAttempted = stravaAttempted;
    }

    public boolean isGarminAttempted() {
        return garminAttempted;
    }

    public void setGarminAttempted(boolean garminAttempted) {
        this.garminAttempted = garminAttempted;
    }

    public int getStravaAdded() {
        return stravaAdded;
    }

    public void addStravaAdded(int count) {
        this.stravaAdded += count;
    }

    public int getGarminInserted() {
        return garminInserted;
    }

    public void addGarminInserted(int count) {
        this.garminInserted += count;
    }

    public int getGarminUpdated() {
        return garminUpdated;
    }

    public void addGarminUpdated(int count) {
        this.garminUpdated += count;
    }
}
