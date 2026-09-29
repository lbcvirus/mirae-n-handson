# 문항 은행(item-bank-php) 비즈니스 규칙 — 교차 검증용 추출본

- 추출 대상: `legacy/item-bank-php` 의 `search.php`, `register.php`, `units.php` (화면 보조 파일 `index.php`, `inc/db.php`, `inc/layout.php` 는 읽었으나 규칙 없음)
- 읽지 않은 것: `vendor/simplelog/Log.php`(외부 라이브러리), `docs/` 폴더 전체
- 기준: 코드에 있는 동작만 적는다. 주석과 코드가 다르면 코드를 따른다.

## A. 검색 조건 조합 (`search.php`)

### CX-01 — 검색 대상은 공개 문항 뷰 `v_item_public` 이다
- 규칙: 검색과 총 건수는 `item` 테이블이 아니라 뷰 `v_item_public` 에서 조회한다. 어떤 문항이 제외되는지는 이 뷰의 정의에 달려 있다.
- 근거: `search.php:521-526`
```php
$from   = " FROM v_item_public";
$sql      = $select . $from . $where . $orderBy . $limit;
$countSql = "SELECT COUNT(*) AS cnt" . $from . $where;
```
- 비고: 뷰 정의(SQL)는 `legacy/item-bank-php` 폴더 안에 없어 이 문서에서 확인하지 못했다.

### CX-02 — 검색 조건은 모두 AND 로 결합한다
- 규칙: 키워드 · 단원 · 난이도 · 태그 조건은 `WHERE 1=1` 뒤에 `AND` 로 이어 붙는다. OR 결합은 키워드의 제목/지문 사이에만 있다.
- 근거: `search.php:44`, `search.php:98`, `search.php:118`, `search.php:244`
```php
$where    = " WHERE 1=1";
$where .= " AND (title LIKE ? OR stem LIKE ?)";
$where .= " AND unit_code = ?";
```

### CX-03 — 키워드는 앞뒤 공백을 제거하고 제목 또는 지문에 부분 일치(LIKE)로 찾는다
- 규칙: `trim` 한 키워드가 빈 문자열이 아니면 `%키워드%` 로 `title` 또는 `stem` 에 LIKE 검색한다. 빈 키워드는 조건을 만들지 않는다.
- 근거: `search.php:85`, `search.php:90-98`
```php
$q = trim($q);
$like = '%' . $q . '%';
$where .= " AND (title LIKE ? OR stem LIKE ?)";
```

### CX-04 — 키워드는 100자까지만 쓴다
- 규칙: 키워드가 100자를 넘으면 앞 100자로 자르고 경고만 남긴다(오류로 막지 않는다).
- 근거: `search.php:86-89`
```php
if (mb_strlen($q, 'UTF-8') > 100) {
    $q = mb_substr($q, 0, 100, 'UTF-8');
```

### CX-05 — 키워드의 `%` · `_` 는 이스케이프하지 않고 와일드카드로 그대로 동작한다
- 규칙: 키워드에 `%` 나 `_` 가 있어도 치환하지 않고 LIKE 에 그대로 넣는다. 경고 문구만 띄운다.
- 근거: `search.php:94-97`
```php
if (strpos($q, '%') !== false || strpos($q, '_') !== false) {
    $warnings[] = '키워드의 % 와 _ 는 와일드카드로 처리됩니다.';
}
$like = '%' . $q . '%';
```

### CX-06 — 단원은 단원 코드와 정확히 일치하는 문항만 찾는다
- 규칙: `trim` 한 단원 값이 비어 있지 않으면 `unit_code = ?` 로 비교한다. 형식이 틀리거나 소문자이거나 등록되지 않은 코드여도 조회는 그대로 실행하고 경고만 남긴다(대개 0건).
- 근거: `search.php:109-120`, `search.php:141-143`
```php
$unit = trim($unit);
$where .= " AND unit_code = ?";
$warnings[] = '등록되지 않은 단원 코드입니다: ' . $unit;
```

