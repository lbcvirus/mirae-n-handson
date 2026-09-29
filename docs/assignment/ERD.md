# legacy/assignment-thymeleaf 데이터 흐름 · ERD — 배포 등록 화면

> 분석 범위: 배포 등록 화면(`GET /distributions/new`, `POST /distributions`)이 읽고 쓰는 테이블. 끝낸 단계: 진입점 · 의존 관계([ARCHITECTURE.md](ARCHITECTURE.md)), 데이터 흐름(이 문서). 비즈니스 규칙은 [BUSINESS-RULES.md](BUSINESS-RULES.md).
> 분석하지 않음: 배포 목록 · 재배포 · 과제 목록 화면의 SQL(`DistributionDao.findAllForList` · `markRedistributed` · `findSubmissions`, `AssignmentDao.findAllForList`).
> 경로 표기는 ARCHITECTURE.md 와 같다. 스키마 · 시드는 저장소 루트 기준(`db/mariadb/init/…`)이다.

## 0. 스키마의 출처

- 모듈 안에는 DDL 이 없다. DAO 가 SQL 문자열만 실행한다(`dao/*.java`).
- compose 는 이 모듈을 `jdbc:mariadb://mariadb:3306/itembank` 로 붙인다(`docker-compose.yml:61`). 설정 파일 기본값도 DB 이름이 `itembank` 다(`application.properties:2`).
- `mariadb` 컨테이너는 `db/mariadb/init` 을 초기화 스크립트로 마운트한다(`docker-compose.yml:25`). 그래서 `db/mariadb/init/01-schema.sql` 을 스키마 근거로 쓴다. 이 파일도 "assignment-thymeleaf 모듈이 같은 DB를 쓴다"고 적는다(`01-schema.sql:73`).
- DB 기본 정렬 규칙은 `utf8mb4_unicode_ci` 다(`01-schema.sql:5`).
- 운영 DB 스키마가 이 파일과 같은지는 미확인이다(6절).

## 1. 테이블 목록 (이 화면이 쓰는 것만)

| 이름 | 주요 컬럼 | 키 · 제약 | 근거 |
|---|---|---|---|
| `assignment` | `id` INT, `title` VARCHAR(200), `unit_id` INT NOT NULL, `due_at` DATETIME NOT NULL, `status` CHAR(1) 기본값 `'O'`(주석 `O=진행 C=마감`) | PK `id`, FK `unit_id` → `unit.id` | 정의 `01-schema.sql:82-90`. 사용 `dao/AssignmentDao.java:21-24`, `:34-48` |
| `unit` | `id`, `code`, `name`, `grade` | PK `id`, UNIQUE `code` | 정의 `01-schema.sql:11-18`. 사용 `dao/AssignmentDao.java:22`, `:24`(LEFT JOIN) |
| `class` | `id` INT, `name` VARCHAR(50), `teacher_id` VARCHAR(20) | PK `id` | 정의 `01-schema.sql:75-80`. 사용 `dao/ClassDao.java:21`, `:25` |
| `distribution` | `id` INT, `assignment_id` INT, `class_id` INT, `distributed_at` DATETIME NOT NULL, `redistributed` TINYINT 기본값 0 | PK `id`, FK `assignment_id` → `assignment.id`, FK `class_id` → `class.id`, 인덱스 두 개. **(`assignment_id`, `class_id`) UNIQUE 없음** | 정의 `01-schema.sql:92-103`. 사용 `dao/AssignmentDao.java:23`, `dao/DistributionDao.java:49`, `:55`, `:61` |

참고

- `assignment.status` 의 스키마 주석에는 `O` · `C` 두 값만 있다(`01-schema.sql:87`). 이 화면의 코드는 `X`(`web/DistributionController.java:61`, `dao/AssignmentDao.java:36`)와 `D`(`service/DistributionService.java:56`)도 비교한다. DB 에 `CHECK` 제약은 없다(`01-schema.sql:82-90`).
- 시드 과제 6개의 상태는 `C` 2개, `O` 4개다(`db/mariadb/init/02-seed.sql:119-125`). `X` · `D` 인 과제는 시드에 없다.
- 모든 `id` 에 `AUTO_INCREMENT` 가 없다(`01-schema.sql:76`, `:83`, `:93`). 새 배포 id 는 코드가 `MAX(id)+1` 로 정한다(`dao/DistributionDao.java:55-56`).
- `submission` 은 같은 스키마에 있지만(`01-schema.sql:105-115`) 이 화면의 코드는 읽지도 쓰지도 않는다.

## 2. 테이블 관계

