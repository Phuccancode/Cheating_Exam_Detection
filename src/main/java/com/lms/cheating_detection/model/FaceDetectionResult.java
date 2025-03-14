package com.lms.cheating_detection.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FaceDetectionResult {
    private int faceCount;
    private HeadPoseResult headPose;
    private EyeGazeResult eyeGaze;
    private boolean suspiciousActivity;
    private String description;
    private String evidenceType;

    public FaceDetectionResult() {
        this.faceCount = 0;
        this.suspiciousActivity = false;
        this.description = "No result yet";
    }

}