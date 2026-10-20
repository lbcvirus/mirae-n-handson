package com.example.specproject;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRepository extends JpaRepository<Student, String> {

    List<Student> findByClassId(String classId);
}
