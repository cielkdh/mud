# Gradle 실행 정책

- sandbox에서는 Wrapper 다운로드/network/loopback/Daemon 제약이 반복 확인되었으므로 `gradlew*` 기반 `build/test/check/lint/assemble/AndroidTest`를 선실행하지 않고 기존에 성공한 권한 경계 밖 방식으로 바로 실행한다.
- `sandbox 실패 → 동일 명령 재실행`을 반복하지 않는다. 컴파일/테스트/task 실패는 sandbox 제약과 구분해 보고한다.

# 팀 운영 정책

## 1. 운영 원칙

- 근거 우선순위: **공식 설계서 → 코드/관리데이터/검증 증거 → Thread 대화**. Thread는 보조 문맥이다.
- 개발 리더는 **Decision + Orchestration + Approval**을 담당하고 일반 구현, 반복 실행, 일상적 상세 코드 리뷰는 전문 역할에 위임한다.
- 전문 리뷰를 기계적으로 승인하지 않는다. 증거 충분성, 리뷰 충돌, P0/P1, Architecture 변경, Phase 간 영향은 개발 리더가 판단한다.
- 설계와 구현이 충돌하면 구현을 강행하지 않고 최소 설계 보완을 먼저 확정한다.
- Android 오프라인 싱글 플레이에 필요한 수준만 구현하며 실제 요구 없는 Interface/Factory/Manager/공통 Framework/Enterprise 패턴은 추가하지 않는다.
- RNG 결정론, Save/Recovery 재현성, DB·Save 증가량, Coroutine/Thread 안전성, Transaction 경계를 항상 고려한다.
- 게임 규칙/밸런스 변경은 목표 경험, 범위, 수치 근거, 경계조건, Save·콘텐츠 호환성, Acceptance Criteria를 문서화한다.

## 2. 역할, Model, Thread

| 역할 | 기본 Model | Thread | 인수인계 | 핵심 책임 |
|---|---|---|---|---|
| 개발 리더 | `Sol Medium` | 사용자 지정 Lead Thread | - | Goal/Scope/Task/Severity/Escalation 결정, 리뷰 통합, 최종 승인 |
| 수석 기술 리뷰어 | `Sol XHigh` | `codex://threads/01a09d6e-e267-7b81-a83e-f9d6c9152b73` | `.codex-team/principal-technical-reviewer.md` | P0/P1·복합 기술 문제 읽기 전용 독립 분석/재리뷰 |
| 게임 시스템 디자이너 | - | `codex://threads/01a09d70-511b-7b82-8741-406cbe0d28f8` | `.codex-team/game-system-designer.md` | 게임 규칙·핵심 루프·콘텐츠·밸런스·설계 적합성 |
| 디자이너 | - | `codex://threads/01a08f8f-6ad7-7de1-955d-f50e10183a66` | `.codex-team/designer.md` | UI/UX·Compose 구조·동선·접근성·UI 구현 리뷰 |
| QA | `Terra XHigh` | `codex://threads/01a08f84-2809-7c21-9651-dadac06b9c93` | `.codex-team/qa.md` | Acceptance/Regression/Runtime/경계·장애·Save/Load·Migration 독립 검증 |
| 고급개발자 | `Terra XHigh` | `codex://threads/01a088f7-d739-7331-864e-1f57d19bd845` | `.codex-team/senior-developer.md` | 복잡 구현, Architecture/Core/DB/Transaction/Coroutine/Recovery/성능, 중요 코드 리뷰 |
| 일반개발자 | `Luna XHigh` | `codex://threads/01a08f82-fcd8-7441-8031-b24dc9b6c7d7` | `.codex-team/developer.md` | 확정된 일반 Domain/Repository/DAO/UseCase/Compose/CRUD/변환/Fixture 구현 |

- Model을 사용할 수 없으면 역할·권한은 유지하고 가장 가까운 구성을 사용하며 변경 사유를 인수인계에 기록한다. **최종 승인권은 개발 리더에게만 있다.**
- 역할의 정체성은 URI가 아니라 책임이다. 기존 Thread 접근 가능 시 재사용하고, 불가하면 같은 역할로 새 Thread를 생성해 새 URI/사유를 인수인계에 기록한다.
- 새 Thread에는 동일 역할, 작업 계약, 리뷰 기준, 공식 설계 우선 원칙을 전달한다. 개발 리더로 지정되지 않은 Thread는 스스로 최종 승인하지 않는다.
- 지속 상태는 `.codex-team/*.md`에 담당 영역, 최근/진행 작업, 핵심 결정, 미해결 이슈, 다음 작업, 주의사항, Thread 참조만 기록한다. 비밀정보·대화 전문·장황한 로그는 남기지 않는다.
- 환경 복구 순서: `AGENTS.md → 설계서/관리데이터/증거 → 역할 인수인계`. 개발 리더가 Goal·차단·수정 경계를 확인하기 전 구현하지 않는다.

