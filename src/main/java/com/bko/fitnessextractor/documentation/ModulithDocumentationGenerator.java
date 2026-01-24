package com.bko.fitnessextractor.documentation;

import com.bko.fitnessextractor.FitnessExtractorApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "modulith.docs", name = "enabled", havingValue = "true")
public class ModulithDocumentationGenerator implements ApplicationRunner {
    private static final Logger logger = LoggerFactory.getLogger(ModulithDocumentationGenerator.class);

    private final String outputFolder;

    public ModulithDocumentationGenerator(
            @Value("${modulith.docs.output:target/modulith-docs}") String outputFolder) {
        this.outputFolder = outputFolder;
    }

    @Override
    public void run(ApplicationArguments args) {
        logger.info("Generating Spring Modulith documentation into {}", outputFolder);
        ApplicationModules modules = ApplicationModules.of(FitnessExtractorApplication.class);
        new Documenter(modules, outputFolder).writeDocumentation();
        logger.info("Spring Modulith documentation generated.");
    }
}
