package com.bko.fitnessextractor.visualization;

import com.bko.fitnessextractor.visualization.app.VisualizationSnapshot;

public interface VisualizationService {
    /**
     * Loads the visualization data and returns a snapshot containing detailed information.
     *
     * @return a {@link VisualizationSnapshot} that encapsulates messages and summary details
     *         from various sources such as Strava and Garmin.
     */
    VisualizationSnapshot loadVisualization();
}
