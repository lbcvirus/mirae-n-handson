# legacy/item-bank-php 비즈니스 규칙 후보

> 선행 문서: [ARCHITECTURE.md](ARCHITECTURE.md)(진입점 · 의존 관계), [ERD.md](ERD.md)(테이블 · 읽기 · 쓰기 위치).
> 근거 경로는 `legacy/item-bank-php/` 기준이다. 스키마는 `db/mariadb/init/01-schema.sql` 을 저장소 루트 기준으로 적는다(근거는 ERD.md 0절).
> 확신도가 "확실"이면 조건문이나 SQL 로 확인했다는 뜻이다. 화면을 실제로 실행해 보지는 않았다.

## 0. 범위와 전제

- 대상 파일은 `search.php`(검색), `register.php`(등록), `units.php`(단원 목록) 3개다. `index.php` 와 `inc/*` 에는 규칙이 없다.
- **수정 기능은 없다.** 메뉴는 검색 · 등록 · 단원 목록 3개뿐이다(`inc/layout.php:9-13`). 모듈 전체에 `UPDATE` 문도 없다(ERD.md 3절). 그래서 "수정 시 검증" 규칙은 만들 수 없다. 6절에 부재로 적었다.
- 규칙은 코드 기준으로 썼다. 주석과 코드가 다르면 비고에 주석 내용을 남겼다.

## 1. 문항 검색 — 대상과 조건 조합 (`search.php`)

### BR-01
- 규칙: 검색 결과에는 `status = 'A'` 인 문항만 나온다.
- 근거: `search.php:522`, `db/mariadb/init/01-schema.sql:70`
- 근거 코드:
  ```php
  $from   = " FROM v_item_public";
  ```
  ```sql
  WHERE i.status = 'A';
  ```
- 확신도: 확실
- 비고: 같은 공개 기준을 `units.php:17` 이 `i.status = 'A'` 로 따로 적는다(BR-22). 상태 코드 `'A'` 는 두 곳에 흩어진 매직 값이다.

### BR-02
- 규칙: 검색 조건(키워드 · 단원 · 난이도 · 태그)이 여러 개 들어오면 모두 `AND` 로 묶어, 모든 조건을 만족하는 문항만 돌려준다.
- 근거: `search.php:44`, `search.php:98`, `search.php:118`, `search.php:215`, `search.php:218`, `search.php:244`
- 근거 코드:
  ```php
  $where    = " WHERE 1=1";
  $where .= " AND unit_code = ?";
  $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
  ```
- 확신도: 확실
- 비고: `OR` 로 묶는 조건은 키워드 안(제목 OR 지문, BR-03)에만 있다.

### BR-03
- 규칙: 키워드가 앞뒤 공백을 뺀 뒤에도 비어 있지 않으면, 제목 또는 지문에 키워드가 들어 있는 문항만 찾는다(부분 일치).
- 근거: `search.php:85`, `search.php:90`, `search.php:97-98`
- 근거 코드:
  ```php
  $like = '%' . $q . '%';
  $where .= " AND (title LIKE ? OR stem LIKE ?)";
  ```
- 확신도: 확실
- 비고: 키워드의 `%` · `_` 를 이스케이프하지 않아 SQL 와일드카드로 동작한다. 코드는 경고만 띄운다(`search.php:94-96`).

### BR-04
- 규칙: 키워드가 100자를 넘으면 앞 100자만 쓰고 경고를 띄운다.
- 근거: `search.php:86-88`, `search.php:494`
- 근거 코드:
  ```php
  if (mb_strlen($q, 'UTF-8') > 100) {
      $q = mb_substr($q, 0, 100, 'UTF-8');
  ```
- 확신도: 확실
- 비고: 매직 넘버 100 이 서버 코드(`:86-88`)와 입력창 `maxlength="100"`(`:494`) 두 곳에 있다.

### BR-05
- 규칙: 키워드가 한 글자이면 경고만 띄우고 검색은 그대로 한다.
- 근거: `search.php:91-92`
- 근거 코드:
  ```php
  if (mb_strlen($q, 'UTF-8') === 1) {
      $warnings[] = '키워드가 한 글자라 결과가 많을 수 있습니다.';
  ```
- 확신도: 확실
- 비고: 최소 길이 제한은 없다.

