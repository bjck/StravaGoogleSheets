package com.bko.fitnessextractor.sync.web;

import com.bko.fitnessextractor.sync.SyncAllUseCase;
import com.bko.fitnessextractor.sync.SyncGarminUseCase;
import com.bko.fitnessextractor.sync.SyncStravaUseCase;
import com.bko.fitnessextractor.sync.app.SyncReport;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sync")
public class SyncApiController {
    private final SyncAllUseCase syncAllUseCase;
    private final SyncStravaUseCase syncStravaUseCase;
    private final SyncGarminUseCase syncGarminUseCase;

    public SyncApiController(SyncAllUseCase syncAllUseCase,
                             SyncStravaUseCase syncStravaUseCase,
                             SyncGarminUseCase syncGarminUseCase) {
        this.syncAllUseCase = syncAllUseCase;
        this.syncStravaUseCase = syncStravaUseCase;
        this.syncGarminUseCase = syncGarminUseCase;
    }

    @PostMapping(value = "/{target}", produces = MediaType.APPLICATION_JSON_VALUE)
    public SyncReport sync(@PathVariable String target) {
        return switch (target) {
            case "strava" -> syncStravaUseCase.syncStrava();
            case "garmin" -> syncGarminUseCase.syncGarmin();
            case "all" -> syncAllUseCase.syncAll();
            default -> throw new IllegalArgumentException("Unknown sync target: " + target);
        };
    }
}
