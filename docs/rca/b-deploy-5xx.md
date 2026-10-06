# RCA — b-deploy-5xx

대상 자료: `incident-logs/b-deploy-5xx/` (`app.log`, `nginx-access.log`, `deploy-history.md`)

## 요약

- **무슨 일이 있었나**: 2026-09-17 **13:46:38 ~ 14:34:45 KST**(마지막 관측. 로그가 14:39:58 에서 끊겨 회복은 미확인)에 학급 1 의 재배포 API `POST /api/distributions/{42,43}/redistribute` 가 **39건 모두 500** 을 냈다. 다른 학급과 다른 API 는 정상이었다. 별도로 13:41:51 ~ 13:41:59 재기동 중 502 가 8건 나왔다.
- **직접 원인**: v1.4.2 배포(13:40:05 ~ 13:42:31)가 재배포 응답에 학급 배포 이력을 붙이려고 `DistributionController.java:40` 에 `listByClass` 호출을 추가했다. 이 호출이 학급 1 의 배포 중 존재하지 않는 `Assignment` 9 를 가리키는 행을 읽다가 예외를 던진다.
- **배경 원인**: 운영 데이터의 참조 무결성 깨짐(배포 전부터 distribution 41 에서 같은 누락이 관측됨), 결함 데이터를 고려하지 않은 변경 검증, 재배포와 이력 조회를 나눈 트랜잭션 경계(실패 응답 뒤에도 재배포가 반영됐을 수 있음).
- **확신 수준: 중간**: 변경 줄 · 시점 · 대상이 정확히 일치한다. 그러나 학급 1 에 고아 참조가 언제부터 있었는지는 운영 DB 를 확인하지 못했다(9절).

## 1. 수집 범위

> 이 절은 분석할 시간 구간만 정한다. 원인 추측은 담지 않는다.
> 파일은 통째로 읽지 않고 `wc` · `head` · `tail` · `grep -c` · `grep -n` · `awk` 집계로만 확인했다.

### 1.1 파일별 기록 기간 · 형식

| 파일 | 줄 수 | 기록 기간(KST) | 시각 형식 | 타임존 |
|---|---|---|---|---|
| `app.log` | 2,938 (시각 있는 줄 1,275 + 스택트레이스 연속 줄 1,663) | 2026-09-17 09:00:04.878 ~ 14:39:57.905 | ISO-8601, 밀리초 포함 `2026-09-17T09:00:04.878+09:00` | `+09:00` (시각 있는 줄 1,275 줄 전부) |
| `nginx-access.log` | 20,365 | 2026-09-17 09:00:00 ~ 14:39:58 | nginx combined `[17/Sep/2026:09:00:00 +0900]`, 초 단위 | `+0900` (20,365 줄 전부) |
| `deploy-history.md` | 39 | 배포 4건: 2026-09-01 ~ 2026-09-17 (v1.4.2 시작 13:40:05, 완료 13:42:31) | `YYYY-MM-DD HH:MM:SS` (오프셋 표기 없음) | 문서 3행에 "모두 KST" 명시 |

**타임존 변환 규칙**: 세 파일 모두 KST(UTC+9)라서 변환이 필요 없다. 다만 정밀도가 다르므로 대조할 때는 다음을 따른다.

- `app.log` 는 밀리초, `nginx-access.log` · `deploy-history.md` 는 초 단위다. 두 로그를 맞출 때는 `app.log` 시각을 초 단위로 버림해서 비교하고, ±1초 차이는 같은 시각으로 본다.
- nginx 의 시각은 요청 처리가 끝난(로그를 쓴) 시각이고, app 로그는 이벤트 발생 시각이다. 같은 요청이라도 nginx 쪽이 같거나 조금 늦을 수 있다.
- 다른 출처(예: UTC 로 남는 인프라 로그)를 추가로 모으면 `KST = UTC + 9시간` 으로 바꿔 넣는다.

### 1.2 분 단위 이상 신호 집계

집계 기준:

- `app.log`: 레벨 `ERROR` · `WARN` 줄(시각이 있는 줄만, 스택트레이스 연속 줄 제외).
- `nginx-access.log`: 상태 코드 5xx(500 · 502 · 504), 4xx.
- 타임아웃: `app.log` 에서 `timeout|timed out|connection is not available` 0건. `nginx-access.log` 에서 504 상태 0건이다(`grep -ci timeout|504` 의 6건은 모두 응답 바이트 수 `504` 와 겹친 오탐).

전체 분포(09:00 ~ 14:39, 340분):

| 항목 | 건수 |
|---|---|
| `app.log` ERROR | 46 |
| `app.log` WARN | 35 |
| `app.log` INFO | 1,194 |
| nginx 200 | 19,278 |
| nginx 4xx (400 29 · 404 969 · 409 35) | 1,033 |
| nginx 500 | 46 |
| nginx 502 | 8 |
| nginx 504 · 타임아웃 | 0 |

구간별 평균(nginx, 분당):

| 구간 | 분 수 | 요청/분 | 4xx/분 | 5xx/분 | 5xx 발생한 분 |
|---|---|---|---|---|---|
| A. 09:00 ~ 13:39 (배포 전) | 280 | 59.7 | 3.03 | 0.025 | 4 |
| B. 13:40 ~ 13:42 (배포 중) | 3 | 71.3 | 3.33 | 2.667 | 1 |
| C. 13:43 ~ 14:39 (배포 후) | 57 | 60.1 | 3.05 | 0.684 | 19 |

요청량(분당 40 ~ 82건)과 4xx 비율은 세 구간에서 거의 같다. 달라지는 것은 5xx 뿐이다.

5xx · ERROR 가 1건 이상인 분(nginx 5xx 와 `app.log` ERROR 는 500 에 대해 분 단위로 1:1 일치):

| 분 | nginx 500 | nginx 502 | app ERROR | 대상 경로 |
|---|---|---|---|---|
| 09:52 | 3 | | 3 | `POST /api/distributions/41/redistribute` |
| 10:31 | 1 | | 1 | `GET /api/distributions/41` |
| 11:17 | 1 | | 1 | `POST /api/distributions/41/redistribute` |
| 11:18 | 2 | | 2 | `POST /api/distributions/41/redistribute` |
| 13:41 | | 8 | 0 | 여러 GET 경로(13:41:51 ~ 13:41:59) |
| 13:46 | 2 | | 2 | `POST /api/distributions/42/redistribute` |
| 13:47 | 1 | | 1 | 42 |
| 13:50 | 4 | | 4 | 43 |
| 13:52 | 1 | | 1 | 42 |
| 13:53 | 1 | | 1 | 42 |
| 13:55 | 3 | | 3 | 43 |
| 13:58 | 2 | | 2 | 42 |
| 14:02 | 2 | | 2 | 42 |
| 14:03 | 4 | | 4 | 42 ×1, 43 ×3 |
| 14:06 | 1 | | 1 | 43 |
| 14:11 | 3 | | 3 | 42 |
| 14:15 | 1 | | 1 | 43 |
| 14:16 | 2 | | 2 | 43 |
| 14:22 | 3 | | 3 | 42 |
| 14:27 | 2 | | 2 | 42 |
| 14:28 | 1 | | 1 | 42 |
| 14:31 | 2 | | 2 | 43 |
| 14:32 | 1 | | 1 | 43 |
| 14:34 | 3 | | 3 | 43 |

`POST .../redistribute` 상태 분포:

| 구간 | 200 | 409 | 500 |
|---|---|---|---|
| A (배포 전) | 65 | 31 | 6 |
| B (배포 중) | 2 | 0 | 0 |
| C (배포 후) | 5 | 4 | 39 |

WARN(= nginx 409) 은 09:06 ~ 14:35 에 걸쳐 1 ~ 2건/분이 드문드문 나오고(35건, 모두 "마감된 과제 재배포" 상태 충돌), 배포 전후로 빈도 변화가 없다. 평소 수준으로 본다.

`app.log` 에서 확인한 배포 중 재기동 시각(`grep -n`):

- 13:41:48.214 `Commencing graceful shutdown` (`app.log:1240`)
- 13:41:50.224 Hikari 풀 종료 (`app.log:1244`)
- 13:41:55.902 `Starting ItemBankApplication v1.4.2` (`app.log:1245`)
- 13:42:00.304 `Started ItemBankApplication` (`app.log:1264`)

nginx 502 8건(13:41:51 ~ 13:41:59)은 이 재기동 틈과 겹치고, 13:42:01 부터 다시 200 이 나온다.

**평소 수준**: 배포 전 280분 동안 5xx 는 분당 0.025건이며 5xx 가 난 분은 4개(전부 `distributions/41`)다. 2시간 넘게 5xx 가 없는 구간(11:19 ~ 13:40)도 있다.

**이상 구간 제안**

| 구분 | 시각(KST) | 근거 |
|---|---|---|
| 시작 | **2026-09-17 13:41:51** | 첫 nginx 502. 배포 시작(13:40:05) 뒤 처음 나온 5xx |
| (참고) 지속 5xx 시작 | 13:46 | 배포 완료(13:42:31) 뒤 첫 500. 이후 3 ~ 6분 간격으로 끊이지 않는다 |
| 끝 | **확정 불가 — 마지막 관측 5xx 는 14:34** | 14:35 ~ 14:39:58(로그 끝) 5분 동안 5xx 0건이지만, 배포 후 500 간격이 최대 6분(14:16 → 14:22)이었으므로 5분 공백으로는 회복을 판단할 수 없다. 로그가 14:39:58 에서 끊긴다 |

