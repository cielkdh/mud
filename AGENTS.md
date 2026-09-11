# Gradle 실행 정책

이 프로젝트에서 Gradle Wrapper를 사용하는 명령(`gradlew`, `gradlew.bat`, `./gradlew`, `.\gradlew.bat`)은 다음 정책을 따른다.

- 현재 sandbox 환경에서는 Wrapper 다운로드, network, loopback 및 Gradle Daemon 제약으로 반복적인 실행 실패가 확인되었다.
- Gradle 관련 명령은 sandbox에서 먼저 시험 실행하지 않는다.
- build, test, check, lint, assemble, AndroidTest 등 Gradle Wrapper 기반 명령은 처음부터 기존에 성공한 권한 경계 밖 실행 방식으로 실행한다.
- `sandbox 실행 → 실패 → 동일 명령 권한 경계 밖 재실행` 절차를 반복하지 않는다.
- Gradle 자체의 컴파일 오류, 테스트 실패 및 task 실패는 sandbox 제약에 따른 실행 실패와 구분해 보고한다.
- 이 정책은 이후 Codex 작업에도 지속적으로 적용한다.
