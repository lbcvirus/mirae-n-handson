# 이관 검증 보고서 2 — `GET /api/items/search`

- 검증 대상: `git diff upstream/main...HEAD` (upstream/main = `1bb4f2f`, HEAD = `4b6683f`, 커밋 4개, 변경 파일 29개)
- 요청 범위(한 문장): 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관.
- 검증 방식: 변경분만 읽고 `templates/approval-checklist.md` 네 기준으로 대조. 코드는 수정하지 않았다.
- 1차 보고서(`migration-review-1.md`)와의 차이: 1차 이후 커밋은 `4b6683f` 하나이고, 이 커밋은 1차 보고서 파일만 추가했다. 코드 · 테스트 · 설정은 `b3fb014` 와 같다. 1차의 이슈 #1 · #2 는 해소되지 않았다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test --rerun-tasks` → 44건 통과 · 실패 0 · 오류 0 · skip 0. `cd characterization && npm test`(레거시 8081) → 37건 통과 · 실패 0. `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) → 36건 통과 · 실패 0 · 1건 skip(기존 샘플 단원 목록의 `it.skipIf(legacyOnly)`, item-bank 케이스 아님). 새 API 서버(8080)는 12:07:59 기동, 소스 파일 마지막 수정은 12:00:22 라서 현재 코드 기준으로 보인다. 이번 변경에서 skip · `@Disabled` 로 바꾼 테스트는 없다. 다만 #1 은 이 테스트들이 잡지 못한 차이다. |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. JPQL 은 고정 조각만 이어 붙이고 입력은 모두 파라미터 바인딩(`ItemSearchRepositoryImpl.java:70-100`). 정렬 조각은 enum 상수(`ItemSearchSort.java:9-21`)라 입력이 섞이지 않는다. 비밀값 · 데이터 삭제 코드 없음. |
| 요청 범위 이탈 없음 | 미충족 | 요청은 "엔드포인트 1개 이관"인데 변경 파일 29개 중 7개는 이 요청으로 설명되지 않고(#2), 4개는 확인이 필요하다. 이관 직접 관련은 `modern/api` 16개(main 11 · test 5), `characterization/` 2개(동작 보존 베이스라인)다. |
| 컨벤션 준수 | 충족 | `CLAUDE.md` 대조 결과 위반 없음(아래 "컨벤션 대조"). 위반 이슈 없음. |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemController.java:29-32` | 대괄호를 인코딩하지 않은 `level[]=3` 요청이 레거시와 다르다. 이번에 다시 실행: `curl -g 'localhost:8081/search.php?level[]=3'` → 200, `curl -g 'localhost:8080/api/items/search?level[]=3'` → 400(Tomcat 이 원문 `[` `]` 를 거부). 컨트롤러 주석(`ItemController.java:26-27`)과 `LegacyQueryParams.java:13` 은 `level[]` 표기를 받는다고 쓰지만 원문 대괄호는 `LegacyQueryParams` 에 닿기 전에 막힌다. `ItemControllerTest` 의 배열 표기 테스트는 MockMvc 로 돌아 Tomcat 의 쿼리 문자 검사를 거치지 않고, characterization 은 `URLSearchParams` 가 `[]` 를 `%5B%5D` 로 인코딩해서 보내므로(`characterization/lib/target.mjs:46-52`) 둘 다 이 차이를 잡지 못한다. | 사람이 (a) Tomcat 이 원문 대괄호를 받게 할지(예: `server.tomcat.relaxed-query-chars`. `application.yml` 변경이라 승인 필요) 또는 (b) 원문 대괄호는 지원하지 않기로 하고 주석 · 문서를 고칠지 정한다. 어느 쪽이든 원문 대괄호 요청을 재현하는 테스트를 추가한다. |
| 2 | 경고 | `docs/assignment/ARCHITECTURE.md:1` | 요청(search 이관)과 무관한 파일이 범위에 들어 있다: `docs/assignment/*` 3개(다른 모듈 문서), `.claude/skills/convention-check/SKILL.md`, `.claude/skills/document-module/SKILL.md`, `CLAUDE.md`, `docs/verify/migration-review-1.md`(검증 기록). upstream/main 이 커밋 `3acc0d1`(docs · Skill) 앞에 있어서 범위에 포함됐다. 1차 이후 `4b6683f` 가 검증 기록 1개를 더했을 뿐 분리되지 않았다. | 문서 · Skill · 검증 기록 변경을 별도 PR 로 나누거나, 요청 범위에 포함된다고 사람이 확인한다. 나눈다면 `a4befce`(characterization)와 `b3fb014`(코드)만 다시 검증한다. |
| 3 | 제안 | `characterization/lib/target.mjs:46-52` | `buildUrl` 이 `URLSearchParams` 로 인코딩해서 보내므로 쿼리스트링 원문 규칙(PHP `$_GET` 파싱을 흉내 낸 `LegacyQueryParams`)은 단위 테스트(`LegacyQueryParamsTest`)로만 보장되고 레거시 대비 비교로는 확인되지 않는다. #1 이 새어 나간 원인이기도 하다. | 원문 쿼리스트링을 그대로 보내는 비교 케이스가 필요하면 사람이 승인한 뒤 추가한다. 이 세션은 `characterization/` 을 고치지 않았다. |
| 4 | 제안 | `modern/api/src/main/java/com/example/item/ItemSearchCondition.java:45` | 정규식 안의 `\\f`(폼 피드)가 PHP `(int)` 의 앞 공백 목록과 같은지는 이 세션이 PHP 로 대조하지 못했다. 단위 테스트에도 폼 피드 케이스가 보이지 않는다. 판정에는 영향 없다. | 경계 입력(`%0C3` 등)을 레거시와 새 API 에 보내 비교하는 케이스를 사람이 정해 추가한다. |

## 컨벤션 대조

- 컨트롤러에 `*Repository` · SQL · `EntityManager` · `try`/`catch` 없음(`ItemController.java:15-32`). `EntityManager` 는 리포지토리 구현(`ItemSearchRepositoryImpl.java:23-24`)에만 있다.
- 서비스는 같은 도메인의 `ItemRepository` 만 주입(`ItemSearchService.java:23-27`). 컨트롤러 반환은 record DTO(`ItemSearchResponse`).
- diff 의 추가 줄에서 `System.out` · `printStackTrace` · `catch` · 와일드카드 import · 인자 없는 `LocalDateTime.now()` · `"A"`/`"D"`/`"R"` 리터럴을 검색했고 0건이다. 상태는 `ItemStatus.ACTIVE` 로 비교(`ItemSearchRepositoryImpl.java:73`). 로그(`ItemSearchService.java:36, 46`)는 `debug` 이고 식별자 · 이메일 · 토큰이 없다.
- 숫자 리터럴은 이름 붙인 상수(`PAGE_SIZE`, `MAX_PAGE`, `HEX_RADIX`, `PERCENT_ESCAPE_LENGTH` 등)로 뺐다.
- 테스트: 새 테스트 클래스 3개(`ItemSearchConditionTest`, `ItemSearchServiceTest`, `LegacyQueryParamsTest`)에 `@ActiveProfiles("test")` 가 있다. 새 public 메서드 `ItemSearchService.search` 와 새 엔드포인트에 테스트가 있다(`ItemControllerTest.java:88-121` 부근, `ItemRepositoryTest` 의 search 케이스).
- DB 스키마 · `application.yml` · 의존성(`build.gradle`) 변경 없음. `legacy/` 아래 변경 없음. 뷰 `v_item_public` 대신 엔티티에 같은 조건을 거는 이유는 `ItemSearchRepositoryImpl.java:15-17` 주석에 적혀 있다.

## 확인 필요

- **의심 동작(레거시 그대로 옮긴 것, `CLAUDE.md` §5.4):**
  - 난이도를 비우면 `level < 5` 라서 난이도 5 문항이 기본 검색에서 빠진다. 레거시 주석은 "1~5 포함"이라고 쓴다(`legacy/item-bank-php/search.php:213-216`, 옮긴 곳 `ItemSearchCondition.java:34-38`).
  - `unit` 정렬은 방향과 관계없이 2차 정렬이 난이도 내림차순이다(`search.php:293-299`, 옮긴 곳 `ItemSearchSort.java:13-15`).
  - 키워드의 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작한다(`search.php:94-98`, 옮긴 곳 `ItemSearchRepositoryImpl.java:75-79`).
- `docs/item-bank/*` 4개(`ARCHITECTURE.md`, `BUSINESS-RULES.md`, `CROSS-CHECK.md`, `ERD.md`)는 이관의 근거 문서라 범위 안으로 볼 수도 있지만, 요청 문장만으로는 단정하지 못한다. 사람이 범위에 넣을지 정한다.
- 뷰 `v_item_public` 의 `WHERE` 절은 이번에도 끝까지 읽지 않았다. 엔티티 조건(공개 상태 + 단원 inner join)과의 동일성은 characterization 통과로만 확인했다.
- 반려 사유는 "요청 범위 이탈 없음" 미충족(#2)이다. #1 은 경고라 단독으로는 반려 사유가 아니다. 사람이 #1 의 수용 · 기각과 #2 의 분리 방식을 정한 뒤 다시 판정한다.
