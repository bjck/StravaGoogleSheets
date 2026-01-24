package com.bko.fitnessextractor.sync.web;

import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.ConfigStatus;
import com.bko.fitnessextractor.sync.SyncAllUseCase;
import com.bko.fitnessextractor.sync.SyncGarminUseCase;
import com.bko.fitnessextractor.sync.SyncStravaUseCase;
import com.bko.fitnessextractor.sync.app.SyncReport;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class SyncController {
    private final SyncAllUseCase syncAllUseCase;
    private final SyncStravaUseCase syncStravaUseCase;
    private final SyncGarminUseCase syncGarminUseCase;
    private final AppSettings settings;

    public SyncController(SyncAllUseCase syncAllUseCase,
                          SyncStravaUseCase syncStravaUseCase,
                          SyncGarminUseCase syncGarminUseCase,
                          AppSettings settings) {
        this.syncAllUseCase = syncAllUseCase;
        this.syncStravaUseCase = syncStravaUseCase;
        this.syncGarminUseCase = syncGarminUseCase;
        this.settings = settings;
    }

    @GetMapping("/")
    public String index(Model model) {
        populateConfig(model);
        return "index";
    }

    @PostMapping("/sync/all")
    public String syncAll(Model model) {
        SyncReport report = syncAllUseCase.syncAll();
        populateConfig(model);
        model.addAttribute("report", report);
        return "index";
    }

    @PostMapping("/sync/strava")
    public String syncStrava(Model model) {
        SyncReport report = syncStravaUseCase.syncStrava();
        populateConfig(model);
        model.addAttribute("report", report);
        return "index";
    }

    @PostMapping("/sync/garmin")
    public String syncGarmin(Model model) {
        SyncReport report = syncGarminUseCase.syncGarmin();
        populateConfig(model);
        model.addAttribute("report", report);
        return "index";
    }

    private void populateConfig(Model model) {
        model.addAttribute("config", ConfigStatus.from(settings));
    }
}
