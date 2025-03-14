package com.lms.cheating_detection.service;

import com.lms.cheating_detection.model.FaceDetectionResult;
import com.lms.cheating_detection.model.EyeGazeResult;
import com.lms.cheating_detection.model.HeadPoseResult;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;
import org.opencv.objdetect.CascadeClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class MediaPipeFaceDetectionService {

    private static final Logger log = LoggerFactory.getLogger(MediaPipeFaceDetectionService.class);

    @Value("${opencv.haar.face:haarcascades/haarcascade_frontalface_default.xml}")
    private String haarFaceCascadePath;

    @Value("${opencv.haar.eye:haarcascades/haarcascade_eye.xml}")
    private String haarEyeCascadePath;

    @Autowired
    private MediaPipeModelDownloadService modelDownloadService;

    private CascadeClassifier faceCascade;
    private CascadeClassifier eyesCascade;
    private boolean useMediaPipe = false;

    @PostConstruct
    public void init() {
        try {
            // Check if MediaPipe models are available
            boolean faceModelAvailable = modelDownloadService.isModelAvailable("face_detection");
            boolean landmarksModelAvailable = modelDownloadService.isModelAvailable("face_landmarks");

            useMediaPipe = faceModelAvailable && landmarksModelAvailable;

            if (useMediaPipe) {
                log.info("Using MediaPipe models for face and landmark detection");
                // Initialize MediaPipe-based detection here if models are available
                // This is where you would load the TensorFlow Lite models
            } else {
                log.info("Using OpenCV Haar cascades for face and eye detection (MediaPipe models not available)");
            }

            // Always load OpenCV cascades as fallback
            loadOpenCVCascades();

        } catch (Exception e) {
            log.error("Error initializing face detection service", e);
            throw new RuntimeException("Error initializing face detection service", e);
        }
    }

    private void loadOpenCVCascades() throws IOException {
        // Load standard Haar cascades from classpath resources
        Resource faceResource = new ClassPathResource(haarFaceCascadePath);
        Resource eyeResource = new ClassPathResource(haarEyeCascadePath);

        // Create temporary files
        File tempFaceFile = File.createTempFile("face", ".xml");
        File tempEyeFile = File.createTempFile("eye", ".xml");
        tempFaceFile.deleteOnExit();
        tempEyeFile.deleteOnExit();

        // Copy resources to temp files
        FileCopyUtils.copy(faceResource.getInputStream(), new FileOutputStream(tempFaceFile));
        FileCopyUtils.copy(eyeResource.getInputStream(), new FileOutputStream(tempEyeFile));

        // Load classifiers
        this.faceCascade = new CascadeClassifier();
        this.eyesCascade = new CascadeClassifier();

        if (!this.faceCascade.load(tempFaceFile.getAbsolutePath())) {
            log.error("Failed to load face cascade classifier");
            throw new RuntimeException("Failed to load face cascade classifier");
        }

        if (!this.eyesCascade.load(tempEyeFile.getAbsolutePath())) {
            log.error("Failed to load eye cascade classifier");
            throw new RuntimeException("Failed to load eye cascade classifier");
        }

        log.info("OpenCV classifiers loaded successfully");
    }

    public FaceDetectionResult analyzeFrame(Mat frame) {
        if (useMediaPipe) {
            return analyzeWithMediaPipe(frame);
        } else {
            return analyzeWithOpenCV(frame);
        }
    }

    private FaceDetectionResult analyzeWithMediaPipe(Mat frame) {
        // This would use MediaPipe models if they're available
        // For now, fall back to OpenCV since we haven't implemented the TFLite integration yet
        log.debug("MediaPipe analysis not fully implemented yet, using OpenCV fallback");
        return analyzeWithOpenCV(frame);
    }

    private FaceDetectionResult analyzeWithOpenCV(Mat frame) {
        FaceDetectionResult result = new FaceDetectionResult();

        try {
            Mat grayFrame = new Mat();
            Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(grayFrame, grayFrame);

            // Detect faces
            MatOfRect faces = new MatOfRect();
            faceCascade.detectMultiScale(
                    grayFrame,
                    faces,
                    1.1,        // Scale factor
                    3,          // Min neighbors
                    0,          // Flags
                    new Size(30, 30),  // Min size
                    new Size()         // Max size
            );

            Rect[] facesArray = faces.toArray();
            result.setFaceCount(facesArray.length);

            if (facesArray.length == 1) {
                // Process single face
                Rect faceRect = facesArray[0];

                // Get face region of interest
                Mat faceROI = grayFrame.submat(faceRect);

                // Detect eyes within the face region
                MatOfRect eyes = new MatOfRect();
                eyesCascade.detectMultiScale(
                        faceROI,
                        eyes,
                        1.1,        // Scale factor (reduced for better detection)
                        2,          // Min neighbors (reduced from default for more lenient detection)
                        0,          // Flags
                        new Size(10, 10), // Smaller minimum eye size
                        new Size((double) faceRect.width /2, (double) faceRect.height /2) // Maximum eye size constraint
                );
                Rect[] eyesArray = eyes.toArray();

                // Analyze eye positions
                if (eyesArray.length >= 2) {
                    // Found at least two eyes
                    // Sort eyes by x-position (left to right)
                    List<Rect> eyesList = new ArrayList<>(List.of(eyesArray));
                    eyesList.sort((e1, e2) -> Integer.compare(e1.x, e2.x));

                    // The first eye should be the left eye, second the right eye
                    Rect leftEye = eyesList.get(0);
                    Rect rightEye = eyesList.get(1);

                    // Calculate eye positions
                    Point leftEyeCenter = new Point(faceRect.x + leftEye.x + leftEye.width/2.0,
                            faceRect.y + leftEye.y + leftEye.height/2.0);
                    Point rightEyeCenter = new Point(faceRect.x + rightEye.x + rightEye.width/2.0,
                            faceRect.y + rightEye.y + rightEye.height/2.0);

                    // Calculate head pose estimates
                    HeadPoseResult headPose = estimateHeadPose(leftEyeCenter, rightEyeCenter, faceRect);
                    result.setHeadPose(headPose);

                    // Try to detect eye gaze (pupil position)
                    EyeGazeResult eyeGaze = estimateEyeGaze(frame, faceRect, leftEye, rightEye);
                    result.setEyeGaze(eyeGaze);

                    // Determine if activity is suspicious
                    result.setSuspiciousActivity(detectSuspiciousActivity(headPose, eyeGaze));
                    result.setDescription(generateDescription(result));
                    result.setEvidenceType(result.isSuspiciousActivity() ? "suspicious_gaze" : "normal");
                } else {
                    result.setSuspiciousActivity(true);
                    result.setDescription("Eyes not detected clearly - student may be looking away");
                    result.setEvidenceType("no_eyes");
                }

                // Clean up
                if (faceROI != null) faceROI.release();
                if (eyes != null) eyes.release();

            } else if (facesArray.length == 0) {
                result.setSuspiciousActivity(true);
                result.setDescription("No face detected - student may be absent");
                result.setEvidenceType("no_face");
            } else {
                result.setSuspiciousActivity(true);
                result.setDescription("Multiple faces detected (" + facesArray.length + ") - potential collaboration");
                result.setEvidenceType("multiple_faces");
            }

            // Clean up
            if (grayFrame != null) grayFrame.release();
            if (faces != null) faces.release();

        } catch (Exception e) {
            log.error("Error analyzing frame", e);
            result.setSuspiciousActivity(false);
            result.setDescription("Error analyzing frame: " + e.getMessage());
        }

        return result;
    }

    // The rest of the methods (estimateHeadPose, estimateEyeGaze, etc.) remain the same as in your existing code...

    private HeadPoseResult estimateHeadPose(Point leftEye, Point rightEye, Rect faceRect) {
        // Simple head pose estimation based on eye position and face dimensions
        // In a real implementation, this would use facial landmarks and 3D geometry

        double yaw = 0.0;  // Left-right rotation
        double pitch = 0.0; // Up-down rotation
        double roll = 0.0;  // Tilting head side to side

        // Calculate roll angle based on eye slope
        double dY = rightEye.y - leftEye.y;
        double dX = rightEye.x - leftEye.x;
        roll = Math.toDegrees(Math.atan2(dY, dX));

        // Calculate yaw based on eye position relative to face width
        double faceCenter = faceRect.x + faceRect.width / 2.0;
        double eyesMidpointX = (leftEye.x + rightEye.x) / 2.0;
        double eyeOffsetRatio = (eyesMidpointX - faceCenter) / (faceRect.width / 2.0);
        yaw = eyeOffsetRatio * 45.0; // Approximate range of -45 to 45 degrees

        // Calculate pitch based on eye position relative to face height
        double faceTop = faceRect.y;
        double faceHeight = faceRect.height;
        double eyesMidpointY = (leftEye.y + rightEye.y) / 2.0;
        double eyeVerticalRatio = (eyesMidpointY - faceTop) / faceHeight;

        // Eyes normally should be around 0.4 from the top in a frontal face
        pitch = (eyeVerticalRatio - 0.4) * 90.0; // Approximate range

        return new HeadPoseResult(yaw, pitch, roll);
    }

    private EyeGazeResult estimateEyeGaze(Mat frame, Rect faceRect, Rect leftEyeRect, Rect rightEyeRect) {
        EyeGazeResult result = new EyeGazeResult();

        try {
            // Convert coordinates to be relative to the entire frame
            Rect absLeftEyeRect = new Rect(
                    faceRect.x + leftEyeRect.x,
                    faceRect.y + leftEyeRect.y,
                    leftEyeRect.width,
                    leftEyeRect.height
            );

            Rect absRightEyeRect = new Rect(
                    faceRect.x + rightEyeRect.x,
                    faceRect.y + rightEyeRect.y,
                    rightEyeRect.width,
                    rightEyeRect.height
            );

            // Get eye regions
            Mat leftEyeRegion = new Mat(frame, absLeftEyeRect);
            Mat rightEyeRegion = new Mat(frame, absRightEyeRect);

            // Find pupils in each eye using simple image processing
            Point leftPupil = findPupilCenter(leftEyeRegion);
            Point rightPupil = findPupilCenter(rightEyeRegion);

            // Normalize pupil positions to -1.0 to 1.0 range where 0,0 is the eye center
            double leftEyeNormX = (leftPupil.x - (leftEyeRect.width / 2.0)) / (leftEyeRect.width / 2.0);
            double leftEyeNormY = (leftPupil.y - (leftEyeRect.height / 2.0)) / (leftEyeRect.height / 2.0);

            double rightEyeNormX = (rightPupil.x - (rightEyeRect.width / 2.0)) / (rightEyeRect.width / 2.0);
            double rightEyeNormY = (rightPupil.y - (rightEyeRect.height / 2.0)) / (rightEyeRect.height / 2.0);

            // Set the gaze direction
            result.setLeftEyeHorizontalGaze(leftEyeNormX);
            result.setLeftEyeVerticalGaze(leftEyeNormY);
            result.setRightEyeHorizontalGaze(rightEyeNormX);
            result.setRightEyeVerticalGaze(rightEyeNormY);

            // Calculate deviation from center
            result.setLeftEyePupilDeviation(Math.sqrt(leftEyeNormX*leftEyeNormX + leftEyeNormY*leftEyeNormY));
            result.setRightEyePupilDeviation(Math.sqrt(rightEyeNormX*rightEyeNormX + rightEyeNormY*rightEyeNormY));

            // Clean up
            if (leftEyeRegion != null) leftEyeRegion.release();
            if (rightEyeRegion != null) rightEyeRegion.release();

        } catch (Exception e) {
            log.warn("Error estimating eye gaze", e);
            // Set default values in case of error
            result.setLeftEyeHorizontalGaze(0);
            result.setLeftEyeVerticalGaze(0);
            result.setRightEyeHorizontalGaze(0);
            result.setRightEyeVerticalGaze(0);
        }

        return result;
    }

    private Point findPupilCenter(Mat eyeRegion) {
        // Convert to grayscale if needed
        Mat grayEye = new Mat();
        if (eyeRegion.channels() > 1) {
            Imgproc.cvtColor(eyeRegion, grayEye, Imgproc.COLOR_BGR2GRAY);
        } else {
            eyeRegion.copyTo(grayEye);
        }

        try {
            // Apply GaussianBlur to reduce noise
            Imgproc.GaussianBlur(grayEye, grayEye, new Size(5, 5), 0);

            // Apply threshold to identify darker regions (pupils are typically dark)
            Mat thresholdEye = new Mat();
            Imgproc.threshold(grayEye, thresholdEye, 70, 255, Imgproc.THRESH_BINARY_INV);

            // Find contours of the thresholded image
            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(thresholdEye, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // Find the largest contour (likely the pupil)
            double maxArea = -1;
            int maxAreaIdx = -1;
            for (int i = 0; i < contours.size(); i++) {
                double area = Imgproc.contourArea(contours.get(i));
                if (area > maxArea) {
                    maxArea = area;
                    maxAreaIdx = i;
                }
            }

            // If we found a contour, calculate its center
            if (maxAreaIdx >= 0) {
                Moments moments = Imgproc.moments(contours.get(maxAreaIdx));
                double centerX = moments.get_m10() / moments.get_m00();
                double centerY = moments.get_m01() / moments.get_m00();

                // Clean up
                if (thresholdEye != null) thresholdEye.release();
                if (hierarchy != null) hierarchy.release();
                for (MatOfPoint contour : contours) {
                    if (contour != null) contour.release();
                }

                return new Point(centerX, centerY);
            } else {
                // If no contour found, assume center of eye region
                // Clean up
                if (thresholdEye != null) thresholdEye.release();
                if (hierarchy != null) hierarchy.release();
                for (MatOfPoint contour : contours) {
                    if (contour != null) contour.release();
                }

                return new Point(grayEye.cols() / 2.0, grayEye.rows() / 2.0);
            }
        } finally {
            if (grayEye != null) grayEye.release();
        }
    }

    private boolean detectSuspiciousActivity(HeadPoseResult headPose, EyeGazeResult eyeGaze) {
        // Check for suspicious head pose (looking away)
        boolean suspiciousHeadPose = Math.abs(headPose.getYaw()) > 30 || Math.abs(headPose.getPitch()) > 20;

        // Check for suspicious eye gaze (looking to the side)
        boolean suspiciousEyeGaze = Math.abs(eyeGaze.getLeftEyeHorizontalGaze()) > 0.3 ||
                Math.abs(eyeGaze.getRightEyeHorizontalGaze()) > 0.3;

        // Combined detection: If both head and eyes are looking in the same direction away from the screen
        boolean combinedSuspicious = false;

        // If head is turning right AND eyes are looking right -> suspicious
        if (headPose.getYaw() > 15 && (eyeGaze.getLeftEyeHorizontalGaze() > 0.2 && eyeGaze.getRightEyeHorizontalGaze() > 0.2)) {
            combinedSuspicious = true;
        }

        // If head is turning left AND eyes are looking left -> suspicious
        if (headPose.getYaw() < -15 && (eyeGaze.getLeftEyeHorizontalGaze() < -0.2 && eyeGaze.getRightEyeHorizontalGaze() < -0.2)) {
            combinedSuspicious = true;
        }

        // If head is straight but eyes are looking significantly to the side -> suspicious
        if (Math.abs(headPose.getYaw()) < 10 &&
                (Math.abs(eyeGaze.getLeftEyeHorizontalGaze()) > 0.4 || Math.abs(eyeGaze.getRightEyeHorizontalGaze()) > 0.4)) {
            combinedSuspicious = true;
        }

        return suspiciousHeadPose || suspiciousEyeGaze || combinedSuspicious;
    }

    private String generateDescription(FaceDetectionResult result) {
        HeadPoseResult headPose = result.getHeadPose();
        EyeGazeResult eyeGaze = result.getEyeGaze();

        // No face or multiple faces
        if (result.getFaceCount() != 1) {
            return result.getDescription();
        }

        // Check head pose
        if (Math.abs(headPose.getYaw()) > 30) {
            return "Head turned " + (headPose.getYaw() > 0 ? "right" : "left") + " - student may be looking away";
        }

        if (Math.abs(headPose.getPitch()) > 20) {
            return "Head tilted " + (headPose.getPitch() > 0 ? "up" : "down") + " - student may be looking at notes";
        }

        // Check eye gaze
        double leftGaze = eyeGaze.getLeftEyeHorizontalGaze();
        double rightGaze = eyeGaze.getRightEyeHorizontalGaze();

        if ((leftGaze > 0.3 && rightGaze > 0.3) || (leftGaze < -0.3 && rightGaze < -0.3)) {
            return "Eyes looking " + (leftGaze > 0 ? "right" : "left") + " - student may be viewing other materials";
        }

        // Combined head and eye behavior
        if (headPose.getYaw() > 15 && (leftGaze > 0.2 && rightGaze > 0.2)) {
            return "Head and eyes turned right - likely looking at unauthorized materials";
        }

        if (headPose.getYaw() < -15 && (leftGaze < -0.2 && rightGaze < -0.2)) {
            return "Head and eyes turned left - likely looking at unauthorized materials";
        }

        if (Math.abs(headPose.getYaw()) < 10 &&
                (Math.abs(leftGaze) > 0.4 || Math.abs(rightGaze) > 0.4)) {
            return "Eyes looking to the side while head is straight - likely attempting to cheat";
        }

        return "No suspicious activity detected";
    }
}