### CX-07 — 난이도를 비우면 5를 뺀 나머지(1~4)만 검색된다 ⚠ 주석과 코드가 다름
- 규칙: 난이도 값이 빈 문자열이면 `level < 5` 조건이 붙어 난이도 5 문항은 결과에서 빠진다. 난이도 5는 `level=5` 로 명시해야만 나온다.
- 근거: `search.php:213-216` (주석은 "1~5 모두 포함"이라 쓰지만 코드는 5를 제외한다. 코드를 기준으로 적었다.)
```php
// 난이도 값이 비어 있으면 전체 난이도 검색 (1~5 모두 포함)
if ($level == '') {
    $where .= " AND level < 5";
```
- 비고: 이 조건 때문에 총 건수(`count_sql`)도 5를 제외한 값이 된다(`search.php:526`, 같은 `$where` 사용).

### CX-08 — 난이도 1~5 는 정확히 일치, 그 밖의 값은 경고 후 정수로 변환해 비교한다
- 규칙: `1`~`5` 한 자리 숫자면 `level = ?`. 그 밖의 값은 경고를 남기고 `(int)` 변환한 값으로 똑같이 `level = ?` 비교한다(숫자가 아닌 문자열은 0 이 된다).
- 근거: `search.php:217-230`
```php
else if (preg_match('/^[1-5]$/', $level)) {
    $where .= " AND level = ?";
...
    $warnings[] = '난이도는 1~5 사이여야 합니다.';
    $values[] = (int)$level;
```

### CX-09 — 태그는 이름이 정확히 일치하는 태그를 가진 문항만 찾고, 한 번에 하나만 지정할 수 있다
- 규칙: `trim` 한 태그 값이 비어 있지 않으면 `item_tag`/`tag` 를 조인한 `EXISTS` 로 `t.name = ?` 정확 일치를 검사한다. 부분 일치는 지원하지 않는다. 등록되지 않은 태그도 조회는 실행하고 경고만 남긴다.
- 근거: `search.php:235-248`
```php
$where .= " AND EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id"
        . " WHERE it.item_id = v_item_public.id AND t.name = ?)";
```

### CX-10 — 태그 이름은 50자까지만 쓴다
- 규칙: 태그 값이 50자를 넘으면 앞 50자로 자르고 경고한다.
- 근거: `search.php:240-243`
```php
if (mb_strlen($tag, 'UTF-8') > 50) {
    $tag = mb_substr($tag, 0, 50, 'UTF-8');
```

### CX-11 — 같은 이름의 파라미터가 배열로 오면 첫 값만 쓴다
- 규칙: `q` · `unit` · `level` · `tag` · `sort` · `dir` · `page` 가 배열(`q[]=…`)로 들어오면 첫 원소만 사용한다.
- 근거: `search.php:53-56`
```php
if (isset($params['q'])) {
    $q = is_array($params['q']) ? (string)reset($params['q']) : (string)$params['q'];
}
```

## B. 목록의 정렬 · 제외 조건

### CX-12 — 검색 목록의 기본 정렬은 난이도 내림차순, 같으면 ID 오름차순이다
- 규칙: `sort` 를 비우면 `ORDER BY level DESC, id ASC` (어려운 문항부터, 같은 난이도면 번호 순).
- 근거: `search.php:317-320`
```php
case '':
    // 기본 정렬: 어려운 문항부터, 같은 난이도면 번호 순
    $orderBy = " ORDER BY level DESC, id ASC";
```

### CX-13 — 알 수 없는 정렬 기준은 기본 정렬로 되돌린다
- 규칙: `sort` 가 `id|title|unit|level|created` 가 아니면 경고를 남기고 기본 정렬(CX-12)을 쓴다. `dir` 이 `asc`/`desc` 가 아니면 방향을 비운 것으로 본다.
- 근거: `search.php:266-273`, `search.php:322-326`
```php
if ($dir !== 'asc' && $dir !== 'desc') {
...
default:
    $warnings[] = '알 수 없는 정렬 기준입니다. 기본 정렬을 사용합니다.';
```

