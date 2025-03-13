package com.lms.cheating_detection.controller;
import com.lms.cheating_detection.response.ApiResponse;
import com.lms.cheating_detection.service.ClientCheatingDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/client-monitoring")
public class ClientMonitoringController {

    private static final Logger log = LoggerFactory.getLogger(ClientMonitoringController.class);
    private final ClientCheatingDetectionService cheatingDetectionService;

    @Autowired
    public ClientMonitoringController(ClientCheatingDetectionService cheatingDetectionService) {
        this.cheatingDetectionService = cheatingDetectionService;
    }

    @PostMapping("/start")
    public ResponseEntity<?> startMonitoring(@RequestParam("sessionId") String sessionId,
                                             @RequestParam("examId") String examId) {
        try {
            cheatingDetectionService.startMonitoring(sessionId, examId);
            return ResponseEntity.ok(new ApiResponse(true, "Monitoring session started"));
        } catch (Exception e) {
            log.error("Error starting monitoring session", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> analyzeFrame(@RequestParam("sessionId") String sessionId,
                                          @RequestParam("examId") String examId,
                                          @RequestParam("image") MultipartFile imageFile) {
        try {
            boolean suspicious = cheatingDetectionService.analyzeFrame(
                    sessionId,
                    examId,
                    imageFile.getBytes()
            );

            return ResponseEntity.ok(new ApiResponse(
                    true,
                    suspicious ? "Suspicious activity detected" : "No suspicious activity detected"
            ));
        } catch (IOException e) {
            log.error("Error processing image", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error processing image: " + e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<?> stopMonitoring(@RequestParam("sessionId") String sessionId) {
        try {
            cheatingDetectionService.stopMonitoring(sessionId);
            return ResponseEntity.ok(new ApiResponse(true, "Monitoring session stopped"));
        } catch (Exception e) {
            log.error("Error stopping monitoring session", e);
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
}