### BR-06
- 규칙: 단원 코드가 비어 있지 않으면, 입력값과 `unit_code` 가 같은 문항만 찾는다. 입력값의 대소문자는 바꾸지 않는다.
- 근거: `search.php:109-110`, `search.php:118-120`
- 근거 코드:
  ```php
  $where .= " AND unit_code = ?";
  $types .= 's';
  $values[] = $unit;
  ```
- 확신도: 확실
- 비고: 형식이 틀리거나(BR-07), 소문자이거나(`search.php:115-116`), 없는 코드여도(`search.php:141-142`) 경고만 띄우고 조회한다. 주석에는 "결과는 대개 0건"이라고 적혀 있다(`search.php:112`). 그런데 DB 정렬 규칙이 `utf8mb4_unicode_ci`(`01-schema.sql:5`)라 대소문자를 구분하지 않을 수 있다. 소문자 `m5-1` 이 0건인지는 미확인이다(6절).

### BR-07
- 규칙: 단원 코드가 `영문 1자 + 숫자 1~2자리 + '-' + 숫자 1~2자리` 형식이 아니면 경고를 띄운다. 조회는 막지 않는다.
- 근거: `search.php:111-113`
- 근거 코드:
  ```php
  if (!preg_match('/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/', $unit)) {
      // 형식이 달라도 그대로 조회한다 (결과는 대개 0건)
  ```
- 확신도: 확실
- 비고: 단원 코드 형식은 이 정규식에만 있다. 스키마는 `VARCHAR(16)` 뿐이다(`01-schema.sql:13`).

### BR-08
- 규칙: 난이도가 비어 있으면 `level < 5` 조건을 붙인다. 그래서 난이도 5 문항은 결과에서 빠진다.
- 근거: `search.php:213-216`
- 근거 코드:
  ```php
  // 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
  if ($level == '') {
      $where .= " AND level < 5";
  ```
- 확신도: 확실
- 비고: **주석과 코드가 다르다.** 주석은 "1~5 모두 포함", 코드는 5 제외다. 파일 머리 주석도 `level : 난이도 (1~5, 빈값 허용)`(`search.php:8`)이라 전체 검색처럼 읽힌다. 난이도 선택 목록의 "전체" 항목도 빈 값이다(`search.php:192`). 사용자가 "전체"를 고르면 5가 빠진다. 매직 넘버 5.

### BR-09
- 규칙: 난이도가 `1`~`5` 중 한 글자이면 그 난이도와 같은 문항만 찾는다.
- 근거: `search.php:217-220`
- 근거 코드:
  ```php
  else if (preg_match('/^[1-5]$/', $level)) {
      $where .= " AND level = ?";
  ```
- 확신도: 확실
- 비고: 같은 정규식이 등록 검증에도 있다(`register.php:70`, BR-29). 결과는 다르다. 검색은 범위 밖 값을 받아들이고(BR-10), 등록은 거부한다.

### BR-10
- 규칙: 난이도가 비어 있지 않고 `1`~`5` 한 글자도 아니면, 경고를 띄운 뒤 PHP 정수 변환 값 `(int)$level` 과 같은 문항을 찾는다.
- 근거: `search.php:223-229`
- 근거 코드:
  ```php
  $warnings[] = '난이도는 1~5 사이여야 합니다.';
  $where .= " AND level = ?";
  $values[] = (int)$level;
  ```
- 확신도: 확실
- 비고: 주석은 "1~5 밖의 값은 그대로 정수로 비교한다 (대개 0건)"이다(`search.php:224`). 하지만 PHP `(int)` 변환 때문에 `03` · `3.7` · `3abc` 는 3 이 된다. 경고가 뜨면서도 난이도 3 문항이 나온다. `abc` 는 0 이 되어 0건이다.

### BR-11
- 규칙: 태그가 비어 있지 않으면, 그 이름과 **정확히 같은** 태그가 달린 문항만 찾는다. 태그는 한 번에 하나만 조건으로 쓴다.
- 근거: `search.php:235-236`, `search.php:244-247`, `search.php:66-67`
- 근거 코드:
  ```php
  $where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
          . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
  ```
- 확신도: 확실
- 비고: 태그에 `%` · `_` 가 있으면 "부분 일치를 지원하지 않습니다" 경고를 띄운다(`search.php:237-238`). 등록되지 않은 태그도 경고만 띄우고 그대로 조회한다(`search.php:250-260`).

