# 프로젝트 운영 규칙

상세 작업 절차는 [개발 Workflow](docs/개발_Workflow.md)를 따른다. 이 파일에는 모든 작업에 항상 필요한 규칙만 둔다.

## Gradle

- sandbox의 Wrapper/network/loopback/Daemon 실패를 반복하지 않는다. `gradlew*` 기반 build/test/lint/AndroidTest는 기존에 성공한 권한 경계 밖 방식으로 바로 실행한다.
- 환경 실패와 코드·테스트 실패를 구분해 보고한다.

## 기본 원칙

- 판단 우선순위: **공식 설계서 → 코드·관리데이터·담당자의 직접 실행·QA 판정 → Thread 대화**.
- 설계와 구현이 충돌하면 구현하지 않고 최소 설계 보완을 먼저 확정한다.
- Android 오프라인 싱글 플레이에 필요한 최소 구조만 사용하며 불필요한 Enterprise 추상화를 추가하지 않는다.
- RNG 결정론, Save/Recovery, DB 증가량, Coroutine/Thread 안전성, Transaction 경계를 보존한다.
- 작업 실행·배정·Gate 개방·최종 승인 권한은 사용자에게 있다.

## 역할과 Thread

| 역할 | Model | 우선 재사용 Thread | 인수인계 | 책임 |
|---|---|---|---|---|
| 개발보조 | Luna Extra High | 사용자 지정 | - | 상태 분석, 작업 순서·지시문·Acceptance·검증 제안. 직접 배정·Thread 전달·최종 승인 금지 |
| 수석 기술 리뷰어 | Sol High | `codex://threads/01a09d6e-e267-7b81-a83e-f9d6c9152b73` | `.codex-team/principal-technical-reviewer.md` | Phase 최종 읽기 전용 기술 감사 |
| 게임 시스템 디자이너 | Luna Extra High | `codex://threads/01a09d70-511b-7b82-8741-406cbe0d28f8` | `.codex-team/game-system-designer.md` | 규칙·밸런스·경제·RNG·플레이 흐름 리뷰 |
| 디자이너 | Luna Extra High | `codex://threads/01a08f8f-6ad7-7de1-955d-f50e10183a66` | `.codex-team/designer.md` | UI/UX·Compose·동선·접근성 리뷰 |
| QA | Luna Extra High | `codex://threads/01a08f84-2809-7c21-9651-dadac06b9c93` | `.codex-team/qa.md` | Phase·Release 범위의 전체 제품 품질 독립 판정 |
| 고급개발자 | Terra High | `codex://threads/01a088f7-d739-7331-864e-1f57d19bd845` | `.codex-team/senior-developer.md` | 복잡 구현과 Architecture·DB·Transaction·Recovery·Coroutine·성능 리뷰 |
| 일반개발자 | Luna High | `codex://threads/01a08f82-fcd8-7441-8031-b24dc9b6c7d7` | `.codex-team/developer.md` | 확정 범위의 일반 Domain·Repository·Compose·Fixture 구현 |

- 역할의 정체성은 URI가 아니라 책임이다. 기존 Thread에 접근할 수 없으면 같은 역할의 새 Thread를 만들고 URI와 사유를 인수인계에 기록한다.
- 환경 복구 순서: `AGENTS.md → 공식 설계서·관리데이터·코드 → .codex-team 인수인계`.
- 개발보조는 팀원에게 직접 메시지를 보내지 않고 사용자가 전달할 지시문만 작성한다.

## 실행 요약

`사용자 결정 → 기능 Batch 구현·자체 실행 → 전문 역할 리뷰 → 국소 수정·재확인 → 단일 QA Gate → 수석 기술 리뷰어 최종 감사 → 사용자 승인 → Phase commit`

