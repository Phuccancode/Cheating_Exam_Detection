package com.lms.cheating_detection.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Service
public class MediaPipeModelDownloadService {

    private static final Logger log = LoggerFactory.getLogger(MediaPipeModelDownloadService.class);

    @Value("${mediapipe.model.directory:models}")
    private String modelDirectory;

    @Value("${mediapipe.download.enabled:false}")
    private boolean enableDownload;

    // Map of model names to download URLs
    private final Map<String, String> modelUrls = new HashMap<>();

    // Track which models were successfully downloaded
    private final Map<String, Boolean> modelAvailability = new HashMap<>();

    public MediaPipeModelDownloadService() {
        // Initialize model URLs
        modelUrls.put("face_detection", "https://storage.googleapis.com/mediapipe-models/face_detector/blaze_face_short_range/float16/latest/face_detection_short_range.tflite");
        modelUrls.put("face_landmarks", "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task");
    }

    @PostConstruct
    public void downloadModelsIfNeeded() {
        // Create model directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(modelDirectory));
            log.info("Model directory created or confirmed at: {}", Paths.get(modelDirectory).toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not create model directory: {}", e.getMessage());
        }

        if (!enableDownload) {
            log.info("MediaPipe model download is disabled. Using OpenCV for face detection instead.");
            return;
        }

        try {
            for (Map.Entry<String, String> entry : modelUrls.entrySet()) {
                String modelName = entry.getKey();
                String modelUrl = entry.getValue();

                Path modelPath = Paths.get(modelDirectory, modelName + ".tflite");

                // Check if model already exists
                if (Files.exists(modelPath)) {
                    log.info("Model {} already exists at {}", modelName, modelPath.toAbsolutePath());
                    modelAvailability.put(modelName, true);
                    continue;
                }

                // Try downloading model with retries
                boolean success = downloadModelWithRetries(modelUrl, modelPath.toString(), 3);
                modelAvailability.put(modelName, success);

                if (success) {
                    log.info("Successfully downloaded model {} to {}", modelName, modelPath.toAbsolutePath());
                } else {
                    log.warn("Failed to download model {}. Will use OpenCV fallback for face detection.", modelName);
                }
            }
        } catch (Exception e) {
            log.error("Error downloading MediaPipe models", e);
            log.info("Using OpenCV for face detection as fallback");
        }
    }

    private boolean downloadModelWithRetries(String url, String outputPath, int maxRetries) {
        int retries = 0;
        while (retries < maxRetries) {
            try {
                downloadFile(url, outputPath);
                return true;
            } catch (Exception e) {
                retries++;
                log.warn("Attempt {} to download model failed: {}", retries, e.getMessage());
                if (retries < maxRetries) {
                    try {
                        // Wait before retrying
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
        }
        return false;
    }

    private void downloadFile(String url, String outputPath) throws IOException {
        log.info("Downloading file from {} to {}", url, outputPath);

        try (
                InputStream inputStream = new URL(url).openStream();
                ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
                FileOutputStream fileOutputStream = new FileOutputStream(outputPath);
                FileChannel fileChannel = fileOutputStream.getChannel()
        ) {
            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
            log.debug("File downloaded successfully");
        }
    }

    public boolean isModelAvailable(String modelName) {
        return modelAvailability.getOrDefault(modelName, false);
    }

    public String getModelPath(String modelName) {
        if (isModelAvailable(modelName)) {
            return Paths.get(modelDirectory, modelName + ".tflite").toString();
        }
        return null;
    }
}