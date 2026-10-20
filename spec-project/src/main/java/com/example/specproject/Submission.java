package com.example.specproject;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 학생이 과제에 낸 제출 한 건. 재제출하면 기록이 하나 더 쌓인다(스펙 3.4). */
@Entity
public class Submission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String assignmentId;

    @Column(nullable = false)
    private String studentId;

    @Column(nullable = false)
    private LocalDateTime submittedAt;

    // 점수는 0 이상 100 이하의 정수다(스펙 3.2).
    @Min(0)
    @Max(100)
    @Column(nullable = false)
    private int score;

    protected Submission() {
    }

    public Submission(String assignmentId, String studentId, LocalDateTime submittedAt, int score) {
        this.assignmentId = assignmentId;
        this.studentId = studentId;
        this.submittedAt = submittedAt;
        this.score = score;
    }

    public Long getId() {
        return id;
    }

    public String getAssignmentId() {
        return assignmentId;
    }

    public String getStudentId() {
        return studentId;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public int getScore() {
        return score;
    }
}
