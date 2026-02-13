package com.bko.fitnessextractor.ai.infrastructure.context;

import com.bko.fitnessextractor.ai.domain.FitnessContext;
import com.bko.fitnessextractor.ai.domain.FitnessContextPort;
import com.bko.fitnessextractor.visualization.VisualizationService;
import com.bko.fitnessextractor.visualization.app.VisualizationSnapshot;
import org.springframework.stereotype.Component;

@Component
public class SheetsFitnessContextProvider implements FitnessContextPort {
    private final VisualizationService visualizationService;

    public SheetsFitnessContextProvider(VisualizationService visualizationService) {
        this.visualizationService = visualizationService;
    }

    @Override
    public FitnessContext loadContext() {
        VisualizationSnapshot snapshot = visualizationService.loadVisualization();
        return FitnessContext.fromSnapshot(snapshot);
    }
}

