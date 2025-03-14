package com.lms.cheating_detection.service;

import com.lms.cheating_detection.model.FaceDetectionResult;
import com.lms.cheating_detection.model.SuspiciousActivity;
import com.lms.cheating_detection.repository.SuspiciousActivityRepository;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientCheatingDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ClientCheatingDetectionService.class);

    private final SuspiciousActivityRepository suspiciousActivityRepository;
    private final MediaPipeFaceDetectionService mediaPipeFaceDetectionService;
    private Map<String, Boolean> activeMonitoringSessions = new ConcurrentHashMap<>();

    @Value("${evidence.folder:evidence}")
    private String evidenceFolder;

    @Autowired
    public ClientCheatingDetectionService(
            SuspiciousActivityRepository suspiciousActivityRepository,
            MediaPipeFaceDetectionService mediaPipeFaceDetectionService) {
        this.suspiciousActivityRepository = suspiciousActivityRepository;
        this.mediaPipeFaceDetectionService = mediaPipeFaceDetectionService;
    }

    @PostConstruct
    public void init() {
        // Create evidence directory if it doesn't exist
        File directory = new File(evidenceFolder);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (created) {
                log.info("Created evidence directory at: {}", directory.getAbsolutePath());
            } else {
                log.warn("Failed to create evidence directory at: {}", directory.getAbsolutePath());
            }
        }
    }

    public void startMonitoring(String sessionId, String examId) {
        if (activeMonitoringSessions.containsKey(sessionId)) {
            log.info("Monitoring already in progress for session: {}", sessionId);
            return;
        }

        activeMonitoringSessions.put(sessionId, true);
        log.info("Started monitoring session for session: {}", sessionId);
    }

    public void stopMonitoring(String sessionId) {
        activeMonitoringSessions.remove(sessionId);
        log.info("Stopped monitoring for session: {}", sessionId);
    }

    public boolean analyzeFrame(String sessionId, String examId, byte[] imageData) {
        if (!activeMonitoringSessions.containsKey(sessionId)) {
            log.warn("No active monitoring session for session: {}", sessionId);
            return false;
        }

        Mat frame = null;

        try {
            // Convert byte array to OpenCV Mat
            frame = Imgcodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
            if (frame.empty()) {
                log.error("Failed to decode image for session: {}", sessionId);
                return false;
            }

            // Use MediaPipe Face Detection service for analysis
            FaceDetectionResult result = mediaPipeFaceDetectionService.analyzeFrame(frame);

            // If suspicious activity is detected, save evidence and log
            if (result.isSuspiciousActivity()) {
                String evidencePath = saveFrame(frame, sessionId,
                        result.getEvidenceType() != null ? result.getEvidenceType() : "suspicious");
                logSuspiciousActivity(sessionId, examId, result.getDescription(), evidencePath);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Error analyzing frame for session: {}", sessionId, e);
            return false;
        } finally {
            // Release resources
            if (frame != null) frame.release();
        }
    }

    private String saveFrame(Mat frame, String sessionId, String type) {
        try {
            // Ensure evidence directory exists
            File directory = new File(evidenceFolder);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("evidence_%s_%s_%s.jpg", sessionId, type, timestamp);
            String path = evidenceFolder + File.separator + filename;

            // Optimize JPEG quality to save space
            MatOfInt compressionParams = new MatOfInt(
                    Imgcodecs.IMWRITE_JPEG_QUALITY,
                    70  // 70% quality instead of default 95%
            );

            try {
                Imgcodecs.imwrite(path, frame, compressionParams);
                log.info("Saved evidence frame to: {}", path);
                return path;
            } finally {
                compressionParams.release();
            }
        } catch (Exception e) {
            log.error("Error saving evidence frame for session: {}", sessionId, e);
            return null;
        }
    }

    private void logSuspiciousActivity(String sessionId, String examId, String description, String evidencePath) {
        try {
            SuspiciousActivity activity = new SuspiciousActivity();
            activity.setSessionId(sessionId);
            activity.setExamId(examId);
            activity.setDescription(description);
            activity.setTimestamp(LocalDateTime.now());
            activity.setEvidencePath(evidencePath);

            suspiciousActivityRepository.save(activity);
            log.info("Suspicious activity logged: {} for session: {}", description, sessionId);
        } catch (Exception e) {
            log.error("Error logging suspicious activity for session: {}", sessionId, e);
        }
    }
}