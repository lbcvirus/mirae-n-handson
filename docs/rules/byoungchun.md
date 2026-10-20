# 실 공통 규칙 후보 — 이병춘

- 작성일: 2026-10-13
- 출처: `.claude/settings.json`(permissions · hooks), `hooks/block-dangerous.mjs`, `hooks/secret-scan.mjs`, `scripts/hook-node.sh`
- 작성자 주 스택: Spring Boot · MariaDB · MySQL
- `.claude/settings.local.json`(개인 설정, `docker compose ps` allow)은 실 공통 후보가 아니라서 표에서 뺐다.

## 스택 코드

| 코드 | 뜻 |
|---|---|
| 공통 | 어느 스택에서든 뜻이 같은 규칙 |
| `SB` | Spring Boot · Gradle · Java |
| `NODE` | npm · React · TypeScript |
| `PY` | pip · Python |
| `DOCKER` | docker compose |
| `MARIADB` | MariaDB/MySQL 접속 MCP(`dbhub`) |
| `MSSQL` | MSSQL 접속 MCP(`dbhub-mssql`) |
| `PROJ` | 이 저장소 전용 MCP |

## 규칙 표

| 규칙 | 종류 | 적용 범위 | 막는 사고 또는 허용 이유 |
|---|---|---|---|
| `Bash(rm -rf *)` | deny | 공통 | 경로 실수 한 번으로 작업 트리 · 홈 디렉터리를 통째로 지우는 사고 |
| `Bash(sudo *)` | deny | 공통 | 시스템 설정 · 패키지 변경, 권한 상승으로 저장소 밖까지 영향 |
| `Bash(git push --force *)` | deny | 공통 | 원격 브랜치 이력 덮어쓰기로 다른 사람 커밋 유실 |
| `Bash(git push -f *)` | deny | 공통 | 위와 같음(짧은 옵션 형태) |
| `Bash(git reset --hard *)` | deny | 공통 | 커밋하지 않은 로컬 변경 영구 유실 |
| `Bash(*prod-db*)` | deny | 공통 | 운영 DB 호스트 접속 · 조회 · 변경 |
| `Bash(*prod.db*)` | deny | 공통 | 위와 같음(도메인 표기 형태) |
| `Bash(curl *)` | deny | 공통 | 코드 · 비밀값을 외부로 전송, 출처 불명 스크립트 내려받기 |
| `Bash(wget *)` | deny | 공통 | 위와 같음 |
| `Read(./.env)` | deny | 공통 | 비밀값이 컨텍스트에 들어가 로그 · 응답으로 새는 사고 |
| `Read(./.env.*)` | deny | 공통 | 위와 같음(`.env.local` 등) |
| `Read(./secrets/**)` | deny | 공통 | 비밀 파일 노출 |
| `Read(**/*.pem)` | deny | 공통 | 개인키 · 인증서 노출 |
| `Read(~/.ssh/**)` | deny | 공통 | SSH 개인키 노출 |
| `Read(~/.aws/**)` | deny | 공통 | 클라우드 자격 증명 노출 |
| `Edit(./.env)` | deny | 공통 | 접속 정보 임의 변경 · 비밀값 덮어쓰기 |
| `Edit(./.env.*)` | deny | 공통 | 위와 같음 |
| `Edit(./secrets/**)` | deny | 공통 | 비밀 파일 변조 |
| `mcp__github__delete_file` | deny | 공통 | 원격 저장소 파일 직접 삭제 |
| `mcp__github__merge_pull_request` | deny | 공통 | 사람 승인 없이 PR 머지 |
| `Bash(git push *)` | ask | 공통 | 원격 공개(외부 전송) 전에 사람이 브랜치 · 내용 확인 |
| `Bash(npm install *)` | ask | `NODE` | 의존성 추가 · lock 파일 변경(팀 규칙 4), 설치 스크립트 실행 |
| `Bash(npm i *)` | ask | `NODE` | 위와 같음(축약형) |
| `Bash(npm ci *)` | ask | `NODE` | 설치 스크립트(postinstall) 실행 확인 |
| `Bash(pip install *)` | ask | `PY` | 의존성 추가, 출처 불명 패키지 설치 |
| `Bash(docker compose down *)` | ask | `DOCKER` | 로컬 DB 등 실행 중인 컨테이너 중단(옵션에 따라 볼륨 삭제) |
| `Bash(docker compose --profile *)` | ask | `DOCKER` | 의도하지 않은 서비스 묶음 기동 · 포트 충돌 |
| `mcp__github__create_pull_request` | ask | 공통 | 원격에 공개되는 작업물 생성 전 확인 |
| `mcp__github__push_files` | ask | 공통 | `git push` 를 거치지 않는 원격 쓰기 |
| `mcp__dbhub` | ask | `MARIADB` | DB 조회 결과(개인정보 포함 가능)가 컨텍스트로 들어오기 전 확인 |
| `mcp__dbhub-mssql` | ask | `MSSQL` | 위와 같음 |
| `Bash(npm test *)` | allow | `NODE` | 로컬 테스트, 부작용 없음 |
| `Bash(npm run test *)` | allow | `NODE` | 위와 같음 |
| `Bash(npm run lint *)` | allow | `NODE` | 읽기 위주 정적 검사 |
| `Bash(npm run typecheck *)` | allow | `NODE` | 읽기 위주 타입 검사 |
| `Bash(npm run build *)` | allow | `NODE` | 로컬 산출물만 생성 |
| `Bash(./gradlew test *)` | allow | `SB` | H2 로 도는 로컬 테스트, 실 DB 접속 없음 |
| `Bash(./gradlew check *)` | allow | `SB` | 테스트 + 정적 검사 |
| `Bash(./gradlew build *)` | allow | `SB` | 로컬 산출물만 생성 |
| `Bash(git status *)` | allow | 공통 | 읽기 전용 |
| `Bash(git diff *)` | allow | 공통 | 읽기 전용 |
| `Bash(git log *)` | allow | 공통 | 읽기 전용 |
| `Bash(git add *)` | allow | 공통 | 로컬 스테이징, 되돌리기 쉬움 |
| `Bash(git commit *)` | allow | 공통 | 로컬 커밋. 시크릿은 secret-scan Hook 이 따로 막음 |
| `WebFetch(domain:docs.spring.io)` | allow | `SB` | 공식 문서 조회, 읽기 전용 |
| `WebFetch(domain:docs.gradle.org)` | allow | `SB` | 공식 문서 조회, 읽기 전용 |
| `WebFetch(domain:developer.mozilla.org)` | allow | `NODE` | 공식 문서 조회, 읽기 전용 |
| `mcp__github__get_file_contents` | allow | 공통 | 읽기 전용 |
| `mcp__github__list_pull_requests` | allow | 공통 | 읽기 전용 |
| `mcp__github__pull_request_read` | allow | 공통 | 읽기 전용 |
| `mcp__github__list_issues` | allow | 공통 | 읽기 전용 |
| `mcp__item-bank__search_items` | allow | `PROJ` | 이 저장소 전용 읽기 전용 검색 도구 |
| block-dangerous: `rm` + 재귀 + 강제 옵션 변형(`-rf` · `-fr` · `-r -f` · `-Rf` · `--recursive --force`) | Hook | 공통 | deny 패턴 `rm -rf *` 를 옵션 순서만 바꿔 피하는 삭제 |
| block-dangerous: `DROP TABLE` / `DROP DATABASE`(대소문자 무시) | Hook | 공통 | CLI 로 테이블 · DB 를 지우는 파괴 명령 |
| block-dangerous: `prod-db` / `prod.db` 문자열 | Hook | 공통 | 운영 DB 접속(deny 와 이중 방어, 파이프 · 서브셸 안까지 검사) |
| secret-scan: AWS Access Key ID(`AKIA…`) | Hook | 공통 | 클라우드 키가 커밋 이력에 남는 사고 |
| secret-scan: `password = '…'` 하드코딩 | Hook | 공통 | 비밀번호 리터럴 커밋 |
| secret-scan: PEM 개인키 헤더 | Hook | 공통 | 개인키 파일 · 문자열 커밋 |
| `scripts/hook-node.sh`: node 미발견 시 차단(fail-closed) | Hook | 공통 | node 가 PATH 에 없는 셸에서 Hook 이 조용히 꺼져 위 규칙이 무력화되는 사고 |

