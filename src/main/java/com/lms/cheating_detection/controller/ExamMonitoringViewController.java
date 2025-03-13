package com.lms.cheating_detection.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ExamMonitoringViewController {

    @GetMapping("/exam-monitor")
    public String examMonitorPage() {
        return "exam-monitor"; // trỏ đến exam-monitor.html
    }
}