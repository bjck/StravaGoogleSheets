package com.bko.fitnessextractor.visualization.web;

import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.ConfigStatus;
import com.bko.fitnessextractor.visualization.VisualizationService;
import com.bko.fitnessextractor.visualization.app.VisualizationSnapshot;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VisualizationController {
    private final VisualizationService visualizationService;
    private final AppSettings settings;

    public VisualizationController(VisualizationService visualizationService, AppSettings settings) {
        this.visualizationService = visualizationService;
        this.settings = settings;
    }

    @GetMapping("/visualize")
    public String visualize(Model model) {
        VisualizationSnapshot snapshot = visualizationService.loadVisualization();
        model.addAttribute("dashboard", snapshot);
        model.addAttribute("config", ConfigStatus.from(settings));
        return "visualization";
    }
}
