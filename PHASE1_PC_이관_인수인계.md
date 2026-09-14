# Phase 1 다른 PC 이관 인수인계

> 최초 작성일: 2026-09-13 / 최종 갱신일: 2026-09-14 (Asia/Seoul)
> 현재 프로젝트: `C:\ai\mud`
> 기준 브랜치/HEAD: `main` / `4f60327157dffe891749a6e0f1234c321f5afc0d` + `LOCAL-WORKTREE`
> 현재 상태: `DONE`
> 개발리더 최종 판정: `APPROVED WITH CONDITIONS` — Phase 1 `PROTOTYPE_ACCEPTED`, P0/P1 0건, `BLOCKED_ASSET` 유지

## 0. 최종 완료 업데이트

- 최종 `phase1Gate`: `BUILD SUCCESSFUL` (10분 25초, 225 tasks)
- Gate runId: `beed72c8-f2ca-41b3-afe7-74ea66a0bc7c`, Phase 1 Test 27/27 PASS
- Android runId: `41c8f027-e851-45e5-ae60-d7319ff70583`, API 36 instrumentation 7/7 PASS
- P1-PT-001: decode 8종, bundle 16종, cache hit 24회, PSS 123,870/123,891 KiB
- P1-IT-003: preview HTML, validation report, SQLite DB, query-plan 보존 및 aggregate artifact SHA 8/8 일치
- 관리 상태: Phase 0+1 Task 42 DONE, Test 35 PASS, 경고 0, 문서 검증 51/51 PASS
- 독립 판정: 디자이너 `APPROVED`, QA `APPROVED WITH CONDITIONS`, 수석 기술 리뷰어 `APPROVED WITH CONDITIONS`
- 후속 조건: 실제 screenshot·TalkBack·MIN/STD 실기기와 실물 10,000 자산·라이선스·PAD·오프라인 설치는 P22~P25에서 검증한다. 실물 자산 승인 전 모든 usage crop·고정 썸네일 범위를 확정하고, 관리데이터의 공통 `actual` 문구는 JVM/Android 실행환경별로 세분화한다. 그 전까지 `FULL_CONTENT_READY`를 주장하지 않는다.

## 1. 새 PC에서 가장 먼저 할 일

1. 이 문서와 `AGENTS.md`를 먼저 읽는다.
2. 공식 설계서와 관리데이터를 확인한다.
   - `docs/02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md`
   - `docs/00*`, `docs/80*`~`docs/88*`, `docs/90*`~`docs/94*`
   - `docs/관리데이터/decisions.json`, `tasks.json`, `tests.json`, `functions.json`, `schema_registry.json`
3. `.codex-team/*.md`를 읽고 역할별 문맥을 복구한다.
4. 현재 작업트리는 Phase 1 변경이 **대량으로 미커밋된 상태**다. 단순히 원격 저장소를 clone/pull하면 이어받을 수 없다. 이 폴더 전체 또는 별도로 만든 WIP commit/patch를 새 PC로 먼저 전달해야 한다. 이 문서는 변경을 commit하거나 push하지 않았다.
5. 팀 Task가 동시에 Gradle/ADB를 실행하지 않는지 확인한 뒤 검증한다. 이 PC에서는 QA와 수석 기술 리뷰어가 마지막 순간까지 작업 중이었고, 병렬 `connectedDebugAndroidTest` 실행끼리 Windows logcat 파일 잠금 충돌이 발생했다.

## 2. 현재 개발환경 기준

- OS: Windows 10 (`10.0.19045`)
- JDK: OpenJDK 17.0.18
- Android SDK: `C:\Users\kdh\AppData\Local\Android\Sdk`
- ADB: 37.0.1, 마지막 확인 장치 `emulator-5554`, Android API 36
- Gradle: 프로젝트의 `gradlew.bat`만 사용한다. 시스템 Gradle 7.4.2는 `native-platform.dll` 초기화 실패가 있으므로 사용하지 않는다.
- Python: 현재 셸의 `python`, `py`, `python3`는 PATH에 없었다. 기존 Gate는 `build.gradle.kts`의 Windows Python 탐색 fallback으로 실행됐지만, 새 PC에는 Python 3를 명시적으로 설치하고 `py -3` 또는 `python` 실행을 확인하는 편이 안전하다.
- `AGENTS.md` 정책에 따라 모든 Gradle Wrapper 명령은 sandbox에서 시험하지 말고 처음부터 승인된 권한 경계 밖에서 실행한다.

