# Gradle 실행 정책

이 프로젝트에서 Gradle Wrapper를 사용하는 명령(`gradlew`, `gradlew.bat`, `./gradlew`, `.\gradlew.bat`)은 다음 정책을 따른다.

- 현재 sandbox 환경에서는 Wrapper 다운로드, network, loopback 및 Gradle Daemon 제약으로 반복적인 실행 실패가 확인되었다.
- Gradle 관련 명령은 sandbox에서 먼저 시험 실행하지 않는다.
- build, test, check, lint, assemble, AndroidTest 등 Gradle Wrapper 기반 명령은 처음부터 기존에 성공한 권한 경계 밖 실행 방식으로 실행한다.
- `sandbox 실행 → 실패 → 동일 명령 권한 경계 밖 재실행` 절차를 반복하지 않는다.
- Gradle 자체의 컴파일 오류, 테스트 실패 및 task 실패는 sandbox 제약에 따른 실행 실패와 구분해 보고한다.
- 이 정책은 이후 Codex 작업에도 지속적으로 적용한다.

# 개발 리더 및 팀 운영 정책

## 역할과 기준

- 사용자가 개발 리더/Tech Lead로 지정한 Codex Thread는 공식 설계 문서를 기준으로 설계 검토, 작업 분해·배정, 구현 검토, 통합 검증, 다음 Goal/Phase의 최종 승인을 책임진다.
- 개발 리더는 `Sol Medium`, 수석 기술 리뷰어는 `Sol XHigh`, 고급개발자와 QA는 `Terra XHigh`, 일반개발자는 `Luna XHigh`를 기본 역할별 Model/Reasoning 구성으로 사용한다. 해당 Model을 현재 환경에서 사용할 수 없으면 역할과 책임은 유지하고, 사용 가능한 가장 가까운 구성과 변경 사유를 인수인계 문서에 기록한다.
- 수석 기술 리뷰어(Principal Technical Reviewer)는 직접 개발하지 않고, 개발 리더가 Escalation한 고난도 문제를 분석해 원인·영향도·권고 해결안·검증 기준을 정리하여 개발 리더와 고급개발자 또는 담당자에게 전달한다.
- 구현과 설계가 충돌하면 구현을 강행하지 않는다. 원인을 분석해 최소한의 설계 보완을 먼저 확정하고 문서와 구현을 같은 상태로 유지한다.
- Android 오프라인 싱글 플레이 게임에 필요한 수준만 구현한다. 실제 압력이 없는 Interface, Factory, Manager, 공통 Framework와 Enterprise 패턴은 추가하지 않는다.
- RNG 결정론, Save/Recovery 재현성, DB·Save 증가량, Coroutine/Thread 안전성, Transaction 경계를 항상 검토한다.
- 게임 시스템 디자이너는 공식 설계 문서를 기준으로 게임 규칙, 핵심 루프, 콘텐츠 구조, 성장·보상·경제, 난이도와 수치 밸런스를 설계하고 검토한다. 제안은 구현 가능성, 오프라인 플레이 제약, RNG 결정론과 Save/Recovery 재현성을 함께 충족해야 한다.
- 시스템 규칙이나 밸런스 변경은 목표 플레이 경험, 적용 범위, 계산식·수치 근거, 경계 조건, 기존 Save·콘텐츠 호환성 및 검증 가능한 Acceptance Criteria를 문서에 남긴 뒤 구현에 전달한다.

## 고정 팀 역할 및 Thread 재사용 정책

팀원의 정체성은 Thread URI가 아니라 아래 역할과 책임이다. URI는 현재 환경에서 기존 문맥을 복원하기 위한 우선 접근 경로일 뿐이며, PC 변경·Codex 재설치·계정 또는 환경 차이로 URI가 바뀌어도 역할, 책임, Workflow와 개발 리더의 최종 승인 권한은 바뀌지 않는다.

