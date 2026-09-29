# legacy/assignment-thymeleaf 비즈니스 규칙 후보 — 배포 등록 화면

> 분석 범위: 배포 등록 화면(`GET /distributions/new`, `POST /distributions`)과 이 화면이 부르는 코드. 끝낸 단계: 진입점 · 의존 관계([ARCHITECTURE.md](ARCHITECTURE.md)), 데이터 흐름([ERD.md](ERD.md)), 비즈니스 규칙(이 문서).
> 분석하지 않음: 배포 목록 · 재배포(`DistributionService.redistribute` `:70-701`) · 과제 목록 화면.
> 경로 표기는 ARCHITECTURE.md 와 같다. 화면을 실제로 실행해 보지는 않았다. 확신도 "확실"은 조건문 · SQL 로 확인했다는 뜻이다.

## 1. 배포 등록 폼 (`GET /distributions/new`)

### BR-01
- 규칙: 과제 선택 목록에는 상태가 `O` · `X` · `C` 인 과제만 나오고, 마감 시각 오름차순, 같으면 id 오름차순으로 정렬한다.
- 근거: `dao/AssignmentDao.java:34-38`, 호출 `web/DistributionController.java:42`
- 근거 코드:
  ```java
  + " WHERE a.status IN ('O', 'X', 'C') "
  + " ORDER BY a.due_at ASC, a.id ASC";
  ```
- 확신도: 확실
- 비고: `D` 와 그 밖의 모든 값이 빠진다. 등록 처리(`POST`)는 `D` 만 막는다(BR-09). 마감이 지났는지와 관계없이 목록에는 나온다(BR-02 가 선택만 막음).

### BR-02
- 규칙: 과제 마감 시각이 현재 시각보다 이전이고 상태가 `X` 가 아니면, 그 과제 선택지는 비활성(`disabled`)이다.
- 근거: `templates/distribution_form.html:16`, 현재 시각 `web/DistributionController.java:44`
- 근거 코드:
  ```html
  th:disabled="${a.dueAt != null and a.dueAt.isBefore(now) and a.status != 'X'}"
  ```
- 확신도: 확실
- 비고: 화면(브라우저)에서만 막는다. 서버 검사는 BR-07 이다. 주석 `:13` 과 코드가 같다. 상태 `C` 라도 마감 전이면 선택할 수 있다.

### BR-03
- 규칙: 과제 선택지 문구는 `id. 제목 (단원코드, 마감 MM-dd HH:mm)` 이다. 마감이 지났으면 뒤에 상태 `X` 는 ` [연장]`, 그 밖에는 ` [마감]` 을 붙인다.
- 근거: `templates/distribution_form.html:17-18`
- 근거 코드:
  ```html
  th:text="${a.id} + '. ' + ${a.title} + ' (' + ${a.unitCode} + ', 마감 ' + ${#temporals.format(a.dueAt, 'MM-dd HH:mm')} + ')'
           + (${a.dueAt != null and a.dueAt.isBefore(now)} ? (${a.status == 'X'} ? ' [연장]' : ' [마감]') : '')"
  ```
- 확신도: 확실
- 비고: 마감 표시에 연도가 없다. 등록 오류 문구는 `yyyy-MM-dd HH:mm` 형식이다(BR-07). `X` 의 뜻 "연장"은 이 표시 문구와 주석(`web/DistributionController.java:59`)에만 있다.

### BR-04
- 규칙: 학급 선택 목록은 모든 학급을 id 오름차순으로 보여 주고, 문구는 `학급명 (교사 id)` 이다.
- 근거: `dao/ClassDao.java:21`, `templates/distribution_form.html:25`
- 근거 코드:
  ```java
  return jdbc.query("SELECT id, name, teacher_id FROM `class` ORDER BY id ASC", MAPPER);
  ```
- 확신도: 확실
- 비고: 로그인 · 교사별 필터가 없어 모든 학급이 나온다(6절).

### BR-05
- 규칙: 폼의 마감 판정과 배포 시각에 쓰는 "현재 시각"은 시스템 속성 `app.clock` 이 `system` 일 때만 실제 시각이고, 그 밖에는 `2026-09-15 00:00:00` 고정이다.
- 근거: `AppClock.java:9`, `AppClock.java:14-20`
- 근거 코드:
  ```java
  String sys = System.getProperty("app.clock");
  if (sys != null && sys.equals("system")) {
      return LocalDateTime.now().withNano(0);
  }
  return LocalDateTime.parse(FIXED, FMT);
  ```
- 확신도: 확실
- 비고: 주석 `:8` 은 "2024-03 QA 기간 중 임시로 고정 … 아직 안 되돌림"이라 적지만 고정값은 2026-09-15 다. `Dockerfile:11` 실행 명령에는 `-Dapp.clock=system` 이 없다.