## 3. Phase 1 목표와 승인 기준

Phase 1은 콘텐츠 canonical source, typed definition, sealed SQLite bundle, 자산 검증·복사·manifest, atomic publish/recovery, Android local asset resolution과 Coil cache, 문서·증거 Gate를 하나의 재현 가능한 파이프라인으로 완성하는 단계다.

최종 승인은 다음을 모두 만족할 때만 가능하다.

- 모든 필수 Task 구현
- Unit/Component/Integration/Exception/Boundary/Regression PASS
- Build 및 Lint/Static Analysis PASS
- Android API 36 instrumentation PASS
- 콘텐츠/자산 Validator PASS
- 27개 Phase 1 Test ID의 fresh evidence PASS
- 미해결 P0/P1 없음
- 설계와 실제 구현 일치
- Phase Goal 및 DoD 충족
- QA 독립 승인 및 수석 기술 재리뷰 통과

## 4. 완료된 주요 구현

### 콘텐츠·빌더

- `core:content`, `core:image`, `tools:content-builder` 모듈 및 관련 Gradle graph 구성
- 20종 canonical content source, 3,448행 변환·검증, definition/version/hash 계약
- 표시명 및 unresolved 설명성 필드를 제외하고 gameplay 의미를 보존하는 `DefinitionHash.v1` projection
- SQLite sealed DB, integrity/FK/query-plan 검증, manifest·pointer 기반 atomic publish
- crash killpoint, orphan quarantine, pointer/manifest/DB hash를 포함하는 recovery 증거
- 실제 CLI subprocess 기준 자산 오류 코드 검증:
  - `INVALID_ASSET_PATH`
  - `MISSING_REQUIRED_ASSET`
  - `ASSET_DECODE_FAILED`

### 자산 경계

- PNG/WebP decode 및 asset manifest/fallback/binding 검증
- Windows: JNA `CreateFile` 및 `GetFinalPathNameByHandleW` 기반 stable handle 검증과 동일 handle hash/copy
- 비-Windows: `SecureDirectoryStream` 및 no-follow 기반 복사
- asset root/parent ABA 교체를 fail-closed 처리하고 동일 바이트 교체 회귀 테스트 추가

### Android runtime

- local-only asset resolver 및 디버그 gallery
- Coil memory cache 64 MiB hard bound, weak reference/disk cache 비활성, bundle 전환 시 cache clear
- P1-PT instrumentation을 PNG/WebP × FULL/LOW × 128/1024의 8개 Cartesian 조합으로 수정
- 두 immutable bundle에서 총 16개 bundle-combination 실행 확인
- decode workload 도중 PSS, cache hit, 조합 수를 `P1_PT_METRICS` log marker로 남기도록 수정
- `android-performance.json`이 idle/relaunch PSS가 아니라 instrumentation decode workload marker에서 생성되도록 Gradle task 수정

### Gate·문서

- `phase1Gate`, `phase1Evidence`, architecture/document/evidence semantic validation 구성
- 증거 freshness, save 불변 sentinel, artifact hash 및 Test ID별 semantic field 검증
- 관련 설계서, 결정대장, 데이터사전, Task/Test 관리데이터 보완

## 5. 최초 이관 시점에 확인한 결과 — 역사 기록

### 최신 소스 기준 PASS

다음 최소 컴파일은 2026-09-13 마지막 정리 시점에 성공했다.

```powershell
.\gradlew.bat :tools:content-builder:compileTestKotlin :app:compileDebugAndroidTestKotlin tasks --no-daemon --console=plain
```

