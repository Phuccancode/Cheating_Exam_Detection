package com.lms.cheating_detection.controller;

import com.lms.cheating_detection.dto.SuspiciousActivityDTO;
import com.lms.cheating_detection.model.SuspiciousActivity;
import com.lms.cheating_detection.repository.SuspiciousActivityRepository;
import com.lms.cheating_detection.response.ApiResponse;
import com.lms.cheating_detection.service.CheatingDetectionService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/monitoring")
@Slf4j
public class CheatingDetectionController {

    private final CheatingDetectionService cheatingDetectionService;

    @Autowired
    public CheatingDetectionController(CheatingDetectionService cheatingDetectionService) {
        this.cheatingDetectionService = cheatingDetectionService;
    }

    @PostMapping("/start")
    public ResponseEntity<ApiResponse> startMonitoring(
            @RequestParam(name = "session_id") String sessionId,
            @RequestParam(name = "exam_id") String examId) {
        try {
            cheatingDetectionService.startMonitoring(sessionId, examId);
            return ResponseEntity.ok(new ApiResponse(true, "Monitoring started successfully"));
        } catch (Exception e) {
            log.error("Failed to start monitoring", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Failed to start monitoring: " + e.getMessage()));
        }
    }

    @PostMapping("/stop")
    public ResponseEntity<ApiResponse> stopMonitoring(@RequestParam String sessionId) {
        try {
            cheatingDetectionService.stopMonitoring(sessionId);
            return ResponseEntity.ok(new ApiResponse(true, "Monitoring stopped successfully"));
        } catch (Exception e) {
            log.error("Failed to stop monitoring", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse(false, "Failed to stop monitoring: " + e.getMessage()));
        }
    }

    @GetMapping("/activities")
    public ResponseEntity<List<SuspiciousActivityDTO>> getSuspiciousActivities(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String examId) {
        try {
            List<SuspiciousActivityDTO> activities = cheatingDetectionService.getSuspiciousActivities(sessionId, examId);
            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            log.error("Failed to retrieve suspicious activities", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @GetMapping("/evidence/{id}")
    public ResponseEntity<Resource> getEvidence(@PathVariable Long id) {
        try {
            Optional<SuspiciousActivity> activity = suspiciousActivityRepository.findById(id);
            if (activity.isPresent() && activity.get().getEvidencePath() != null) {
                Path path = Paths.get(activity.get().getEvidencePath());
                Resource resource = new UrlResource(path.toUri());

                if (resource.exists() && resource.isReadable()) {
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName().toString() + "\"")
                            .body(resource);
                }
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Failed to retrieve evidence", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Autowired
    private SuspiciousActivityRepository suspiciousActivityRepository;
}