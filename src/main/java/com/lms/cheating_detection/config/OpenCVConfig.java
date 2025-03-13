package com.lms.cheating_detection.config;


import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class OpenCVConfig {

    @PostConstruct
    public void loadOpenCV() {
        try {
            nu.pattern.OpenCV.loadLocally();
            System.out.println("OpenCV loaded successfully.");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load OpenCV library.");
            e.printStackTrace();
        }
    }
}