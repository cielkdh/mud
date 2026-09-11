# Phase0 cancellation Gate 재검증 — 2026-09-11

- 승인 revision: `714941d` (`Fix WorldSession cancellation recovery`)
- 승인·증거 record: `dacb508` (`Record Phase0 cancellation gate acceptance`)
- 독립 리뷰: ACCEPT — P0-CN-001 취소/종료 계약 충족
- 관리 생명주기: `P0-TASK-005`, `P0-TASK-010`, `P0-TASK-015`, `P0-TASK-020`, `P0-TASK-021`, Phase0 모두 `DONE`; Phase0 책임자는 사용자의 명시적 자기 지정에 따라 `사용자`, 독립 리뷰어는 `Codex`
- 실행 환경: JDK 17, Gradle Wrapper 9.6.0, 저장소 `.android-sdk`, API 36 emulator `emulator-5554`

## 수정 범위

- `SavePort.findReceipt()` 또는 `commit()`의 `CancellationException`은 호출자에게 전파하되 consumer는 다음 요청을 계속 처리한다.
- commit 직후 취소는 non-cancellable durable receipt 조회로 결과를 확인하고, 확인된 accepted delta의 aggregate/RNG/stateVersion을 게시한다.
- receipt 조회가 취소·실패하거나 receipt가 불일치하면 session을 중지하고 queued reply를 모두 예외 완료한다.
- parent scope 취소도 queue를 닫고 미시작 요청을 완료한다. `close()`의 join·lifecycle·completion 정리는 non-cancellable 구간에서 수행한다.

## Gate 재실행

```text
JAVA_HOME=C:\Program Files\Java\jdk-17.0.121
JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=<repo>\.gradle\disabled-unix-domain-socket
.\gradlew.bat --no-daemon --offline --max-workers=1 --rerun-tasks verifyPhase0Architecture :core:simulation:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:connectedDebugAndroidTest
```

| 검증 | 결과 |
| --- | --- |
| `verifyPhase0Architecture` | PASS — 종료 전 모든 후속 task가 실행됐고 architecture fixture 실패 없음 |
| `WorldSessionTest` | PASS — 16 tests, failures 0, errors 0 |
| `:app:lintDebug` | PASS — errors 0, warnings 2 |
| debug/release APK | PASS — 2026-09-11 재생성 |
| API 36 instrumentation | PASS — `AppRootTest` 2 tests, failures 0, errors 0, exit code 0 |
| cold launch | PASS — `com.imsi.mud/.MainActivity`, COLD, 1.200s; `Feature unavailable` / `SCR-START-001` 확인, crash buffer 0 |

`build/` 하위 결과는 재생성 산출물이다. 이 문서와 `WorldSession` 코드·회귀 테스트가 revision 보존 근거다.