### BR-12
- 규칙: 태그 이름이 50자를 넘으면 앞 50자만 쓰고 경고를 띄운다.
- 근거: `search.php:240-242`
- 근거 코드:
  ```php
  if (mb_strlen($tag, 'UTF-8') > 50) {
      $tag = mb_substr($tag, 0, 50, 'UTF-8');
  ```
- 확신도: 확실
- 비고: 50 은 `tag.name VARCHAR(50)`(`01-schema.sql:37`)과 같은 값이다. 코드에 따로 적힌 매직 넘버다.

### BR-13
- 규칙: 같은 이름의 GET 파라미터가 배열로 들어오면 첫 값만 쓴다(`q` · `unit` · `level` · `tag` · `sort` · `dir` · `page` 모두).
- 근거: `search.php:51-80`
- 근거 코드:
  ```php
  // 파라미터 꺼내기 (배열로 들어오면 첫 값만 쓴다)
  $unit = is_array($params['unit']) ? (string)reset($params['unit']) : (string)$params['unit'];
  ```
- 확신도: 확실
- 비고: BR-11 의 "태그 하나만"이 이 규칙에서 나온다.

## 2. 문항 검색 — 정렬 · 페이지 · 결과 표시 (`search.php`)

### BR-14
- 규칙: 정렬 기준이 비어 있거나 알 수 없는 값이면 기본 정렬 "난이도 내림차순, 같은 난이도는 id 오름차순"을 쓴다. 알 수 없는 값일 때는 경고도 띄운다.
- 근거: `search.php:317-326`
- 근거 코드:
  ```php
  // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
  $orderBy = " ORDER BY level DESC, id ASC";
  ```
- 확신도: 확실
- 비고: 기본 정렬 문자열이 `case ''`(`:319`)와 `default`(`:325`) 두 곳에 똑같이 있다.

### BR-15
- 규칙: 정렬 기준에 따라 아래 `ORDER BY` 를 쓴다. `id` 정렬이 아니면 동점일 때 `id ASC` 로 정한다.

  | `sort` | `ORDER BY` (`dir` 에 따라 첫 열 방향만 바뀜) | 근거 |
  |---|---|---|
  | `id` | `id` | `search.php:277-283` |
  | `title` | `title`, `id ASC` | `search.php:285-291` |
  | `unit` | `unit_code`, **`level DESC`**, `id ASC` | `search.php:293-299` |
  | `level` | `level`, `id ASC` | `search.php:301-307` |
  | `created` | `created_at`, `id ASC` | `search.php:309-315` |

- 근거 코드:
  ```php
  $orderBy = " ORDER BY unit_code ASC, level DESC, id ASC";
  ```
- 확신도: 확실
- 비고: `unit` 정렬은 방향과 관계없이 2차 정렬이 늘 `level DESC` 다.

### BR-16
- 규칙: 정렬 방향이 `asc` · `desc` 가 아니면 무시한다. 값이 있었으면 경고를 띄운다. 방향이 없으면 `level` · `created` 는 내림차순, 나머지는 오름차순으로 정렬한다.
- 근거: `search.php:266-273`, `search.php:278-313`, `search.php:328-330`
- 근거 코드:
  ```php
  if ($dir !== 'asc' && $dir !== 'desc') {
  $dir = ($sort === 'level' || $sort === 'created') ? 'desc' : 'asc';
  ```
- 확신도: 확실
- 비고: `sort` · `dir` 는 소문자로 바꾼 뒤 비교한다(`search.php:266-267`). 실제 기본 방향은 각 `case` 의 `if ($dir === 'desc')` / `if ($dir === 'asc')` 분기가 정한다. `:330` 은 화면 표시용이다(주석 `:329`). 두 곳의 결과는 같다.

### BR-17
- 규칙: 결과 표 머리글의 정렬 링크는 이렇게 동작한다. 지금 정렬 중인 열이면 방향을 뒤집는다. 다른 열이면 `asc` 로 건다. 단 난이도 열은 첫 클릭이 `desc` 다.
- 근거: `search.php:399-413`
- 근거 코드:
  ```php
  } else if ($key === 'level') {
      // 난이도는 첫 클릭에 내림차순
      $nextDir = 'desc';
  ```
