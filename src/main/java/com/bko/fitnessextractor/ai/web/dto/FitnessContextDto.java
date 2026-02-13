package com.bko.fitnessextractor.ai.web.dto;

import com.bko.fitnessextractor.visualization.app.GarminSummary;
import com.bko.fitnessextractor.visualization.app.RecoverySummary;
import com.bko.fitnessextractor.visualization.app.StravaSummary;

import java.util.List;

public record FitnessContextDto(
        List<String> messages,
        StravaSummary strava,
        GarminSummary garmin,
        RecoverySummary recovery
) { }