배포 전 `distributions/41` 의 500 (09:52 ~ 11:18, 7건)은 위 구간 밖이다. 이상 구간에 넣지 않고, 비교용 평소 수준 표본으로 따로 둔다.

### 1.3 여유 구간 제안

| 구분 | 범위(KST) | 이유 |
|---|---|---|
| 앞 여유 | 30분 → **13:10:00 부터** | 배포 승인(13:38, `deploy-history.md:21`), 배포 시작(13:40:05, `deploy-history.md:7`) 등 이상 직전의 변경 · 상태가 모두 들어가도록 앞을 넉넉히 잡는다. 13:10 ~ 13:40 은 5xx 0건이라 "직전 정상 상태"의 기준선이 된다 |
| 뒤 여유 | 최소 15분 → **14:50:00 까지** (또는 5xx 가 30분 이상 연속 0건이 될 때까지) | 지금 로그는 14:39:58 에서 끝나 마지막 5xx 뒤 여유가 약 6분뿐이다. 배포 후 최대 오류 간격(약 6분)의 2배 이상을 확보하려면 14:40 이후 `app.log` · `nginx-access.log` 를 추가로 받아야 한다 |
| 비교 표본 | 09:00:00 ~ 13:09:59 | 평소 요청량 · 4xx · 409 수준과, 배포 전부터 있던 `distributions/41` 500 의 패턴을 비교하는 데 쓴다. 분석 본 구간에는 넣지 않는다 |

**최종 수집 범위**: 2026-09-17 **13:10:00 ~ 14:50:00 KST** (본 구간), 09:00:00 ~ 13:09:59 (비교 표본).

추가로 받아야 할 자료:

- 14:39:58 이후의 `app.log` · `nginx-access.log` (최소 14:50 까지, 회복 확인 시점까지).
- 대기 호스트 API-2(`api-2`, 10.20.x.x) 로그. `deploy-history.md:3` 에 "평소 정지"로 적혀 있고, 현재 자료에는 호스트 구분 정보가 없다.
- 위 구간의 MariaDB 로그(에러 로그 · 슬로우 쿼리). 현재 자료에 DB 쪽 로그가 없다.

## 2. 타임라인

> 대상: 1절에서 정한 본 구간 2026-09-17 13:10:00 ~ 14:50:00 KST. 실제 로그는 14:39:58 에서 끝나므로 14:40 ~ 14:50 은 비어 있다.
> 세 파일 모두 KST 라 시각을 그대로 합쳤다. 출처의 `nginx` 는 `nginx-access.log`, `deploy` 는 `deploy-history.md` 다.
> 원문 발췌에 들어 있는 학생 식별자는 등장 순서 별칭(학생A, 학생B …)으로, IP · 이메일 · 토큰 · 접속 문자열은 문서 끝 마스킹 규칙대로 가렸다. 원인 해석은 쓰지 않는다.

**선후 표기 규칙**

- `app.log` 는 밀리초, `nginx` · `deploy` 는 초 단위다. 다른 파일의 사건끼리 2초 이내로 붙어 있으면 **(선후 불확실)** 이라고 적었다.
- 같은 요청을 두 파일이 같은 초에 남긴 경우(app 의 처리 로그와 nginx 의 응답 줄)에는 한 줄로 합치고 두 출처를 함께 적었다. 이런 줄에서는 app 과 nginx 사이의 선후를 따지지 않는다.

### 2.1 시간순 사건

| 시각(KST) | 출처(파일:줄번호) | 사건 요약 | 원문 발췌(80자 이내) |
|---|---|---|---|
| 13:10:00 | `nginx:14793` | 본 구간 첫 줄. 13:10 ~ 13:39 요청 47 ~ 82건/분, 5xx 0건 | `[17/Sep/2026:13:10:00 +0900]` |
| 13:11:41.157 | `app.log:1146` | 반복 INFO(404 · 400 류) 구간 첫 줄. 종류별 빈도는 2.2절 | `not found: 문항이 없습니다: id=60 (/api/items/60)` |
| 13:12:21 | `app.log:1149`, `nginx:14926` | 재배포 성공(200, 189 B). 배포 전 재배포 200 응답 크기는 13:41:20 까지 10건 모두 170 ~ 199 B | `POST /api/distributions/6/redistribute HTTP/1.1" 200 189` |
| 13:12:57.133 | `app.log:1152` | 학급 리포트 생성. 이후 14:36 까지 13건 모두 같은 형식 · 같은 수치 | `built class report for class 3: 3 distributions, 10 submissions` |
| 13:13:03 | `app.log:1153`, `nginx:14965` | WARN 409 상태 충돌 첫 발생(본 구간). 이후 14:35:26 까지 총 6건, 약 0.07건/분 | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=1` |
| 13:13:05 | `app.log:1154`, `nginx:14969` | distribution 43 재배포 200 (178 B). 13:13:27(`app.log:1157`, `nginx:15002`)에도 200 | `redistributed distribution 43 (assignment 6, class 1) reason=보강 수업 후 재배포` |
| 13:25:29 | `app.log:1193`, `nginx:15758` | 배포 전 재배포 성공 중 이 구간의 마지막(distribution 44, 188 B). 이후 13:41:20 까지 재배포 요청 없음 | `redistributed distribution 44 (assignment 5, class 2) reason=학생A 요청 — 제출 화면 오류` |
| 13:36 ~ 13:38 | `nginx` (13:36 · 13:37 · 13:38 의 4xx 줄) | **평소와 다른 점**: 4xx 가 5 · 6 · 6건/분으로, 배포 전 평균 3.03건/분의 약 2배. 모두 404(`/apple-touch-icon.png` 6, `/robots.txt` 4, `/api/items/…` 4, `/api/unit` · `/api/item/…` · `/favicon.ico` 각 1). 요청량은 63 · 62 · 76건/분으로 평소 범위 | `"GET /apple-touch-icon.png HTTP/1.1" 404` |
| 13:38 | `deploy:21` | v1.4.2 배포 승인(분 단위 기록) | `- 승인: ***@example.com (13:38)` |
| 13:40:05 | `deploy:7` | v1.4.2 배포 시작. 단일 호스트 `api-1` 재기동, 무중단 아님(`deploy:18`). 배포 명령은 `deploy:20` | `\| v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31 \|` |
| 13:40 ~ 13:41 | `nginx` | 요청 73 · 72건/분. 배포 전 평균 59.7건/분보다 많지만, 배포 전 최대치 82건/분 안쪽 | — |
| 13:41:20 | `app.log:1238`, `nginx:16829` | 재기동 전 마지막 재배포. distribution 43, 200, 183 B | `redistributed distribution 43 (assignment 6, class 1) reason=학생A 요청 — 제출 화면 오류` |
| 13:41:48.214 | `app.log:1240` | 앱 graceful shutdown 시작 | `Commencing graceful shutdown. Waiting for active requests to complete` |
| 13:41:50.144 | `app.log:1241` | graceful shutdown 완료 | `Graceful shutdown complete` |
| 13:41:50.194 ~ .224 | `app.log:1243` ~ `1244` | Hikari 풀 종료 시작 · 완료 | `itembank-pool - Shutdown initiated...` / `Shutdown completed.` |
| 13:41:51 | `nginx:16865` | **(선후 불확실)** 첫 502. 앱 종료 로그(13:41:50.2)와 1초 차이. 이후 13:41:59 까지 8건(`nginx:16865` ~ `16872`), 1분 동안 8건 | `"GET /api/units/M5-2/items?student=학생B HTTP/1.1" 502` |
| 13:41:55.902 | `app.log:1245` | 새 버전 기동 시작 | `Starting ItemBankApplication v1.4.2 using Java 21.0.4 with PID 1` |
| 13:41:55.906 | `app.log:1246` | **설정 관련 줄**: 활성 프로파일 없이 `default` 프로파일로 기동. 이 로그에는 비교할 이전 기동 기록이 없다 | `No active profile set, falling back to 1 default profile: "default"` |
| 13:41:58.351 ~ .535 | `app.log:1258` ~ `1260` | Hikari 풀 시작, 커넥션 추가 줄 1건 | `itembank-pool - Added connection org.mariadb.jdbc.Connection@4c1e7a3b` |
| 13:41:59 | `nginx:16872` | **(선후 불확실)** 마지막 502. `Tomcat started`(13:42:00.290)와 1초 차이 | `"GET /api/units/M6-1/items?student=학생A HTTP/1.1" 502` |
| 13:42:00.290 | `app.log:1263` | Tomcat 8080 포트 수신 시작 | `Tomcat started on port 8080 (http) with context path '/'` |
| 13:42:00.304 | `app.log:1264` | 앱 기동 완료(4.921초) | `Started ItemBankApplication in 4.921 seconds (process running for 5.488)` |
| 13:42:01 | `nginx:16873` | **(선후 불확실)** 재기동 후 첫 200. 기동 완료와 1초 차이 | `"GET /api/units HTTP/1.1" 200` |
| 13:42:10 | `app.log:1266`, `nginx:16884` | **평소와 다른 점**: 재기동 후 첫 재배포 200 의 응답 크기가 802 B 로, 배포 전(170 ~ 199 B)보다 크다. 이후 재배포 200 은 640 ~ 910 B(13:50:00 · 13:51:08 · 13:56:43 · 14:21:02 · 14:22:46) | `POST /api/distributions/7/redistribute HTTP/1.1" 200 802` |
| 13:42:31 | `deploy:7` | 배포 완료로 기록됨 | `\| v1.4.2 \| … \| 2026-09-17 13:42:31 \|` |
| 13:43 ~ 13:45 | `nginx` | 요청 59 · 58 · 59건/분, 5xx 0건 | — |
| 13:46:38.107 | `app.log:1274` | distribution 42 재배포 처리 INFO(스레드 `nio-8080-exec-1`) | `redistributed distribution 42 (assignment 4, class 1) reason=보강 수업 후 재배포` |
| 13:46:38.113 | `app.log:1275`, `1286`, `1294`, `1304`, `nginx:17155` | **가장 많이 나오는 오류의 첫 발생**. 같은 스레드에서 6 ms 뒤 ERROR, nginx 500(176 B). 스택에 `DistributionService.listByClass(DistributionService.java:38)` ← `DistributionController.redistribute(DistributionController.java:40)`, `Caused by: FetchNotFoundException`. 이후 14:34:45 까지 같은 메시지 38건 더, 13:46 ~ 14:34(49분) 평균 0.8건/분, 발생한 분에는 1 ~ 4건/분 | `JpaObjectRetrievalFailureException: Entity …Assignment with identifier value 9 does not exist` |
| 13:46 ~ 14:34 | `app.log` ERROR 39줄 | 39건 모두 바로 앞 줄이 같은 distribution 의 `redistributed distribution …` INFO 다. 대상은 distribution 42(class 1) 19건, 43(class 1) 20건 | `unhandled exception on /api/distributions/42/redistribute` |
| 13:50:00 | `app.log:1393`, `nginx:17353` | 500 이 이어지는 동안 다른 재배포는 200. 13:50:00(8, class 2), 13:51:08(7, class 3), 13:56:43(44, class 2), 14:21:02 · 14:22:46(5, class 3) | `POST /api/distributions/8/redistribute HTTP/1.1" 200 908` |
| 13:59:30 | `app.log:1837`, `nginx:17867` | 409 계속. 14:24:12 · 14:30:00 · 14:35:26 에도 1건씩(2.2절) | `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=3` |
| 14:34:45 | `app.log:2887`, `nginx:20084` | 가장 많은 오류의 마지막 관측(distribution 43) | `unhandled exception on /api/distributions/43/redistribute` |
| 14:36:00.919 | `app.log:2929` | 학급 리포트 생성(이 구간 마지막). 수치는 13:12 와 같다 | `built class report for class 3: 3 distributions, 10 submissions` |
| 14:39:57.905 | `app.log:2938` | `app.log` 마지막 줄 | `no handler for /favicon.ico` |
| 14:39:58 | `nginx:20365` | **(선후 불확실)** `nginx-access.log` 마지막 줄. app 마지막 줄과 1초 이내. 14:35 ~ 14:39 5xx 0건 | `"GET /api/units HTTP/1.1" 200 613` |
| 14:40 ~ 14:50 | — | 로그 없음(1절 "추가로 받아야 할 자료") | — |

