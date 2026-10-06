# RCA — a-connection-pool (작성 중: 1 ~ 3절 가설 표까지)

대상 자료: `incident-logs/a-connection-pool/` (`app.log`, `nginx-access.log`, `nginx-error.log`, `mariadb-slow.log`)

> 이번 범위는 1절(수집 범위), 2절(타임라인), 3절의 가설 표까지다. 로그 반증 실행 · 코드 대조 · 판정과 4절 이후(원인 · 수정안 등)는 아직 하지 않았다.
> 출처 표기는 `app` = `app.log`, `nginx` = `nginx-access.log`, `nginx-err` = `nginx-error.log`, `slow` = `mariadb-slow.log` 다.

## 1. 수집 범위

> 이 절은 분석할 시간 구간만 정한다. 원인 추측은 담지 않는다.
> 파일은 통째로 읽지 않고 `wc` · `head` · `tail` · `grep -c` · `grep -n` · `awk` 집계로만 확인했다.

### 1.1 파일별 기록 기간 · 형식

| 파일 | 줄 수 | 기록 기간(KST) | 시각 형식 | 타임존 |
|---|---|---|---|---|
| `app.log` | 5,371 (시각 있는 줄 1,927 + 스택트레이스 연속 줄 3,444) | 2026-09-16 06:43:56.753 ~ 15:29:51.760 | ISO-8601, 밀리초 `2026-09-16T06:43:56.753+09:00` | `+09:00` (시각 있는 줄 1,927 줄 전부) |
| `nginx-access.log` | 21,086 | 2026-09-16 06:43:56 ~ 15:29:59 | nginx combined `[16/Sep/2026:06:43:56 +0900]`, 초 단위 | `+0900` (21,086 줄 전부) |
| `nginx-error.log` | 168 | 2026-09-16 09:12:03 ~ 14:51:47 | `2026/09/16 09:12:03`, 초 단위 | **표기 없음 → KST 로 판단**. `nginx-err:6`(14:41:06 `upstream timed out`, `/api/classes/3/report`)이 `nginx:18933`(14:41:06 +0900, 같은 경로 504)과 같은 초다 |
| `mariadb-slow.log` | 21 (쿼리 3건) | 2026-09-16 09:14:07 ~ 14:37:52 (KST 환산) | `# Time: 260916  5:37:52` (YYMMDD, 초 단위) + `SET timestamp=<epoch>` | **표기 없음 → UTC 로 판단**. `SET timestamp=1789537062` 는 UTC 05:37:42 이고, 여기에 `Query_time` 9.8초를 더하면 `# Time` 05:37:52 와 맞는다. 3건 모두 같은 관계다 |

**타임존 변환 규칙**

- `mariadb-slow.log` 만 UTC 다. `KST = UTC + 9시간` 으로 바꿔 넣는다. `# Time` 은 쿼리가 **끝난** 시각, `SET timestamp` 는 쿼리가 **시작한** 시각이다.
- 나머지 세 파일은 KST 라 그대로 쓴다.

**정밀도 대조 규칙**

- `app.log` 는 밀리초, 나머지는 초 단위다. 대조할 때는 `app.log` 시각을 초 단위로 버리고, ±1초 차이는 같은 시각으로 본다.
- nginx 의 시각은 응답을 마친(로그를 쓴) 시각이다. `nginx-access.log` 에는 응답 시간 필드가 없어서 요청이 시작된 시각은 알 수 없다.

### 1.2 이상 신호 집계

시간 단위(전체 기간):

| 시 | app 누수 경고 | app 풀 타임아웃 | app 미처리 예외(500) | nginx 500 | nginx 502 | nginx 504 | nginx 전체 요청 | nginx-err `[error]` |
|---|---|---|---|---|---|---|---|---|
| 06 ~ 13 | 0 | 0 | 0 | 0 | 0 | 0 | 396 ~ 2,734/시 | 0 |
| **14** | **134** | **49** | **49** | **49** | **82** | **55** | 2,608 | **137** |
| 15 | 0 | 0 | 0 | 0 | 0 | 0 | 1,361 (15:29 까지) | 0 |