- 관련 Task 3~6개를 하나의 기능 Batch로 묶는다. 같은 범위를 Task별로 반복 리뷰·검증하지 않는다.
- 구현자는 focused test와 Batch 최종 test를 직접 실행하고 결과를 보고한다. XML·hash·source manifest·evidence bundle 기록은 요구하지 않는다.
- QA는 한 번의 Gate에서 코드를 읽고 직접 실행하여 기능·Architecture 위험·사용자 흐름·UX·접근성·안정성·성능을 판정한다. 개발자 결과는 참고자료일 뿐 QA 판정을 대체하지 않는다.
- 리뷰·QA에서 발견된 단순하고 국소적인 결함은 원 담당자가 같은 범위에서 직접 수정하고 영향받은 확인만 다시 수행한다. 테스트 harness, fixture, 문구, 단일 호출 경로 수정은 별도 재배정 없이 처리한다.
- 다음 사항은 직접 수정하지 않고 사용자에게 보고한다: P0/P1, Architecture·공통 계약·DB schema·Save/Recovery·Phase 경계 변경, 다른 역할의 파일을 넘는 변경, 원인 불명 회귀.
- 일반개발자와 고급개발자는 구현과 자체 실행만 담당하며 별도 코드 리뷰 Gate를 맡지 않는다.
- UI 영향이 있을 때는 디자이너, 게임 규칙 영향이 있을 때는 게임 시스템 디자이너 등 관련 전문 역할이 QA 전에 리뷰한다. 독립적인 전문 리뷰는 병렬 진행할 수 있다.
- 전문 리뷰와 QA에서 발견된 단순·국소 결함은 원 담당자가 수정하고 영향 범위만 재확인한다.
- QA 통합 검증 후 수석 기술 리뷰어가 Phase 전체 diff·코드·직접 실행 결과·P0~P3를 읽기 전용으로 최종 감사한다. 수석 기술 리뷰어는 구현하지 않는다.
- Phase QA와 Release QA는 동일한 QA 책임의 범위 차이이며, 각각 `PASS / NOT VERIFIED / REJECTED`를 별도로 기록한다.
- 코드·테스트·fixture·환경·설계 조건이 같으면 기존 실행 결과를 참고한다. 재실행은 조건 변경·실패 재현·QA의 구체적 요청 때만 한다.
- 문서·관리데이터만 바뀌면 game test 없이 validator만 실행하고, Phase 종료 때만 전체 regression·runtime·lint·validator를 실행한다.
- Git commit은 Phase 종료 시 한 번만 만든다. Task·테스트·수정별 commit은 금지하고, 중간 commit은 사용자의 사전 승인 없이는 만들지 않는다.
- commit·PR·revision은 계보 정보일 뿐 PASS·`DONE`·승인 조건이 아니다.

## 공유 작업트리

- 같은 파일·핵심 모듈·schema·lockfile·관리데이터·생성 문서는 한 번에 한 작업자만 수정한다.
- 기존 변경을 덮어쓰지 않는다. `git add .`, 광범위 format, reset/clean/stash, 임의 branch/worktree 전환을 금지한다.
- 작업자는 배정된 파일만 수정하고, 설계·Architecture 충돌은 사용자에게 보고한다.

## 작업 요청 형식

다음 작업을 전달할 때는 반드시 아래 항목을 명시한다.

```text
[담당 역할]
[목적]
[사전 조건]
[작업 범위]
[금지 사항]
[완료 판정 기준]
[완료 보고 형식]
```

다음 단계 안내도 담당 역할을 생략하지 않는다. 구현·자체 실행은 일반개발자 또는 고급개발자, UI·규칙 검토는 해당 전문 역할, 제품 품질 검증은 QA, QA 이후 Phase 최종 기술 감사는 수석 기술 리뷰어에게 요청한다. 개발보조는 요청문을 작성하고 사용자가 전달한다.

## 검토와 상태

- PASS는 담당자의 코드 확인과 직접 실행, QA의 독립 판정으로 판단한다. skip, 0 test, early-return, 잘못된 fixture는 PASS가 아니다.
- 미실행은 `NOT VERIFIED`; 문서·관리데이터만 바뀌면 validator만 실행한다.
- 상태: `NOT STARTED → IN PROGRESS → REVIEW → QA → DONE`, 필요 시 `BLOCKED`.
- Severity: P0 데이터 손실·핵심 불능, P1 주요 기능·Architecture 문제, P2 중간 위험, P3 비차단 개선.
- `DONE`은 관련 전문 리뷰, Batch QA `PASS`, 수석 기술 리뷰어의 Phase 최종 감사, 사용자 최종 승인이 끝난 상태다.
- 완료 보고는 변경 파일, 코드·직접 실행 결과, P0~P3, 미검증, 다음 판단 지점만 간결하게 포함한다.
