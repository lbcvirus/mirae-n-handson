`docs/project/DESIGN.md` 의 단계대로 만든 `spec-project/` 변경분을 `/verify` 가 tester(`./gradlew test` 실행)와 reviewer(`templates/approval-checklist.md` 네 기준 대조)에 나눠 맡겨 머지 전 승인 · 반려 판정까지 자동으로 낸다.
`cd spec-project && ./gradlew test` 로 테스트를 돌린 뒤 Claude Code 에서 `/verify day4-start..HEAD` 를 실행한다.
지금은 1단계(엔티티 · 리포지토리 · 시드)까지만 있어 테스트가 `HealthControllerTest` 1건뿐이고 DESIGN.md 4번 질문 2~7 과 modern/api 컨벤션의 골격 적용 여부가 미해결이며, 현업에 옮길 때는 H2 메모리 DB(`ddl-auto: create-drop`)를 팀 DB 와 스키마 관리 방식으로, 저장소 안 `spec-project/` 위치를 별도 저장소로 바꿔야 한다.