- `app.log` 의 WARN 은 위 누수 경고 134건 외에 `SQL Error: 0, SQLState: null` 49건과 `state conflict`(409) 20건이다. `state conflict` 는 하루 내내 고르게 나온다. 14:37:52 이전의 WARN 은 모두 `state conflict` 다.
- `nginx-error.log` 의 14시 이외 줄은 5줄(09:12 · 10:42 · 11:47 · 13:05 · 13:18)이고 모두 `[warn]` · `[info]` 다.

분 단위(14:37 ~ 14:51, 이 밖의 분은 모든 열이 0):

| 분 | 누수 경고 | 누수 반환 | 풀 타임아웃 | nginx 500 | nginx 502 | nginx 504 | nginx 전체 | 리포트 요청 | nginx-err `[error]` |
|---|---|---|---|---|---|---|---|---|---|
| 14:37 | 1 | 0 | 0 | 0 | 0 | 0 | 50 | 0 | 0 |
| 14:38 | 2 | 3 | 0 | 0 | 0 | 0 | 41 | 3 | 0 |
| 14:39 | 3 | 3 | 0 | 0 | 0 | 0 | 42 | 3 | 0 |
| 14:40 | 14 | 10 | 1 | 1 | 0 | 0 | 42 | 10 | 0 |
| 14:41 | 13 | 15 | 6 | 6 | 0 | 3 | 42 | 16 | 3 |
| 14:42 | 10 | 10 | 2 | 2 | 13 | 5 | 41 | 15 | 18 |
| 14:43 | 14 | 15 | 10 | 10 | 2 | 5 | 58 | 19 | 7 |
| 14:44 | 11 | 11 | 2 | 2 | 5 | 6 | 34 | 14 | 11 |
| 14:45 | 9 | 8 | 8 | 8 | 14 | 5 | 39 | 15 | 19 |
| 14:46 | 12 | 13 | 4 | 4 | 16 | 6 | 51 | 19 | 22 |
| 14:47 | 9 | 9 | 5 | 5 | 6 | 3 | 38 | 12 | 9 |
| 14:48 | 10 | 10 | 4 | 4 | 6 | 5 | 42 | 14 | 11 |
| 14:49 | 12 | 13 | 7 | 7 | 8 | 8 | 42 | 16 | 16 |
| 14:50 | 11 | 9 | 0 | 0 | 11 | 7 | 34 | 10 | 18 |
| 14:51 | 3 | 5 | 0 | 0 | 1 | 2 | 46 | 5 | 3 |

- "리포트 요청"은 `nginx-access.log` 의 `GET /api/classes/{id}/report` 건수다. 06 ~ 13시에는 시간당 0 ~ 11건이었고 14시에는 179건이다.
- app 풀 타임아웃 49건과 nginx 500 49건은 분 단위로 일치한다.

### 1.3 분석 구간

- **이상 구간(관측)**: 14:37:42(`slow` 9.8초 쿼리 시작, KST 환산) ~ 14:51:49(`app.log` 마지막 누수 반환).
- **분석 구간(여유 포함)**: **2026-09-16 14:30:00 ~ 15:00:00 KST**. 앞쪽 7분은 평상시 기준선, 뒤쪽 8분은 회복 확인용이다. 14:52 ~ 15:29 의 5xx 는 0건이다.
- **구간 밖 참고 신호**: `slow` 의 나머지 2건(09:14:07 · 11:02:40 KST 시작, 2.3초 · 2.0초)은 같은 시각에 app · nginx 오류가 없다.

## 2. 타임라인

> 대상: 1.3 의 분석 구간 14:30:00 ~ 15:00:00 KST. `slow` 시각은 KST 로 바꿔 적었다.
> 원문 발췌의 IP · 학생 식별자는 문서 끝 마스킹 규칙대로 가렸다. 원인 해석은 쓰지 않는다.

**선후 표기 규칙**

- 다른 파일의 사건끼리 2초 이내로 붙어 있으면 **(선후 불확실)** 이라고 적었다.
- 같은 요청을 두 파일이 같은 초에 남긴 경우에는 한 줄로 합치고 두 출처를 함께 적었다.

### 2.1 시간순 사건