## 2. 배포 등록 처리 — 검증 (`POST /distributions`)

### BR-06
- 규칙: 검사는 과제 존재 → 마감 → 학급 존재 → 삭제 상태 → 중복 순서로 하고, 첫 실패에서 멈춘다. 오류 문구 하나를 flash `error` 로 담아 폼(`/distributions/new`)으로 되돌린다.
- 근거: `web/DistributionController.java:53-75`, `service/DistributionService.java:48-61`
- 근거 코드:
  ```java
  } catch (IllegalArgumentException | IllegalStateException e) {
      ra.addFlashAttribute("error", e.getMessage());
      return "redirect:/distributions/new";
  ```
- 확신도: 확실
- 비고: 컨트롤러 검사(과제 존재 · 마감)가 서비스 검사보다 먼저다. 예를 들어 마감 지난 과제 + 없는 학급이면 마감 오류만 보인다. item-bank 등록과 달리 오류를 모으지 않는다.

### BR-07
- 규칙: 과제의 마감 시각이 현재 시각보다 이전이고 상태가 `X` 가 아니면 배포를 거부한다. 문구는 `마감(yyyy-MM-dd HH:mm)이 지난 과제는 배포할 수 없습니다. [제목]` 이다.
- 근거: `web/DistributionController.java:58-65`, 형식 `AppClock.java:12`, `:22-27`
- 근거 코드:
  ```java
  // 마감 지난 과제는 새 배포 불가. 연장(X) 상태만 예외
  if (a.getDueAt() != null && a.getDueAt().isBefore(now)) {
      if (!"X".equals(a.getStatus())) {
  ```
- 확신도: 확실
- 비고: 주석과 코드가 같다. `isBefore` 라 마감 시각과 현재 시각이 같으면 통과한다. 상태 `C`(스키마 주석상 "마감", `01-schema.sql:87`)라도 마감 시각 전이면 통과한다. 상태로 막는 검사는 `D` 뿐이다(BR-09). `due_at` 은 `NOT NULL` 이라(`01-schema.sql:86`) `null` 분기는 스키마상 타지 않는다.

### BR-08
- 규칙: 과제 id 또는 학급 id 에 해당하는 행이 없으면 배포를 거부한다.
- 근거: `web/DistributionController.java:53-57`, `service/DistributionService.java:48-55`
- 근거 코드:
  ```java
  ClassRow c = classDao.findById(classId);
  if (c == null) {
      throw new IllegalArgumentException("존재하지 않는 학급입니다. (id=" + classId + ")");
  ```
- 확신도: 확실
- 비고: 과제 존재 검사는 두 곳에 있다. 컨트롤러 문구는 "존재하지 않는 과제입니다."(`:55`), 서비스 문구는 id 를 붙인다(`service/DistributionService.java:50`). 서비스 쪽 과제 검사는 컨트롤러 조회(`:53`)와 서비스 조회(`service/DistributionService.java:48`) 사이에 행이 사라질 때만 걸린다. 실행해 보지 않았다.

### BR-09
- 규칙: 과제 상태가 `D` 이면 "삭제된 과제는 배포할 수 없습니다."로 거부한다.
- 근거: `service/DistributionService.java:56-58`
- 근거 코드:
  ```java
  if ("D".equals(a.getStatus())) {
      throw new IllegalStateException("삭제된 과제는 배포할 수 없습니다.");
  ```
- 확신도: 확실
- 비고: 폼 목록(BR-01)은 `O` · `X` · `C` 외 값을 모두 빼지만, 등록 처리는 `D` 만 막는다. 그 밖의 상태 값(예: 스키마 주석에 없는 값)은 직접 POST 하면 통과한다. `D` 의 뜻 "삭제"는 이 문구에만 있고 스키마 주석(`01-schema.sql:87`)에는 없다.

### BR-10
- 규칙: 같은 과제 · 같은 학급 배포가 한 건이라도 있으면 새 배포를 거부하고 재배포를 쓰라고 안내한다.
- 근거: `service/DistributionService.java:59-61`, `dao/DistributionDao.java:46-52`
- 근거 코드:
  ```java
  // 같은 학급 + 같은 과제 는 한 건만 존재해야 한다 (UNIQUE 제약은 없음, 여기서 막는다)
  "SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?",
  ```
- 확신도: 확실
- 비고: `redistributed` 값과 관계없이 모든 행을 센다. 주석은 "한 건만 존재해야 한다"지만 시드에는 같은 쌍이 두 건 있다(`db/mariadb/init/02-seed.sql:133-134`). DB 에 UNIQUE 가 없다(`01-schema.sql:92-103`).