- 결과: `BUILD SUCCESSFUL`
- `tools:content-builder` main/test Kotlin 컴파일 PASS
- Android debug instrumentation Kotlin 컴파일 PASS
- Gradle root script 구성 PASS
- 경고만 존재:
  - `ContentRepository.kt`: Java `Object` 사용 권고 경고
  - `DebugAssetGalleryTest.kt`: Compose `createComposeRule` v1 deprecation

다음 집중 테스트도 최신 자산 보완 코드에서 PASS했다.

```powershell
.\gradlew.bat :tools:content-builder:test --tests "*assets CLI process*" --tests "*asset root replacement*" --tests "*parent link swap*" --rerun-tasks --no-daemon --console=plain
```

### 과거 전체 Gate PASS — 최신 증거로 사용 금지

- 이전 `phase1Gate`는 약 8분 동안 225-task graph를 실행해 27/27 PASS와 Android API 36의 7/7 PASS를 만들었다.
- 현재 `build/reports/phase1/phase1-test-evidence.json`은 2026-09-13 22:56:39의 27/27 PASS 산출물이다.
- 현재 `build/reports/phase1/android-performance.json`은 2026-09-13 23:12:14 산출물이지만 **구 스키마**이며 `measurementSource`, `decodeCombinationCount`, `bundleCombinationCount`, `memoryCacheHitCount`, `workloadLog`가 없다.
- 이후 P1-PT workload 계측 코드와 Gradle semantic gate가 변경됐으므로 위 산출물은 stale이다.
- 마지막 전체 Gate 재시도는 코드 실패가 아니라 Android test logcat 파일에 대한 Windows 동시 접근 잠금으로 `connectedDebugAndroidTest`가 실패했다. 병렬 실행을 제거한 뒤 반드시 다시 실행해야 한다.

## 6. 최초 이관 시점 미해결 문제 — 모두 해결됨

아래 항목은 이관 당시의 문제 기록이다. 최종 완료 상태는 0절을 기준으로 한다.

### [해결 P1] 동일 global hash가 definition tuple 검증을 우회함

파일: `core/content/src/main/kotlin/com/imsi/mud/content/ContentCompatibility.kt`

현재 `ContentCompatibilityPlanner.plan()`은 다음 조건에서 즉시 `Compatible`을 반환한다.

```kotlin
if (saved.logicalContentHash == installed.identity.logicalContentHash) return BindingPlan.Compatible
```

하지만 `ContentCompatibilitySnapshot.create()`는 전달된 global hash와 `requiredDefinitions` tuple 집합의 상호 일관성을 증명하지 않는다. 따라서 동일 global hash를 가진 손상·불일치 save snapshot이 실제 `(kind, id, definitionVersion, definitionHash)` 변경을 legacy provenance 없이 통과할 수 있다.

필수 수정:

- global hash가 같아도 모든 saved definition tuple이 installed tuple과 정확히 같을 때만 fast-path `Compatible`을 허용한다.
- 하나라도 다르면 기존 fail-closed 판단 경로를 계속 실행한다.
- `동일 logicalContentHash + 불일치 tuple + legacy 증거 없음 -> BindingPlan.Unsupported` 회귀 테스트를 추가한다.
- 수정 후 `:core:content:test`와 전체 `phase1Gate`를 재실행한다.

### [해결 P1] 최신 P1-PT runtime evidence 미확정

- 8/16 Cartesian 조합 및 in-workload PSS 코드는 컴파일됐지만 emulator에서 최신 코드로 아직 성공 실행되지 않았다.
- 새 `android-performance.json` 필드와 raw workload log의 marker가 실제로 서로 일치하는지 확인해야 한다.
- 수석 기술 리뷰어의 최종 재판정도 아직 없다.

### [P2] 범위 밖 후속

- typed AST evaluator와 quest/crafting runtime consumer는 이후 Phase 범위다.
- PROTOTYPE의 unresolved/disabled content는 후속 의미 확정 전 활성화하지 않는다.