### 2.2 반복 메시지 묶음 (13:10 ~ 14:39, 90분)

모두 `app.log` INFO(`GlobalExceptionHandler`)이고 nginx 의 4xx 줄과 짝을 이룬다. 배포 전후로 빈도 변화가 눈에 띄지 않아 2.1 에는 넣지 않았다.

| 첫 발생 | 출처 | 메시지(숫자는 N) | 건수 | 평균 |
|---|---|---|---|---|
| 13:11:41.157 | `app.log:1146` | `not found: 문항이 없습니다: id=N (/api/items/N)` | 52 | 0.58건/분 |
| 13:13:50.662 | `app.log:1158` | `no handler for /robots.txt` | 44 | 0.49건/분 |
| 13:11:50.852 | `app.log:1148` | `no handler for /api/unit` | 32 | 0.36건/분 |
| 13:14:13.014 | `app.log:1160` | `no handler for /favicon.ico` | 31 | 0.34건/분 |
| 13:13:54.854 | `app.log:1159` | `no handler for /api/item/N` | 30 | 0.33건/분 |
| 13:12:29.578 | `app.log:1150` | `not found: 배포가 없습니다: id=N (/api/distributions/N)` | 23 | 0.26건/분 |
| 13:21:22.270 | `app.log:1177` | `no handler for /apple-touch-icon.png` | 23 | 0.26건/분 |
| 13:14:31.581 | `app.log:1163` | `bad request: Method parameter 'id': Failed to convert value …` | 9 | 0.10건/분 |
| 13:13:13.352 | `app.log:1155` | `not found: 단원이 없습니다: code=MN-N (/api/units/MN-N/items)` | 7 | 0.08건/분 |
| 13:13:03.243 | `app.log:1153` | WARN `state conflict: 마감된 과제는 재배포할 수 없습니다: distributionId=N` | 6 | 0.07건/분 |

### 2.3 로그에서 찾지 못한 신호

가장 많은 오류(13:46:38)보다 앞선 줄을 다음 기준으로 찾았지만 해당 줄이 없었다.

- **느린 쿼리:** 없음. `app.log` 에 쿼리 시간 · 슬로우 쿼리 로그가 없고, `nginx-access.log` 형식에는 응답 시간 필드가 없다.
- **타임아웃 · 커넥션 풀 고갈:** 없음. `timeout` · `timed out` · `Connection is not available` 0건, nginx 504 0건.
- **설정 재적재:** 재기동 외에 설정을 다시 읽은 줄(`refresh` · `reload` 류)은 없다. 설정과 관련된 줄은 `app.log:1246` 의 프로파일 줄 하나다.
- **13:46 이전의 WARN · ERROR:** 13:13:03 · 13:15:54 의 409 WARN 2건 뿐이다. 둘 다 평소 패턴과 같다.

## 3. 가설과 검증

> 2절 타임라인을 바탕으로 원인 가설을 세운다. 3.0 의 "로그 판정" 열은 **로그로 확인할 수 있는 반증 조건만** 실행한 결과다(명령과 출력은 3.6). 최종 원인 판정이 아니다.
> `JpaObjectRetrievalFailureException … Assignment … 9 does not exist`(2.1 의 13:46:38 줄)는 **증상**이다. 가설은 "왜 그 증상이 13:46 부터 학급 1 의 재배포에서만 나오는가"를 설명하려는 것이다.
> 운영 DB 에는 직접 접속하지 않는다(CLAUDE.md 금지 사항). 운영 데이터 확인은 DB 담당자에게 읽기 전용 조회를 요청하고, 재현은 로컬 `docker compose --profile modern` 의 시드 DB 나 H2 테스트로 한다.

### 3.0 가설 요약

| ID | 계층 | 가설 한 문장 | 로그 판정 | 이유(한 줄) | 판단에 더 필요한 로그 · 자료 |
|---|---|---|---|---|---|
| H1 | 애플리케이션 코드(배포 변경분) | v1.4.2 가 재배포 응답을 만들 때 같은 학급의 배포 이력을 모두 조회하도록 바뀌어서, 그 학급의 배포 한 건에 결함이 있으면 이제 그 학급의 모든 재배포 요청이 실패한다 | **유지** | 42 · 43 재배포는 재기동 전 18건 모두 200, 후 39건 모두 500 이고, 오류 스택의 `listByClass` 는 재기동 후에만 39건 나온다(C1 · C2 · C3) | 반증 조건 3 · 4(v1.4.1 / v1.4.2 × 결함 데이터 재현)는 로그가 아닌 재현 테스트가 필요하다 |
| H2 | DB · 데이터 | 운영 DB 에 존재하지 않는 `assignment` 9 를 가리키는 `distribution` 행이 학급 1 에 있다. 이 참조 무결성 깨짐은 배포 전부터 있었다 | **유지** (DB 조건은 판단 불가) | 누락 id 는 하루 내내 `9` 하나뿐이고 배포 전 distribution 41 에서도 같은 누락이 7건이며, 학급 1 의 42 · 43 자신의 assignment 는 4 · 6 이라 9 는 학급 1 의 다른 행에서 온다(C4 ~ C6) | 반증 조건 1 ~ 3 은 운영 DB 조회 결과(`LEFT JOIN` 고아 행, `SHOW CREATE TABLE distribution`)가 있어야 판단할 수 있다. distribution 41 의 `class_id` 는 41 요청이 항상 로드 단계에서 실패해 로그에 남지 않는다. 학급 1 리포트가 배포 전 17건 모두 200 인 이유는 `ReportService` 코드 확인이 필요하다(C7) |
| H3 | 배포 · 설정 변경 | 새 인스턴스가 활성 프로파일 없이 `default` 로 기동해서, 이전 인스턴스와 다른 설정(접속 DB · 스키마 · JPA 옵션)으로 동작한다 | **기각** | 반증 조건 3 충족: 재기동 전 인스턴스도 같은 `Assignment` 9 누락을 7건 남겨, 두 인스턴스가 같은 데이터를 보고 있었다(C4 · C8) | 반증 조건 1(이전 기동도 `default` 였는지)은 이 로그에 기동 기록이 1건뿐이라 판단 불가다. 2026-09-14 v1.4.1 기동 로그가 있었다면 확인할 수 있었다. 조건 4(JDBC URL)는 Hikari 로그에 URL 이 없어 판단 불가다 |
| H4 | 트래픽 · 인프라 | 단일 호스트를 재기동하는 동안 nginx 가 일부 요청을 대기 호스트 `api-2`(다른 버전 · 다른 데이터)로 보냈고, 그 뒤로도 일부 요청이 그쪽에서 처리된다 | **기각** | 반증 조건 2 충족: nginx 500 46건이 모두 `app.log` ERROR 46건과 ±1초 안에서 1:1 로 대응하고, 그 `app.log` 는 13:41 에 v1.4.2 로 재기동한 바로 그 인스턴스(PID 1)다(C9 · C10) | nginx 에 `$upstream_addr` 필드가 있었다면 호스트를 직접 확인할 수 있었다. 반증 조건 1 · 3(upstream 설정, `api-2` 기동 이력)은 실행하지 않았다 |

