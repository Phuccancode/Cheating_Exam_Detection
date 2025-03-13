package com.lms.cheating_detection.repository;


import com.lms.cheating_detection.model.SuspiciousActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SuspiciousActivityRepository extends JpaRepository<SuspiciousActivity, Long> {
    List<SuspiciousActivity> findBySessionId(String sessionId);
    List<SuspiciousActivity> findByExamId(String examId);
    List<SuspiciousActivity> findBySessionIdAndExamId(String sessionId, String examId);
    List<SuspiciousActivity> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}