## 추가 제안 (Spring Boot · MariaDB · MySQL 에 필요한데 지금 없는 규칙)

| 규칙 | 종류 | 적용 범위 | 이유 |
|---|---|---|---|
| block-dangerous 에 `DROP SCHEMA` 추가 | Hook | `MARIADB` | MySQL · MariaDB 에서 `DROP SCHEMA` 는 `DROP DATABASE` 와 같은데 지금 정규식이 놓친다 |
| `TRUNCATE`, WHERE 없는 `DELETE FROM` · `UPDATE … SET`, `ALTER TABLE … DROP` 차단 | Hook | `MARIADB` | DROP 만 막으면 데이터 전체 삭제 · 컬럼 삭제는 그대로 통과한다 |
| `Bash(mysql *)` · `Bash(mariadb *)` · `Bash(mysqldump *)` | ask | `MARIADB` | MCP(`dbhub`)는 ask 인데 CLI 직접 접속은 아무 확인 없이 된다 |
| `Bash(./gradlew bootRun *)` | ask | `SB` | 테스트와 달리 `application.yml` 의 실제 DB 에 접속한다 |
| `Bash(./gradlew flywayClean *)` | deny | `SB` | 스키마 전체 삭제 |
| `Bash(./gradlew flywayMigrate *)` · `Bash(./gradlew update *)`(Liquibase) | ask | `SB` | 스키마 변경은 사람 확인 대상(CLAUDE.md 금지 사항) |
| `Bash(./gradlew publish *)` | deny | `SB` | 사내 · 외부 Maven 저장소로 산출물 전송 |
| `Edit(**/build.gradle*)` · `Edit(**/pom.xml)` | ask | `SB` | 의존성 추가 전 사람 확인(팀 규칙 4)을 도구 수준에서 강제 |
| `Edit(db/**/*.sql)` · `Edit(**/db/migration/**)` | ask | `MARIADB` | 스키마 · 시드 변경 전 사람 확인 |
| `Edit(**/application*.yml)` · `Edit(**/application*.properties)` | ask | `SB` | 접속 정보 · Hikari 설정 임의 변경 방지 |
| `Read(**/application-prod*.yml)` · `Read(**/application-prod*.properties)` | deny | `SB` | 운영 접속 정보 노출 |
| `Read(~/.m2/settings.xml)` · `Read(~/.gradle/gradle.properties)` | deny | `SB` | 사내 저장소 계정 · 토큰 노출 |
| `Read(~/.my.cnf)` · `Read(~/.mylogin.cnf)` | deny | `MARIADB` | MySQL 클라이언트 저장 비밀번호 노출 |
| secret-scan 에 따옴표 없는 `spring.datasource.password=…`, YAML `password: …`(`${…}` 제외) 추가 | Hook | `SB` | 지금 패턴은 `password = '…'` 처럼 따옴표가 있어야 잡혀서 Spring 설정 파일 형식을 놓친다 |
| secret-scan 에 `jdbc:(mysql\|mariadb)://user:pass@` · `?password=` 추가 | Hook | `MARIADB` | JDBC URL 에 박힌 비밀번호 탐지 |
| secret-scan 트리거를 "명령 안에 `git commit` 포함"으로 넓힘 | Hook | 공통 | `cd modern/api && git commit`, `git -C x commit` 은 "`git commit` 으로 시작" 조건을 피해 검사가 건너뛰어진다 |
| `Bash(./mvnw test *)` · `Bash(./mvnw verify *)` | allow | `SB` | Maven 을 쓰는 Spring 프로젝트용 로컬 테스트 |
| `Bash(./gradlew clean *)` | allow | `SB` | `rm -rf build` 대신 쓰는 안전한 정리 방법 |
| `Bash(docker compose down -v *)` | deny | `DOCKER` | 로컬 MariaDB 볼륨 삭제로 시드 · 실습 데이터 유실 |

