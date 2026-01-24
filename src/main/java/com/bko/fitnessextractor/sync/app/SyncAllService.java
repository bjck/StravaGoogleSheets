package com.bko.fitnessextractor.sync.app;

import com.bko.fitnessextractor.sync.SyncAllUseCase;
import com.bko.fitnessextractor.sync.SyncGarminUseCase;
import com.bko.fitnessextractor.sync.SyncStravaUseCase;
import org.springframework.stereotype.Service;

@Service
public class SyncAllService implements SyncAllUseCase {
    private final SyncStravaUseCase syncStravaUseCase;
    private final SyncGarminUseCase syncGarminUseCase;

    public SyncAllService(SyncStravaUseCase syncStravaUseCase, SyncGarminUseCase syncGarminUseCase) {
        this.syncStravaUseCase = syncStravaUseCase;
        this.syncGarminUseCase = syncGarminUseCase;
    }

    @Override
    public SyncReport syncAll() {
        SyncReport report = new SyncReport();
        report.merge(syncStravaUseCase.syncStrava());
        report.merge(syncGarminUseCase.syncGarmin());
        return report;
    }
}
