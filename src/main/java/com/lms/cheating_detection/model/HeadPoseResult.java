package com.lms.cheating_detection.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class HeadPoseResult {
    private double yaw;   // head turning left/right (in degrees)
    private double pitch; // head looking up/down (in degrees)
    private double roll;  // head tilting left/right (in degrees)

    public HeadPoseResult() {
    }

    public HeadPoseResult(double yaw, double pitch, double roll) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.roll = roll;
    }

    @Override
    public String toString() {
        return String.format("Head Pose: yaw=%.2f°, pitch=%.2f°, roll=%.2f°", yaw, pitch, roll);
    }
}