### BR-11
- 규칙: `assignmentId` · `classId` 는 필수 숫자 파라미터다. 폼에서도 두 선택 목록이 `required` 이고 첫 선택지는 빈 값이다.
- 근거: `web/DistributionController.java:50-51`, `templates/distribution_form.html:11-12`, `:23-24`
- 근거 코드:
  ```java
  public String create(@RequestParam("assignmentId") long assignmentId,
                       @RequestParam("classId") long classId,
  ```
- 확신도: 추정
- 비고: 값이 없거나 숫자가 아닐 때 어떤 화면이 나오는지는 Spring 기본 동작에 달려 있다. 모듈 안에 처리 코드가 없다(`DbErrorAdvice` 는 `DataAccessException` 만 받는다, `web/DbErrorAdvice.java:11`). 실행해 보지 않았다.

## 3. 배포 등록 처리 — 저장

### BR-12
- 규칙: 새 배포 id 는 `distribution` 의 `MAX(id) + 1` 이고, 행이 없으면 1 이다.
- 근거: `dao/DistributionDao.java:54-57`, 호출 `service/DistributionService.java:62`
- 근거 코드:
  ```java
  Long max = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM distribution", Long.class);
  return (max == null ? 0L : max.longValue()) + 1L;
  ```
- 확신도: 확실
- 비고: `FOR UPDATE` 같은 잠금이 없다. `@Transactional`(`service/DistributionService.java:46`) 안에서 실행된다. 동시 요청 결과는 미확인이다(6절).

### BR-13
- 규칙: 새 배포는 `distributed_at = AppClock.now()`, `redistributed = 0` 으로 저장한다. 저장 행 수가 1이 아니면 "배포 저장에 실패했습니다."로 거부한다.
- 근거: `service/DistributionService.java:63-66`, `dao/DistributionDao.java:59-63`
- 근거 코드:
  ```java
  int n = distributionDao.insert(id, assignmentId, classId, AppClock.now());
  if (n != 1) {
      throw new IllegalStateException("배포 저장에 실패했습니다.");
  ```
- 확신도: 확실
- 비고: 배포 시각은 DB `NOW()` 가 아니라 BR-05 의 시각이다(기본 고정값). 예외가 나면 트랜잭션이 롤백될 것으로 보이지만, 이는 Spring `@Transactional` 기본 동작에 기댄 추정이다. 롤백 설정 코드는 없다.

### BR-14
- 규칙: 저장에 성공하면 "배포가 등록되었습니다. (배포 #id)"를 flash `message` 로 담아 배포 목록(`/distributions`)으로 보낸다.
- 근거: `web/DistributionController.java:67-68`, `:76`
- 근거 코드:
  ```java
  ra.addFlashAttribute("message", "배포가 등록되었습니다. (배포 #" + id + ")");
  ...
  return "redirect:/distributions";
  ```
- 확신도: 확실
- 비고: 문구 표시는 `templates/layout.html:29` 의 `flash` 조각이다. 목록 화면 자체는 범위 밖이다.

### BR-15
- 규칙: `distribute` 안에서 DB 예외가 나면 "DB 오류로 배포에 실패했습니다."를 flash `error` 로 담아 폼으로 되돌린다. 컨트롤러의 첫 과제 조회와 폼 화면 조회에서 난 DB 예외는 `db_error` 화면으로 간다.
- 근거: `web/DistributionController.java:53`, `:66`, `:72-74`, `web/DbErrorAdvice.java:11-16`
- 근거 코드:
  ```java
  } catch (DataAccessException e) {
      ra.addFlashAttribute("error", "DB 오류로 배포에 실패했습니다.");
      return "redirect:/distributions/new";
  ```
- 확신도: 추정
- 비고: `:53` 이 `try`(`:66`) 밖이라는 것은 코드로 확인했다. 그 예외가 `DbErrorAdvice` 로 가는 것은 Spring `@ControllerAdvice` 동작에 기댄 추정이고 실행해 보지 않았다. `DbErrorAdvice` 는 `System.out.println` 으로 원인을 출력한다(`:13`).

## 4. 매직 넘버 · 매직 값

