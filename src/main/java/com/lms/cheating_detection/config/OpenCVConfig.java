package com.lms.cheating_detection.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class OpenCVConfig {
     

    private static final Logger log = LoggerFactory.getLogger(OpenCVConfig.class);

    @Value("${mediapipe.model.directory:models}")
    private String modelDirectory;

    @PostConstruct
    public void loadLibraries() {
        try {
            log.info("Starting OpenCV initialization...");

            // Load OpenCV using nu.pattern library
            nu.pattern.OpenCV.loadLocally();
            log.info("OpenCV loaded successfully.");

            // Print OpenCV details
            try {
                String version = org.opencv.core.Core.VERSION;
                String nativePath = org.opencv.core.Core.NATIVE_LIBRARY_NAME;
                log.info("OpenCV Version: {}, Native Library: {}", version, nativePath);
            } catch (Exception e) {
                log.warn("Could not print OpenCV details: {}", e.getMessage());
            }

            // Create models directory if it doesn't exist
            File directory = new File(modelDirectory);
            if (!directory.exists()) {
                directory.mkdirs();
                log.info("Created model directory at: {}", directory.getAbsolutePath());
            }

            // Ensure necessary model directories exist
            createModelSubdirectories();

        } catch (UnsatisfiedLinkError e) {
            log.error("Failed to load OpenCV library: {}", e.getMessage());
            log.error("Java library path: {}", System.getProperty("java.library.path"));
            log.error("LD_LIBRARY_PATH: {}", System.getenv("LD_LIBRARY_PATH"));
            throw new RuntimeException("Failed to load OpenCV library.", e);
        } catch (Exception e) {
            log.error("Error during library initialization", e);
            throw new RuntimeException("Error during library initialization", e);
        }
    }

    private void createModelSubdirectories() {
        try {
            // Create subdirectories for each model type
            String[] subDirs = {"face_detection", "face_landmarks", "head_pose", "eye_gaze"};
            for (String dir : subDirs) {
                Path path = Paths.get(modelDirectory, dir);
                if (!Files.exists(path)) {
                    Files.createDirectories(path);
                    log.info("Created model subdirectory: {}", path);
                }
            }
        } catch (Exception e) {
            log.error("Error creating model subdirectories", e);
            throw new RuntimeException("Failed to create model directories", e);
        }
    }
}