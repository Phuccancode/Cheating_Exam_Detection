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
import java.util.concurrent.TimeUnit;

@Component
public class OpenCVConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenCVConfig.class);

    @Value("${mediapipe.model.directory:models}")
    private String modelDirectory;

    @Value("${opencv.loading.method:auto}")
    private String opencvLoadingMethod;

    @PostConstruct
    public void loadLibraries() {
        try {
            // Load OpenCV with retry and multiple methods
            boolean loaded = loadOpenCVWithRetry();

            if (!loaded) {
                throw new RuntimeException("Failed to load OpenCV after multiple attempts");
            }

            log.info("OpenCV loaded successfully.");

            // Create models directory if it doesn't exist
            File directory = new File(modelDirectory);
            if (!directory.exists()) {
                directory.mkdirs();
                log.info("Created model directory at: {}", directory.getAbsolutePath());
            }

            // Ensure necessary model directories exist
            createModelSubdirectories();

        } catch (UnsatisfiedLinkError e) {
            log.error("Failed to load OpenCV library.", e);
            throw new RuntimeException("Failed to load OpenCV library.", e);
        } catch (Exception e) {
            log.error("Error during library initialization", e);
            throw new RuntimeException("Error during library initialization", e);
        }
    }

    private boolean loadOpenCVWithRetry() {
        // Try up to 3 times with different methods
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                log.info("OpenCV loading attempt #{}", attempt);

                if ("system".equalsIgnoreCase(opencvLoadingMethod)) {
                    // Try loading from system paths
                    System.loadLibrary("opencv_java");
                    log.info("OpenCV loaded from system library paths");
                    return true;
                } else if ("manual".equalsIgnoreCase(opencvLoadingMethod)) {
                    // Try specific paths that might exist in Docker container
                    String[] possiblePaths = {
                            "/usr/local/share/java/opencv4/libopencv_java.so",
                            "/usr/lib/jni/libopencv_java.so",
                            "/usr/lib/x86_64-linux-gnu/libopencv_java.so"
                    };

                    for (String path : possiblePaths) {
                        try {
                            if (new File(path).exists()) {
                                System.load(path);
                                log.info("OpenCV loaded from {}", path);
                                return true;
                            }
                        } catch (Exception e) {
                            log.debug("Failed to load from {}: {}", path, e.getMessage());
                        }
                    }

                    log.warn("Could not find OpenCV library in standard locations");
                } else {
                    // Default method - use nu.pattern.OpenCV
                    log.info("Loading OpenCV using nu.pattern.OpenCV.loadLocally()");
                    nu.pattern.OpenCV.loadLocally();
                    logOpenCVInfo();
                    return true;
                }
            } catch (Exception e) {
                log.warn("OpenCV loading attempt #{} failed: {}", attempt, e.getMessage());

                if (attempt < 3) {
                    try {
                        // Wait a bit before retrying
                        TimeUnit.SECONDS.sleep(2);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        return false;
    }

    private void logOpenCVInfo() {
        try {
            // Get OpenCV version - requires successful loading first
            String version = org.opencv.core.Core.VERSION;
            String nativePath = org.opencv.core.Core.NATIVE_LIBRARY_NAME;
            log.info("OpenCV Version: {}, Native Library: {}", version, nativePath);

            // Print some environment info
            log.info("Java Library Path: {}", System.getProperty("java.library.path"));
            log.info("Working Directory: {}", System.getProperty("user.dir"));
        } catch (Exception e) {
            log.warn("Could not print OpenCV details: {}", e.getMessage());
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