| 시각(KST) | 출처 | 사건 | 원문 발췌 |
|---|---|---|---|
| 14:30:11 ~ 14:37:43 | `app:1213` ~ `1242` | 평상시와 같은 INFO(404 · 400) · WARN(409) 만 있다. ERROR 0건 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=2` (`app:1229`) |
| 14:37:42 ~ 14:37:52 | `slow:15` ~ `21` | `submission` 조회 1건이 9.8초 걸림. 검사 행 1,284,310, 반환 4행. 접속 호스트는 API-1 | `# Query_time: 9.812417  Lock_time: 0.000071  Rows_sent: 4  Rows_examined: 1284310` / `… from submission s1_0 where s1_0.distribution_id=5 order by s1_0.student_id;` |
| 14:37:52.199 | `app:1243` ~ `1257` | **(선후 불확실)** 첫 Hikari 누수 경고. `slow` 쿼리 종료(14:37:52)와 같은 초. 스택은 `ReportController.classReport` → `ReportService.buildClassReport` 의 트랜잭션 시작 | `Connection leak detection triggered for org.mariadb.jdbc.Connection@6e8f14d3 on thread http-nio-8080-exec-6, stack trace follows` |
| 14:38:01 | `app:1258` ~ `1259`, `nginx:18801` | 위 커넥션이 반환되고 학급 3 리포트가 200 으로 끝남. 이 시각부터 리포트 요청의 referer 가 리포트 화면 자체(`/teacher/classes/3/report`)로 바뀜(그 전에는 `/teacher/classes/3`) | `built class report for class 3: 3 distributions, 10 submissions` / `"GET /api/classes/3/report HTTP/1.1" 200 266 "https://lms.example.com/teacher/classes/3/report"` (교사단말B) |
| 14:38:29.715 | `app:1278` | 학급 3 의 distribution 5 재배포 | `redistributed distribution 5 (assignment 3, class 3) reason=null` |
| 14:40:11 ~ | `nginx:18888` ~ | 교사단말A 가 학급 1 · 2 리포트를 10 ~ 15초 간격으로 번갈아 요청하기 시작. 리포트 요청이 분당 3건 → 10 ~ 19건 | `"GET /api/classes/1/report HTTP/1.1" 200 259` (교사단말A) |
| 14:40:52 | `app:1522`, `nginx:18918` | 첫 풀 타임아웃과 첫 500. 리포트가 아닌 `/api/units/M6-2/items` 요청 | `itembank-pool - Connection is not available, request timed out after 3000ms (total=5, active=5, idle=0, waiting=4)` / `"GET /api/units/M6-2/items HTTP/1.1" 500 162` |
| 14:41:06 | `nginx-err:6`, `nginx:18933` | 첫 upstream 응답 시간 초과와 첫 504. upstream 은 API-1 | `upstream timed out (110: Connection timed out) while reading response header from upstream, client: 교사단말B, … upstream: "http://API-1:8080/api/classes/3/report"` / `"GET /api/classes/3/report HTTP/1.1" 504 167` |
| 14:42:01 | `nginx-err:10` | nginx 가 API-1 을 일시 제외 | `upstream server temporarily disabled while reading response header from upstream, … upstream: "http://API-1:8080/api/classes/1/report"` |
| 14:42:01 | `nginx-err:11`, `nginx:18970` | API-2 로 연결 시도가 거부됨. 첫 502 | `connect() failed (111: Connection refused) while connecting to upstream, … upstream: "http://API-2:8080/api/classes/2/report"` / `"GET /api/classes/2/report HTTP/1.1" 502 157` |
| 14:42:04 | `nginx-err:12` | 첫 `no live upstreams`. 학생 화면 요청도 502 | `no live upstreams while connecting to upstream, client: 10.20.x.x, … request: "GET /api/units/M6-2/items HTTP/1.1"` |
| 14:42 ~ 14:50 | 1.2 분 단위 표, 2.2 | 누수 경고 · 풀 타임아웃 · 500 · 502 · 504 반복. 정점은 14:43(풀 타임아웃 10건), 14:46(nginx-err `[error]` 22건) | 2.2 참조 |
| 14:49:51 | `app:4981`, `nginx:19305` | 마지막 풀 타임아웃 경유 500 | `unhandled exception on /api/units/M5-3/items` / `"GET /api/units/M5-3/items HTTP/1.1" 500 162` |
| 14:51:02 | `nginx-err:166`, `nginx:19347` | 마지막 502 | `no live upstreams while connecting to upstream, client: 10.20.x.x, … request: "GET /api/units/M5-3/items?student=학생A HTTP/1.1"` |
| 14:51:37.674 | `app:5251` | 마지막 누수 경고 | `Connection leak detection triggered for org.mariadb.jdbc.Connection@6e8f14d3 on thread http-nio-8080-exec-10, stack trace follows` |
| 14:51:47 | `nginx-err:168`, `nginx:19381` | 마지막 504 | `upstream timed out (110: Connection timed out) …` / `"GET /api/classes/3/report HTTP/1.1" 504 167` (교사단말C) |
| 14:51:49.148 | `app:5267` | 마지막 누수 반환. 이후 15:29 까지 누수 경고 · ERROR · 5xx 0건 | `Previously reported leaked connection org.mariadb.jdbc.Connection@6e8f14d3 on thread http-nio-8080-exec-10 was returned to the pool (unleaked)` |
| 14:59:15 | `nginx:19701` | 다음 리포트 요청이 200 | `"GET /api/classes/3/report HTTP/1.1" 200 257` (교사단말C) |