```mermaid
erDiagram
    unit ||--o{ assignment : "선언 fk_assignment_unit"
    assignment ||--o{ distribution : "선언 fk_distribution_assignment"
    class ||--o{ distribution : "선언 fk_distribution_class"

    assignment {
        INT id PK
        VARCHAR title "VARCHAR(200)"
        INT unit_id FK
        DATETIME due_at
        CHAR status "주석 O 진행, C 마감"
    }
    unit {
        INT id PK
        VARCHAR code UK
        VARCHAR name
    }
    class {
        INT id PK
        VARCHAR name "VARCHAR(50)"
        VARCHAR teacher_id "VARCHAR(20)"
    }
    distribution {
        INT id PK "코드가 MAX(id)+1 로 채번"
        INT assignment_id FK
        INT class_id FK
        DATETIME distributed_at
        TINYINT redistributed "기본 0"
    }
```

| 관계 | 구분 | 선언 근거 | 코드에서 JOIN · 조건으로 쓰는 곳 |
|---|---|---|---|
| `unit` 1 — N `assignment` | 선언 | `01-schema.sql:89` | `dao/AssignmentDao.java:24` (`LEFT JOIN unit u ON u.id = a.unit_id`) |
| `assignment` 1 — N `distribution` | 선언 | `01-schema.sql:101` | `dao/AssignmentDao.java:23`(상관 서브쿼리 `d.assignment_id = a.id`), `dao/DistributionDao.java:49`(`WHERE assignment_id = ?`) |
| `class` 1 — N `distribution` | 선언 | `01-schema.sql:102` | `dao/DistributionDao.java:49`(`AND class_id = ?`) |

- 추정 관계는 없다. 범위 안 SQL 의 JOIN · 조건은 모두 선언된 외래 키와 같은 컬럼을 잇는다.
- `assignment.unit_id` 는 `NOT NULL` + FK 인데(`01-schema.sql:85`, `:89`) 코드는 `LEFT JOIN` 으로 붙인다(`dao/AssignmentDao.java:24`).
- `assignment` 와 `class` 는 `distribution` 을 거치는 N:M 관계다. 같은 (과제, 학급) 쌍의 중복은 DB 가 막지 않고 코드가 막는다(`dao/DistributionDao.java:46`, BUSINESS-RULES.md 참고). 시드에는 같은 쌍(과제 2, 학급 1)이 두 건 있다(`db/mariadb/init/02-seed.sql:133-134`, 주석 `:128` "재배포한 이력").

## 3. 읽기 · 쓰기 위치

### `assignment` (+ `unit`, `distribution` 서브쿼리)

| 동작 | 파일 · 메서드 | SQL 요지 | 근거 |
|---|---|---|---|
| 읽기 | `AssignmentDao.findAllForSelect` ← `DistributionController.form` | `BASE_SELECT WHERE a.status IN ('O', 'X', 'C') ORDER BY a.due_at ASC, a.id ASC`. 폼의 과제 선택 목록 | `dao/AssignmentDao.java:34-39`, 호출 `web/DistributionController.java:42` |
| 읽기 | `AssignmentDao.findById` ← `DistributionController.create` | `BASE_SELECT WHERE a.id = ?`. 상태 필터 없음 | `dao/AssignmentDao.java:41-48`, 호출 `web/DistributionController.java:53` |
| 읽기 | `AssignmentDao.findById` ← `DistributionService.distribute`(트랜잭션 안) | 위와 같음 | 호출 `service/DistributionService.java:48` |
| 쓰기 | 없음 | 범위 안에 `INSERT` · `UPDATE` · `DELETE assignment` 가 없다. 모듈 · `db/` 전체에서도 시드 INSERT(`02-seed.sql:119`) 말고는 찾지 못했다 | — |

`BASE_SELECT`(`dao/AssignmentDao.java:21-24`)는 `a.id, a.title, a.unit_id, u.code, u.name, a.due_at, a.status` 와 `class_cnt`(`SELECT COUNT(DISTINCT d.class_id) FROM distribution d WHERE d.assignment_id = a.id`)를 읽는다. `class_cnt` 는 `AssignmentRow.classCount` 에 담기지만(`:62`) 범위 안 코드와 `distribution_form.html` 은 쓰지 않는다.

### `class`

| 동작 | 파일 · 메서드 | SQL 요지 | 근거 |
|---|---|---|---|
| 읽기 | `ClassDao.findAll` ← `DistributionController.form` | ``SELECT id, name, teacher_id FROM `class` ORDER BY id ASC``. 폼의 학급 선택 목록 | `dao/ClassDao.java:20-22`, 호출 `web/DistributionController.java:43` |
| 읽기 | `ClassDao.findById` ← `DistributionService.distribute` | ``SELECT … FROM `class` WHERE id = ?`` | `dao/ClassDao.java:24-30`, 호출 `service/DistributionService.java:52` |
| 쓰기 | 없음 | 시드로만 들어간다(`02-seed.sql:111-114`) | — |

