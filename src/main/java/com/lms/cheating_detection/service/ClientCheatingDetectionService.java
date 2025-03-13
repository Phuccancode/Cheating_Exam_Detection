package com.lms.cheating_detection.service;

import com.lms.cheating_detection.model.SuspiciousActivity;
import com.lms.cheating_detection.repository.SuspiciousActivityRepository;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClientCheatingDetectionService {

    private static final Logger log = LoggerFactory.getLogger(ClientCheatingDetectionService.class);

    private final SuspiciousActivityRepository suspiciousActivityRepository;
    private Map<String, Boolean> activeMonitoringSessions = new ConcurrentHashMap<>();

    private CascadeClassifier faceCascade;
    private CascadeClassifier eyesCascade;

    @Value("${evidence.folder:evidence}")
    private String evidenceFolder;

    @Autowired
    public ClientCheatingDetectionService(SuspiciousActivityRepository suspiciousActivityRepository) {
        this.suspiciousActivityRepository = suspiciousActivityRepository;
    }

    @PostConstruct
    public void init() {
        try {
            // Tải file từ classpath sử dụng Spring Resource
            Resource faceResource = new ClassPathResource("haarcascades/haarcascade_frontalface_default.xml");
            Resource eyeResource = new ClassPathResource("haarcascades/haarcascade_eye.xml");

            // Sao chép các file vào thư mục tạm để OpenCV có thể đọc được
            File tempFaceFile = File.createTempFile("face", ".xml");
            File tempEyeFile = File.createTempFile("eye", ".xml");
            tempFaceFile.deleteOnExit();
            tempEyeFile.deleteOnExit();

            FileCopyUtils.copy(faceResource.getInputStream(), new FileOutputStream(tempFaceFile));
            FileCopyUtils.copy(eyeResource.getInputStream(), new FileOutputStream(tempEyeFile));

            // Load các file từ đường dẫn tạm
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

            log.info("Cascade classifiers loaded successfully");

            // Create evidence directory if it doesn't exist
            File directory = new File(evidenceFolder);
            if (!directory.exists()) {
                directory.mkdirs();
            }
        } catch (IOException e) {
            log.error("Error loading cascade classifiers", e);
            throw new RuntimeException("Error loading cascade classifiers", e);
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
        Mat grayFrame = null;
        MatOfRect faces = null;

        try {
            // Chuyển đổi mảng byte thành hình ảnh OpenCV
            frame = Imgcodecs.imdecode(new MatOfByte(imageData), Imgcodecs.IMREAD_COLOR);
            if (frame.empty()) {
                log.error("Failed to decode image for session: {}", sessionId);
                return false;
            }

            // Xử lý hình ảnh
            grayFrame = new Mat();
            Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(grayFrame, grayFrame);

            // Phát hiện khuôn mặt với tham số tối ưu
            faces = new MatOfRect();
            faceCascade.detectMultiScale(
                    grayFrame,
                    faces,
                    1.2,        // Tăng scaleFactor để cải thiện tốc độ
                    5,          // Tăng minNeighbors để giảm false positives
                    0,
                    new Size(60, 60),  // Kích thước mặt tối thiểu
                    new Size()
            );

            Rect[] facesArray = faces.toArray();

            boolean suspiciousActivity = false;
            String description = null;
            String evidenceType = null;

            // Kiểm tra kết quả phát hiện
            if (facesArray.length == 0) {
                description = "No face detected - student may be absent";
                evidenceType = "no_face";
                suspiciousActivity = true;
            }
            else if (facesArray.length > 1) {
                description = "Multiple faces detected - potential collaboration";
                evidenceType = "multiple_faces";
                suspiciousActivity = true;
            }
            else {
                // Khi chỉ phát hiện một khuôn mặt
                Rect faceRect = facesArray[0];
                Mat faceROI = null;
                MatOfRect eyes = null;

                try {
                    // Kiểm tra xem vùng khuôn mặt có hợp lệ không
                    if (faceRect.x >= 0 && faceRect.y >= 0 &&
                            faceRect.x + faceRect.width < grayFrame.width() &&
                            faceRect.y + faceRect.height < grayFrame.height()) {

                        faceROI = grayFrame.submat(faceRect);
                        eyes = new MatOfRect();
                        eyesCascade.detectMultiScale(faceROI, eyes);

                        if (eyes.toArray().length == 0) {
                            description = "No eyes detected - student may be looking away";
                            evidenceType = "no_eyes";
                            suspiciousActivity = true;
                        }
                        else {
                            // Kiểm tra vị trí khuôn mặt
                            double frameCenter = frame.width() / 2.0;
                            double faceCenter = faceRect.x + (faceRect.width / 2.0);

                            if (Math.abs(frameCenter - faceCenter) > (frame.width() * 0.2)) {
                                description = "Face not centered - student may be looking to the side";
                                evidenceType = "face_off_center";
                                suspiciousActivity = true;
                            }
                        }
                    }
                } finally {
                    // Giải phóng tài nguyên
                    if (faceROI != null) faceROI.release();
                    if (eyes != null) eyes.release();
                }
            }

            // Lưu kết quả nếu đáng ngờ
            if (suspiciousActivity) {
                String evidencePath = saveFrame(frame, sessionId, evidenceType);
                logSuspiciousActivity(sessionId, examId, description, evidencePath);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Error analyzing frame for session: {}", sessionId, e);
            return false;
        } finally {
            // Giải phóng tài nguyên
            if (frame != null) frame.release();
            if (grayFrame != null) grayFrame.release();
            if (faces != null) faces.release();
        }
    }

    private String saveFrame(Mat frame, String sessionId, String type) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("evidence_%s_%s_%s.jpg", sessionId, type, timestamp);
            String path = evidenceFolder + File.separator + filename;

            // Tối ưu chất lượng JPEG để tiết kiệm dung lượng
            MatOfInt compressionParams = new MatOfInt(
                    Imgcodecs.IMWRITE_JPEG_QUALITY,
                    70  // Chất lượng 70% thay vì mặc định 95%
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