# 실전 프로젝트 설계 — 백엔드 / 과제 채점 리포트 요약 API

## 1. 목표
`specs/grading-report-api.md` 의 2.1(과제별 채점 리포트 요약 값)만을 `specs/starters/java` 시작 골격 위에 `GET` 엔드포인트 하나로 구현하고, 테스트로 수용 기준 5.1 · 5.2 · 5.5 · 5.8 을 확인한다.

## 2. 재료

2026-10-20 에 저장소를 직접 열어 보고 판정했다.

| 재료 | 있음/없음 | 경로 |
|---|---|---|
| 요구사항 스펙 | 있음 | `specs/grading-report-api.md` (2.1 · 3.2~3.4 · 5.1 · 5.2 · 5.5 · 5.8 을 쓴다) |
| 시작 골격 (Spring Boot 3.3 · Java 21 · H2 메모리 DB) | 있음 | `specs/starters/java/` (`GET /health`, `HealthControllerTest`, 비어 있는 `LocalSeedData.run()`) |
| 필요한 의존성 (web · data-jpa · validation · h2 · test) | 있음 | `specs/starters/java/build.gradle`. 새로 추가할 의존성은 없다 |
| 검증 Skill | 있음 | `.claude/skills/verify/SKILL.md` (판정 기준 `templates/approval-checklist.md`) |
| 컨벤션 점검 Skill | 있음 | `.claude/skills/convention-check/SKILL.md`. 8개 항목이 `modern/api` 기준이라 골격 코드에 그대로 맞는지는 확인하지 않았다 (4번 질문 7) |
| 테스트 · 리뷰 Sub-agent | 있음 | `.claude/agents/tester.md`, `.claude/agents/reviewer.md` |
| 이번에 쓰지 않는 Skill · Agent | 있음 | `.claude/skills/batch-check/`, `.claude/skills/rca/`, `.claude/skills/document-module/`, `.claude/agents/iac-reviewer.md` |
| Hook (위험 명령 차단 · 비밀값 검사) | 있음 | `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`. `.claude/settings.json` 의 `hooks` 에 등록되어 있음 |
| 동작 보존 테스트 틀 | 있음 (이번엔 해당 없음) | `characterization/`. 레거시 → 현행 비교용이고, 이번 작업은 레거시가 없는 신규 개발이다 |
| 현행 API 의 비슷한 기능 | 있음 (참고만) | `modern/api/src/main/java/com/example/assignment/ReportService.java` (학급 단위 리포트). 식별자가 `Integer` 이고 학급-학생 소속 테이블이 없어서 스펙과 모델이 다르다 |
| 과제 도메인 문서 | 있음 (참고만) | `docs/assignment/ARCHITECTURE.md`, `docs/assignment/BUSINESS-RULES.md`, `docs/assignment/ERD.md` |
| 파이프라인 샘플 | 있음 (백엔드 트랙에서 쓰지 않음) | `pipeline-samples/` (배치 로그 · 적재 건수 CSV · Terraform) |
| 스펙 프로젝트 작업 폴더 | **없음 → 새로 만들 것** | `~/work/spec-project` 가 없다. 어디에 둘지는 4번 질문 1 |
| 엔티티 · 리포지토리 (과제 · 학생 · 제출) | **없음 → 새로 만들 것** | `<P>/src/main/java/com/example/specproject/` |
| 리포트 서비스 · 컨트롤러 · 응답 record | **없음 → 새로 만들 것** | 위와 같은 패키지 |
| 리포트 테스트 | **없음 → 새로 만들 것** | `<P>/src/test/java/com/example/specproject/` |
| 로컬 시드 데이터 | **없음 → 새로 만들 것** | `<P>/src/main/java/com/example/specproject/LocalSeedData.java` 의 `run()` 안 |

`<P>` 는 스펙 프로젝트 루트다(4번 질문 1 에서 정한다).

## 3. 범위
### 한다
1. 과제 · 학생(학급 소속) · 제출 엔티티 세 개와 리포지토리, 로컬 시드 데이터.
2. 과제 하나의 요약 값을 돌려주는 `GET` 엔드포인트 하나: 과제 ID · 제목 · 학급 ID · 마감 시각 · 대상 학생 수 · 제출 / 미제출 / 지각 건수 · 평균 / 최고 / 최저 점수.
3. 수용 기준 5.1 · 5.2 · 5.5 · 5.8 을 확인하는 테스트.

