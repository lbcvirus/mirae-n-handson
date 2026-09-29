# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

- 답변과 문서는 한국어로 쓴다. 코드 · 명령 · 경로 · 식별자만 원문 그대로 둔다.
- `legacy/` 는 분석 · 이관 대상이다. 허락 없이 수정하지 않는다.

대상 코드: `modern/api`(Spring Boot 3.3 · Java 21 · Gradle · MariaDB 10.11), `modern/web`(React 18 · TypeScript · Vite · Vitest).

## 1. 빌드 · 테스트 명령

```bash
# modern/api
cd modern/api && ./gradlew test                                                  # 전체 테스트. H2 로 돌아 DB 없이 통과한다
cd modern/api && ./gradlew test --tests 'com.example.item.UnitServiceTest'       # 클래스 하나
cd modern/api && ./gradlew test --tests 'com.example.item.UnitServiceTest.listUnitsEmpty'  # 메서드 하나
docker compose --profile modern up -d && (cd modern/api && ./gradlew bootRun)   # 로컬 실행(8080). 저장소 루트에서 실행

# modern/web
cd modern/web && npm ci                                                          # 설치. npm install 은 lock 파일을 바꾸므로 쓰지 않는다
cd modern/web && npm run dev                                                     # 5173
cd modern/web && npm run lint && npm run typecheck && npm test                   # 검증 3종
cd modern/web && npx vitest run src/components/ItemTable.test.tsx                # 파일 하나
```

- `modern/api` 를 고쳤으면 `./gradlew test` 를, `modern/web` 을 고쳤으면 검증 3종을 실행하고 통과 · 실패 수를 답변에 적는다. 실행하지 않았으면 "실행하지 않음"이라고 적는다.
- 테스트 설정은 `src/test/resources/application-test.yml`(H2, MariaDB 모드)이다. 테스트가 실제 MariaDB 에 붙는 설정을 만들지 않는다.

## 2. 코딩 컨벤션

### 팀 규칙
1. 컨트롤러 클래스의 생성자 · 필드에 `*Repository` 타입이 없다. 컨트롤러 안에 SQL 문자열 · `JdbcTemplate` · `EntityManager` 가 없다.
2. 비어 있는 `catch` 블록이 없다. `catch` 블록은 로그를 남기고 다시 던지거나, 도메인 예외로 바꿔 던진다.
3. 로그는 `org.slf4j.Logger` 로만 남긴다. `System.out` · `System.err` · `printStackTrace()` 가 코드에 없다.
4. 의존성 추가(`build.gradle` 의 `dependencies`, `package.json`)와 DB 스키마 변경은 코드를 고치기 전에 사람에게 묻는다. 물을 때 이유와 대안을 함께 적는다.

### modern/api
- 패키지는 도메인 단위(`com.example.item`, `com.example.assignment`)다. 호출 방향은 Controller → Service → Repository → 엔티티 한 방향이다.
- 서비스는 다른 도메인 패키지의 `*Repository` 를 주입받지 않는다. 다른 도메인 데이터는 그 도메인의 `*Service` 로 가져온다.
- 컨트롤러 메서드의 반환 타입은 record DTO(`*Response`)다. `@Entity` 클래스를 반환하지 않는다.
- 예외 → HTTP 상태 변환은 `common/GlobalExceptionHandler` 에만 있다. 컨트롤러 메서드 안에 `try`/`catch` 가 없다. 현재 매핑: `NotFoundException` 404 · 검증 실패 400 · `IllegalStateException` 409 · 그 밖의 예외 500.
- 현재 시각은 주입받은 `Clock` 으로 구한다(`LocalDateTime.now(clock)`). 인자 없는 `LocalDateTime.now()` 를 쓰지 않는다.
- 문항 상태는 `ItemStatus` 상수로 비교한다. 코드에 `"A"` · `"D"` · `"R"` 문자열을 직접 쓰지 않는다. 외부에 보이는 문항은 `ItemStatus.ACTIVE` 만이다.
- 로그 문자열에 학생 식별자(`STU-…`) · 이메일 · 토큰 값을 넣지 않는다. 로그 레벨은 정상 흐름 `INFO` 이하, 복구 가능한 실패 `WARN`, 요청을 실패시키는 예외 `ERROR` 로 한다.
- 이름: 클래스 `PascalCase`, 메서드 · 필드 `camelCase`, 상수 `UPPER_SNAKE_CASE`. 약어는 첫 글자만 대문자로 쓴다(`ItemDto`).
- 메서드 본문의 비교 · 계산에 쓰는 숫자 리터럴은 0 · 1 을 빼고 이름 붙인 상수로 뺀다(예: `MAX_LEVEL = 5`). 애너테이션 속성(`@Size(max = 200)`)과 설정 빌더 인자(`maxAge(3600)`)는 예외다.
- 들여쓰기는 공백 4칸, 한 줄 120자 이내, 와일드카드 import(`.*`)는 쓰지 않는다.

### modern/api 테스트
- 새 public 서비스 메서드와 새 엔드포인트마다 테스트가 1개 이상 있다.
- 계층별 방식: 컨트롤러는 `@WebMvcTest` + `@MockBean` 서비스, 서비스는 `@ExtendWith(MockitoExtension.class)`, 리포지토리는 `@DataJpaTest`(H2).
- 모든 테스트 클래스에 `@ActiveProfiles("test")` 가 있다. 엔티티는 `ItemFixtures` 로 만든다.
- 테스트 메서드 이름은 확인하는 동작을 camelCase 로 쓰고(`rejectsOtherOrigins`), `@DisplayName` 에 한국어 설명을 붙인다.

