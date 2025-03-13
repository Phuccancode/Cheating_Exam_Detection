package com.lms.cheating_detection.service;
import com.lms.cheating_detection.dto.SuspiciousActivityDTO;
import com.lms.cheating_detection.model.SuspiciousActivity;
import com.lms.cheating_detection.repository.SuspiciousActivityRepository;
import jakarta.annotation.PostConstruct;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CheatingDetectionService {

    private static final int CAPTURE_INTERVAL = 5000; // 5 seconds
    private final SuspiciousActivityRepository suspiciousActivityRepository;
    private Map<String, VideoCapture> studentCaptures = new ConcurrentHashMap<>();
    private Map<String, ScheduledFuture<?>> monitoringTasks = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            Math.min(100, Runtime.getRuntime().availableProcessors() * 2)
    );
    private CascadeClassifier faceCascade;
    private CascadeClassifier eyesCascade;

    @Value("${opencv.haar.face:classpath:haarcascades/haarcascade_frontalface_default.xml}")
    private String faceClassifierPath;

    @Value("${opencv.haar.eye:classpath:haarcascades/haarcascade_eye.xml}")
    private String eyeClassifierPath;

    @Value("${evidence.folder:evidence}")
    private String evidenceFolder;

    @Autowired
    public CheatingDetectionService(SuspiciousActivityRepository suspiciousActivityRepository) {
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
        if (studentCaptures.containsKey(sessionId)) {
            log.info("Monitoring already in progress for session: {}", sessionId);
            return;
        }

        try {
            // Initialize camera (0 is usually the default webcam)
            VideoCapture videoCapture = new VideoCapture(0);

            if (!videoCapture.isOpened()) {
                log.error("Failed to open webcam for session: {}", sessionId);
                throw new RuntimeException("Cannot open webcam");
            }
            videoCapture.set(Videoio.CAP_PROP_FRAME_WIDTH, 640);  // Giảm từ 1280 xuống 640
            videoCapture.set(Videoio.CAP_PROP_FRAME_HEIGHT, 480); // Giảm từ 720 xuống 480

            studentCaptures.put(sessionId, videoCapture);

            // Start periodic capture
            ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                    () -> captureAndAnalyze(sessionId, examId),
                    0, CAPTURE_INTERVAL, TimeUnit.MILLISECONDS
            );

            monitoringTasks.put(sessionId, task);
            log.info("Started monitoring for session: {}", sessionId);
        } catch (Exception e) {
            log.error("Error starting webcam monitoring for session: {}", sessionId, e);
            throw new RuntimeException("Error starting webcam monitoring", e);
        }
    }

    public void stopMonitoring(String sessionId) {
        ScheduledFuture<?> task = monitoringTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }

        VideoCapture capture = studentCaptures.remove(sessionId);
        if (capture != null && capture.isOpened()) {
            capture.release();
        }

        log.info("Stopped webcam monitoring for session: {}", sessionId);
    }

    private void captureAndAnalyze(String sessionId, String examId) {
        VideoCapture videoCapture = studentCaptures.get(sessionId);
        if (videoCapture == null || !videoCapture.isOpened()) {
            log.error("Cannot capture frame, webcam not available for session: {}", sessionId);
            return;
        }

        Mat frame = null;
        Mat grayFrame = null;
        Mat smallFrame = null;
        MatOfRect faces = null;
        try {
            frame = new Mat();
            if (!videoCapture.read(frame) || frame.empty()) {
                log.error("Empty frame captured for session: {}", sessionId);
                return;
            }

            // Convert to grayscale for detection
            grayFrame = new Mat();
            Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);

            // Tạo phiên bản thu nhỏ để xử lý nhanh hơn
            smallFrame = new Mat();
            Imgproc.resize(grayFrame, smallFrame, new Size(), 0.5, 0.5);
            Imgproc.equalizeHist(smallFrame, smallFrame);

            faces = new MatOfRect();
            // Detect faces
            faceCascade.detectMultiScale(
                    smallFrame,
                    faces,
                    1.2,        // Tăng scaleFactor để cải thiện tốc độ
                    5,          // Tăng minNeighbors để giảm false positives
                    0,
                    new Size(30, 30),  // Kích thước mặt tối thiểu với khung hình thu nhỏ
                    new Size()
            );

            Rect[] facesArray = faces.toArray();

            // Điều chỉnh lại tọa độ khuôn mặt cho kích thước gốc
            for (Rect rect : facesArray) {
                rect.x *= 2;
                rect.y *= 2;
                rect.width *= 2;
                rect.height *= 2;
            }
            boolean suspiciousActivity = false;
            String description = null;
            String evidenceType = null;

            // Check if no faces detected (student might be away)
            if (facesArray.length == 0) {
                description = "No face detected - student may be absent";
                evidenceType = "no_face";
                suspiciousActivity = true;
            }
            // Check if multiple faces detected (potential collaboration)
            else if (facesArray.length > 1) {
                description = "Multiple faces detected - potential collaboration";
                evidenceType = "multiple_faces";
                suspiciousActivity = true;
            }
            else {
                // Khi chỉ phát hiện một khuôn mặt
                Rect faceRect = facesArray[0];

                // Kiểm tra phần ROI (region of interest) của khuôn mặt
                if (faceRect.x >= 0 && faceRect.y >= 0 &&
                        faceRect.x + faceRect.width < grayFrame.width() &&
                        faceRect.y + faceRect.height < grayFrame.height()) {

                    Mat faceROI = null;
                    MatOfRect eyes = null;

                    try {
                        // Chỉ phân tích vùng khuôn mặt (ROI) để tiết kiệm tài nguyên
                        faceROI = grayFrame.submat(faceRect);
                        eyes = new MatOfRect();

                        // Tối ưu tham số phát hiện mắt
                        eyesCascade.detectMultiScale(
                                faceROI,
                                eyes,
                                1.1,
                                3,
                                0,
                                new Size(15, 15),
                                new Size()
                        );

                        if (eyes.toArray().length == 0) {
                            description = "No eyes detected - student may be looking away";
                            evidenceType = "no_eyes";
                            suspiciousActivity = true;
                        }
                        else {
                            // Kiểm tra vị trí khuôn mặt
                            double frameCenter = grayFrame.width() / 2.0;
                            double faceCenter = faceRect.x + (faceRect.width / 2.0);

                            // Nếu mặt lệch khỏi trung tâm quá 20%
                            if (Math.abs(frameCenter - faceCenter) > (grayFrame.width() * 0.2)) {
                                description = "Face not centered - student may be looking to the side";
                                evidenceType = "face_off_center";
                                suspiciousActivity = true;
                            }
                        }
                    } finally {
                        // Giải phóng tài nguyên của ROI
                        if (faceROI != null) faceROI.release();
                        if (eyes != null) eyes.release();
                    }
                }
            }

            if (suspiciousActivity) {
                // Lưu hình ảnh có độ phân giải thấp hơn làm bằng chứng để tiết kiệm không gian
                Mat evidenceFrame = new Mat();
                Imgproc.resize(frame, evidenceFrame, new Size(640, 480));

                try {
                    String evidencePath = saveFrame(evidenceFrame, sessionId, evidenceType);
                    logSuspiciousActivity(sessionId, examId, description, evidencePath);
                } finally {
                    evidenceFrame.release();
                }
            }

        } catch (Exception e) {
            log.error("Error analyzing webcam frame for session: {}", sessionId, e);
        } finally {
            // Đảm bảo giải phóng tất cả tài nguyên Mat
            if (frame != null) frame.release();
            if (grayFrame != null) grayFrame.release();
            if (smallFrame != null) smallFrame.release();
            if (faces != null) faces.release();
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

    // Phương thức saveFrame() tối ưu hóa
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
                log.info("Saved optimized evidence frame to: {}", path);
                return path;
            } finally {
                compressionParams.release();
            }
        } catch (Exception e) {
            log.error("Error saving evidence frame for session: {}", sessionId, e);
            return null;
        }
    }

    public List<SuspiciousActivityDTO> getSuspiciousActivities(String sessionId, String examId) {
        List<SuspiciousActivity> activities;

        if (sessionId != null && examId != null) {
            activities = suspiciousActivityRepository.findBySessionIdAndExamId(sessionId, examId);
        } else if (sessionId != null) {
            activities = suspiciousActivityRepository.findBySessionId(sessionId);
        } else if (examId != null) {
            activities = suspiciousActivityRepository.findByExamId(examId);
        } else {
            activities = suspiciousActivityRepository.findAll();
        }

        return activities.stream()
                .map(activity -> {
                    SuspiciousActivityDTO dto = new SuspiciousActivityDTO();
                    dto.setId(activity.getId());
                    dto.setSessionId(activity.getSessionId());
                    dto.setExamId(activity.getExamId());
                    dto.setDescription(activity.getDescription());
                    dto.setTimestamp(activity.getTimestamp());
                    dto.setEvidencePath(activity.getEvidencePath());
                    return dto;
                })
                .collect(Collectors.toList());
    }
}