---
name: convention-check
description: 변경된 코드(또는 지정한 경로)가 이 저장소의 코딩 컨벤션 · 금지 사항을 지키는지 8개 항목으로 점검하고, 위반을 파일:줄번호와 함께 표로 보고한다. "컨벤션 점검", "커밋 전 점검", "리뷰 전에 확인", "규칙 어긴 곳 있어?" 같은 요청이나 커밋 · PR 직전에 쓴다.
argument-hint: "[경로 ...] (생략하면 git 변경 파일 전체)"
allowed-tools:
  - Read
  - Grep
  - Glob
  - Bash(git diff *)
  - Bash(git status *)
---

# 코딩 컨벤션 점검

- 코드를 직접 고치지 않는다. 판정과 수정 방향만 보고한다.
- 근거 라인을 댈 수 없는 지적은 하지 않고 "확인 필요"로 남긴다.

## 1. 점검 대상 정하기

1. 인자로 경로가 오면 그 경로만 본다(디렉터리면 `Glob` 으로 파일을 펼친다).
2. 인자가 없으면 `git status --porcelain` 과 `git diff --name-only HEAD` 로 변경 파일을 모은다. `??`(아직 add 하지 않은 새 파일)도 포함하고, 삭제된 파일은 뺀다.
3. 대상이 없으면 "점검 대상 없음"이라고만 답하고 끝낸다.

## 2. 점검 항목 (예 = 지킴)

| # | 항목 | 예로 판정하는 기준 |
|---|---|---|
| 1 | 컨트롤러 계층 분리 | `*Controller` 의 생성자 · 필드에 `*Repository` 타입이 없고, SQL 문자열 · `JdbcTemplate` · `EntityManager` 가 없다 |
| 2 | 컨트롤러 반환 · 예외 | 컨트롤러 메서드가 record `*Response` 를 반환하고 `@Entity` 를 반환하지 않으며, 메서드 안에 `try`/`catch` 가 없다 |
| 3 | 도메인 경계 | `*Service` 가 다른 도메인 패키지(`com.example.<다른 도메인>`)의 `*Repository` 를 주입받지 않는다 |
| 4 | 빈 catch 없음 | 본문이 비어 있는 `catch` 블록이 없다 |
| 5 | 로그 수단 | `System.out` · `System.err` · `printStackTrace()` 가 없다 |
| 6 | 시각 주입 | 인자 없는 `LocalDateTime.now()` 가 없다 |
| 7 | 상태 상수 | 상태 비교 · 조회에 `"A"` · `"D"` · `"R"` 문자열을 직접 쓰지 않고 `ItemStatus` 상수를 쓴다 |
| 8 | 금지 경로 | `legacy/` · `db/` · `modern/api/src/main/resources/application.yml` · `vendor-prs/` · `incident-logs/` · `build.gradle` 의 `dependencies` · `package.json` 에 변경이 없다. 변경이 있으면 허락 여부를 코드로 알 수 없으므로 "확인 필요"로 판정한다 |

- 1~7 은 `modern/api` 의 `src/main` Java 파일에만 적용한다. 테스트 코드와 다른 파일은 8 만 본다.
- 인자 없이 돌 때는 변경된 줄(`git diff` 의 `+` 줄, 새 파일은 전체)에서 나온 위반만 보고한다.
- `Grep` 으로 후보를 찾은 뒤 반드시 `Read` 로 앞뒤 문맥을 확인하고 판정한다(주석 · 문자열 안의 일치는 위반이 아니다).

## 3. 출력 형식 (순서 고정)

**판정 요약**

- 점검 파일 N개 · 위반 N건 · 확인 필요 N건
- 항목별: `1 예 / 2 아니오 / … / 8 확인 필요` 한 줄

**위반 목록**

| 파일:줄번호 | 어긴 규칙 | 수정 방향 |
|---|---|---|
| `modern/api/src/main/java/com/example/item/FooController.java:21` | 1 컨트롤러 계층 분리 — `ItemRepository` 필드 | `ItemService` 를 주입받아 호출한다 |

- 위반이 없으면 표 대신 "위반 없음"이라고 적는다.
- "확인 필요" 항목은 같은 표에 넣고, 어긴 규칙 칸 앞에 `확인 필요:` 를 붙이고 무엇을 사람에게 물어야 하는지 수정 방향 칸에 적는다.
- 표 밖에 다른 제안(리팩터링 · 스타일 의견)을 덧붙이지 않는다.