| 값 | 위치 | 추정 의미 |
|---|---|---|
| `'O'` · `'X'` · `'C'` | `dao/AssignmentDao.java:36` | 배포 폼에 보일 과제 상태. 스키마 주석상 `O`=진행, `C`=마감(`01-schema.sql:87`) |
| `'X'` / `"X"` | `web/DistributionController.java:61`, `templates/distribution_form.html:16`, `:18` | 마감이 지나도 배포할 수 있는 상태("연장", 표시 문구 `:18`) |
| `"D"` | `service/DistributionService.java:56` | 삭제 상태(문구 `:57`) |
| `"2026-09-15 00:00:00"` | `AppClock.java:9` | 고정 현재 시각 |
| `"system"` | `AppClock.java:16` | 실제 시각을 쓰게 하는 `app.clock` 값 |
| `'MM-dd HH:mm'` | `templates/distribution_form.html:17` | 선택지의 마감 표시 형식(연도 없음) |
| `"yyyy-MM-dd HH:mm"` | `AppClock.java:12` | 오류 문구의 마감 표시 형식 |
| `0` | `dao/DistributionDao.java:55`(`COALESCE`), `:61`(`redistributed`) | 배포가 없을 때 id 시작값 / 첫 배포 표시 |
| `1` | `service/DistributionService.java:64` | 정상 INSERT 행 수 |

## 5. 주석만 있거나 주석과 코드가 다른 곳

### 주석만 있음 (규칙으로 올리지 않음)

| 내용 | 위치 | 설명 |
|---|---|---|
| `status` 값 `O=진행 C=마감` | `01-schema.sql:87` | 코드는 `C` 를 "마감이라 배포 불가"로 다루지 않는다. 마감 판정은 `due_at` 으로만 한다(BR-07) |
| `X` = 연장 | `web/DistributionController.java:59`, 표시 문구 `templates/distribution_form.html:18` | `X` 로 바꾸는 코드가 모듈 · `db/` 에 없다(ERD.md 6절) |

### 주석과 코드가 다름

| 주석 | 코드 | 위치 | 관련 규칙 |
|---|---|---|---|
| 스키마 `status` 주석에 `O` · `C` 만 있음 | `X` · `D` 도 비교한다 | `01-schema.sql:87`, `dao/AssignmentDao.java:36`, `service/DistributionService.java:56` | BR-01, BR-07, BR-09 |
| "2024-03 QA 기간 중 임시로 고정" | 고정값이 `2026-09-15` | `AppClock.java:8-9` | BR-05 |
| "같은 학급 + 같은 과제는 한 건만 존재해야 한다" | 코드는 새 배포만 막는다. 시드에는 같은 쌍이 두 건 있다 | `dao/DistributionDao.java:46`, `02-seed.sql:133-134` | BR-10 |

### 같은 규칙을 여러 곳에서 다르게 구현

| 규칙 | 구현 A | 구현 B |
|---|---|---|
| 마감 지난 과제 배포 불가(`X` 예외) | 화면: 선택지 `disabled`(`templates/distribution_form.html:16`) | 서버: 거부(`web/DistributionController.java:60-64`) |
| 배포할 수 없는 상태 | 폼 목록: `O` · `X` · `C` 만 포함(`dao/AssignmentDao.java:36`) | 등록 처리: `D` 만 거부(`service/DistributionService.java:56`) |
| 과제 존재 | 컨트롤러: "존재하지 않는 과제입니다."(`web/DistributionController.java:53-57`) | 서비스: 같은 문구 + id(`service/DistributionService.java:48-51`) |
| (과제, 학급) 중복 금지 | 코드 `COUNT(*) > 0`(`dao/DistributionDao.java:47-52`) | DB 제약 없음(`01-schema.sql:92-103`) |
| 마감 표시 형식 | `MM-dd HH:mm`(`templates/distribution_form.html:17`) | `yyyy-MM-dd HH:mm`(`AppClock.java:12`) |

## 6. 없는 규칙 · 미확인

| 항목 | 상태 | 이유 |
|---|---|---|
| 누가 배포할 수 있는지(로그인 · 담당 교사 확인) | 없음 | 범위 안 코드에 인증 · 권한 검사가 없고, `build.gradle:24-30` 에 보안 의존성이 없다 |
| 과제 단원과 학급 학년이 맞는지 | 없음 | `distribute`(`service/DistributionService.java:46-68`)에 그런 비교가 없다 |
| 상태 `C` 이면서 마감 전인 과제의 배포 | 코드상 허용 | BR-07 비고. 실제 데이터에 그런 행이 있는지는 확인하지 않았다(시드에는 없음, `02-seed.sql:120-121` 은 마감이 지난 `C`) |
| 동시 요청 때 id 충돌 · 중복 배포 | 미확인 | BR-10 · BR-12 가 잠금 없이 읽고 쓴다. 격리 수준 설정이 모듈에 없고 실행해 보지 않았다 |
| 파라미터 누락 · 숫자 아님일 때 화면 | 미확인 | BR-11 비고 |
| `X` · `D` 로 상태를 바꾸는 주체 | 미확인 | ERD.md 6절 |
