# 이관 검증 보고서 3 — `GET /api/items/search`

- 검증 대상: `git diff upstream/main...HEAD -- modern characterization` (upstream/main = `1bb4f2f`, HEAD = `d304803`, 해당 커밋 `a4befce` · `b3fb014`, 변경 파일 18개: `modern/api` 16 · `characterization` 2)
- 요청 범위(한 문장): 레거시 엔드포인트 1개를 동작을 바꾸지 않고 modern 으로 이관. (`legacy/item-bank-php/search.php` → `GET /api/items/search`)
- 검증 방식: `.claude/skills/verify/SKILL.md` 절차. 3단계(체크리스트 대조)는 reviewer Sub-agent, 4단계(테스트 실행)는 tester Sub-agent 에 위임했고, 메인 세션이 두 보고를 받아 최종 판정했다. 판정 기준은 `templates/approval-checklist.md` v1 · 2회차.
- 2차 보고서(`migration-review-2.md`)와의 차이: 이번에는 범위를 pathspec `-- modern characterization` 으로 좁혔다. 그래서 2차의 범위 이슈(docs · Skill 파일 혼입)는 판정에서 빠졌다. 이관 코드는 `b3fb014` 그대로다.

판정: 반려

| 기준 | 결과 | 근거 |
|---|---|---|
| 테스트 통과 | 충족 | `cd modern/api && ./gradlew test`: 46건 통과 · 실패 0 · skip 0. 여기에는 tester 가 이번에 추가한 2건(아래 "tester 가 추가한 테스트")이 들어 있다. `cd characterization && npm test`(레거시 8081 대상): 37건 통과 · 실패 0. `cd characterization && TARGET_BASE_URL=http://localhost:8080 npm test`(새 API 대상): 36건 통과 · 실패 0 · skip 1(`example-units.test.js` 의 단원 목록 예시. `it.skipIf(legacyOnly)` 로 설계된 skip 이고 item-bank 케이스가 아님) · obsolete 스냅샷 0. 새 API 서버는 사람이 `bootRun` 으로 띄웠다. 새 public 메서드와 엔드포인트마다 테스트가 있고, skip · `@Disabled` 로 바꾼 테스트는 없다. |
| 치명 이슈 0건 | 충족 | 치명 이슈 없음. JPQL 에는 고정 조각만 이어 붙이고(`ItemSearchRepositoryImpl.java:21, 29, 37-39, 77-98`), 정렬 조각은 enum 허용 목록(`ItemSearchSort.java:9-21`)에서만 고른다. 입력값은 모두 `:name` 으로 바인딩한다. LIKE 검색어는 `:keyword` 바인딩 값이다(`:77-78`). `createNativeQuery` · `JdbcTemplate` · 비밀값 리터럴 · 데이터 삭제 코드는 0건이다. |
| 요청 범위 이탈 없음 | **미충족** | 파일 18개는 모두 search 이관과 동작 보존 베이스라인으로 설명된다. 하지만 요청 문장의 "동작을 바꾸지 않고"와 어긋나는 응답 상태 차이가 재현됐다(#1). 요청에 없는 동작 변경이므로 이 기준을 미충족으로 본다. 커밋 메시지 "회고 반영"은 "확인 필요" 1번에 따로 적었다. |
| 컨벤션 준수 | 충족 | `CLAUDE.md` 와 `convention-check` 8개 항목 모두 예. 120자 규칙은 메인 세션이 코드포인트 기준으로 다시 세어 초과 0줄을 확인했다. 체크리스트 4절의 catch · 자원 · static 컬렉션 · Executor 항목은 해당 없음. |

### 판정 근거 보충

- #1 은 체크리스트 3절의 특정 항목에 글자 그대로 들어맞지는 않는다. 그래도 요청 문장 자체가 "동작을 바꾸지 않고"이므로, 레거시 200 → 모던 400 차이는 요청 밖 변경으로 판단했다. 이것은 메인 세션의 판단이다.
- 반려 사유는 #1 하나다. 사람이 아래 (a) · (b) 중 하나를 정하고 반영하면 다시 판정한다.

## 이슈 목록

| # | 심각도 | 파일:줄번호 | 근거 | 수정 방향 |
|---|---|---|---|---|
| 1 | 경고 | `modern/api/src/main/java/com/example/item/ItemController.java:29-32` | 인코딩하지 않은 대괄호 요청이 레거시와 다르다. 이번 검증에서 메인 세션이 다시 재현했다: `curl -g 'localhost:8081/search.php?level[]=3'` → 200, `curl -g 'localhost:8080/api/items/search?level[]=3'` → 400, `curl 'localhost:8080/api/items/search?level%5B%5D=3'` → 200. Tomcat 이 원문 `[` `]` 를 쿼리 문자 검사 단계에서 거부한다. `application.yml` 에 `server.tomcat.relaxed-query-chars` 가 없고 `TomcatConnectorCustomizer` · `WebServerFactoryCustomizer` 도 없다(reviewer `git grep` 0건). 원인이 같으므로 `\|` `{` `}` `^` `"` `<` `>` `\` 가 쿼리에 그대로 있어도 같은 차이가 날 것으로 보인다(재현하지 않음). 주석(`ItemController.java:26-27`, `LegacyQueryParams.java:13`)은 `level[]` 을 받는다고 쓴다. `ItemControllerTest` 는 MockMvc 라 Tomcat 을 거치지 않고, characterization 은 `URLSearchParams` 로 인코딩해서 보내므로 둘 다 이 차이를 잡지 못한다. | 사람이 정한다. (a) 원문 대괄호를 허용한다: `application.yml` 에 `server.tomcat.relaxed-query-chars` 를 추가한다. 설정 파일 변경이라 승인이 필요하다. (b) 지원하지 않기로 하고 요청 · 주석 · `docs/item-bank/BUSINESS-RULES.md` 이관 회고에 동작 차이로 적는다. 어느 쪽이든 원문 대괄호 요청을 재현하는 테스트(`@SpringBootTest(webEnvironment = RANDOM_PORT)` 등 실제 Tomcat 경유)를 추가한다. |
| 2 | 제안 | `modern/api/src/main/java/com/example/item/LegacyQueryParams.java:96-98` | `decode` 가 잘못된 UTF-8 바이트(예: `q=%FF`)를 `out.toString(UTF_8)` 에서 U+FFFD 로 바꾼다. PHP 는 원래 바이트를 그대로 바인딩한다. 결과가 같은지는 확인하지 않았다. | 경계 입력 비교 케이스를 추가할지 사람이 정한다. |
| 3 | 제안 | `characterization/tests/item-bank.test.js:115-117` | 배열 케이스를 `URLSearchParams` 로 인코딩해서 보내므로 #1 같은 원문 쿼리스트링 차이를 동작 보존 테스트로 잡지 못한다. `level=1e3` 같은 지수 표기 케이스도 없다. | characterization 수정은 사람의 승인을 받은 뒤 한다. 기존 베이스라인 테스트와 스냅샷은 고치지 않는다. |

## tester 가 추가한 테스트 (작업 트리, 커밋하지 않음)

새 로직마다 테스트는 있었다. 다만 정렬 일부가 `resolve()` 단위 테스트로만 확인되고 실제 쿼리로는 돌지 않았다. 그래서 tester 가 아래를 추가했다.

- `modern/api/src/test/java/com/example/item/ItemRepositoryTest.java`
  - `searchSortsByCreatedAt`: `sort=created`(기본 · desc · asc)의 순서를 확인하고, 등록 시각이 같으면 id ASC 인지도 본다.
  - `searchSortsByIdLevelTitleAndMatchesStem`: `id desc`, `level asc`, `title desc` 정렬과 지문(stem) 키워드 일치를 확인한다.
- `modern/api/src/test/java/com/example/item/ItemFixtures.java`: 등록 시각을 받는 `item(...)` 오버로드를 추가했다. 기존 6인자 메서드는 이 오버로드에 `SEED_TIME` 을 넘기므로 동작이 같다.

두 파일은 HEAD 에 커밋되지 않았다. 만든 쪽이 검토한 뒤 커밋할지 정한다.

## reviewer · tester 보고 대조

엇갈린 지점은 없다.

- reviewer 는 "새 메서드 · 엔드포인트마다 테스트 1개 이상 → 예"라고 봤다.
- tester 는 "정렬 일부가 실제 쿼리로 검증되지 않음"을 찾아 테스트를 추가했다.
- 앞의 것은 메서드 단위의 존재 여부이고, 뒤의 것은 분기 단위의 커버리지다. 서로 모순되지 않는다.

## 동작 보존 대조 (reviewer, `legacy/item-bank-php/search.php` 직접 대조)

- 공개 뷰 `v_item_public`(`db/mariadb/init/01-schema.sql:52-70`)는 `status='A'` + 단원 inner join 이고, 태그는 `t.id` 순이다. `ItemSearchRepositoryImpl.java:21, 57, 73-74` 와 같다.
- PHP 7.4(`legacy/item-bank-php/Dockerfile:1`)이므로 `(int)"1e3"` = 1000 이다. `ItemSearchCondition.phpIntval` 과 같다.
- trim 문자, 페이지 정규식(끝 `\n` 허용), 정렬 매핑 11가지, 반복 파라미터 덮어쓰기, 배열 첫 값, `level < 5` 기본값이 모두 일치한다. 레거시 버그를 몰래 고친 곳은 없다.
- characterization 테스트 코드와 스냅샷은 베이스라인 커밋 `a4befce` 뒤로 바뀐 적이 없다.

## 의심 동작 (레거시 그대로 옮긴 것, `CLAUDE.md` §5.4)

1. 난이도를 비우면 `level < 5` 가 되어 난이도 5 문항이 빠진다. 레거시 주석에는 "1~5 모두 포함"이라고 적혀 있다(`search.php:213-216` → `ItemSearchCondition.java:34-38`, `ItemSearchRepositoryImpl.java:86-88`).
2. `unit` 정렬은 방향과 관계없이 2차 정렬이 항상 `level DESC` 다(`search.php:293-299` → `ItemSearchSort.java:13-15`).
3. 키워드의 `%` · `_` 를 이스케이프하지 않아 와일드카드로 동작한다(`search.php:94-98` → `ItemSearchRepositoryImpl.java:76-78`).
4. 범위 밖 난이도를 `(int)` 로 바꾸므로 `3abc` 는 3, `abc` 는 0 으로 검색된다(`search.php:223-229` → `ItemSearchCondition.java:80-89`).

## 확인 필요

1. 커밋 `b3fb014` 의 제목은 "feat: item-bank 기능 1개 이관, 회고 반영"이다. 이 커밋은 pathspec 밖의 `docs/item-bank/BUSINESS-RULES.md` 도 바꿨다. "회고 반영"을 요청 범위에 넣을지 사람이 정한다. 그 회고 표는 바뀐 지점을 "없음"으로 적었는데, #1 과 맞지 않는다.
2. 레거시는 DB 오류가 나도 200 에 오류 문구를 보여 준다(`search.php:712-729`). 모던은 `GlobalExceptionHandler` 를 거쳐 500 을 준다. 정상 경로에서는 차이가 없다. 의도된 차이인지 사람이 확인한다.
3. 레거시 화면의 경고 목록과 조건 요약(`search.php:354-366, 509-516`)은 새 응답 `{rows, count, message}` 에 없다. 의도한 축소인지 사람이 확인한다.
4. pathspec 밖 참고(판정에서 제외): `upstream/main...HEAD` 전체에는 docs · `.claude` · `.mcp.json` · `dbhub.toml` 등 이관과 무관한 커밋이 섞여 있다. PR 로 올릴 때 나눌지 정한다(2차 보고서 #2).