### 하지 않는다
- 2.2 학생별 제출 현황 목록 — 범위를 "2.1만"으로 정했다. 응답에 학생 배열을 넣지 않는다(5.3 · 5.4 제외).
- 2.3 조회 권한(교사 ID 확인) — 2.1 범위 밖이고 오류 상태 코드 결정이 따로 필요하다(5.6 · 5.7 제외).
- 학급 엔티티 · 담당 교사 정보 — 권한 검사를 하지 않으므로 학급은 학생과 과제에 붙는 `classId` 문자열로만 둔다.
- `modern/api` 수정 · `characterization` 테스트 — 대상 코드가 시작 골격이고, 비교할 레거시가 없다.
- 화면 · 인증 · CSV/PDF 내보내기 · 점수 수정 — 스펙 6장 "범위 밖"에 있다.
- 의존성 추가 · 외부 DB 연결 — 골격의 H2 로 충분하고, 스펙 4장이 "시작 골격에 설정된 저장 방식"을 요구한다.

## 4. 미해결 질문

스펙과 저장소에서 답을 찾지 못한 것들이다. 각 항목의 "제안"은 설계안일 뿐 확인된 사실이 아니다. 2단계를 시작하기 전에 정해야 한다.

1. **프로젝트 위치** — `specs/starters/java` 를 어디에 복사해서 작업하나? `~/work/spec-project`(harness-baseline README 의 Day 4-2 경로)에 두면 이 저장소의 `.claude/` · `hooks/` · `templates/` 가 적용되지 않아 `/verify` 를 그대로 쓸 수 없다. 이 저장소 안에 두면 커밋 범위가 섞인다. 제안: 이 저장소 밖 `~/work/spec-project`.
2. **재제출의 대표 제출** (스펙 3.4) — 요약 값의 점수와 지각 여부는 학생별 최신 · 최고 · 최초 제출 중 무엇으로 계산하나? 제안: 최신 제출.
3. **"제출 건수"의 단위** (스펙 2.1) — 제출한 학생 수인가, 제출 기록 수인가? 미제출 건수 = 대상 학생 수 − 제출 건수가 성립하려면 학생 수여야 한다. 제안: 제출한 학생 수.
4. **평균 반올림** (5.2 "보기 좋게 적절히") — 자릿수와 방식은? 제안: 소수 첫째 자리, `HALF_UP`.
5. **제출이 0건일 때** (5.5) — 평균 · 최고 · 최저를 `null` 로 줄지 `0` 으로 줄지? 제안: `null`.
6. **지각 경계와 없는 과제의 오류** — 마감 시각과 같은 시각의 제출은 지각인가(제안: 마감 시각보다 늦어야 지각)? 5.8 "적절한 오류"의 상태 코드는?(제안: 404 + `{"message": …}`)
7. **엔드포인트 경로 · 엔티티 생성 승인** — 제안 경로는 `GET /api/assignments/{assignmentId}/grading-report`. 골격에 `@Entity` 를 새로 만드는 일이 CLAUDE.md 팀 규칙 4(DB 스키마 변경은 먼저 묻는다)에 해당하는지, `/convention-check` 의 `modern/api` 기준(예: `ItemFixtures`, `@ActiveProfiles("test")`)을 골격 테스트에도 적용하는지 확인이 필요하다.

## 5. 단계

### 1단계 — 골격 준비 · 엔티티 · 시드 (약 10분)
- **할 일**: 4번 질문 1 에서 정한 위치에 `specs/starters/java` 를 복사한다. `Assignment`(id · title · classId · dueAt), `Student`(id · classId), `Submission`(자동 id · assignmentId · studentId · submittedAt · score 정수 0~100) 엔티티와 리포지토리를 만든다. `LocalSeedData.run()` 에 과제 `A1`(제출 · 재제출 · 지각 섞음)과 제출 0건 과제 `A2`, 학급 `C1` 학생 3명을 넣는다.
- **만들거나 고칠 파일**: `<P>/src/main/java/com/example/specproject/` 아래 `Assignment.java` · `Student.java` · `Submission.java` · `AssignmentRepository.java` · `StudentRepository.java` · `SubmissionRepository.java`, `LocalSeedData.java`(수정).
- **확인 방법**:
  - `cd <P> && ./gradlew test` → `BUILD SUCCESSFUL`, `HealthControllerTest` 1건 통과.
  - `cd <P> && ./gradlew bootRun` → 로그에 `local 프로필: 시드 데이터 적재 완료` 한 줄이 보이고 예외 스택이 없다.