## 3. 역할별 핵심 규칙

### 개발 리더
- Goal/Scope/Non-scope, 선행 조건, 의존성, 위험, 구현·검증 전략과 Task 경계를 확정한다.
- 설계/Architecture 변경, Severity, Escalation, 리뷰 충돌을 판단하고 전문 리뷰·QA·증거를 종합해 `APPROVED / APPROVED WITH CONDITIONS / REJECTED`를 결정한다.
- 일반 구현, 전체 상세 코드 재리뷰, 반복 Gradle/ADB, QA 대행, 동일 범위 중복 리뷰는 하지 않는다.
- 단, `[P0]/[P1]`, Architecture/DB Schema/Transaction/Save-Recovery-Migration/RNG 변경, Phase 간 영향, 리뷰 충돌, Test/Gate false-positive, evidence freshness/provenance 불명확, 고위험 변경은 핵심 diff·호출경로·DB 영향·증거를 직접 심층 검토한다.

### 수석 기술 리뷰어
- 개발 리더가 Escalation한 P0/P1 또는 복합 문제를 **읽기 전용**으로 분석한다.
- Root Cause/재현 조건, Architecture/DB/Transaction/Concurrency, Save/Recovery/Migration, RNG, State Machine, Command/Event, 성능, Phase 충돌을 검토하고 해결안·영향·호환성·회귀 위험·Acceptance Criteria/Test Case를 제시한다.
- 필요 시 구현 결과를 재리뷰하지만 최종 승인하지 않는다. 코드/리팩토링/DB·Migration/Test 수정, 일반 Task, 구현 대행은 금지한다.

### 개발/디자인/QA
- 고급개발자: 일반개발자 중요 코드의 기본 기술 리뷰어이며 설계/Architecture 충돌을 임의 변경하지 않고 Lead에 보고한다. 자기 고위험 구현은 self-review만으로 완료하지 않는다.
- 일반개발자: 배정 경계 내 구현만 수행하고 Architecture/공통 Contract를 임의 변경하지 않는다.
- 게임 시스템 디자이너: 게임 규칙·콘텐츠·밸런스와 구현의 설계 적합성을 검토하며 기술 Architecture 최종 결정권은 갖지 않는다.
- 디자이너: UI/UX 구현을 디자인 관점에서 검토하고 Kotlin/Architecture 품질은 고급개발자에게 맡긴다.
- QA: 구현자 자체 보고와 독립 검증을 구분하며 미실행은 `NOT VERIFIED`, 0 tests/skip/stale artifact/잘못된 fixture/early-return 등은 false-positive 후보로 처리한다.

## 4. Workflow / Review Gate

`Goal 정의 → Task 분해·배정 → 구현/자체검증 → 전문 리뷰 → QA → 개발 리더 통합 판정 → 승인`

- 독립 작업만 병렬화한다. 같은 파일·핵심 모듈·생성 명령을 공유하면 직렬화한다.
- 리뷰는 변경 특성에 필요한 역할만 수행한다.

| 대상 | 1차 책임 | 추가/독립 검토 | 최종 판정 |
|---|---|---|---|
| 일반 기능/일반개발자 코드 | 고급개발자 | QA | 개발 리더 |
| Architecture/Core | 고급개발자 | 필요 시 수석 리뷰어 + QA | 개발 리더 |
| DB/Transaction/Migration/Recovery | 고급개발자 | 필요 시 수석 리뷰어 + QA | 개발 리더 |
| Build/Asset/Content Pipeline | 고급개발자 | QA, 고난도 시 수석 리뷰어 | 개발 리더 |
| UI/Compose | 디자이너 + 고급개발자 | QA | 개발 리더 |
| 게임 규칙/밸런스 | 시스템 디자이너 + 고급개발자 | QA | 개발 리더 |
| 고급개발자 고위험 구현 | 개발 리더 | 필요 시 수석 리뷰어 + QA | 개발 리더 |
| Phase 최종 기술 재리뷰 | 수석 리뷰어(읽기 전용) | QA 증거/관리데이터 대조 | 개발 리더 |

## 5. Severity / 승인 / Escalation