H1 과 H2 는 서로를 배제하지 않는다. 둘 다 맞을 수도 있다(H2 의 데이터 결함을 H1 의 코드 변경이 드러냄). 그래서 반증 조건을 따로 확인한다.

### 3.1 H1 — 애플리케이션 코드(v1.4.2 변경분)

| 항목 | 내용 |
|---|---|
| 가설 | v1.4.2 에서 `DistributionController.redistribute` 가 재배포 뒤에 `DistributionService.listByClass(classId)` 를 호출한다. 그래서 그 학급의 배포 중 연관 엔티티를 읽지 못하는 행이 하나라도 있으면, 재배포 대상이 그 행이 아니어도 요청 전체가 500 이 된다 |
| 지지 근거 | **변경 내용**: `deploy:15` ~ `16`(응답에 `history[]` 추가, `listByClass` 호출 추가)<br>**오류 위치**: 13:46:38 오류 스택이 새 호출 경로를 지난다. `app.log:1286`(`DistributionService.java:38`), `app.log:1294`(`DistributionController.java:40`)<br>**배포 전에는 성공**: distribution 43 재배포가 200 이었다. 13:13:05 · 13:13:27 · 13:41:20(`app.log:1154`, `1157`, `1238`)<br>**배포 후에는 실패**: 같은 43 이 13:50 부터 500 이다(`app.log:1396` 등)<br>**재배포 처리는 끝난 뒤 오류**: 39건 모두 `redistributed …` INFO 바로 다음 줄이 ERROR 다(`app.log:1274` → `1275`)<br>**다른 학급은 정상**: 학급 2 · 3 재배포는 계속 200 이다(`app.log:1393`, `nginx:17353` 등)<br>**응답 형식 변화**: 200 응답 크기가 170 ~ 199 B 에서 802 B 로 커졌다(`nginx:16884`) |
| 반증 조건 | 1. 배포된 v1.4.2 코드에서 `DistributionController.java:40` 의 `listByClass` 호출이 재배포 응답 경로에 없다.<br>2. 배포 전(09:00 ~ 13:40)에도 distribution 42 · 43 재배포가 같은 스택(`listByClass` 경유)으로 실패한 기록이 있다.<br>3. v1.4.1 코드로, 같은 데이터(학급 1 에 결함 행이 있는 상태)에서 42 · 43 재배포가 500 이 된다.<br>4. v1.4.2 코드로, 결함 행이 없는 데이터에서도 학급 1 재배포가 500 이 된다. 이 경우 원인은 데이터가 아닌 코드 쪽 다른 결함이다 |
| 확인 방법 | **조건 1**:<br>`git log -p -- modern/api/src/main/java/com/example/assignment/DistributionController.java`<br>`sed -n '30,45p' modern/api/src/main/java/com/example/assignment/DistributionController.java`<br>배포 태그 · 이미지가 있으면 `git show v1.4.2:…/DistributionController.java` 로 같은 줄을 비교한다.<br>**조건 2**:<br>`grep -nE 'distributions/4[23]/redistribute' incident-logs/b-deploy-5xx/nginx-access.log \| awk -F'"' '{print $3}' \| awk '{print $1}' \| sort \| uniq -c`<br>(배포 전 구간의 상태 코드 분포를 본다)<br>**조건 3 · 4**: `DistributionService` 서비스 테스트(`@ExtendWith(MockitoExtension.class)`)나 `@DataJpaTest`(H2)로 재현한다. 학급 하나에 존재하지 않는 `assignment_id` 를 가진 배포를 넣고 `redistribute` → `listByClass` 를 호출한다. 코드는 v1.4.1 / v1.4.2 두 가지로 돌린다 |

### 3.2 H2 — DB · 데이터 무결성

| 항목 | 내용 |
|---|---|
| 가설 | 운영 DB 의 `distribution` 테이블에 존재하지 않는 `assignment` 9 를 참조하는 행이 있고, 그 행의 `class_id` 가 1 이다. 배포 전부터 있던 상태다. 후보는 distribution 41 |
| 지지 근거 | **배포 전에도 같은 엔티티 누락**: 같은 엔티티 · 같은 id(`Assignment` 9) 누락이 배포 전에 7건 있었다. `app.log:194`(09:52:17, distribution 41, `DistributionService.loadOrThrow` 경유), `app.log:232` · `270` · `451` · `655` · `695` · `733`. 1.2 의 09:52 ~ 11:18 nginx 500<br>**배포 후 원인 예외**: 배포 후 오류의 원인 예외도 같은 id 다. `app.log:1304`(`FetchNotFoundException … Assignment … 9`)<br>**실패 범위**: 실패가 학급 1(distribution 42 · 43)에만 있다(`app.log:1274`, 2.1 의 13:46 ~ 14:34 줄)<br>**반대 방향 관찰**: 학급 1 리포트는 13:24:45 에 정상 생성됐다(`app.log:1191`, `class 1: 3 distributions`). 같은 `findByClassRoomIdOrderByDistributedAtAsc` 를 쓰는 리포트가 학급 1 에서 성공한 이유를 설명해야 한다 |
| 반증 조건 | 1. 운영 DB 에 `assignment.id = 9` 행이 있다.<br>2. `assignment_id = 9` 인 `distribution` 행이 없거나, 있어도 `class_id ≠ 1` 이다.<br>3. 운영 DB 에 `fk_distribution_assignment` 외래 키가 걸려 있고 위반 행이 0건이다. 이 경우 고아 행이 생길 수 없다.<br>4. distribution 41 의 오류(`app.log:194`)와 42 · 43 의 오류가 서로 다른 `distribution` 행에서 나온다. 이 경우 같은 결함으로 묶을 근거가 사라진다 |
| 확인 방법 | **스키마 · 시드 확인(로컬 파일)**:<br>`grep -n 'fk_distribution_assignment' db/mariadb/init/01-schema.sql`<br>`grep -nE 'INSERT INTO (assignment\|distribution)' db/mariadb/init/02-seed.sql`<br>(시드에 assignment 9 · distribution 41 이 있는지 본다)<br>**운영 DB(DB 담당자에게 읽기 전용 조회 요청)**:<br>`SELECT d.id, d.class_id, d.assignment_id FROM distribution d LEFT JOIN assignment a ON a.id = d.assignment_id WHERE a.id IS NULL;`<br>`SELECT * FROM assignment WHERE id = 9;`<br>`SHOW CREATE TABLE distribution;`<br>`SELECT @@foreign_key_checks;`<br>**리포트 성공 이유(반대 관찰)**: `sed -n '40,60p' modern/api/src/main/java/com/example/assignment/ReportService.java` 로 리포트가 `Assignment` 를 읽는지 확인한다. `Distribution.java:23` ~ `24` 의 `@ManyToOne(fetch = LAZY)` 와도 대조한다 |

### 3.3 H3 — 배포 · 설정 변경(프로파일)

| 항목 | 내용 |
|---|---|
| 가설 | v1.4.2 인스턴스가 `SPRING_PROFILES_ACTIVE` 없이 `default` 프로파일로 떠서, 이전 인스턴스와 다른 데이터소스나 JPA 설정으로 동작한다. 그래서 일부 데이터가 다르게 보인다 |
| 지지 근거 | **프로파일 없이 기동**: `app.log:1246`(`No active profile set, falling back to 1 default profile: "default"`)<br>**배포 명령에 설정 인자 없음**: 배포 명령 인자는 `version` · `host` 뿐이다(`deploy:20`, 토큰은 가림)<br>**배포 직후 시작**: 500 이 배포 완료(`deploy:7`, 13:42:31) 직후부터 시작된다. 배포 전 280분 동안 학급 1 재배포 500 은 없었다(1.2)<br>**새 커넥션**: 새 풀이 커넥션을 새로 맺었다(`app.log:1258` ~ `1260`) |
| 반증 조건 | 1. 이전 v1.4.1 기동 로그(2026-09-14 18:05 ~ 18:07)에도 같은 `No active profile set` 줄이 있다.<br>2. 저장소에 프로파일별 설정 파일이 없고, `application.yml` 에 프로파일 분기가 없다. 이 경우 `default` 가 정상 상태다.<br>3. 배포 전 오류(`app.log:194`, 09:52)가 이미 같은 `Assignment` 9 누락을 보였다. 즉 이전 인스턴스도 같은 데이터를 보고 있었다는 것이 확인된다(같은 DB · 스키마 접속 확인).<br>4. 배포 전후 커넥션 대상(JDBC URL · 스키마)이 같다 |
| 확인 방법 | **설정 파일**:<br>`ls modern/api/src/main/resources/`<br>`grep -n 'profiles' modern/api/src/main/resources/application.yml`<br>**v1.4.1 기동 로그**: 운영 로그 보관소에서 `grep -n 'No active profile\|The following .* profile' app.log.2026-09-14` 로 확인한다. 파일명은 보관 정책에 따른다.<br>**배포 환경 변수**: 배포 파이프라인 설정에서 `SPRING_PROFILES_ACTIVE` · `SPRING_DATASOURCE_URL` 을 v1.4.1 · v1.4.2 릴리스끼리 비교한다.<br>**조건 3**:<br>`sed -n '194,195p' incident-logs/b-deploy-5xx/app.log`<br>(이미 2.1 · 3.2 에서 인용. 판정은 4절에서 한다) |

