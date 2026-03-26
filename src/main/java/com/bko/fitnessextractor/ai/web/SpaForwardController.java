package com.bko.fitnessextractor.ai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards SPA routes to the React index.html so that client-side
 * routing works on page refresh or direct URL access.
 *
 * Without this, hitting /ai/ directly returns a 404 because Spring
 * looks for a controller or template instead of the static file.
 */
@Controller
public class SpaForwardController {

    @GetMapping("/ai/")
    public String forwardRoot() {
        return "forward:/ai/index.html";
    }

    @GetMapping("/ai/console")
    public String forwardConsole() {
        return "forward:/ai/index.html";
    }
}
