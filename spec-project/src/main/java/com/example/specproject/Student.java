package com.example.specproject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** 학급에 속한 학생. 학급은 별도 엔티티 없이 classId 문자열로만 둔다. */
@Entity
public class Student {

    @Id
    private String id;

    @Column(nullable = false)
    private String classId;

    protected Student() {
    }

    public Student(String id, String classId) {
        this.id = id;
        this.classId = classId;
    }

    public String getId() {
        return id;
    }

    public String getClassId() {
        return classId;
    }
}