### 3.4 H4 — 트래픽 · 인프라(라우팅)

| 항목 | 내용 |
|---|---|
| 가설 | 재기동 중 upstream 이 실패하면서(502), nginx 가 대기 호스트 `api-2` 로 요청을 넘기기 시작했다. 이후 일부 요청이 다른 버전 · 다른 데이터를 가진 `api-2` 에서 처리되어 500 이 난다 |
| 지지 근거 | **대기 호스트 존재**: `deploy:3`(대기 호스트 `api-2` 가 있다. "평소 정지")<br>**upstream 실패**: `nginx:16865` ~ `16872`(재기동 중 502 8건)<br>**호스트 구분 불가**: 현재 로그에는 처리 호스트를 구분할 필드(`$upstream_addr`)가 없다(1.3) |
| 반증 조건 | 1. nginx upstream 설정에 `api-1` 만 있거나, `api-2` 가 `backup` 이 아니고 꺼져 있다.<br>2. 13:46 ~ 14:34 의 nginx 500 39건이 전부 `app.log` 의 ERROR 39건과 1:1 로 대응한다. 즉 모두 `app.log` 를 남긴 인스턴스가 처리했다(1.2 에서 분 단위 일치 확인. 초 단위 대조 필요).<br>3. `api-2` 프로세스가 해당 시간대에 떠 있지 않았다 |
| 확인 방법 | **nginx 설정**: nginx 설정 파일(`/etc/nginx/conf.d/*.conf` 등, 저장소 밖)의 `upstream` 블록을 인프라 담당자에게 받는다.<br>**api-2 상태**: `api-2` 의 프로세스 · 컨테이너 기동 이력(`docker ps -a` · systemd 로그)을 받는다.<br>**초 단위 대조**:<br>`awk '$9==500{print substr($4,14,8)}' incident-logs/b-deploy-5xx/nginx-access.log > /tmp/n500` 과<br>`grep -E '^2026-09-17T\S+ +ERROR' incident-logs/b-deploy-5xx/app.log \| cut -c12-19 > /tmp/a500` 을 `diff` 로 비교한다(경로는 작업 공간에 맞게 바꾼다) |

### 3.5 가설과 무관하게 확인할 항목

- **재배포 처리가 반영됐는지**: 500 을 받은 39건도 `redistributed …` INFO 를 먼저 남겼다(`app.log:1274` 등). 실패 응답을 받은 재배포가 DB 에 반영됐는지(커밋 · 롤백)에 따라 영향 범위가 달라진다. `DistributionController.redistribute` 와 `DistributionService.redistribute` · `listByClass` 의 트랜잭션 경계를 확인하고, 운영 DB 에서 42 · 43 의 재배포 시각 컬럼을 조회한다(읽기 전용 요청).
- **14:40 이후 로그**: 1절에서 정한 추가 수집 자료다. 오류가 계속되는지 보아야 끝 시각을 정할 수 있다.

### 3.6 로그 검증 실행 기록

- **실행 위치:** 아래 명령은 `incident-logs/b-deploy-5xx/` 에서 실행했다. C3 만 저장소 루트에서 실행했다.
- **임시 파일:** `$S` 는 세션 작업 공간(scratchpad) 경로이고, 저장소 밖이다.
- **결과 칸:** 명령의 출력 숫자를 그대로 옮겼다.

| ID | 가설 · 조건 | 명령 | 결과(출력 그대로) | 해석 |
|---|---|---|---|---|
| C1 | H1 반증 조건 2 | `grep -nE 'distributions/4[23]/redistribute' nginx-access.log \| awk '{t=substr($4,14,5); print (t<"13:42"?"before":"after"), $9}' \| sort \| uniq -c` | `18 before 200`<br>`39 after 500` | 배포 전에는 42 · 43 재배포가 실패하지 않았다. 반증 조건 2 미충족 |
| C2 | H1 반증 조건 2 | `grep -n 'DistributionService.listByClass' app.log \| awk -F: '{print ($1<1240?"before_restart":"after_restart")}' \| sort \| uniq -c` | `39 after_restart` | `listByClass` 경유 오류는 재기동(`app.log:1240`) 뒤에만 있다 |
| C2b | H1 보조 | `grep -n 'DistributionController.redistribute(DistributionController.java' app.log \| awk -F: '{split($0,a,"java:"); print ($1<1240?"before":"after"), a[2]}' \| sort \| uniq -c` | `6 before 38)`<br>`39 after 40)` | 재기동 전 오류는 컨트롤러 38행(재배포 본처리)에서, 재기동 후 오류는 40행에서 났다 |
| C3 | H1 반증 조건 1 | `git log --oneline -- modern/api/src/main/java/com/example/assignment/DistributionController.java`<br>`git tag -l 'v1.4*' \| wc -l`<br>`sed -n '30,45p' …/DistributionController.java` | `1260542 실습 저장소 초기 구성: …` (1건)<br>`0`<br>40행: `List<DistributionResponse> history = distributionService.listByClass(redistributed.classId());` | 저장소의 40행이 스택의 `DistributionController.java:40` 과 같은 호출이다. 저장소에 `v1.4.2` 태그가 없어서, 배포 이미지와 같은 코드인지는 확인하지 못했다 |
| C4 | H2 지지 · H3 반증 조건 3 | `grep -oE 'identifier value \`[0-9]+\`' app.log \| sort \| uniq -c` | `92 identifier value \`9\`` | 하루 동안 누락된 id 는 9 하나다. 92줄 = 예외 46건 × (본 예외 + `Caused by`) |
| C5 | H2 지지 | `grep -nE '^2026-09-17T\S+ +ERROR' app.log \| awk -F: '$1<1240' \| grep -oE 'on /api/distributions/[0-9]+[^ ]*' \| sort \| uniq -c` | `1 on /api/distributions/41`<br>`6 on /api/distributions/41/redistribute` | 재기동 전 오류 7건은 모두 distribution 41 이다(`app.log:194` · `232` · `270` · `451` · `655` · `695` · `733`) |
| C6 | H2 반증 조건 4 | `grep -oE 'redistributed distribution [0-9]+ \(assignment [0-9]+, class [0-9]+\)' app.log \| sort \| uniq -c` | `24 … 42 (assignment 4, class 1)`<br>`33 … 43 (assignment 6, class 1)`<br>`18 … 44 (assignment 5, class 2)`<br>`6 … 5 (assignment 3, class 3)`<br>`9 … 6 (assignment 4, class 3)`<br>`12 … 7 (assignment 5, class 3)`<br>`9 … 8 (assignment 6, class 2)` | 42 · 43 자신의 assignment 는 4 · 6 이다. 학급 1 목록 조회에서 나온 9 는 학급 1 의 다른 행에서 온다. 41 의 `class_id` 는 로그에 없다 |
| C7 | H2 반대 관찰 | `grep -oE 'built class report for class [0-9]+: [0-9]+ distributions' app.log \| sort \| uniq -c`<br>`awk '$7 ~ /^\/api\/classes\/1\/report/ {t=substr($4,14,5); print (t<"13:42"?"before":"after"), $9}' nginx-access.log \| sort \| uniq -c` | `17 … class 1: 3 distributions`<br>`24 … class 2: 3 distributions`<br>`19 … class 3: 3 distributions`<br>`17 before 200` (마지막 13:24:45) | 학급 1 에는 배포가 3건이다. 학급 1 리포트는 배포 전 17건 모두 성공했고, 배포 후 요청은 없다. 같은 목록을 읽는 리포트가 왜 성공했는지는 로그로 설명할 수 없다 |
| C8 | H3 반증 조건 1 | `grep -c 'Starting ItemBankApplication' app.log`<br>`grep -c 'No active profile set' app.log` | `1`<br>`1` | 기동 기록이 v1.4.2 의 1건뿐이라 이전 버전과 비교할 수 없다 |
| C9 | H4 반증 조건 2 | `awk '$9==500{print substr($4,14,8)}' nginx-access.log > $S/n500`<br>`grep -E '^2026-09-17T\S+ +ERROR' app.log \| cut -c12-19 > $S/a500`<br>`wc -l < $S/n500; wc -l < $S/a500; diff $S/n500 $S/a500` | `46`<br>`46`<br>diff 1곳: `< 14:28:06` / `> 14:28:05`<br>순서대로 짝지은 최대 시각 차 `max_abs_diff_sec 1` | nginx 500 은 모두 `app.log` ERROR 와 1초 안에서 1:1 로 대응한다. 1초 차이는 초 버림(14:28:05.996)에 따른 것이다 |
| C10 | H4 반증 조건 2 보조 | `grep -cE ' 1 --- ' app.log; grep -cE '^2026-09-17T' app.log`<br>`grep -cE 'api-2\|10\.20\.x\.x' nginx-access.log app.log` | `1275`<br>`1275`<br>`app.log:0`<br>`nginx-access.log:0` | `app.log` 의 시각 줄은 모두 PID 1 한 프로세스다. `api-2` 를 언급한 줄은 없다 |

