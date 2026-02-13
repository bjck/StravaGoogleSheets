package com.bko.fitnessextractor.sync.web;

import com.bko.fitnessextractor.shared.AppSettings;
import com.bko.fitnessextractor.shared.ConfigStatus;
import com.bko.fitnessextractor.sync.ExportCsvUseCase;
import com.bko.fitnessextractor.sync.SyncAllUseCase;
import com.bko.fitnessextractor.sync.SyncGarminUseCase;
import com.bko.fitnessextractor.sync.SyncStravaUseCase;
import com.bko.fitnessextractor.sync.app.SyncReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Controller
public class SyncController {
    private static final Logger logger = LoggerFactory.getLogger(SyncController.class);
    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SyncAllUseCase syncAllUseCase;
    private final SyncStravaUseCase syncStravaUseCase;
    private final SyncGarminUseCase syncGarminUseCase;
    private final ExportCsvUseCase exportCsvUseCase;
    private final AppSettings settings;

    public SyncController(SyncAllUseCase syncAllUseCase,
                          SyncStravaUseCase syncStravaUseCase,
                          SyncGarminUseCase syncGarminUseCase,
                          ExportCsvUseCase exportCsvUseCase,
                          AppSettings settings) {
        this.syncAllUseCase = syncAllUseCase;
        this.syncStravaUseCase = syncStravaUseCase;
        this.syncGarminUseCase = syncGarminUseCase;
        this.exportCsvUseCase = exportCsvUseCase;
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

    @GetMapping("/sync/export")
    public ResponseEntity<byte[]> exportCsvBundle() {
        try {
            byte[] zip = exportCsvUseCase.exportAllCsvZip();
            String timestamp = LocalDateTime.now().format(EXPORT_TIMESTAMP);
            String filename = "fitness-extractor-export-" + timestamp + ".zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zip);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(e.getMessage().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("CSV export failed", e);
            String message = "Failed to export CSVs: " + e.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(message.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void populateConfig(Model model) {
        model.addAttribute("config", ConfigStatus.from(settings));
    }
}
