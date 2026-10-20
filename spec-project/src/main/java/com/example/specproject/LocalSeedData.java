package com.example.specproject;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로컬 확인용 시드 데이터를 넣는 곳.
 *
 * <p>이 시작 골격이 정한 시드 방식은 이 클래스 하나다. {@code ./gradlew bootRun} 은 local 프로필로 뜨므로
 * 서버가 시작될 때 {@link #run} 이 한 번 실행된다. 테스트({@code ./gradlew test})에서는 실행되지 않는다.
 *
 * <p>더미 데이터가 필요해지면 저장소(Repository)를 생성자로 주입받아 {@link #run} 안에서 저장한다.
 * 실제 학교 · 학생 이름은 쓰지 않는다.
 */
@Component
@Profile("local")
public class LocalSeedData implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalSeedData.class);

    private static final String CLASS_ID = "C1";
    private static final LocalDateTime A1_DUE_AT = LocalDateTime.of(2026, 9, 20, 23, 59);

    private final AssignmentRepository assignmentRepository;
    private final StudentRepository studentRepository;
    private final SubmissionRepository submissionRepository;

    public LocalSeedData(AssignmentRepository assignmentRepository, StudentRepository studentRepository,
            SubmissionRepository submissionRepository) {
        this.assignmentRepository = assignmentRepository;
        this.studentRepository = studentRepository;
        this.submissionRepository = submissionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        studentRepository.saveAll(List.of(
                new Student("S001", CLASS_ID),
                new Student("S002", CLASS_ID),
                new Student("S003", CLASS_ID)));

        // A1: 제출 · 재제출 · 지각이 섞인 과제. S003 은 내지 않았다.
        assignmentRepository.save(new Assignment("A1", "분수의 덧셈 연습", CLASS_ID, A1_DUE_AT));
        submissionRepository.saveAll(List.of(
                new Submission("A1", "S001", A1_DUE_AT.minusDays(2), 70),
                new Submission("A1", "S001", A1_DUE_AT.minusDays(1), 85),
                new Submission("A1", "S002", A1_DUE_AT.plusHours(3), 92)));

        // A2: 제출이 0건인 과제.
        assignmentRepository.save(new Assignment("A2", "도형의 넓이 복습", CLASS_ID, A1_DUE_AT.plusDays(7)));

        log.info("local 프로필: 시드 데이터 적재 완료");
    }
}