로그로 확인하지 않은 항목은 다음과 같다. 다음 단계에서 다룬다.

- **H1 반증 조건 3 · 4:** 재현 테스트가 필요하다.
- **H2 반증 조건 1 ~ 3:** 운영 DB 의 읽기 전용 조회가 필요하다.
- **H2 반대 관찰(C7):** `ReportService` 코드를 읽어야 한다.
- **H4 반증 조건 1 · 3:** nginx upstream 설정과 `api-2` 기동 이력이 필요하다.

### 3.7 판정

> **출발점**: 재배포 42 · 43 은 재기동 전 `18 before 200`, 재기동 후 `39 after 500` 이다(3.6 C1).
> **방법**: 로그에 찍힌 클래스 · 메서드 · 설정 키를 단서로 저장소의 코드와 설정을 읽었다.
> **하지 않은 것**: 테스트 · 재현 실행은 하지 않았다. 설정값은 파일에서 읽은 값만 썼다.
> **전제**: 저장소에 `v1.4.2` 태그가 없다(3.6 C3). 그래서 저장소 코드가 운영에 배포된 코드와 같다는 것을 전제로 대조했다. 이 전제는 `deploy:16` 의 변경 설명과 스택의 줄번호가 일치하는 것으로만 뒷받침된다.

#### 3.7.1 로그 단서 → 코드 위치

| 로그 단서 | 코드 위치 | 읽은 내용 |
|---|---|---|
| `DistributionController.redistribute(DistributionController.java:40)` (`app.log:1294`) | `modern/api/src/main/java/com/example/assignment/DistributionController.java:38` ~ `41` | 38행 `distributionService.redistribute(id, reason)` 다음에 40행 `distributionService.listByClass(redistributed.classId())` 를 호출하고, 41행에서 둘을 묶어 반환한다. 39행 주석: `// v1.4.2 — 재배포 응답에 학급 배포 이력(history)을 함께 돌려주도록 변경. 이전 버전은 재배포 건만 반환했다.` |
| 재기동 전 오류의 `DistributionController.java:38` (3.6 C2b, `6 before 38)`) | 같은 파일 38행 | 재배포 본처리 호출 줄이다. 재기동 전 distribution 41 오류는 이 줄에서, 재기동 후 42 · 43 오류는 40행에서 났다 |
| `DistributionService.listByClass(DistributionService.java:38)` (`app.log:1286`) | `…/DistributionService.java:36` ~ `40` | `@Transactional(readOnly = true)`. `findByClassRoomIdOrderByDistributedAtAsc(classId)` 결과 전체를 `DistributionResponse::from` 으로 바꾼다 |
| `$Proxy141.findByClassRoomIdOrderByDistributedAtAsc` (1절 스택 집계 39건) | `…/DistributionRepository.java:13` ~ `14` | `@EntityGraph(attributePaths = {"assignment", "classRoom"})`. 학급의 모든 배포를 `assignment` 와 함께 읽는다 |
| `FetchNotFoundException … Assignment … 9` (`app.log:1304`) | `…/Distribution.java:23` ~ `25` | `@ManyToOne(fetch = FetchType.LAZY, optional = false)`, `@JoinColumn(name = "assignment_id", nullable = false)` |
| `DistributionResponse` 변환 | `…/DistributionResponse.java:17` ~ `18` | 행마다 `d.getAssignment().getId()` 와 `getTitle()` 을 읽는다 |
| `redistributed distribution …` INFO 바로 뒤의 ERROR (`app.log:1274` → `1275`) | `…/DistributionService.java:49` ~ `61`, `DistributionController.java:12` ~ `15` | `redistribute` 는 자체 `@Transactional` 이고, 56행에서 INFO 를 남긴 뒤 반환한다. 컨트롤러에는 `@Transactional` 이 없다. 따라서 재배포 트랜잭션이 끝난 **뒤에** 별도 읽기 트랜잭션에서 `listByClass` 가 실패한다 |
| `unhandled exception on …` (`app.log:1275`) | `modern/api/src/main/java/com/example/common/GlobalExceptionHandler.java:53` ~ `56` | `@ExceptionHandler(Exception.class)` 가 ERROR 로그와 500 을 만든다. `JpaObjectRetrievalFailureException` 은 404 · 400 · 409 매핑(24 · 30 · 36 · 47행)에 해당하지 않는다 |
| 학급 1 리포트 성공(`app.log:1191`, 3.6 C7) | `…/ReportService.java:41` ~ `46` | `buildClassReport` 도 **같은** `distributionRepository.findByClassRoomIdOrderByDistributedAtAsc(classId)` 를 같은 `@EntityGraph` 로 호출한다 |
| `No active profile set` (`app.log:1246`) | `modern/api/src/main/resources/` | 파일은 `application.yml` 하나뿐이다. 저장소의 `java` · `resources` 아래에 `profiles` · `@Profile` 문자열이 없다 |
| 데이터소스 · 풀 설정 | `application.yml:5`, `13` ~ `18` | `url: [SECRET]`, `maximum-pool-size: 5`, `connection-timeout: 3000`, `leak-detection-threshold: 10000`. 운영에서 URL 을 덮어쓰는 방식은 저장소에서 찾지 못했다 |
| `fk_distribution_assignment` | `db/mariadb/init/01-schema.sql:101` | `FOREIGN KEY (assignment_id) REFERENCES assignment (id)`. 저장소 스키마에는 외래 키가 있다 |
| 운영 데이터 id(41 ~ 44, assignment 9) | `db/mariadb/init/02-seed.sql:119` ~ `138` | 시드에는 assignment 1 ~ 6, distribution 1 ~ 8 만 있다. 운영 데이터와 다르므로 고아 행이 있는지를 시드로는 확인할 수 없다 |

#### 3.7.2 가설별 코드 · 설정 대조 결과

| 가설 | 로그 판정(3.0) | 코드 · 설정 판정 | 근거(파일:줄번호) |
|---|---|---|---|
| H1 코드 변경 | 유지 | **지지한다** | **새 호출의 위치**: `DistributionController.java:40` 의 새 호출이 스택(`app.log:1294`)의 위치와 정확히 같다. 39행 주석과 `deploy:16` 이 이 줄을 v1.4.2 변경분으로 적고 있다<br>**학급 전체를 읽는다**: `listByClass` 는 재배포 대상 한 건이 아니라 학급 전체를 `assignment` 와 함께 읽는다(`DistributionRepository.java:13` ~ `14`, `DistributionResponse.java:17` ~ `18`). 그래서 대상이 42 · 43 이어도, 같은 학급의 다른 행이 읽히지 않으면 실패한다<br>**v1.4.1 경로는 대상 한 건만 읽는다**: 재배포 본처리는 `findWithDetailsById(id)`(`DistributionService.java:51`, `65`)로 대상 한 건만 읽는다. 이 경로는 재기동 전 42 · 43 에서 18건 모두 성공했다(C1). 재기동 뒤 달라진 것은 40행 호출뿐이다(C2 · C2b) |
| H2 데이터 결함 | 유지 | **일부 반박한다** (그 학급에 고아 참조가 있다는 선행 조건은 코드로 판단 불가) | **반박: 학급 1 리포트가 같은 쿼리로 성공했다**: `ReportService.java:46` 은 `listByClass` 와 같은 리포지토리 메서드 · 같은 `@EntityGraph` 를 쓴다. 그런데 학급 1 리포트는 09:01 ~ 13:24:45 에 17건 모두 성공했다. 이 기간에는 distribution 41 의 `Assignment` 9 누락 오류(09:52 ~ 11:18)가 이미 나고 있었다. 따라서 "41 이 학급 1 에 속한 고아 행이고 배포 전부터 있었다"는 가설의 구체적 형태는 코드와 로그를 합치면 성립하기 어렵다<br>**판단 불가: 고아 행의 존재**: `01-schema.sql:101` 의 외래 키가 운영에도 걸려 있으면 고아 행이 생길 수 없다. 운영 스키마는 확인하지 못했다<br>**지지: 누락 대상**: 학급 1 목록 조회가 `Assignment` 9 를 찾지 못한 것 자체는 스택이 직접 보여 준다(`app.log:1304`) |
| H3 프로파일 설정 | 기각 | **반박한다** | **프로파일 분기가 없다**: 설정 파일이 `application.yml` 하나뿐이고 프로파일 분기가 없다. 따라서 `default` 로 뜨는 것이 이 저장소의 정상 상태다(반증 조건 2 충족)<br>**같은 데이터를 봤다**: 로그상 이전 인스턴스도 같은 `Assignment` 9 누락을 봤다(C4 · C5) |
| H4 api-2 라우팅 | 기각 | **관련 코드를 찾지 못했다** | 저장소에 nginx `upstream` 설정이 없다(`*.conf` 중 `upstream` 을 포함한 파일은 다른 실습 자료 `incident-logs/a-connection-pool/nginx-error.log` 뿐). 기각은 로그 근거(C9 · C10)로만 유지한다 |