## 주의 (다른 스택에 그대로 걸면 일을 막을 수 있는 규칙)

- **`scripts/hook-node.sh` fail-closed**: node 가 없는 PC(Java · PHP 전용 개발자)에서는 모든 Bash 명령이 막힌다. 실 공통으로 걸려면 node 설치를 전제 조건으로 공지하거나, Hook 을 셸 · 각 스택 런타임으로도 제공해야 한다.
- **`Bash(curl *)` · `Bash(wget *)` deny**: `curl localhost:8080/api/...` 같은 로컬 API 확인까지 막힌다. 백엔드 · 프런트 모두 불편하다. `Bash(curl http://localhost*)` 만 allow 하는 식의 예외가 필요하다.
- **`rm -rf` deny + Hook**: `rm -rf node_modules`(`NODE`), `rm -rf dist`, `rm -rf .terraform`(`terraform`), `rm -rf vendor`(`composer`) 같은 일상 정리가 막힌다. 각 도구의 clean 명령을 대안으로 안내해야 한다.
- **`*prod-db*` deny + Hook**: 명령 전체 문자열을 검사하므로 `grep -r prod-db`, 커밋 메시지에 `prod-db` 가 들어간 `git commit`, Terraform 리소스 이름 검색도 막힌다. 인프라 담당에게 특히 영향이 크다.
- **`DROP TABLE` Hook**: `grep -r "DROP TABLE" legacy/` 같은 레거시 SQL 분석(`MSSQL` 저장 프로시저 포함)과 로컬 개발 DB 초기화 스크립트 실행도 막힌다.
- **secret-scan `password = '…'`**: PHP · Python · JS 의 테스트 픽스처에 있는 더미 비밀번호(`app-pass` 등)도 커밋을 막는다. 허용 목록(실습 더미 값) 또는 테스트 경로 제외가 필요하다.
- **`Bash(npm ci *)` ask**: CLAUDE.md 가 표준 설치 명령으로 정한 명령인데 매번 확인을 받는다. `NODE` 팀은 속도가 떨어진다. lock 파일을 바꾸지 않는 `npm ci` 는 allow 로 옮기는 것을 검토할 만하다.
- **`Read(**/*.pem)` deny**: 인프라 담당이 공개 인증서(`.pem`)를 확인하는 일도 막힌다.
- **`Bash(docker compose --profile *)` ask**: 로컬 실행 환경을 프로필로 나눈 팀은 기동할 때마다 확인을 받는다.
- **`Bash(git commit *)` allow**: secret-scan Hook 이 함께 걸려 있다는 전제의 allow 다. Hook 없이 allow 만 가져가면 시크릿 커밋을 막을 장치가 없다.
