package com.example.specproject;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 학급 하나에 배포된 과제. 식별자는 문자열이다(스펙 3.1). */
@Entity
public class Assignment {

    @Id
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String classId;

    @Column(nullable = false)
    private LocalDateTime dueAt;

    protected Assignment() {
    }

    public Assignment(String id, String title, String classId, LocalDateTime dueAt) {
        this.id = id;
        this.title = title;
        this.classId = classId;
        this.dueAt = dueAt;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getClassId() {
        return classId;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }
}