### CX-14 — 정렬 방향을 안 주면 `level` · `created` 는 내림차순, 나머지는 오름차순이다
- 규칙: `sort=level`/`created` 는 `dir` 이 `asc` 일 때만 오름차순이고 그 외(비어 있을 때 포함)는 내림차순이다. `id`/`title`/`unit` 은 `dir` 이 `desc` 일 때만 내림차순이고 그 외는 오름차순이다.
- 근거: `search.php:277-315`
```php
case 'level':
    if ($dir === 'asc') { ... " ORDER BY level ASC, id ASC"; } else { ... " ORDER BY level DESC, id ASC"; }
case 'id':
    if ($dir === 'desc') { ... " ORDER BY id DESC"; } else { ... " ORDER BY id ASC"; }
```

### CX-15 — 정렬 기준에 따라 동점 처리(2차 정렬)가 다르다
- 규칙: `title`, `level`, `created` 는 2차 정렬이 `id ASC` 이고, `unit` 은 `unit_code` 다음 `level DESC`, 그다음 `id ASC` 다(`unit` 을 내림차순으로 줘도 `level` 은 항상 내림차순). `id` 정렬은 2차 정렬이 없다.
- 근거: `search.php:285-299`
```php
$orderBy = " ORDER BY title DESC, id ASC";
$orderBy = " ORDER BY unit_code DESC, level DESC, id ASC";
$orderBy = " ORDER BY unit_code ASC, level DESC, id ASC";
```

### CX-16 — 검색 결과는 한 페이지 20건, 페이지 번호는 1~999 로 보정한다
- 규칙: `LIMIT 20 OFFSET (page-1)*20`. 숫자가 아닌 `page` 는 1 로, 0 이하는 1 로, 999 초과는 999 로 바꾸고 경고한다.
- 근거: `search.php:336-349`, `search.php:523`
```php
if ($page > 999) {
    $page = 999;
$offset = ($page - 1) * 20;
$limit  = " LIMIT 20 OFFSET " . (int)$offset;
```

### CX-17 — 단원 목록은 학년 오름차순, 같으면 단원 코드 오름차순으로 정렬한다
- 규칙: 단원 목록 화면과 검색·등록 화면의 단원 선택 목록 모두 `grade ASC, code ASC` 다.
- 근거: `units.php:19`, `search.php:156`, `register.php:27`
```php
ORDER BY u.grade ASC, u.code ASC
```

### CX-18 — 단원 목록의 문항 수는 상태가 `'A'` 인 문항만 센다
- 규칙: 단원별 `item_count` 는 `item.status = 'A'` 인 행 수다. 다른 상태(주석상 삭제 · 검수중)는 세지 않는다. 문항이 없는 단원도 목록에는 나온다(0건).
- 근거: `units.php:15-19`
```php
// 단원별 공개(status='A') 문항 수. 삭제 · 검수중 문항은 세지 않는다.
(SELECT COUNT(*) FROM item i WHERE i.unit_id = u.id AND i.status = 'A') AS item_count
```

## C. 등록 시 검증 (`register.php`)

검증은 POST 값을 모두 `trim` 한 뒤(`register.php:41-44`) 실행하며, 오류는 첫 오류에서 멈추지 않고 모두 모은다. 오류가 하나라도 있으면 저장하지 않는다(`register.php:83`).

### CX-19 — 제목은 5자 이상 200자 이하다
- 규칙: 앞뒤 공백을 제거한 제목의 글자 수가 5 미만이면 오류, 200 초과이면 오류다. 공백을 뺀 글자 수가 아니라 `trim` 후 전체 글자 수를 센다.
- 근거: `register.php:48-54`
```php
if (mb_strlen($title, 'UTF-8') < 5) {
if (mb_strlen($title, 'UTF-8') > 200) {
```