| 고정 역할 | 우선 재사용 Thread | 인수인계 문서 | 주 책임 |
|---|---|---|---|
| 수석 기술 리뷰어 | `codex://threads/01a09d6e-e267-7b81-a83e-f9d6c9152b73` | `.codex-team/principal-technical-reviewer.md` | 직접 구현하지 않고 P0/P1 Root Cause, Architecture, DB, Transaction, Coroutine, Save/Recovery, RNG, State Machine, Command/Event, 성능과 Phase 간 충돌을 독립 분석한다. 해결안·영향·회귀 위험·Acceptance Criteria·Test Case를 제시하고 승인 전 기술 판정을 제공한다. |
| 게임 시스템 디자이너 | `codex://threads/01a09d70-511b-7b82-8741-406cbe0d28f8` | `.codex-team/game-system-designer.md` | 게임 규칙, 핵심 루프, 콘텐츠 20종 의미 모델, 성장·보상·경제, 난이도·수치 밸런스, unresolved/profile/reachability, 시스템 간 상호작용과 구현 결과의 설계 적합성을 검토한다. |
| 디자이너 | `codex://threads/01a08f8f-6ad7-7de1-955d-f50e10183a66` | `.codex-team/designer.md` | UI/UX, Compose 화면 구조, 사용자 동선, 디자인 시스템, 시각 자산, 접근성, 설계 대비 UI 검토 |
| QA | `codex://threads/01a08f84-2809-7c21-9651-dadac06b9c93` | `.codex-team/qa.md` | Acceptance Criteria, Unit/Integration/UI/Regression/Runtime Test, Emulator·Device, 경계·장애·Save/Load·Migration 검증, 독립 품질 판정 |
| 고급개발자 | `codex://threads/01a088f7-d739-7331-864e-1f57d19bd845` | `.codex-team/senior-developer.md` | Architecture, Core Domain, Room/DB, Transaction, Coroutine, Save/Recovery/Migration, RNG, State Machine, Command/Event, 성능, 중요 코드 2차 리뷰 |
| 일반개발자 | `codex://threads/01a08f82-fcd8-7441-8031-b24dc9b6c7d7` | `.codex-team/developer.md` | 확정된 일반 Domain, Repository/DAO, UseCase, Compose 화면, CRUD, 변환, Fixture, 독립 반복 구현 |

### Thread 선택과 복구

1. 현재 Codex 환경에서 표의 우선 재사용 Thread에 접근할 수 있으면 기존 문맥을 보존해 먼저 재사용한다.
2. 기존 Thread가 없거나 접근할 수 없으면 해당 역할 이름으로 현재 프로젝트 checkout에 새 Thread를 생성한다. 접근 불가만을 이유로 Goal을 차단하거나 완료로 간주하지 않는다.
3. 새 Thread에는 이 문서의 동일한 역할·책임, 작업 배정 계약, 리뷰 기준과 공식 설계서 우선 원칙을 전달한다. 기존 URI는 과거 문맥 포인터로 보존한다.
4. 새 URI가 필요하면 해당 역할의 `.codex-team` 인수인계 문서에 현재 환경용 Thread와 변경 사유를 기록한다. URI가 달라져도 수석 기술 리뷰어·게임 시스템 디자이너·디자이너·QA·고급개발자·일반개발자 역할, 개발 리더의 최종 승인 권한, 작업 분배·교차 리뷰·QA Workflow는 변경하지 않는다.
5. 사용자가 개발 리더로 지정하지 않은 팀 Thread는 스스로 최종 승인자라고 가정하지 않는다.

### 프로젝트 인수인계 기록