#### 3.7.3 최종 판정

**채택: H1 — v1.4.2 의 재배포 응답 변경(`DistributionController.java:40` 의 `listByClass` 호출 추가)**

- **장애 구조**: 재배포 대상 한 건만 읽던 경로가 v1.4.2 에서 "학급 전체 배포 + 각 과제"까지 읽도록 넓어졌다.
- **실패 지점**: 학급 1 목록 조회에서 `Assignment` 9 를 읽지 못해 예외가 나고, `GlobalExceptionHandler.java:53` ~ `56` 이 이를 500 으로 바꾼다. 그래서 학급 1(42 · 43)의 재배포 요청만 13:46 부터 모두 500 이 된다.
- **증상 이름 주의**: `JpaObjectRetrievalFailureException` 은 이 경로가 만든 **증상**이다.
- **확신 수준: 중간**
  - **높게 볼 근거**:
    - 실패 줄(`DistributionController.java:40`)이 v1.4.2 변경 줄과 일치한다.
    - 같은 대상(42 · 43)이 변경 전 18건 모두 성공하고 변경 후 39건 모두 실패했다.
    - 재기동 전에는 `listByClass` 경유 오류가 0건이다.
  - **"높음"으로 올리지 못한 이유**:
    1. 학급 1 목록에 `Assignment` 9 를 가리키는 행이 왜 있는지 아직 설명하지 못했다. 같은 쿼리를 쓰는 학급 1 리포트가 13:24:45 까지 성공했으므로, 그 행이 13:24:45 이후에 생겼거나 두 호출 사이에 아직 찾지 못한 차이가 있다. 운영 DB 조회 없이는 가릴 수 없다.
    2. 저장소에 `v1.4.2` 태그가 없어서 배포된 바이너리와 저장소 코드가 같은지 확인하지 못했다.

**기각한 가설과 사유**

| 가설 | 기각 사유 | 확신 수준 | 이유 |
|---|---|---|---|
| H2 데이터 결함(단독 원인) | 배포 전부터 있던 데이터 결함만으로는 42 · 43 재배포가 13:46 부터 **모두** 실패하기 시작한 것을 설명하지 못한다. 같은 데이터에서 재기동 전 18건은 성공했다. "41 이 학급 1 의 고아 행"이라는 구체적 형태는 같은 쿼리를 쓰는 학급 1 리포트의 성공(`ReportService.java:46`, C7)과 충돌한다 | 중간 | 다만 학급 1 에 `Assignment` 9 를 가리키는 행이 있는 것은 H1 이 실패하는 **선행 조건**이다. 이 선행 조건은 기각하지 않았고 운영 DB 조회로 확인해야 한다. 원인 사슬의 한 고리로 남겨 둔다 |
| H3 프로파일 설정 | 프로파일 분기가 없는 단일 `application.yml` 구성이라 `default` 기동이 정상이다(`resources/` 에 `application.yml` 하나). 이전 인스턴스도 같은 누락을 봤다(C4 · C5) | 높음 | 코드 · 설정과 로그 양쪽에서 각각 반증 조건이 충족됐다 |
| H4 api-2 라우팅 | nginx 500 46건이 모두 v1.4.2 로 재기동한 같은 인스턴스(PID 1)의 ERROR 와 1초 안에서 1:1 로 대응한다(C9 · C10) | 중간 | 로그 근거는 강하다. 그러나 nginx upstream 설정과 `api-2` 기동 이력을 직접 보지 못했다 |

#### 3.7.4 판정 과정에서 드러난 영향 범위 사항(원인과 별개)

- **실패 응답 뒤에도 재배포가 반영됐을 수 있다**: `DistributionService.redistribute` 는 자체 트랜잭션(`DistributionService.java:49`)에서 `markRedistributed`(`Distribution.java:50` ~ `53`)를 마치고 반환한다. 그 뒤 컨트롤러가 별도 트랜잭션의 `listByClass` 를 호출한다(`DistributionController.java:38`, `40`). 따라서 500 을 받은 39건의 재배포도 DB 에 반영됐을 가능성이 높다(`distributed_at` 갱신, `redistributed = 1`). 사용자는 실패로 보고 다시 시도했을 수 있다. 같은 대상이 1 ~ 4건/분으로 반복된 것(2.1)과 부합하지만, 반영 여부는 운영 DB 에서 42 · 43 의 `distributed_at` 을 조회해야 확정된다.
- **원인과 무관한 관찰**: `ReportService.java:69` ~ `71` 에 트랜잭션 안에서 도는 긴 서명 루프(TODO 주석)가 있다. 이번 장애 로그에는 타임아웃 · 풀 고갈이 0건이라 이번 판정과는 무관하다. CLAUDE.md 금지 사항("`@Transactional` 안에서 반복 횟수가 입력에 따라 늘어나는 루프")과 관련된 별도 점검 대상으로만 적어 둔다.

## 4. 원인

인과 순서로 적는다. **[직접]** 은 이번 500 을 일으킨 고리, **[배경]** 은 그 고리가 장애로 이어지게 한 조건이다. 문장 번호(4-n)는 5절 근거와 짝을 이룬다.

1. **[배경]** 운영 데이터에 존재하지 않는 `Assignment` 9 를 참조하는 배포 행이 있었다. 배포 전 09:52 부터 distribution 41 조회가 같은 누락으로 500 을 냈지만, 대응 기록은 없다.
2. **[배경]** 교무부가 "수업 중 반영"을 요청했고, 배포는 단일 호스트 재기동(무중단 아님) 방식으로 13:40:05 에 시작됐다.
3. **[직접]** v1.4.2 는 재배포 응답에 같은 학급의 배포 이력을 붙이려고, 재배포 뒤에 `listByClass(classId)` 를 호출하도록 바뀌었다. 이 호출은 대상 한 건이 아니라 **학급 전체 배포와 각 과제**를 읽는다.
4. **[직접]** 재기동(13:41:48 ~ 13:42:00) 뒤 첫 학급 1 재배포(13:46:38, distribution 42)에서, 재배포 본처리는 끝났다. 그러나 이어진 `listByClass` 가 `Assignment` 9 를 찾지 못해 예외를 던졌고, 공통 예외 처리기가 이를 500 으로 바꿨다.
5. **[직접]** 같은 학급의 distribution 42 · 43 재배포는 배포 전 18건 모두 200 이었다. 배포 후에는 39건 모두 500 이었고, 다른 학급(2 · 3) 재배포는 계속 200 이었다. 마지막 관측은 14:34:45 다.
6. **[배경]** 재배포(쓰기)와 이력 조회(읽기)가 서로 다른 트랜잭션이다. 그래서 500 을 받은 요청도 재배포는 이미 커밋됐을 수 있다. 교사들은 실패로 보고 같은 요청을 1 ~ 4건/분으로 반복했다.
7. **[미해결]** 같은 쿼리를 쓰는 학급 1 리포트가 13:24:45 까지 성공했다. 그러므로 학급 1 에 결함 행이 생긴 시점은 확정하지 못했다(9절).

## 5. 근거 로그 줄

| 문장 | 파일:줄번호 | 발췌 |
|---|---|---|
| 4-1 | `app.log:194` | `09:52:17.335 ERROR … unhandled exception on /api/distributions/41/redistribute` |
| 4-1 | `app.log:195` | `JpaObjectRetrievalFailureException: Entity …Assignment with identifier value 9 does not exist` |
| 4-2 | `deploy-history.md:14` | `재배포 응답에 같은 학급의 배포 이력을 함께 돌려 달라(교무부 요청, 수업 중 반영 요청)` |
| 4-2 | `deploy-history.md:7` · `:18` | `v1.4.2 \| 2026-09-17 13:40:05 \| 2026-09-17 13:42:31` / `단일 호스트 재기동(무중단 아님)` |
| 4-3 | `deploy-history.md:16` | `` `DistributionController.redistribute` 에서 재배포 후 `DistributionService.listByClass(classId)` 호출 추가 `` |
| 4-3 | `DistributionController.java:40` (코드) | `List<DistributionResponse> history = distributionService.listByClass(redistributed.classId());` |
| 4-4 | `app.log:1240` · `:1264` | `Commencing graceful shutdown` / `Started ItemBankApplication in 4.921 seconds` |
| 4-4 | `app.log:1274` | `13:46:38.107 … redistributed distribution 42 (assignment 4, class 1)` |
| 4-4 | `app.log:1286` · `:1294` | `at …DistributionService.listByClass(DistributionService.java:38)` / `…redistribute(DistributionController.java:40)` |
| 4-4 | `app.log:1304` | `Caused by: org.hibernate.FetchNotFoundException: Entity …Assignment with identifier value 9` |
| 4-4 | `nginx-access.log:17155` | `[17/Sep/2026:13:46:38 +0900] "POST /api/distributions/42/redistribute HTTP/1.1" 500 176` |
| 4-5 | `nginx-access.log:14969` · `:16829` | `13:13:05 "POST /api/distributions/43/redistribute" 200 178` / `13:41:20 … 43 … 200 183` |
| 4-5 | `nginx-access.log:17353` | `13:50:00 "POST /api/distributions/8/redistribute HTTP/1.1" 200 908` |
| 4-5 | `app.log:2887` · `nginx-access.log:20084` | `14:34:45.439 ERROR … unhandled exception on /api/distributions/43/redistribute` |
| 4-6 | `DistributionService.java:49` · `DistributionController.java:12` ~ `15` (코드) | 재배포만 `@Transactional`, 컨트롤러에는 트랜잭션 없음 |
| 4-6 | `nginx-access.log:17365` · `:17366` · `:17376` | `13:50:12` · `13:50:13` · `13:50:19` 에 `POST /api/distributions/43/redistribute` 500 반복 |
| 4-7 | `app.log:1191` | `13:24:45 … built class report for class 1: 3 distributions, 10 submissions` |

