package com.lms.cheating_detection.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class SuspiciousActivityDTO {
    private Long id;
    private String sessionId;
    private String examId;
    private String description;
    private LocalDateTime timestamp;
    private String evidencePath;
}