### 2.2 반복 메시지 묶음 (14:37 ~ 14:51)

| 메시지 | 건수 | 기간 | 비고 |
|---|---|---|---|
| `app` 누수 경고 `Connection leak detection triggered` | 134 | 14:37:52 ~ 14:51:37 | 스택의 컨트롤러 프레임은 134건 모두 `ReportController.classReport(ReportController.java:22)`. 커넥션 객체는 5종(`@6e8f14d3` 등) |
| `app` 누수 반환 `Previously reported leaked connection … was returned` | 134 | 14:38:01 ~ 14:51:49 | 경고 134건이 모두 반환됐다. 경고 → 반환 간격 5 ~ 11초(5초 4건, 6초 11건, 7초 23건, 8초 33건, 9초 14건, 10초 33건, 11초 16건) |
| `app` 리포트 완료 `built class report` | 134 | 같은 구간 | 학급 1: 57, 학급 2: 24, 학급 3: 61(14시 전체 기준). 하루 190건 모두 `3 distributions, 10 submissions` |
| `app` 풀 타임아웃 `Connection is not available, request timed out after 3000ms` | 49 | 14:40:52 ~ 14:49:51 | 49건 모두 `total=5, active=5, idle=0`. `waiting` 은 1 ~ 7 |
| `app` 미처리 예외 → nginx 500 | 49 | 같은 구간 | 경로: `/api/units/{code}/items` 19, `/api/units` 14, `/api/items/{id}` 7, `/api/classes/{id}/report` 5, `/api/distributions/{id}` 4 |
| `nginx` 504 / `nginx-err` `upstream timed out` | 55 / 55 | 14:41:06 ~ 14:51:47 | 504 55건 모두 `/api/classes/{id}/report`. upstream 은 모두 API-1 |
| `nginx-err` `upstream server temporarily disabled` | 26 | 14:42:01 ~ 14:50:52 | 모두 API-1 |
| `nginx-err` `connect() failed (111: Connection refused)` | 14 | 14:42:01 ~ 14:50:52 | 모두 API-2 |
| `nginx` 502 / `nginx-err` `no live upstreams` | 82 / 68 | 14:42:01 ~ 14:51:02 | 502 경로: 리포트 32, 나머지 50건은 학생 · 교사 화면의 문항 · 단원 · 배포 조회 |
| 리포트 요청 단말 | 165 | 14:40 ~ 14:51 | 교사단말A 66, 교사단말B 35, 교사단말C 35, 교사단말D 29. 교사단말D 는 06 ~ 13시에 리포트 요청이 없다 |

### 2.3 로그에서 찾지 못한 신호

첫 누수 경고(14:37:52)보다 앞선 줄을 다음 기준으로 찾았지만 해당 줄이 없었다.