### `distribution`

| 동작 | 파일 · 메서드 | SQL 요지 | 근거 |
|---|---|---|---|
| 읽기 | `DistributionDao.countByAssignmentAndClass` ← `distribute` | `SELECT COUNT(*) FROM distribution WHERE assignment_id = ? AND class_id = ?` | `dao/DistributionDao.java:47-52`, 호출 `service/DistributionService.java:59` |
| 읽기 | `DistributionDao.nextId` ← `distribute` | `SELECT COALESCE(MAX(id), 0) FROM distribution` 에 1을 더함. `FOR UPDATE` 없음 | `dao/DistributionDao.java:54-57`, 호출 `service/DistributionService.java:62` |
| 쓰기 | `DistributionDao.insert` ← `distribute` | `INSERT INTO distribution (id, assignment_id, class_id, distributed_at, redistributed) VALUES (?, ?, ?, ?, 0)`. `distributed_at` 은 `AppClock.now()` | `dao/DistributionDao.java:59-63`, 호출 `service/DistributionService.java:63` |
| 읽기(서브쿼리) | `AssignmentDao` `BASE_SELECT` | 과제별 배포 학급 수 `class_cnt` | `dao/AssignmentDao.java:23` |

### 한눈에 보기

| 테이블 | `GET /distributions/new` | `POST /distributions` |
|---|---|---|
| `assignment` | R | R(컨트롤러) · R(서비스) |
| `unit` | R(LEFT JOIN) | R(LEFT JOIN, 2회) |
| `class` | R | R |
| `distribution` | R(`class_cnt` 서브쿼리) | R(`class_cnt` 2회, `COUNT`, `MAX`) · **C** |

R = SELECT, C = INSERT. 범위 안에 `UPDATE` · `DELETE` 가 없다.

## 4. 흐름 요약

- 폼: `assignment`(상태 `O` · `X` · `C`)와 `class` 전체를 읽어 선택 목록을 만든다(`web/DistributionController.java:42-43`).
- 등록: 컨트롤러가 과제를 한 번 읽어 마감을 검사한다(`web/DistributionController.java:53-65`). 이어서 `distribute` 가 한 트랜잭션 안에서 과제 · 학급을 다시 읽고, 같은 (과제, 학급) 배포 수를 센다. 그다음 `MAX(id)+1` 로 id 를 정해 `distribution` 을 `redistributed = 0` 으로 INSERT 한다(`service/DistributionService.java:46-68`).
- 배포 시각은 DB `NOW()` 가 아니라 `AppClock.now()` 값이다. 기본은 `2026-09-15 00:00:00` 고정이다(`AppClock.java:9`, `:19`).

## 5. 이관 때 확인할 관찰

버그인지 의도한 규칙인지는 판단하지 않았다.

| 관찰 | 근거 |
|---|---|
| (과제, 학급) 중복 방지가 DB 제약이 아니라 `COUNT(*) > 0` 검사다. 시드에는 이미 같은 쌍이 두 건 있다 | `dao/DistributionDao.java:46-52`, `01-schema.sql:92-103`, `02-seed.sql:133-134` |
| 새 배포 id 를 잠금 없이 `MAX(id)+1` 로 정한다(item-bank 의 `FOR UPDATE` 와 다름) | `dao/DistributionDao.java:55` |
| 배포 시각이 고정 시각(`2026-09-15 00:00:00`)으로 저장될 수 있다 | `AppClock.java:8-9`, `:14-19`, `service/DistributionService.java:63` |
| 코드가 쓰는 상태 값 `X` · `D` 가 스키마 주석에 없다 | `01-schema.sql:87`, `web/DistributionController.java:61`, `service/DistributionService.java:56` |

## 6. 미확인 목록

| 항목 | 이유 |
|---|---|
| `assignment.status` 를 `X`(연장) · `D`(삭제)로 바꾸는 주체 | 모듈 · `db/` 에서 `UPDATE assignment` 를 찾지 못했다. `legacy/grade-mssql` 에도 `'X'` 가 나오지만 별칭 `sub` 테이블의 `status` 비교다(`legacy/grade-mssql/sql/usp_aggregate_grades.sql:130`). `assignment.status` 와 같은 값인지는 확인하지 않았다 |
| 운영 DB 스키마가 `01-schema.sql` 과 같은지 | 모듈 안에 DDL 이 없고 compose 설정으로만 연결을 확인했다 |
| 동시에 두 요청이 들어올 때 `nextId` · 중복 검사 결과 | 트랜잭션 격리 수준 설정을 모듈에서 찾지 못했고(`application.properties:1-14`) 실행해 보지 않았다 |