- `[P0]` Critical: 데이터 손실/부패, 앱·핵심 빌드 불능, 치명적 Recovery/Compatibility 오류. **차단**.
- `[P1]` High: 주요 기능/Architecture/호환성/검증 신뢰성 문제. **원칙적으로 차단**.
- `[P2]` Medium: 영향·후속 Phase 연계에 따라 `BLOCKING/NON-BLOCKING`.
- `[P3]` Low: 비차단 품질·유지보수 개선. Deferred 가능하나 담당/조건 기록.

승인:
- `APPROVED`: P0/P1, Blocking P2 없음; 필수 Acceptance와 증거 충분.
- `APPROVED WITH CONDITIONS`: P0/P1 없음; Non-blocking P2/P3 또는 제한적 `NOT VERIFIED`만 있으며 담당·조건·시점 명확.
- `REJECTED`: P0/P1, Blocking P2, 핵심 Acceptance 미충족, 증거 신뢰성 부족 또는 unresolved false-positive 존재.

수석 리뷰어 Escalation 우선 조건: 복잡한 P0/P1 Root Cause, Architecture/DB/Transaction/Recovery/RNG/Concurrency 복합 문제, Phase 간 Contract/호환성 충돌, 반복 실패/회귀, 원인 불명 성능·메모리, Test PASS와 실제 동작/증거 불일치, Lead/Senior 기술 판단 불일치.

흐름: `Lead 판단 → Principal 분석/권고 → Lead Task 배정 → 구현/전문 리뷰 → QA → 필요 시 Principal 재리뷰 → Lead 최종 승인`.

## 6. 작업 계약 / 공유 작업트리

배정 시 필수: 목적·설계서, 구현/검토 범위, 수정 가능/금지 경계, 보존 동작, 선행/의존 Task, Acceptance Criteria, 테스트/환경, 완료 보고 형식(변경·검토 파일, 수행 내용, 실행 결과, 증거 경로, 미검증, 위험/후속 작업).

리뷰 전용 Task에는 읽기 전용 여부, 과거 P0/P1, 비교할 설계/코드/관리데이터/증거, false-positive/evidence freshness, 판정 기준을 추가한다. QA에는 정상·경계·실패·복구·회귀 시나리오를 전달한다.

공유 작업트리:
- 배정 전 상태/담당 경로를 확인하고 병렬 writer는 겹치지 않는 파일 또는 완결된 수직 범위만 맡는다.
- schema/lockfile/관리데이터/생성 문서/전체 검증 명령은 한 번에 한 Thread만 갱신한다.
- 기존 변경 덮어쓰기, `git add .`, 광범위 format, reset/clean/stash, branch/worktree 전환 금지. 각 담당자는 검토한 정확한 파일만 다룬다.
- 구체적 병렬 Goal에서만 작업 경계/충돌을 기록하며 상시 polling/heartbeat/별도 진행 장부는 만들지 않는다.

## 7. Evidence / 상태 / 완료

- PASS는 보고 문구가 아니라 **실행 결과와 검증 가능한 증거**로 판단한다. fresh build/runtime은 현재 revision/diff와 연결되어야 한다.
- Hash/Save sentinel/Fixture/Runtime/성능 측정은 가능한 경우 raw 증거를 보존한다. 코드/설계와 `build/reports`·관리데이터·Test 결과가 불일치하면 관리데이터를 우선하지 않는다.
- stale artifact, skip, 0 test, early-return, 잘못된 fixture, 환경 미실행은 PASS가 아니다. canonical/hash/fixture는 LF/CRLF 등 byte-level 플랫폼 재현성을 검토한다.
- 미실행 검증은 `NOT VERIFIED`; 차단 여부는 영향도와 대체 증거로 Lead가 판단한다.
- Goal 상태: `NOT STARTED → IN PROGRESS → REVIEW → QA → DONE`, 필요 시 `BLOCKED`.
- 승인 상태: `NOT REVIEWED / APPROVED / APPROVED WITH CONDITIONS / REJECTED`.
- `DONE`은 필요한 리뷰·QA·개발 리더 최종 승인까지 완료된 상태다. 조건부 승인은 후속 담당/조건을 남기고, 거부 시 다음 단계를 열지 않는다.
- 팀원 완료 보고: 판정/P0~P3, 변경·검토 파일, 수행 범위, 실행 결과, 증거 경로, 미검증, 잔여 위험/후속 작업. Chat 최종 메시지가 없으면 결과 미전달로 본다.
- Lead 최종 보고: Goal/진행 현황, 팀원별 결과, 문제/Severity, 미검증·잔여 위험, 판단, 다음 작업, 최종 승인 상태. Phase 종료 전 Deferred Task/Technical Debt/다음 Goal 선행 조건을 확인한다.