- 확신도: 확실
- 비고: 머리글 링크는 `id` · `title` · `unit` · `level` 4개뿐이다(`search.php:392-397`). `created` 정렬은 URL 로만 쓸 수 있고(`search.php:10` 머리 주석에는 있음), 태그 열은 정렬할 수 없다(`search.php:418`).

### BR-18
- 규칙: 결과는 한 페이지에 20건씩 보여 주고, `(page - 1) × 20` 건을 건너뛴다.
- 근거: `search.php:349`, `search.php:523`, `search.php:553`
- 근거 코드:
  ```php
  $offset = ($page - 1) * 20;
  $limit  = " LIMIT 20 OFFSET " . (int)$offset;
  'page_size'    => 20,
  ```
- 확신도: 확실
- 비고: 같은 매직 넘버 20 이 세 곳에 따로 적혀 있다.

### BR-19
- 규칙: 페이지 번호는 이렇게 보정한다. 숫자로만 된 값이 아니면 1 로 두고, 1 보다 작으면 1, 999 보다 크면 999 로 바꾼다.
- 근거: `search.php:336-348`
- 근거 코드:
  ```php
  if ($page > 999) {
      $page = 999;
      $warnings[] = '페이지 번호는 999 를 넘을 수 없습니다.';
  ```
- 확신도: 확실
- 비고: 숫자가 아닌 값은 `''` · `'1'` 이 아닐 때만 경고를 띄운다(`:339-340`). `0` 은 경고 없이 1 이 된다. 매직 넘버 999.

### BR-20
- 규칙: 검색 결과가 0건이면 결과 표 대신 "검색 결과가 없습니다"만 보여 준다.
- 근거: `search.php:657-659`
- 근거 코드:
  ```php
  if ($total === 0) {
      echo '<p id="message">검색 결과가 없습니다</p>' . "\n";
  ```
- 확신도: 확실
- 비고: 총 건수는 있는데 페이지 번호가 마지막 페이지보다 크면(예: 25건에 `page=5`) 이 분기를 타지 않는다. 행 없는 표 머리글만 나온다(`search.php:657`, `:663-681`). 코드로 본 결과이고 실행해 보지는 않았다.

### BR-21
- 규칙: 전체 페이지가 2쪽 이상일 때만 페이지 링크를 보여 준다. 지금 페이지가 1보다 크면 "이전", 마지막보다 작으면 "다음" 링크를 붙인다.
- 근거: `search.php:685-701`
- 근거 코드:
  ```php
  $pages = (int)ceil($total / $pageSize);
  if ($pages > 1) {
  ```
- 확신도: 확실
- 비고: 페이지 번호 링크는 1쪽부터 마지막 쪽까지 전부 그린다(`:692-698`). 최대 999쪽 보정(BR-19)과 따로 논다.

## 3. 단원 · 태그 목록 (`units.php`, `register.php`, `search.php`)

### BR-22
- 규칙: 단원 목록은 모든 단원을 학년 오름차순, 코드 오름차순으로 보여 준다. 단원마다 `status = 'A'` 인 문항만 센다(삭제 · 검수중 제외, 문항 0개인 단원도 표시).
- 근거: `units.php:16-19`
- 근거 코드:
  ```php
  $sql = "SELECT u.id, u.code, u.name, u.grade,
                 (SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
  ```
- 확신도: 확실
- 비고: 공개 기준이 BR-01 의 뷰와 따로 구현되어 있다. 주석 `units.php:15` 은 코드와 같다.

### BR-23
- 규칙: 화면의 단원 선택 목록은 학년 → 코드 순, 태그 선택 목록은 태그 id 순으로 보여 준다.
- 근거: `register.php:27`, `register.php:34`, `search.php:156`, `search.php:178`
- 근거 코드:
  ```php
  $res = $conn->query("SELECT id, code, name FROM unit ORDER BY grade ASC, code ASC");
  $res = $conn->query("SELECT id, name FROM tag ORDER BY id ASC");
  ```
- 확신도: 확실
- 비고: 같은 정렬을 `units.php:19` · `register.php:27` · `search.php:156` 세 곳이 따로 적는다. 검색 화면만 학년별 `optgroup` 으로 묶는다(`search.php:452-462`). 검색 화면은 태그를 이름으로 넘기고(`search.php:182`), 등록 화면은 id 로 넘긴다(`register.php:171`).

## 4. 문항 등록 — 검증 (`register.php`)