### 2단계 — 리포트 서비스 · 컨트롤러 · 테스트 (약 20분)
- **할 일**: 4번 질문 2~7 에서 정한 규칙으로 요약 값을 계산하는 서비스와 `GET` 엔드포인트, 응답 record 를 만든다. 학생 수가 40명 이하(스펙 4장)라 학생 · 제출을 한 번씩 읽어 메모리에서 집계한다. 테스트는 테스트 안에서 데이터를 만든다(골격 README "시드 데이터").
- **만들거나 고칠 파일**: `<P>/src/main/java/com/example/specproject/` 아래 `GradingReportService.java` · `GradingReportController.java` · `GradingReportResponse.java` · 없는 과제용 예외와 예외 처리기, `<P>/src/test/java/com/example/specproject/GradingReportServiceTest.java` · `GradingReportControllerTest.java`.
- **확인 방법**: `cd <P> && ./gradlew test` → 실패 0건. 출력의 `PASSED` 줄에 5.1(제출 건수 · 평균), 5.2(반올림), 5.5(제출 0건), 5.8(없는 과제) 테스트가 각각 보인다.

### 3단계 — 실제 호출 · 검증 (약 10분)
- **할 일**: 서버를 띄워 시드 데이터로 직접 호출하고, `reviewer` 또는 `/verify` 로 변경분을 검토한다.
- **만들거나 고칠 파일**: 없음(검토에서 나온 지적만 2단계 파일에 반영).
- **확인 방법**:
  - `curl -s http://localhost:8080/api/assignments/A1/grading-report | python3 -m json.tool` → 6장의 필드 11개가 보이고, 숫자가 시드 데이터로 손계산한 값과 같다.
  - `curl -s http://localhost:8080/api/assignments/A2/grading-report` → 상태 200, `submittedCount` 가 `0`.
  - `curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/assignments/NOPE/grading-report` → 4번 질문 6 에서 정한 상태 코드(제안 `404`).
  - (참고) 이 저장소의 `.claude/settings.json` 은 `curl` 을 deny 하므로 위 명령은 Claude Code 가 아니라 사람이 터미널에서 실행한다.

## 6. 완성 기준 (Day 4-1에서 점검)
- [ ] `cd <P> && ./gradlew test` 결과가 `BUILD SUCCESSFUL` 이고 실패 0건이며, 리포트 관련 테스트가 4건 이상(5.1 · 5.2 · 5.5 · 5.8 각 1건 이상) 통과 목록에 있다.
- [ ] 시드 과제 `A1` 응답 JSON 에 `assignmentId` · `title` · `classId` · `dueAt` · `studentCount` · `submittedCount` · `notSubmittedCount` · `lateCount` · `averageScore` · `maxScore` · `minScore` 11개 필드가 모두 있고, `submittedCount + notSubmittedCount == studentCount` 이다.
- [ ] 시드 과제 `A1` 의 `averageScore` · `lateCount` 가 4번 질문 2 · 4 · 6 에서 정한 규칙으로 손계산한 값과 같다.
- [ ] 제출 0건 과제 `A2` 를 호출하면 상태 200 이고 `submittedCount` 가 `0` 이며, 점수 필드는 4번 질문 5 에서 정한 값이다.
- [ ] 없는 과제 ID 를 호출하면 4번 질문 6 에서 정한 상태 코드(제안 404)가 오고, 응답 본문에 스택 트레이스가 없다.
- [ ] 응답에 학생별 목록 배열이 없고, 요청에서 교사 ID 를 읽는 코드가 없다(범위 2.2 · 2.3 제외 확인).
- [ ] `<P>/build.gradle` 의 `dependencies` 가 골격 원본(`specs/starters/java/build.gradle`)과 같다.
