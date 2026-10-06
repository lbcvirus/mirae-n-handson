# 외주사 PR 판정 기록

판정 기준: `templates/approval-checklist.md` 네 기준(pr-1 · pr-2 는 v0, pr-3 은 v1 · 2회차 기준. pr-1 · pr-2 를 v1 로 다시 봐도 판정은 같다)(테스트 통과 / 치명 이슈 0건 / 요청 범위 이탈 없음 / 컨벤션 준수). 하나라도 미충족이면 반려.

| PR | 판정 | 미충족 기준 | 근거(파일:줄번호) | 외주사에 돌려보낼 수정 요청 |
|---|---|---|---|---|
| pr-1 `단원별 문항 수 통계 API 추가` (`vendor-prs/pr-1-missing-tests.patch`, 리뷰 커밋 `4fe4654`) | 반려 | 테스트 통과(테스트 충분성) · 컨벤션 준수 | 1) 새 public 서비스 메서드 `modern/api/src/main/java/com/example/item/UnitService.java:36` 과 새 엔드포인트 `modern/api/src/main/java/com/example/item/UnitController.java:26-29` 에 대응하는 테스트가 없다(`UnitServiceTest` 등 `src/test` 에서 `countActiveItemsByUnit` · `item-counts` 검색 결과 0건). CLAUDE.md "새 public 서비스 메서드와 새 엔드포인트마다 테스트 1개 이상" 위반. 기존 44건이 통과한 것은 새 코드를 건드리지 않아서다.<br>2) 단원마다 `itemRepository.countByUnitIdAndStatus` 를 호출해 단원 수만큼 쿼리가 나간다(N+1). 클래스 전체가 `@Transactional(readOnly = true)`(`UnitService.java:10`) 라서 루프(`UnitService.java:37-39`)가 트랜잭션 · Hikari 연결(최대 5)을 쥔 채 돈다. CLAUDE.md "집계 결과는 JPQL 생성자 표현식으로 DTO 를 바로 만든다" 와도 다르다. | 1) `UnitServiceTest` 에 `countActiveItemsByUnit` 테스트를 추가한다: 문항 0개 단원이 0 으로 포함되는지, 학년 · 코드 순서, `ItemStatus.ACTIVE` 만 세는지(`ItemStatus.DELETED` 문항 제외). `@ExtendWith(MockitoExtension.class)` · `@ActiveProfiles("test")` · `ItemFixtures` 사용.<br>2) `UnitController` 의 `GET /api/units/item-counts` 테스트를 추가한다(`@WebMvcTest` + `@MockBean UnitService`, 응답 필드 `code` · `name` · `grade` · `activeItemCount` 확인).<br>3) 단원별 반복 조회를 `UnitRepository` 의 단일 JPQL 집계(단원 LEFT JOIN 문항, `UnitItemCountResponse` 생성자 표현식, 학년 · 코드 순)로 바꾸고, 필요한 리포지토리 쿼리는 `@DataJpaTest` 로 검증한다.<br>4) 위 수정 후 `cd modern/api && ./gradlew test` 결과를 PR 에 적어 다시 보낸다. |
| pr-2 `외부 채점 서버 연동 클라이언트 추가` (`vendor-prs/pr-2-hardcoded-secret.patch`, 리뷰 커밋 `c9047d7`) | 반려 | 치명 이슈 0건 · 테스트 통과(테스트 충분성) · 컨벤션 준수 | 1) **치명** 채점 서버 계정 · 비밀번호가 소스에 리터럴로 박혀 있다: `modern/api/src/main/java/com/example/assignment/GradingServerClient.java:34-35`(`itembank-grader` / `Gr4de!Srv#2026`). 더미(`app-pass`, `readonly-pass`)가 아니라 실제 값으로 보이며, 저장소 이력에 영구히 남는다. CLAUDE.md 3절 "실제 비밀값을 코드나 설정 파일에 리터럴로 넣지 않는다" 위반. 이 값은 `:72-73` 에서 Basic 헤더로 만들어져 요청마다 나간다.<br>2) 새 public 메서드 `requestGrading`(`GradingServerClient.java:44-59`)에 테스트가 없다. 테스트는 package-private `buildRequest` 하나뿐이고(`GradingServerClientTest.java:96-104`), 성공 · 비 200 응답 · `IOException` · `InterruptedException` 분기가 전부 미검증이다. CLAUDE.md "새 public 서비스 메서드마다 테스트 1개 이상" 위반.<br>3) 테스트 클래스에 `@ActiveProfiles("test")` 가 없다(`GradingServerClientTest.java:90`). CLAUDE.md "모든 테스트 클래스에 `@ActiveProfiles("test")`" 위반. | 1) 계정 · 비밀번호 리터럴을 코드에서 제거하고 설정값(`${grading.username}` · `${grading.password}` 등 환경변수 주입)으로 받는다. 기본값도 두지 않는다. **이미 소스에 노출된 `Gr4de!Srv#2026` 은 폐기하고 채점 서버 쪽에서 재발급**한다(git 이력에 남으므로 코드만 지워서는 해결되지 않는다). 설정 키 추가에 따른 `application.yml` 변경은 CLAUDE.md 3절상 접속 정보 변경 금지 대상일 수 있으니 우리 쪽 담당자와 먼저 협의한다.<br>2) `requestGrading` 테스트를 추가한다. `HttpClient` 를 주입받도록 바꾸거나 로컬 목 서버(`HttpServer`)를 써서 200 응답 본문 반환, 비 200 → `IllegalStateException`, 연결 실패 → `IllegalStateException` 을 검증한다.<br>3) `GradingServerClientTest` 에 `@ActiveProfiles("test")` 를 붙이고, 테스트 메서드 이름 · `@DisplayName` 규칙을 맞춘다.<br>4) (경고) `GradingServerClient.java:62` 의 JSON 문자열 조립을 `ObjectMapper`(Jackson, Spring Boot 기본 포함) 로 직렬화하도록 바꾼다. 의존성 추가는 필요 없다.<br>5) 위 수정 후 `cd modern/api && ./gradlew test` 결과를 PR 에 적어 다시 보낸다. |
| pr-3 `문항 검색에 난이도 필터 추가` (`vendor-prs/pr-3-out-of-scope.patch`, `day2` HEAD `5bd0c32` 기준 임시 worktree 에 적용 · 커밋 없음) — **체크리스트 v1 로 판정** | 반려 | 요청 범위 이탈 없음 · 테스트 통과(새 엔드포인트 테스트) | 1) 요청과 무관한 `assignment` 도메인 파일 2개를 고쳤다: `DistributionService.java:52,62`(마감 검사를 `assertNotClosed` 로 추출) · `ReportService.java:65,83`(평균 계산을 `averageOf` 로 추출). PR 설명에 "주변 코드도 조금 정리했습니다(가독성 개선, 동작 변화 없음)"라고 적혀 있다. 체크리스트 v1 3절 "모든 파일이 요청 문장으로 설명된다" · "요청과 무관한 리팩토링이 없다" · "PR 설명에 '주변 정리'가 없다" 세 항목 위반.<br>2) "동작 변화 없음"과 달리 `ReportService.java:71` 의 로그 문구가 바뀌었다(`average={}` 추가). `ReportService` 는 레거시 `grade-mssql` 을 옮긴 이관 코드인데, 요청 범위 밖에서 고쳤다.<br>3) 새 엔드포인트 `GET /api/units/{code}/items?level=`(`UnitController.java:31-32`)를 호출하는 테스트가 없다. 서비스 테스트(`UnitServiceTest.java:54,69`)만 추가됐다. 체크리스트 v1 1절 "새 엔드포인트마다 테스트 1개 이상" 위반. | 1) `DistributionService` · `ReportService` 변경을 이 PR 에서 빼고 `item` 도메인 3개 파일(`UnitController` · `UnitService` · `UnitServiceTest`)만 남긴다. 정리가 필요하면 요청서를 따로 받아 별도 PR 로 보낸다.<br>2) `@WebMvcTest(UnitController.class)` + `@MockBean UnitService` 로 `?level=` 요청 테스트를 추가한다: 200 과 응답 필드, 없는 단원이면 404, `level` 이 숫자가 아니면 400. `@ActiveProfiles("test")` · `@DisplayName` 한국어 설명을 붙인다.<br>3) 위 수정 후 `cd modern/api && ./gradlew test` 결과를 PR 에 적어 다시 보낸다. |