집계 근거(42 · 43 재배포 18 / 39건 등)는 3.6 의 C1 ~ C10 에 있다.

## 6. 수정안 (지금 당장)

코드는 고치지 않았다. 아래는 제안이다. 데이터 · 스키마에 손대는 6-2 는 CLAUDE.md 팀 규칙 4 에 따라 승인을 받은 뒤 진행한다.

| # | 대상 | 무엇을 어떻게 |
|---|---|---|
| 6-1 | 배포(설정) | `deploy-history.md:22` 의 롤백 계획대로 `v1.4.1` 이미지로 재기동한다(약 3분). 근거: v1.4.1 경로에서 42 · 43 재배포 18건이 모두 성공했다(C1) |
| 6-2 | 운영 데이터(승인 필요) | DB 담당자가 읽기 전용으로 `assignment_id` 가 `assignment` 에 없는 `distribution` 행을 찾고(3.2 의 `LEFT JOIN` 쿼리), 원 과제를 복구할지 행을 정리할지 교무부와 정한다. 같은 조회로 42 · 43 의 `distributed_at` · `redistributed` 를 확인해, 500 을 받고도 반영된 재배포를 교사에게 알린다 |
| 6-3 | `modern/api/.../DistributionService.java` · `DistributionController.java:38` ~ `41` | 재배포와 이력 조회를 서비스의 한 `@Transactional` 메서드(예: `redistributeWithHistory`)로 합치고, 컨트롤러는 그 메서드 하나만 호출한다. 이력 조회가 실패하면 재배포도 롤백되어 "500 인데 반영됨" 상태가 없어진다. 새 public 메서드이므로 `@ExtendWith(MockitoExtension.class)` 서비스 테스트와 `@WebMvcTest` 테스트를 함께 추가한다 |
| 6-4 | 같은 파일, 재배포 후 재반영 시 | 6-2 로 데이터를 정리하기 전에 v1.4.2 를 다시 배포하지 않는다. 다시 배포한다면 학급 1 재배포를 스테이징의 운영 데이터 사본으로 먼저 확인한다 |

## 7. 재발 방지 (구조적 장치)

- **참조 무결성을 DB 가 보장하게 한다**: 저장소 스키마에는 `fk_distribution_assignment` 가 있다(`01-schema.sql:101`). 운영 스키마가 저장소와 같은지 비교하는 점검을 배포 파이프라인에 넣는다. 외래 키를 추가하는 경우는 스키마 변경이므로 승인을 받는다.
- **결함 데이터 픽스처로 테스트한다**: "단건 조회 → 목록 조회"처럼 읽는 범위가 넓어지는 변경은 고아 참조가 섞인 픽스처로 `@DataJpaTest` 를 돌리도록 리뷰 체크리스트(`templates/approval-checklist.md`)에 항목을 추가한다. v1.4.2 는 시드 데이터로만 확인했다(`deploy-history.md:17`).
- **한 요청이 여러 서비스를 쓰는 경우의 트랜잭션 규칙**: CLAUDE.md 의 컨벤션에 다음을 추가한다. "컨트롤러가 쓰기 서비스와 읽기 서비스를 차례로 호출하면, 서비스 한 메서드 · 한 트랜잭션으로 묶는다."
- **배포 방식**: 수업 시간에는 배포하지 않는다. 대기 호스트 `api-2` 를 이용한 무중단 전환과, 일부 트래픽만 먼저 받는 카나리 배포를 도입한다. 배포 직후 학급별 이력 조회(`listByClass` 경로)를 도는 자동 스모크를 실행한다.
- **추적성**: 배포한 버전마다 git 태그를 의무로 단다. 이번에는 태그가 없어 배포 코드와 저장소 코드를 대조하지 못했다(C3).
- **기존 ERROR 분류 절차**: 09:52 부터 있던 distribution 41 의 500 이 4시간 가까이 방치됐다. 새로 나타난 ERROR 시그니처는 당일 담당자를 정해 분류하는 절차를 둔다.

## 8. 모니터링 항목

경보 시각은 2절 타임라인과 1.2 · 3.6 의 집계를 그대로 적용해 계산했다. 새로 실행한 계산은 없다.

| 지표 | 임계값 | 이번 타임라인에서 울렸을 시각 |
|---|---|---|
| 새 ERROR 시그니처(예외 클래스 + 첫 앱 스택 프레임) 첫 발생 | 1건 → 즉시 알림. 배포 후 60분 동안은 호출 등급으로 올림 | **09:52:17**(`Assignment` 누락 + `loadOrThrow`, 배포 전 경고) / **13:46:38**(`listByClass` 경유, 배포 후 4분 7초) |
| 엔드포인트별 5xx 비율(`POST …/redistribute`) | 10분 창에서 요청 3건 이상 + 5xx 비율 50% 이상 | **13:47:08**. 창 안의 재배포 5건 중 3건 500(13:41:20 · 13:42:10 은 200) |
| 전체 5xx 건수(배포 중 502 제외) | 10분 이동합 5건 이상. 배포 전 최대는 3건(09:52, 11:17 ~ 11:18) | **13:50:13**(13:46:38 부터 다섯 번째 500) |
| 같은 경로 POST 의 5xx 반복 | 같은 경로가 1분 안에 3회 이상 5xx | **13:50:19**(43 이 13:50:12 · :13 · :19) |
| 배포 중 upstream 실패(502) 지속 시간 | 15초 초과(예상 중단은 10초 안팎, `deploy-history.md:18`) | 울리지 않음. 실제 13:41:51 ~ 13:41:59, 8초 |
| 배포 버전별 5xx 비율 비교 | 새 버전의 5xx 비율이 직전 버전 평균(0.025건/분)의 10배를 30분 넘게 유지 | 배포 후 평균 0.684건/분(1.2)이다. 이 조건은 **14:16 무렵** 충족된다(13:46 기준 30분) |

## 9. 확인하지 못한 것

**판단하지 못한 부분**

- **장애 종료 시각**: 로그가 14:39:58 에서 끝난다. 14:35 이후 5xx 0건은 오류 간격(최대 6분)보다 짧아 회복으로 볼 수 없다.
- **결함 행의 정체와 생성 시점**: `Assignment` 9 를 참조하는 학급 1 의 행이 무엇이고 언제 생겼는지 모른다. 학급 1 리포트가 13:24:45 까지 성공한 것과의 모순도 풀지 못했다(운영 DB 조회 필요).
- **500 뒤에도 재배포가 반영됐는지**: 39건이 실제로 반영됐는지, 즉 사용자 영향의 크기를 모른다.
- **배포 코드 일치 여부**: 배포된 v1.4.2 바이너리가 저장소 코드와 같은지 확인하지 못했다(태그 없음).
- **H3 · H4 일부 조건**: 이전 버전도 같은 프로파일 · JDBC URL 이었는지, nginx upstream 구성과 `api-2` 상태는 어땠는지 확인하지 못했다.

**다음을 위해 남겨야 할 로그**

| 로그 | 남길 내용 | 이번에 있었다면 |
|---|---|---|
| nginx access | `$request_time`, `$upstream_addr`, `$upstream_response_time`, 요청 ID 헤더 | 느린 요청 여부와 처리 호스트(H4)를 바로 판단 |
| app 요청 로그 | MDC 요청 ID, 처리 시간. 오류 로그에 도메인 키(distribution id, class id) | nginx · app 짝 맞추기를 시각 추정 없이 수행. 41 의 학급 확인 |
| app 기동 로그 | 활성 프로파일, JDBC URL(비밀번호 제외), 앱 버전 · git 커밋 | H3 반증 조건 1 · 4 와 배포 코드 일치를 바로 확인 |
| DB | MariaDB 에러 로그, 슬로우 쿼리 로그, 데이터 변경 감사 로그(`distribution` · `assignment`) | 결함 행이 언제 생겼는지 확인 |
| 보관 정책 | 직전 버전 기동 로그 포함 최소 7일 보관. 장애 수집 시 앞뒤 여유(1.3) 포함 | v1.4.1 기동 로그와 14:40 이후 로그 확보 |
| 보안 | `deploy-history.md:20` 처럼 배포 기록에 토큰을 남기지 않는다. 오류 · INFO 로그에 학생 식별자(`STU-…`)를 넣지 않는다(`DistributionService.java:56` 의 `reason` 이 그대로 기록됨) | 이번 문서 작성 시 가림 처리가 필요 없음 |

---

마스킹 적용: IP 2건(`10.20.x.x` · 역할 별칭 API-2) · 이메일 1건 · 학생 식별자 4건(별칭 2명) · 토큰 0건 · 비밀번호 · 접속 문자열 1건