### BR-24
- 규칙: 요청이 `POST` 일 때만 입력값을 검증하고 저장한다. `GET` 이면 빈 폼만 보여 준다.
- 근거: `register.php:40`
- 근거 코드:
  ```php
  if ($_SERVER['REQUEST_METHOD'] === 'POST') {
  ```
- 확신도: 확실
- 비고: —

### BR-25
- 규칙: 제목이 앞뒤 공백을 뺀 뒤 5자 미만이면 오류로 거부한다.
- 근거: `register.php:41`, `register.php:49-50`
- 근거 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') < 5) {
      $errors[] = '제목은 5자 이상 입력해야 합니다.';
  ```
- 확신도: 확실
- 비고: 주석 `register.php:48`("공백 제외 아님, 앞뒤 공백만 제거")은 코드와 같다. 가운데 공백도 글자 수에 들어간다. 매직 넘버 5.

### BR-26
- 규칙: 제목이 200자를 넘으면 오류로 거부한다.
- 근거: `register.php:52-53`
- 근거 코드:
  ```php
  if (mb_strlen($title, 'UTF-8') > 200) {
      $errors[] = '제목은 200자를 넘을 수 없습니다.';
  ```
- 확신도: 확실
- 비고: 200 은 `item.title VARCHAR(200)`(`01-schema.sql:23`)과 같은 값이다. 코드에 따로 적힌 매직 넘버다. 폼 입력창에는 `maxlength` 가 없다(`register.php:144`).

### BR-27
- 규칙: 지문이 앞뒤 공백을 뺀 뒤 빈 문자열이면 오류로 거부한다.
- 근거: `register.php:42`, `register.php:56-57`
- 근거 코드:
  ```php
  if ($stem === '') {
      $errors[] = '지문을 입력해야 합니다.';
  ```
- 확신도: 확실
- 비고: 지문은 최대 길이 검사가 없다(컬럼 `TEXT`, `01-schema.sql:24`).

### BR-28
- 규칙: 단원 id 가 단원 목록(`unit` 전체)의 id 와 문자열로 정확히 같지 않으면 오류로 거부한다.
- 근거: `register.php:60-68`
- 근거 코드:
  ```php
  if ((string)$u['id'] === $unitId) {
      $unitOk = true;
  ```
- 확신도: 확실
- 비고: 문자열 비교라 `01` 처럼 앞에 0 이 붙은 값은 거부한다. 검색 화면은 없는 단원을 경고만 하고 조회한다(BR-06). 같은 "단원 존재" 판단을 두 화면이 다르게 처리한다.

### BR-29
- 규칙: 난이도가 `1`~`5` 중 한 글자가 아니면 오류로 거부한다.
- 근거: `register.php:70-71`
- 근거 코드:
  ```php
  if (!preg_match('/^[1-5]$/', $level)) {
      $errors[] = '난이도는 1~5 사이여야 합니다.';
  ```
- 확신도: 확실
- 비고: 검색은 같은 정규식을 쓰지만 범위 밖 값을 거부하지 않는다(BR-10). 폼 선택 목록도 1~5 다(`register.php:163`). 스키마에는 주석만 있다(`01-schema.sql:25`, `CHECK` 제약 없음).

### BR-30
- 규칙: 선택한 태그 id 가운데 태그 목록에 없는 것은 오류 없이 버린다. 태그를 하나도 고르지 않아도 된다.
- 근거: `register.php:45`, `register.php:74-81`, `register.php:104`
- 근거 코드:
  ```php
  if ((string)$t['id'] === (string)$tid) {
      $validTagIds[] = (int)$tid;
  ```
- 확신도: 확실
- 비고: 중복을 없애지 않는다. `tags[]` 에 같은 id 가 두 번 오면 `item_tag` 에 같은 값을 두 번 넣으려다 PK(`01-schema.sql:45`) 위반으로 실패한다. 그러면 전체를 롤백하고 "저장 중 오류"를 띄울 것으로 보인다(`register.php:108-109`, `:119-122`). 코드와 스키마를 읽고 내린 결론이며 실행해 보지는 않았다. 단원 · 난이도는 틀리면 거부하고, 태그만 조용히 버린다.

### BR-31
- 규칙: 검증 오류가 하나라도 있으면 저장하지 않는다. 오류 전부와 입력값을 그대로 다시 보여 준다.
- 근거: `register.php:83`, `register.php:133-138`, `register.php:144`, `register.php:148`
- 근거 코드:
  ```php
  if (empty($errors)) {
  ```
- 확신도: 확실
- 비고: 검증은 첫 오류에서 멈추지 않고 모든 항목을 끝까지 검사한다(`register.php:49-81`).

## 5. 문항 등록 — 저장 (`register.php`)

### BR-32
- 규칙: 새 문항은 늘 검수중(`'R'`) 상태로 저장한다. 생성 · 수정 시각은 DB 의 `NOW()` 로 채운다.
- 근거: `register.php:93-94`
- 근거 코드:
  ```php
  "INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
   VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
  ```
- 확신도: 확실
- 비고: BR-01 과 이어서 보면, 등록한 문항은 검색 · 단원 목록에 나오지 않는다. 주석 `register.php:84` 와 안내 문구 `register.php:116` 도 그렇게 말한다. 스키마 기본값은 `'A'`(`01-schema.sql:26`)인데 코드가 `'R'` 로 덮어쓴다. 검수를 완료하는 코드는 없다(6절).

### BR-33
- 규칙: 새 문항 id 는 트랜잭션 안에서 `item` 을 잠근 뒤 구한 `MAX(id) + 1` 이다. 문항이 없으면 1 이다.
- 근거: `register.php:85-90`
- 근거 코드:
  ```php
  $res = $conn->query("SELECT COALESCE(MAX(id), 0) + 1 AS next_id FROM item FOR UPDATE");
  ```
- 확신도: 확실
- 비고: `item.id` 에 `AUTO_INCREMENT` 가 없다(`01-schema.sql:21`).

### BR-34
- 규칙: 문항과 문항 · 태그 연결은 한 트랜잭션으로 저장한다. 어느 하나라도 실패하면 모두 롤백하고 "저장 중 오류가 발생했습니다"를 띄운다.
- 근거: `register.php:85`, `register.php:114`, `register.php:119-122`
- 근거 코드:
  ```php
  } catch (Exception $e) {
      $conn->rollback();
      Log::error('item register failed', array('msg' => $e->getMessage()));
  ```
- 확신도: 확실
- 비고: —

### BR-35
- 규칙: 저장에 성공하면 "문항 #id 을(를) 등록했습니다" 안내를 띄우고 폼을 비운다.
- 근거: `register.php:116-118`
- 근거 코드:
  ```php
  $notice = '문항 #' . $newId . ' 을(를) 등록했습니다. 검수 완료 후 검색에 노출됩니다.';
  $title = $stem = $unitId = $level = '';
  ```
- 확신도: 확실
- 비고: 안내 문구의 "검수 완료 후 검색에 노출"은 BR-01 · BR-32 와 맞는다. 검수 완료 절차 자체는 코드에 없다.

## 6. 매직 넘버 · 매직 값

| 값 | 위치 | 추정 의미 |
|---|---|---|
| `100` | `search.php:86`, `:87`, `:88`(문구), `:494`(`maxlength`) | 검색 키워드 최대 글자 수 |
| `1` | `search.php:91` | "너무 짧은 키워드"로 경고하는 글자 수 |
| `50` | `search.php:240`, `:241`, `:242`(문구) | 태그 이름 최대 글자 수(`tag.name VARCHAR(50)` 과 같은 값) |
| `5` | `search.php:215` (`level < 5`) | 빈 난이도 조건의 상한. 주석대로면 "전체"여야 하므로 의도가 불분명하다(BR-08) |
| `[1-5]` · `1`~`5` | `search.php:217`, `search.php:193-199`, `register.php:70`, `register.php:163` | 난이도 범위 |
| `20` | `search.php:349`, `:523`, `:553` | 한 페이지 결과 수 |
| `999` | `search.php:345`, `:346`, `:347`(문구) | 최대 페이지 번호 |
| `1` | `search.php:77`, `:336`, `:342-343` | 기본 · 최소 페이지 번호 |
| `/^[A-Za-z][0-9]{1,2}-[0-9]{1,2}$/` | `search.php:111` | 단원 코드 형식(예: `M5-1`, 문구 `:113`) |
| `5` | `register.php:49` | 제목 최소 글자 수 |
| `200` | `register.php:52` | 제목 최대 글자 수(`item.title VARCHAR(200)` 과 같은 값) |
| `0` | `register.php:87` (`COALESCE(MAX(id), 0) + 1`) | 문항이 없을 때 id 계산의 시작값 |
| `'A'` | `units.php:17`, `01-schema.sql:70` | 공개 상태 코드 |
| `'R'` | `register.php:94` | 검수중 상태 코드 |

## 7. 주석만 있거나 주석과 코드가 다른 곳

### 주석만 있음 (규칙으로 올리지 않음)

| 내용 | 위치 | 설명 |
|---|---|---|
| `D` = 삭제 | `01-schema.sql:26` | `'D'` 를 쓰거나 읽는 코드가 없다. 뷰와 `units.php` 는 `'A'` 만 골라서 결과적으로 빠질 뿐이다 |
| 검수를 완료하면 검색에 노출 | `register.php:84`, 문구 `register.php:116` | `R` → `A` 로 바꾸는 코드가 없다. "노출되지 않음"은 BR-01 · BR-32 로 확실하지만, "완료하면 노출"은 주석만 있다 |
| 형식이 틀린 단원 · 범위 밖 난이도는 "대개 0건" | `search.php:112`, `search.php:224` | 결과 건수를 막는 코드는 없다. BR-10 대로 난이도는 0건이 아닐 수 있다 |

### 주석과 코드가 다름

| 주석 | 코드 | 위치 | 관련 규칙 |
|---|---|---|---|
| 빈 난이도는 "1~5 모두 포함" | `level < 5` 로 난이도 5 를 뺀다 | `search.php:213-215` | BR-08 |
| 머리 주석 `level : 난이도 (1~5, 빈값 허용)` | 위와 같음 | `search.php:8` | BR-08 |
| 검색 · **등록 화면**이 모두 `v_item_public` 을 기준으로 삼는다 | `register.php` 는 이 뷰를 조회하지 않는다 | `01-schema.sql:51` | — |
| 정렬 기준 `id \| title \| unit \| level \| created` | 머리글 링크에는 `created` 가 없다(URL 로만 가능) | `search.php:10`, `:392-397` | BR-17 |

### 같은 규칙을 여러 곳에서 다르게 구현

| 규칙 | 구현 A | 구현 B |
|---|---|---|
| 공개 문항 판정 | 뷰 `WHERE i.status = 'A'`(`01-schema.sql:70`) | `units.php:17` 의 서브쿼리 |
| 난이도 1~5 | 검색: 범위 밖이면 경고하고 `(int)` 로 조회(`search.php:223-229`) | 등록: 오류로 거부(`register.php:70-71`) |
| 단원 존재 여부 | 검색: 경고하고 조회(`search.php:141-142`) | 등록: 오류로 거부(`register.php:66-68`) |
| 태그 존재 여부 | 검색: 경고하고 조회(`search.php:258-259`) | 등록: 오류 없이 버림(`register.php:74-81`) |
| 단원 목록 정렬 `grade, code` | `units.php:19` | `register.php:27`, `search.php:156` |
| 기본 정렬 | `search.php:319` | `search.php:325` |

## 8. 없는 규칙 · 미확인

| 항목 | 상태 | 이유 |
|---|---|---|
| 문항 수정 시 검증 | 없음 | 수정 화면 · `UPDATE` 문이 모듈에 없다(`inc/layout.php:9-13`, ERD.md 3절) |
| 문항 삭제(`D`) · 검수 완료(`A`) 전환 규칙 | 미확인 | `legacy/` · `db/` 에서 `UPDATE item` 을 찾지 못했다(ERD.md 6절) |
| 제목 중복 · 같은 단원 안 중복 금지 | 없음 | 등록 검증(`register.php:49-81`)에 중복 검사가 없고, 스키마에도 `UNIQUE` 가 없다(`01-schema.sql:20-33`) |
| 소문자 단원 코드(`m5-1`)의 검색 결과 | 미확인 | 코드는 입력값 그대로 비교한다(`search.php:118`). 정렬 규칙 `utf8mb4_unicode_ci` 에서 대소문자 구분 없이 일치하는지는 실행해 보지 않았다 |
| 태그 id 를 중복으로 보낼 때의 실제 동작 | 미확인(코드상 롤백 예상) | BR-30 비고 참고. 실행해 보지 않았다 |
| 권한 · 로그인 규칙(누가 등록할 수 있는지) | 없음 | 세 화면 어디에도 인증 · 권한 검사 코드가 없다 |