- **앱 재기동 · 배포:** `Started` · `Shutting down` · `Tomcat started` 0건. 배포 기록 파일은 이 묶음에 없다.
- **풀 설정 출력:** Hikari 기동 로그 · `leakDetectionThreshold` · `maximumPoolSize` 를 찍은 줄이 없다. 누수 경고 임계값은 로그로 알 수 없다(풀 크기 5 는 타임아웃 메시지의 `total=5` 로만 보인다).
- **DB 오류:** `OutOfMemory` · `deadlock` · `lock wait` · `Too many connections` · `Communications link` 0건. MariaDB 에러 로그 · 상태 지표(CPU, 연결 수)는 이 묶음에 없다.
- **14:37:52 이전의 WARN · ERROR:** `state conflict`(409) WARN 뿐이다. 평소 패턴과 같다.
- **느린 쿼리:** `slow` 에는 하루 3건뿐이다. 14:38 ~ 14:51 의 리포트 134건 중 slow log 에 남은 쿼리는 없다. `long_query_time` 설정값은 알 수 없다.
- **요청 처리 시간:** `nginx-access.log` 형식에 `$request_time` · `$upstream_response_time` 이 없다.
- **API-2 기동 기록:** `app.log` 는 API-1 한 대 것으로 보이며(slow 의 접속 호스트와 같은 API-1), API-2 의 로그는 없다. API-2 는 `nginx-err` 의 `Connection refused` 14건에만 나타난다.

## 3. 가설과 검증

> 2절 타임라인을 바탕으로 가설만 세운다. 반증 조건의 실행(로그 검증 기록), 코드 · 설정 대조, 판정은 다음 단계에서 한다.
> 누수 경고 134건과 풀 타임아웃 49건은 **증상**이다. 가설은 "왜 14:37 부터 14:51 사이에만, 리포트 경로에서 커넥션이 오래 잡혀 풀(5개)이 고갈되었는가"를 설명하려는 것이다.
> 운영 DB 에는 직접 접속하지 않는다. 운영 데이터 확인은 DB 담당자에게 읽기 전용 조회를 요청한다.

### 3.0 가설 요약