### modern/web
- HTTP 요청은 `src/api/client.ts` 의 `getJson` 으로만 보낸다. 그 밖의 파일에 `fetch(` 가 없다(테스트 도우미 `src/test/` 제외).
- API 응답 타입은 `src/api/types.ts` 에 둔다. 데이터 로딩은 `src/hooks/` 의 훅으로 하고, 컴포넌트 안에서 `useEffect` 로 직접 요청하지 않는다.
- 테스트 파일은 대상 옆에 `*.test.ts(x)` 로 두고, 요청은 `src/test/mockFetch.ts` 로 흉내 낸다.

## 3. 금지 사항

- `legacy/` 아래 파일을 허락 없이 수정하지 않는다.
- DB 스키마 · 시드(`db/` 아래 SQL, 엔티티의 테이블 · 컬럼 매핑)를 허락 없이 바꾸지 않는다. 마이그레이션 파일을 추가하지 않는다.
- `modern/api/src/main/resources/application.yml` 의 접속 정보와 Hikari 설정을 바꾸지 않는다.
- 실제 비밀값(운영 계정 · 토큰 · 키)을 코드나 설정 파일에 리터럴로 넣지 않는다. 실습용 더미(`app-pass`, `readonly-pass`)만 예외다. 루트 `.env` 를 만들지 않는다.
- 운영 DB 호스트(`prod-db` 등)에 접속하는 명령 · 설정을 만들지 않는다.
- `@Transactional` 메서드 안에서 외부 HTTP 호출 · 파일 입출력 · 반복 횟수가 입력에 따라 늘어나는 루프를 돌리지 않는다.
- 요청에 없는 파일은 고치지 않는다. 포맷팅 · import 정리도 요청 범위 안의 파일에서만 한다. 범위 밖 파일을 고쳤다면 답변에 파일명과 이유를 적는다.
- `vendor-prs/*.patch` 와 `incident-logs/` 는 문제가 일부러 심어진 실습 자료다. 적용하거나 고치지 않는다.
- 수업 중 참가자가 만드는 파일(`.claude/`, `hooks/`, `.mcp.json`, `characterization/tests/<모듈>.test.js`, `/api/items/search`)은 요청 없이 만들지 않는다.

## 4. 아키텍처 안내

### modern/api
```
com.example.item        문항 · 단원 · 태그     GET /api/units, /api/units/item-counts, /api/units/{code}/items, /api/items/{id}
com.example.assignment  과제 배포 · 학급 리포트  GET /api/distributions/{id}, POST /api/distributions/{id}/redistribute, GET /api/classes/{id}/report
com.example.common      GlobalExceptionHandler, ErrorResponse, NotFoundException, ClockConfig, RootController(GET /)
com.example.config      WebConfig — CORS: http://localhost:5173 출처의 GET 만 허용
```
- 새 엔드포인트는 `/api/<도메인 복수형>` 아래에 두고, 컨트롤러 · 서비스 · 리포지토리 · 테스트를 같은 도메인 패키지에 만든다.
- 집계 결과는 JPQL 생성자 표현식으로 DTO 를 바로 만든다(`UnitRepository.countItemsPerUnit`).
- Hikari 풀은 운영 값과 같게 작다(최대 5, 대기 3초). 트랜잭션을 오래 쥐면 풀이 금방 고갈된다. `open-in-view` 는 꺼져 있어서 연관 엔티티는 `@EntityGraph` 로 함께 읽는다.

### modern/web
- 흐름: `api/client.ts`(`getJson`, `ApiError`) → `api/items.ts` → `hooks/useApiQuery.ts` 기반 훅(`useUnits`, `useUnitItems`, `useItem`, `AbortSignal` 로 취소) → `components/AsyncSection` 이 로딩 · 오류 표시를 맡는다.
- API 주소는 `VITE_API_BASE`(기본 `http://localhost:8080`)다. 빈 문자열이면 상대 경로를 쓰고, Vite 프록시가 `/api` 를 8080 으로 넘긴다.

### 레거시 → 현행 대응
| 레거시 | 현행 |
|---|---|
| `legacy/item-bank-php` (화면 파일마다 SQL 직접 실행) | `com.example.item` |
| `legacy/assignment-thymeleaf` (JdbcTemplate DAO, `AppClock` 이 2026-09-15 로 고정) | `com.example.assignment` 의 `Distribution*` |
| `legacy/grade-mssql` (로직은 저장 프로시저 `sql/*.sql`) | `com.example.assignment` 의 `Report*` |

- 레거시 규칙을 옮길 때는 근거를 `파일:줄번호` 로 코드 주석과 답변에 적는다(예: `legacy/item-bank-php/units.php:15-19`).
- 이관 결과는 `characterization/` 의 동작 보존 테스트로 비교한다. 비교가 실패하면 테스트 · 스냅샷을 고치지 않고, 먼저 사람에게 알린 뒤 이관 코드를 고친다.

## 5. 완료 기준

1. 이관 · 리팩토링 작업은 `cd characterization && npm test` 가 전부 통과해야 완료로 보고한다. 하나라도 실패하면 완료라고 하지 않는다.
2. 테스트가 실패하면 실패한 케이스 이름과 스냅샷 차이를 그대로 보고한다. 요약해서 "거의 됐다"고 말하지 않는다.
3. 테스트를 통과시키려고 `characterization/` 의 테스트 코드나 스냅샷 파일(`__snapshots__/*.snap`)을 고치지 않는다. 스냅샷을 바꿔야 한다고 판단되면 작업을 멈추고 사람에게 묻는다.
4. 레거시 동작이 버그로 보여도 이관 중에는 고치지 않고 그대로 옮긴다. 그런 동작은 답변에 "의심 동작" 목록으로 따로 적는다.
