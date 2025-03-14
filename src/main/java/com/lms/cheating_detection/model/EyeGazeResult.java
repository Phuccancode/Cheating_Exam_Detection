package com.lms.cheating_detection.model;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class EyeGazeResult {
    // Normalized values from -1.0 to 1.0
    // where 0,0 is looking straight at the camera
    private double leftEyeHorizontalGaze;  // negative = looking left, positive = looking right
    private double leftEyeVerticalGaze;    // negative = looking down, positive = looking up

    private double rightEyeHorizontalGaze; // negative = looking left, positive = looking right
    private double rightEyeVerticalGaze;   // negative = looking down, positive = looking up

    // Distance between pupil center and eye center, normalized by eye width
    private double leftEyePupilDeviation;
    private double rightEyePupilDeviation;

    public EyeGazeResult() {
    }

    @Override
    public String toString() {
        return String.format("Eye Gaze: left(h=%.2f, v=%.2f, dev=%.2f), right(h=%.2f, v=%.2f, dev=%.2f)",
                leftEyeHorizontalGaze, leftEyeVerticalGaze, leftEyePupilDeviation,
                rightEyeHorizontalGaze, rightEyeVerticalGaze, rightEyePupilDeviation);
    }
}