### CX-20 — 지문은 필수다
- 규칙: `trim` 한 지문이 빈 문자열이면 오류다. 길이 상한은 코드에 없다.
- 근거: `register.php:55-58`
```php
if ($stem === '') {
    $errors[] = '지문을 입력해야 합니다.';
```

### CX-21 — 단원은 필수이며 단원 목록에 있는 ID 여야 한다
- 규칙: 제출된 `unit_id` 가 `unit` 테이블의 `id` 중 하나와 문자열로 같아야 통과한다. 아니면 오류다.
- 근거: `register.php:59-68`
```php
if ((string)$u['id'] === $unitId) {
    $unitOk = true;
...
if (!$unitOk) {
```

### CX-22 — 난이도는 1~5 의 한 자리 정수다
- 규칙: `trim` 한 난이도가 `^[1-5]$` 가 아니면 오류다(빈 값 포함).
- 근거: `register.php:69-72`
```php
if (!preg_match('/^[1-5]$/', $level)) {
    $errors[] = '난이도는 1~5 사이여야 합니다.';
```

### CX-23 — 태그는 선택 사항이고, 목록에 없는 태그 ID 는 오류 없이 조용히 버린다
- 규칙: 제출된 `tags[]` 중 `tag` 테이블 `id` 와 일치하는 것만 저장 대상이 된다. 일치하지 않는 값은 오류를 내지 않고 무시한다. 태그가 하나도 없어도 등록된다.
- 근거: `register.php:73-81`, `register.php:104`
```php
foreach ($tags as $t) {
    if ((string)$t['id'] === (string)$tid) {
        $validTagIds[] = (int)$tid;
```

### CX-24 — 등록된 문항은 검수중(`'R'`) 상태로 저장된다
- 규칙: 등록 INSERT 는 `status` 를 항상 `'R'` 로 넣는다. 등록 화면에서 상태를 고를 수 없다. (주석은 "검수 완료 전까지 검색 화면에 나오지 않는다"고 하나, 실제 제외 여부는 CX-01 의 뷰 정의에 달려 있어 이 폴더의 코드만으로는 확인되지 않는다.)
- 근거: `register.php:84`, `register.php:92-95`
```php
"INSERT INTO item (id, unit_id, title, stem, level, status, created_at, updated_at)
 VALUES (?, ?, ?, ?, ?, 'R', NOW(), NOW())"
```

## D. 범위 밖 · 확인하지 못한 것

- **수정(update) 기능**: 이 폴더에서 수정 화면을 찾지 못했다. 파일 목록 명령(Bash)이 계속 실패해, 화면 파일 이름을 추정해 읽는 방식으로 확인했다(`units.php`, `search.php`, `register.php`, `index.php`, `inc/db.php`, `inc/layout.php` 는 존재. `items.php` · `item.php` · `edit.php` · `update.php` · `modify.php` · `detail.php` · `inc/validate.php` · `README.md` 는 없음). 이름이 다른 수정 파일이 있다면 이 문서에서 빠져 있다. 확인 시 "수정 시 검증" 규칙(CX-19~23 과 같은지 여부)을 추가해야 한다.
- **`v_item_public` 정의**: 폴더 밖 SQL 에 있을 것으로 보이며 읽지 않았다. 검색에서 `status` 별 제외 조건(`'A'` 만 노출되는지 등)은 이 뷰가 정한다.
- **대소문자 · 정렬 규칙(collation)**: 단원 코드 · 태그 이름 · 키워드 비교의 대소문자 구분 여부는 DB 설정에 달려 있어 코드로 판단할 수 없다.
- `legacy/` 아래 파일은 수정하지 않았고, `docs/` 폴더는 읽지 않았다.
