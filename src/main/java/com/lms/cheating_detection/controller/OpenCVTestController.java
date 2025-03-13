package com.lms.cheating_detection.controller;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenCVTestController {

    @GetMapping("/opencv-test")
    public String opencvTest() {
        try {
            // Kiểm tra phiên bản OpenCV
            String version = Core.VERSION;
            System.out.println("OpenCV version: " + version);

            // Đọc một hình ảnh mẫu
            String imagePath = "src/main/resources/images.jpg"; // Đảm bảo hình ảnh này tồn tại
            Mat image = Imgcodecs.imread(imagePath);
            if (image.empty()) {
                return "Không thể đọc hình ảnh từ đường dẫn: " + imagePath;
            } else {
                return "Đọc hình ảnh thành công từ đường dẫn: " + imagePath;
            }
        } catch (UnsatisfiedLinkError e) {
            return "Không thể tải thư viện OpenCV.";
        } catch (Exception e) {
            return "Lỗi: " + e.getMessage();
        }
    }
}