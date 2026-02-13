package com.bko.fitnessextractor.ai.web;

import com.bko.fitnessextractor.ai.infrastructure.gemini.GeminiProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/ai/console")
public class AiPageController {
    private final GeminiProperties properties;

    public AiPageController(GeminiProperties properties) {
        this.properties = properties;
    }

    @GetMapping
    public String console(Model model) {
        // Serve the SPA built into /static/ai/index.html
        model.addAttribute("defaultModel", properties.defaultModel());
        return "forward:/ai/index.html";
    }
}