| ID | 계층 | 가설 한 문장 | 지지 근거(로그) | 반증 조건 | 확인 방법 · 필요한 자료 |
|---|---|---|---|---|---|
| H1 | 애플리케이션 코드 | 학급 리포트(`ReportService.buildClassReport`)가 한 트랜잭션 안에서 커넥션을 수 초 이상 쥐는 구조라서, 리포트가 동시에 몇 건만 들어와도 크기 5 의 풀이 고갈된다 | 누수 경고 134건의 스택이 모두 `ReportController.classReport` 다. 경고 뒤 5 ~ 11초 지나 반환된다. 다른 경로에서는 누수 경고가 0건이다 | 1. 06:43 ~ 14:37:52 이전의 리포트 완료 50건이 같은 코드인데 누수 경고가 0건이고, 그 시간대 리포트 1건의 보유 시간이 임계값보다 짧다고 확인된다(코드만으로는 14:37 시점을 설명하지 못함).<br>2. 로컬에서 같은 규모의 데이터로 리포트 1건을 돌렸을 때 보유 시간이 누수 임계값보다 짧다 | `ReportService` · `ReportController` 의 트랜잭션 범위와 쿼리 수, `application.yml` 의 `leak-detection-threshold` · `maximum-pool-size` · `connection-timeout` 확인. 로컬 `docker compose --profile modern` 에서 리포트 1건 처리 시간 측정 |
| H2 | DB · 데이터 | `submission` 조회가 인덱스를 타지 못해 풀스캔한다(검사 130만 행 → 반환 4행). 이 쿼리가 리포트 처리 중 실행되어 커넥션 보유 시간이 길어졌다. 학급 3 의 distribution 5 데이터가 많아진 것이 계기다 | 14:37:42 ~ 52 의 9.8초 쿼리(`distribution_id=5`) 종료와 첫 누수 경고가 같은 초다. distribution 5 는 학급 3 소속이다(`app:1278`). 14:38:29 에 distribution 5 가 재배포됐다 | 1. 운영 `submission` 에 `distribution_id` 인덱스가 있고, `EXPLAIN` 결과가 인덱스를 쓴다.<br>2. 학급 1 · 2(distribution 5 가 없는 학급) 리포트도 같은 5 ~ 11초 동안 커넥션을 쥔다(2.2 의 누수 경고 학급별 분포로 확인 가능).<br>3. `submission` 행 수가 평소와 같다(리포트 응답은 하루 내내 `10 submissions` 인데 검사 행은 128만이라 서로 맞지 않는다) | 누수 경고 스레드 → 직후 `built class report for class N` 짝짓기로 학급별 보유 시간 집계. DB 담당자에게 읽기 전용 요청: `SHOW INDEX FROM submission`, `EXPLAIN SELECT … FROM submission WHERE distribution_id=5`, `SELECT distribution_id, COUNT(*) FROM submission GROUP BY distribution_id` |
| H3 | 트래픽 · 사용 패턴 | 교사 단말 4대가 리포트 화면에서 리포트를 10 ~ 15초 간격으로 반복 요청(자동 새로고침 또는 실패 후 재시도)해서, 동시 리포트 수가 풀 크기 5 를 넘었다 | 14:38 부터 referer 가 `/teacher/classes/N/report` 로 바뀐다. 리포트 요청이 시간당 0 ~ 11건에서 분당 10 ~ 19건으로 늘었다. 요청이 분당 5건 이하로 줄어든 14:51 에 오류도 멈췄다 | 1. 리포트 요청 급증(14:40)보다 커넥션 장기 보유(14:37:52)가 먼저다. 급증은 지연의 **결과**(재시도)일 수 있다. 14:38 ~ 14:39 처럼 리포트가 분당 3건일 때도 보유 시간이 5초 이상이면 트래픽만으로는 설명되지 않는다.<br>2. 풀 타임아웃이 난 시각에 진행 중인 리포트가 5건 미만이다 | 누수 경고 시각과 반환 시각으로 분 단위 동시 보유 수 집계. 웹 리포트 화면의 자동 새로고침 · 재시도 코드 확인(`modern/web` 에 해당 화면이 없으면 LMS 담당자에게 문의) |
| H4 | 인프라 · 라우팅 | 대기 호스트 API-2 가 꺼져 있어(`Connection refused`) API-1 한 대가 모든 요청을 받았고, nginx 가 API-1 을 일시 제외하자 갈 곳이 없어 학생 화면까지 502 가 났다 | `connect() failed (111: Connection refused)` 14건이 모두 API-2 다. `temporarily disabled` 26건 · `no live upstreams` 68건이 이어진다. 502 82건 중 50건이 리포트가 아닌 경로다 | 1. nginx upstream 설정에서 API-2 가 `backup` 이고 평소 정지가 설계된 상태다(그러면 피해 확대 요인일 뿐, 풀 고갈의 원인이 아님).<br>2. 502 가 시작되기 전(14:40:52 ~ 14:42:00)에 이미 풀 타임아웃 · 504 가 있다 | nginx `upstream item_bank_api` 설정(`max_fails` · `fail_timeout` · `backup`, `proxy_read_timeout`), API-2 의 기동 이력 · 운영 기록 |
| H5 | DB 서버 부하 | 14:37 무렵 DB 서버에 다른 부하(배치 · 백업 · 다른 서비스)가 걸려 모든 쿼리가 느려졌고, 쿼리가 많은 리포트 경로가 먼저 임계값을 넘었다 | 9.8초 쿼리의 `Lock_time` 은 0.00007초로 잠금 대기가 아니다. DB 서버 지표는 묶음에 없다 | 1. 같은 구간에 리포트가 아닌 경로(문항 · 단원 조회)는 커넥션을 얻은 경우 정상 속도로 끝났다(누수 경고 0건이 이를 뒷받침).<br>2. DB 서버의 CPU · I/O · 연결 수가 14:30 ~ 14:52 에 평소와 같다 | DB 담당자에게 14:30 ~ 14:52 의 서버 지표, 같은 시간대 실행된 배치 · 백업 일정, `SHOW PROCESSLIST` 이력(있으면) 요청 |

H1 ~ H3 은 서로를 배제하지 않는다(예: 리포트 구조(H1) + 느린 쿼리(H2) + 반복 요청(H3)이 겹쳐 고갈). H4 는 고갈 이후 피해가 학생 화면까지 번진 이유를 설명하는 가설이다. 다음 단계에서 반증 조건을 하나씩 따로 확인한다.

---

마스킹 적용: IP 2건(10.20.x.x) · 역할 별칭 6종(API-1 · API-2 · 교사단말A ~ D) · 이메일 0건 · 학생 식별자 1건(별칭 1명) · 토큰 0건 · 비밀번호 · 접속 문자열 0건