## 상세 (pr-1)

- 요청 문장: "단원별 문항 수 통계 API 추가" (patch `Subject:` 줄). 변경 파일 3개(`UnitController` · `UnitItemCountResponse` · `UnitService`)는 모두 요청으로 설명된다.
- 테스트 통과: `cd modern/api && ./gradlew test --rerun-tasks` → 44건 통과 · 실패 0 · 오류 0 · skip 0. 다만 새 코드를 검증하는 테스트가 0건이라 "충분하다" 항목이 미충족이다. `characterization/` 은 이관 코드가 아니어서 실행하지 않음.
- 치명 이슈: 없음(SQL 문자열 조립 · 비밀값 · 데이터 삭제 코드 없음).
- 요청 범위 이탈: 없음. 의존성 · 스키마 · 설정 변경 없음.
- 컨벤션: 컨트롤러는 서비스만 호출하고 `*Response` record 를 반환한다. 서비스의 `ItemRepository` 주입은 같은 `item` 도메인이라 허용. 위반은 위 2) 한 건.
- 이슈 심각도: 1) 경고(테스트 기준 미충족으로 반려 사유) · 2) 경고.
- 확인 필요: CLAUDE.md 4절이 `UnitRepository.countItemsPerUnit` 을 예시로 들지만 현재 코드에 없다. 외주사가 이 쿼리를 새로 만들어야 하는지는 사람이 정해 달라.
- 이 검증은 코드를 수정하지 않았다.