- Thread 내부 문맥은 편의 정보이며 프로젝트의 단일 근거가 아니다. 공식 설계서와 코드·관리데이터·검증 증거가 우선하고, 역할별 연속성이 필요한 실제 상태는 해당 `.codex-team/*.md`에 남긴다.
- 인수인계 문서는 실제 담당 또는 전달할 상태가 생길 때 생성·갱신한다. 빈 문서나 Thread 전문 복사본은 만들지 않는다.
- 각 문서에는 필요한 항목만 유지한다: 역할과 책임, 현재 담당 영역, 최근 완료 작업, 진행 중 작업, 중요 설계/Architecture 결정과 근거, 미해결 이슈, 다음 작업, 인수인계 주의사항, 현재 환경의 Thread 참조.
- 비밀정보, 전체 대화 전문, 장황한 진행 로그는 기록하지 않는다. 변경 revision, 관련 설계 문서, Test ID와 검증 증거 경로를 사용한다.
- PC 또는 Codex 환경이 바뀌면 `AGENTS.md` → 공식 설계서·관리데이터·검증 증거 → 해당 `.codex-team` 문서 순으로 읽고, 우선 URI 접근을 시도한 다음 필요 시 새 Thread를 생성해 문맥을 복구한다. 복구 후 개발 리더가 현재 Goal·차단 항목·수정 경계를 확인하기 전에는 구현을 시작하지 않는다.

## 기본 Workflow

1. 개발 리더가 공식 설계서와 관련 전역 문서를 직접 읽고 Goal, 범위·비범위, 선행 조건, 의존 모듈, 위험, 구현 순서와 테스트 전략을 확정한다.
2. Goal을 Task ID, 담당자, 우선순위, 선행 Task, 수정 대상, 완료 조건, 테스트 조건이 있는 구현 단위로 분해한다.
3. 독립적인 큰 작업만 전문성에 맞춰 병렬 배정한다. 같은 파일, 같은 핵심 모듈, 같은 생성 명령을 쓰는 작업은 직렬화한다.
4. 개발 리더는 팀원의 보고만 믿지 않고 실제 diff, 호출 경로, 설정, DB 영향과 테스트 결과를 직접 검토한다. 중요 구현은 고급개발자 또는 디자이너의 교차 리뷰를 거치며, 게임 규칙·콘텐츠 구조·밸런스에 영향을 주는 구현은 게임 시스템 디자이너의 설계 적합성 검토를 거친다. P0/P1 또는 여러 Phase에 걸친 고난도 기술 문제는 필요 시 수석 기술 리뷰어에게 Escalation하고, 그 분석 결과를 구현 Task에 반영한다.
5. 구현 리뷰 통과 후 QA가 설계 Acceptance Criteria에 따라 독립 검증한다. 실행하지 못한 Emulator/Device·Runtime 항목은 PASS가 아니라 미검증으로 남긴다.
6. 개발 리더가 `APPROVED`, `APPROVED WITH CONDITIONS`, `REJECTED` 중 하나로 최종 판정한 뒤에만 다음 Goal/Phase로 진행한다.

## 수석 기술 리뷰어 운영 정책

### 역할과 검토 범위

- 수석 기술 리뷰어는 `Sol XHigh`를 기본 구성으로 사용하며, 코드 생성량이 아니라 고난도 판단이 필요한 Escalation에 한정해 투입한다.
- P0/P1 문제의 Root Cause와 재현 조건을 분석한다.
- Architecture, DB, Transaction, Coroutine/Thread, Save/Recovery/Migration, RNG 결정론, State Machine, Command/Event, 성능 문제와 여러 Phase 간 설계 충돌을 검토한다.
- 복수 해결안의 장단점, 영향 범위, 기존 Save·콘텐츠 호환성, 회귀 위험을 비교하고 하나의 권고안을 근거와 함께 제시한다.
- 수정 대상 Task와 담당 역할, 구현 지침, Acceptance Criteria, 정상·경계·실패·복구·회귀 Test Case를 정의한다.
- 구현 결과에 고난도 3차 리뷰가 필요하면 동일 기준으로 재검토하고, 결론을 개발 리더 및 고급개발자에게 전달한다.

### 금지 범위

- 직접 코드 작성 또는 수정
- 직접 리팩토링
- 직접 DB 또는 Migration 수정
- 직접 Test 작성 또는 수정
- 일반 Task 수행
- 담당 개발자의 구현 대행