### [P3] 비차단 후속

- production bundle activation 통합은 Phase 3/P22에 연결한다.
- Compose test API v2 전환과 Kotlin `Object` 경고 정리는 별도 기술부채로 남길 수 있다.

## 7. 최초 이관 재개 순서 — 완료됨

### 7.1 P1 수정 및 집중 검증

1. `ContentCompatibilityPlanner`의 hash-only fast path를 tuple 일치 조건으로 제한한다.
2. 동일 hash/불일치 tuple 회귀 테스트를 추가한다.
3. 다음을 실행한다.

```powershell
.\gradlew.bat :core:content:test --rerun-tasks --no-daemon --console=plain
.\gradlew.bat :tools:content-builder:test --tests "*assets CLI process*" --tests "*asset root replacement*" --tests "*parent link swap*" --rerun-tasks --no-daemon --console=plain
```

### 7.2 Android 단독 검증

다른 Gradle/ADB/QA Task가 실행 중이지 않은 상태에서 API 36 emulator 하나만 연결한다.

```powershell
adb devices
.\gradlew.bat :app:connectedDebugAndroidTest --rerun-tasks --no-daemon --console=plain
```

확인값:

- Android instrumentation: 7/7 PASS
- logcat marker: `P1_PT_METRICS`
- `decodeCombinationCount=8`
- `bundleCombinationCount=16`
- `memoryCacheHitCount > 0`
- `coilCacheMaxBytes=67108864`
- `sampleCount >= 3`
- steady PSS `<= 393216 KiB`
- transient peak PSS `<= 524288 KiB`

### 7.3 전체 Gate

Android 단독 검증이 끝나고 파일 잠금이 해제된 뒤 한 프로세스만 실행한다.

```powershell
.\gradlew.bat phase1Gate --no-daemon --console=plain
```

반드시 확인할 fresh artifact:

- `build/reports/phase1/phase1-test-evidence.json`: 27개 모두 PASS
- `build/reports/phase1/android-performance.json`:
  - `measurementSource = INSTRUMENTATION_DECODE_WORKLOAD`
  - 조합 수 8/16
  - cache hit 양수
  - PSS 상한 준수
  - `workloadLog`가 `app/build/outputs/androidTest-results/` 아래 안전한 상대경로
- Android result XML: 7 tests, failures/errors 0
- 최신 코드 수정시각보다 모든 필수 evidence가 새로워야 함

### 7.4 독립 리뷰와 승인

1. 고급개발자에게 save compatibility P1 수정 재리뷰를 요청한다.
2. 수석 기술 리뷰어에게 전체 diff, fresh Gate, P1-PT raw log/artifact를 전달해 최종 기술 판정을 받는다.
3. QA에게 정상·경계·실패·복구·회귀 및 emulator 증거를 기준으로 독립 최종 판정을 요청한다.
4. 개발리더가 설계와 실제 코드·결과를 직접 대조한다.
5. 미해결 P0/P1이 0일 때만 `APPROVED`한다.

## 8. 팀 Thread와 복구 규칙

현재 환경에서 우선 재사용한 Thread:

- 디자이너: `codex://threads/01a08f8f-6ad7-7de1-955d-f50e10183a66`
- QA: `codex://threads/01a08f84-2809-7c21-9651-dadac06b9c93`
- 고급개발자: `codex://threads/01a088f7-d739-7331-864e-1f57d19bd845`
- 일반개발자: `codex://threads/01a08f82-fcd8-7441-8031-b24dc9b6c7d7`
- 게임 시스템 디자이너: `codex://threads/01a09d70-511b-7b82-8741-406cbe0d28f8`
- 수석 기술 리뷰어: `codex://threads/01a09d6e-e267-7b81-a83e-f9d6c9152b73`

새 PC에서 URI 접근이 안 되면 완료를 막지 말고 동일 역할 이름으로 프로젝트 checkout에 새 Task를 만든다. 역할·책임, 공식 설계 우선, 작업 배정 계약, 최종 승인 권한은 `AGENTS.md`대로 유지한다. 새 URI와 변경 사유는 `.codex-team/<role>.md`에 기록한다.

