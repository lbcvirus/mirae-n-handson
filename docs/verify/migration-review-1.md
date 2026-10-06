# 이관 검증 보고서 1 — `GET /api/items/search`

- 검증 대상: `git diff upstream/main...HEAD` (upstream/main = `1bb4f2f`, HEAD = `b3fb014`, 커밋 3개)
- 요청 범위(한 문장): 레거시 엔드포인트 1개(`/search.php`)를 동작을 바꾸지 않고 modern 으로 이관.
- 검증 방식: 변경분만 읽고 `templates/approval-checklist.md` 네 기준으로 대조. 코드는 수정하지 않았다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test --rerun-tasks` → 44건 통과 · 실패 0 · skip 0. `cd characterization && npm test`(레거시 8081) → 37건 통과. `TARGET_BASE_URL=http://localhost:8080 npm test`(새 API) → 36건 통과 · 1건 skip. skip 은 `tests/example-units.test.js:21` 의 `it.skipIf(legacyOnly)` 로, 기존 샘플(단원 목록)이 의도한 skip 이고 item-bank 케이스가 아니다. 이번 변경에서 skip · `@Disabled` 로 바꾼 테스트는 없다. 단, #1 은 테스트가 잡지 못한 차이다. |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. JPQL 은 고정 조각만 이어 붙이고 입력은 전부 파라미터 바인딩(`ItemSearchRepositoryImpl.java:84-103`). 비밀값 · 데이터 삭제 코드 없음. |
| 요청 범위 이탈 없음 | 미충족 | 요청은 "엔드포인트 1개 이관"인데 변경 파일 28개 중 11개(#2)가 이 요청으로 설명되지 않는다. 코드 커밋 `b3fb014` 단독은 17개 파일 모두 이관으로 설명된다. |
| 컨벤션 준수 | 충족 | `CLAUDE.md` 대조 결과 위반 없음(아래 "컨벤션 대조" 참고). |

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemController.java:29-31` | 대괄호를 인코딩하지 않은 `level[]=3` 요청이 레거시와 다르다. `curl -g 'localhost:8081/search.php?level[]=3&tag[]=%EA%B3%84%EC%82%B0'` → 200, `curl -g 'localhost:8080/api/items/search?level[]=3&tag[]=%EA%B3%84%EC%82%B0'` → 400(Tomcat 이 원문 `[` `]` 를 거부). 컨트롤러 주석(`ItemController.java:26-27`)과 `LegacyQueryParams` 는 `level[]` 표기를 받는다고 쓰지만, 원문 대괄호는 `LegacyQueryParams` 에 닿기 전에 막힌다. characterization 은 `URLSearchParams` 가 `[]` 를 `%5B%5D` 로 인코딩해서 보내므로 이 차이를 잡지 못했다(`characterization/lib/target.mjs:46-52`). 레거시 PHP 화면은 원문 대괄호를 그대로 받는다. | Tomcat 이 원문 `[` `]` 를 받게 할지(예: `server.tomcat.relaxed-query-chars`, `application.yml` 이라 사람 승인 필요) 또는 원문 대괄호는 지원하지 않기로 하고 주석을 고칠지 사람이 정한다. 어느 쪽이든 원문 대괄호 요청 케이스 테스트를 추가한다. |
| 2 | 경고 | `docs/assignment/ARCHITECTURE.md:1` | 요청(search 이관)과 무관한 파일이 범위에 들어 있다. `docs/assignment/*` 3개(다른 모듈 문서), `.claude/skills/convention-check/SKILL.md`, `.claude/skills/document-module/SKILL.md`, `CLAUDE.md`, `docs/item-bank/*` 4개. 모두 커밋 `3acc0d1`(docs · Skill)에서 왔고, upstream/main 이 그 앞에 있어서 범위에 포함됐다. | 이 문서 · Skill 변경을 별도 PR 로 나누거나, 요청 범위에 포함된다고 사람이 확인한다. 나눈다면 `a4befce`(characterization)와 `b3fb014`(코드)만 다시 검증한다. |
| 3 | 제안 | `characterization/lib/target.mjs:46-52` | `buildUrl` 이 `URLSearchParams` 로 파라미터를 인코딩해서 보내므로 쿼리스트링 원문 규칙(`LegacyQueryParams` 가 흉내 내는 PHP `$_GET` 파싱)은 단위 테스트(`LegacyQueryParamsTest`)로만 보장된다. 레거시 대비 비교로는 확인되지 않는다. | 원문 쿼리스트링을 그대로 보내는 비교 케이스가 필요하면 사람이 승인한 뒤 추가한다. 이 세션은 `characterization/` 을 고치지 않았다. |

## 컨벤션 대조

- 컨트롤러에 `*Repository` · SQL · `EntityManager` 없음(`ItemController.java:15-32`). `EntityManager` 는 리포지토리 구현(`ItemSearchRepositoryImpl.java:23-24`)에만 있다.
- 서비스는 같은 도메인의 `ItemRepository` 만 주입(`ItemSearchService.java:26-28`). 컨트롤러 반환은 record DTO(`ItemSearchResponse`). 컨트롤러 안에 `try`/`catch` 없음.
- diff 에서 `System.out` · `printStackTrace` · 빈 `catch` · 와일드카드 import · 인자 없는 `LocalDateTime.now()` · `"A"`/`"D"`/`"R"` 리터럴을 검색했고 0건이다. 상태는 `ItemStatus.ACTIVE` 로 비교(`ItemSearchRepositoryImpl.java:77`). 로그(`ItemSearchService.java:40-47`)에 식별자 · 이메일 · 토큰 없음, 레벨 `debug`.
- 숫자 리터럴은 이름 붙인 상수(`PAGE_SIZE`, `MAX_PAGE`, `HEX_RADIX` 등)로 뺐다.
- 테스트: 새 테스트 클래스 3개(`ItemSearchConditionTest`, `ItemSearchServiceTest`, `LegacyQueryParamsTest`)에 `@ActiveProfiles("test")` 와 한국어 `@DisplayName` 이 있다. 새 public 메서드 `ItemSearchService.search` 와 엔드포인트에 테스트가 있다. 컨트롤러 테스트는 `@WebMvcTest` + `@MockBean`, 리포지토리 테스트는 `ItemFixtures` 를 쓴다.
- DB 스키마 · `application.yml` · 의존성 변경 없음. 레거시 아래 파일 변경 없음. 뷰 `v_item_public` 대신 엔티티에 같은 조건을 거는 이유는 `ItemSearchRepositoryImpl.java:15-17` 주석에 적혀 있다.

## 확인 필요

- **의심 동작(레거시 그대로 옮긴 것, `CLAUDE.md` §5.4):**
  - 난이도를 비우면 `level < 5` 라서 난이도 5 문항이 기본 검색에서 빠진다. 레거시 주석은 "1~5 포함"이라고 쓴다(`legacy/item-bank-php/search.php:213-216`, 옮긴 곳 `ItemSearchCondition.java:42-46`).
  - `unit` 정렬은 방향과 관계없이 2차 정렬이 난이도 내림차순이다(`search.php:293-299`).
  - 키워드의 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작한다(`search.php:94-98`).
- 새 API 서버(8080)가 현재 `HEAD` 코드로 떠 있는지는 확인하지 못했다. 새 API 대상 characterization 결과는 지금 떠 있는 서버 기준이다. 판정 전에 사람이 `bootRun` 재기동 뒤 한 번 더 실행하면 확실하다.
- 뷰 `v_item_public` 의 `WHERE` 절을 끝까지 읽지는 않았다. 엔티티 조건(공개 상태 + 단원 inner join)과의 동일성은 characterization 통과로만 확인했다.
- 반려 사유는 #2(범위) 뿐이고 #1 은 경고다. 승인 여부는 사람이 #1 의 수용 · 기각과 #2 의 분리 방식을 정한 뒤 다시 판정한다.