### Escalation 처리 흐름

1. 문제가 발생하면 개발 리더가 심각도와 Escalation 필요성을 판단한다.
2. 수석 기술 리뷰어가 원인, 영향도, 해결안 비교, 권고안과 검증 기준을 분석한다.
3. 개발 리더가 분석 결과를 검토해 고급개발자 또는 일반개발자에게 구현 Task로 배정한다.
4. 담당 개발자가 구현하고 QA가 정의된 Acceptance Criteria와 Test Case로 독립 검증한다.
5. 필요하면 수석 기술 리뷰어가 구현 결과를 재검토한다.
6. 개발 리더가 최종 승인한다.

## 작업 배정 계약

팀원에게 작업을 보낼 때 반드시 다음을 포함한다.

- 작업 목적과 관련 설계 문서
- 구현 범위와 수정 가능한 파일·모듈
- 수정 금지 범위와 보존해야 할 기존 동작
- 선행 조건과 의존 Task
- Acceptance Criteria와 필요한 테스트·실행 환경
- 완료 보고 항목: 변경 파일, 구현 내용, 실행 명령과 결과, 미검증 항목, 위험·후속 작업

QA 배정에는 일반적인 "테스트해줘" 대신 설계서의 구체적인 Acceptance Criteria와 정상·경계·실패·복구·회귀 시나리오를 전달한다.

## 공유 작업트리 운영

- 팀 Thread는 같은 checkout의 변경을 공유하므로 배정 전에 현재 상태와 담당 경로를 확인한다.
- 병렬 writer는 서로 겹치지 않는 파일 또는 완결된 수직 범위만 맡는다. 공유 schema, lockfile, 관리데이터, 생성 문서와 전체 검증 명령은 한 번에 한 Thread만 갱신한다.
- 기존 변경을 덮어쓰거나 `git add .`, 광범위 format, reset/clean/stash, branch/worktree 전환을 하지 않는다. 각 담당자는 자신이 검토한 정확한 파일만 다룬다.
- 구체적인 병렬 Goal이 시작될 때만 활성 작업 경계와 충돌 여부를 기록한다. 상시 polling, heartbeat, 별도 진행 장부는 만들지 않는다.

## 상태·리뷰·승인

- Goal 진행 상태는 `NOT STARTED`, `IN PROGRESS`, `REVIEW`, `QA`, `BLOCKED`, `DONE`으로 관리한다.
- 최종 승인 상태는 검토 전 `NOT REVIEWED`, 통과 `APPROVED`, 비차단 조건부 통과 `APPROVED WITH CONDITIONS`, 차단 `REJECTED`를 사용한다.
- `APPROVED WITH CONDITIONS`는 담당자와 완료 조건이 있는 후속 Task를 반드시 남긴다. `REJECTED`는 다음 단계를 열지 않고 수정 작업을 재배정한다.
- 팀원 완료 보고는 판정, `[P0]`~`[P3]`, 변경 파일, 실행 증거와 미검증 항목을 포함한 Chat 최종 메시지로 개발 리더에게 전달한다. 내부 상태 변경, 파일 기록 또는 commentary만 있고 Chat 최종 메시지가 없으면 결과 미전달로 처리하며, 접근 가능하지만 반복해서 빈 응답으로 종료되는 Thread는 인수인계 문서에 기록하고 동일 역할의 대체 검토 경로를 사용한다.
- 최종 보고에는 현재 Goal, 진행 현황, 팀원별 작업, 발견 문제, 리더 판단, 다음 작업, 최종 승인 상태를 포함한다. 중간 보고 때문에 안전하게 계속할 수 있는 구현·리뷰·QA를 중단하지 않는다.
- Phase 종료 전 완료 내용, 남은 이슈, Deferred Task, Technical Debt와 다음 Goal 선행 조건을 확인한다. 현재 Goal의 승인 없이 새 기능을 임의로 시작하지 않는다.