최종 팀 상태:

- 디자이너: crop·Exact/Fallback·CLI preview 경로 재검토 후 `APPROVED`.
- 게임 시스템 디자이너: `APPROVED WITH CONDITIONS`; 실물 자산과 P25 Full/RC는 후속.
- 고급개발자: preview/evidence/tool identity P1 수정 및 focused 검증 완료.
- QA: 고정 Thread가 반복해 빈 최종 응답으로 종료되어 대체 독립 QA를 수행했고 `APPROVED WITH CONDITIONS`.
- 수석 기술 리뷰어: 실제 artifact·SHA와 builder v2 재검토 후 `APPROVED WITH CONDITIONS`; P0/P1 0건.
- 일반개발자: compatibility tuple 및 canonical byte/Android semantics 구현은 코드와 전체 Gate로 확인했으나 고정 Thread의 최종 Chat 응답은 비어 있었다.

## 9. 변경 영역 요약

현재 작업트리는 다음 범주가 변경 또는 신규 생성됐다.

- Root/모듈 Gradle, version catalog, dependency lock
- `core/content`, `core/image`, `tools/content-builder`
- Android manifest/application/debug gallery/instrumentation assets/tests
- canonical `content/` source와 `phase1-fixtures/`
- Phase 1 및 연관 전역 설계서·결정대장·관리데이터·검증도구
- simulation의 immutable content snapshot 연결
- `.codex-team/` 역할 인수인계
- 이 문서

대량 변경이므로 새 PC에서 광범위 format, `git add .`, reset/clean/stash, branch/worktree 전환을 하지 않는다. 먼저 `git status --short`와 `git diff --check`로 전달 무결성을 확인하고, 담당 파일만 정확히 다룬다.

## 10. DoD 현황

- [x] Phase 1 설계 및 관련 전역 문서 검토
- [x] 핵심 콘텐츠/자산/builder/runtime 구조 구현
- [x] 콘텐츠 및 자산 validator 구현
- [x] atomic publish/recovery 구현
- [x] Coil cache bound 및 bundle boundary 구현
- [x] Gate/evidence semantic validation 구현
- [x] 최신 소스의 JVM/Android test Kotlin 최소 컴파일
- [x] save compatibility 동일-hash tuple 우회 P1 수정
- [x] 최신 Android instrumentation 7/7 PASS
- [x] 최신 in-workload PSS evidence PASS
- [x] 최신 전체 `phase1Gate` 27/27 PASS
- [x] Build/Lint/Regression의 최종 fresh evidence 확인
- [x] 고급개발자 구현·focused 검증 및 개발리더 재리뷰
- [x] 수석 기술 리뷰어 최종 승인 — 비차단 조건 포함
- [x] QA 독립 최종 승인 — 비차단 조건 포함
- [x] 미해결 P0/P1 0건 확인
- [x] 개발리더 Phase Goal/DoD 최종 승인

## 11. 최종 보고 형식

완료 시 다음 순서로 보고한다.

- Phase 1 전체 상태
- 팀원별 수행 작업
- 완료 Task / 미완료 Task
- 변경 파일
- 주요 구현 내용
- Test 결과
- 발견된 `[P0]`~`[P3]`
- 해결한 문제
- 남은 Risk / Technical Debt
- 설계 대비 변경사항
- Goal 달성 여부
- DoD 체크리스트
- QA 판정
- 개발리더 최종 판정: `APPROVED / CONDITIONAL APPROVAL / REJECTED`

현재 판정은 **`APPROVED WITH CONDITIONS`**다. Phase 1 `PROTOTYPE_ACCEPTED` Goal/DoD는 완료됐으며 다음 개발 Phase를 진행할 수 있다. 단, 실제 자산·실기기·production UI 검증 전에는 `FULL_CONTENT_READY` 또는 출시 승인을 주장하지 않는다.