## 상세 (pr-2)

- 요청 문장: "외부 채점 서버 연동 클라이언트 추가" (patch `Subject:` 줄). 변경 파일 2개(`GradingServerClient` · `GradingServerClientTest`)는 모두 요청으로 설명된다.
- 테스트 통과: `cd modern/api && ./gradlew test --rerun-tasks` → 45건 통과 · 실패 0 · 오류 0 · skip 0. 그러나 새 public 메서드 `requestGrading` 은 검증하는 테스트가 없어 "충분하다" 항목이 미충족이다. 이관 코드가 아니어서 `characterization/` 은 실행하지 않음.
- 치명 이슈: 1건 — 비밀번호 리터럴(`GradingServerClient.java:34-35`).
- 요청 범위 이탈: 없음. 의존성 · 스키마 · 설정 파일(`application.yml`) 변경 없음. `grading.base-url` 설정 키를 `@Value` 로 읽지만 기본값(`http://grading.internal:9000`, `:38`)이 있어 설정 파일 수정은 없다.
- 컨벤션: 로거는 slf4j 이고(`:29`) 로그에 학생 식별자가 없으며(`:51`) `catch` 블록은 도메인 예외로 바꿔 던진다(`:53-58`). 위반은 위 2) · 3) 두 건. 상수 `TIMEOUT` 은 이름 붙은 상수다.
- 이슈 심각도: 1) 치명 · 2) 경고(테스트 기준 미충족) · 3) 경고 · 4) 경고(JSON 조립, 아래).
- 경고(4): `GradingServerClient.java:62` 는 `studentId` 를 이스케이프 없이 JSON 문자열에 이어 붙인다. `"` 나 `\` 가 들어간 값이 오면 요청 본문이 깨지거나 필드가 주입된다. 현재 호출처가 없어 당장 영향은 없다.
- 제안: 채점 서버 장애(`IllegalStateException`)는 `GlobalExceptionHandler` 에서 409 로 매핑된다. 외부 서버 장애에 어울리는 상태(예: 502/503)와 전용 예외가 필요한지 검토한다. 호출처가 생길 때 `@Transactional` 메서드 안에서 이 클라이언트를 부르면 CLAUDE.md 3절 위반이므로 함께 확인한다.
- 확인 필요: 노출된 비밀번호가 실제 운영 값인지는 코드만으로 알 수 없다. 우리 쪽 담당자가 채점 서버 관리자에게 확인해 폐기 · 재발급해야 한다. 외주사 PR 이 `review/pr-2` 브랜치에 이미 적용된 상태(`c9047d7`)이므로 이 브랜치를 공유 저장소에 push 하면 이력에 비밀번호가 남는다.
- 이 검증은 코드를 수정하지 않았다.

## 상세 (pr-3)

- 판정 기준: `templates/approval-checklist.md` v1 · 2회차.
- 요청 문장: "문항 검색에 난이도 필터 추가" (patch `Subject:` 줄). 변경 파일 5개 중 `item` 도메인 3개(`UnitController` · `UnitService` · `UnitServiceTest`)만 요청으로 설명된다. `assignment` 도메인 2개(`DistributionService` · `ReportService`)는 설명되지 않는다.
- 테스트 통과: `day2` HEAD(`5bd0c32`)로 임시 worktree 를 만들어 patch 를 적용하고 `cd modern/api && ./gradlew test --rerun-tasks` → 46건 통과 · 실패 0 · 오류 0 · skip 0. 새 서비스 메서드 테스트 2건은 있지만 새 엔드포인트 테스트가 없어 1절 마지막 항목이 미충족이다. worktree 는 검증 후 지웠다.
- 치명 이슈: 없음. 2절 SQL 항목: 새 코드는 파생 쿼리 메서드(`findByUnitCodeAndStatus`)만 쓰고 SQL · JPQL 문자열이 없다. 비밀값 리터럴 · 데이터 삭제 코드도 없다.
- 요청 범위 이탈: **있음**(위 1 · 2). 의존성 · 스키마 · 설정 파일 변경은 없다. 기존 public 메서드 시그니처 변경은 `UnitService` 생성자(`ItemRepository` 인자 추가) 하나이고, 요청 기능에 필요한 변경이라 범위 안으로 본다.
- 컨벤션: 4절 항목은 모두 충족이다. 출력문 · `catch` · 자원 누수 해당 없음. 컨트롤러는 서비스만 호출하고, 상태는 `ItemStatus.ACTIVE` 로 비교하며, 서비스의 `ItemRepository` 주입은 같은 `item` 도메인이라 허용된다.
- 이슈 심각도: 1) 경고(범위 기준 미충족으로 반려 사유) · 2) 경고 · 3) 경고(테스트 기준 미충족으로 반려 사유).
- 제안: (a) `GET /api/units/{code}/items` 는 원래 `ItemController#listItemsByUnit` · `ItemService#listActiveItemsByUnit` 이 맡는다. 같은 경로를 `UnitController` 에 `params = "level"` 로 나눠 두면 한 URL 의 처리가 두 컨트롤러로 갈라진다. `ItemController` · `ItemService` 에 선택 파라미터로 넣는 편이 찾기 쉽다. (b) 난이도를 메모리에서 거른다(`UnitService.java:43-44`). 문항 수가 많아지면 리포지토리 쿼리 조건으로 옮긴다. (c) `level` 범위(1~5) 검증이 없어 `level=9` 도 200 빈 목록이 된다.
- 확인 필요: `ReportService` 는 이관 코드이지만, `characterization/tests` 는 item-bank 만 다루고 학급 리포트를 다루지 않는다. 그래서 `characterization` 은 실행하지 않았다. 범위 밖 변경을 빼면 해당이 없어진다.
- 이 검증은 코드를 수정하지 않았다.
