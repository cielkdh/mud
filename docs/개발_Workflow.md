# 개발 Workflow

## 1. 목적

토큰 사용과 반복 실행을 줄이면서 구현·테스트·리뷰 신뢰성을 유지한다. 기본 작업 단위는 **기능 Batch**, 최종 Git 단위는 **Phase**다.

## 2. Batch 구성

- 같은 기능·호출 경로·테스트를 공유하는 Task 3~6개를 묶는다.
- 시작 전에 범위, 비범위, 선행 Task, 수정 파일, Acceptance Criteria, 테스트를 한 번만 확정한다.
- 같은 파일 writer는 한 명만 둔다. 독립 파일이어도 같은 schema·관리데이터·생성 명령을 사용하면 직렬화한다.
- 기존 Thread 문맥을 재사용하고 전체 설계서를 매번 다시 전달하지 않는다.

## 3. 역할별 진행 순서

```text
사용자 작업 결정
→ 개발자 구현·focused test·Batch 최종 직접 실행
→ 관련 전문 역할 리뷰(디자이너·게임 시스템 디자이너 등)
→ 원 담당자 국소 수정·영향 범위 재확인
→ 단일 QA Gate(코드 확인 + 직접 실행)
→ QA 수정·재확인
→ 수석 기술 리뷰어 Phase 최종 읽기 전용 감사
→ 사용자 최종 승인
→ Phase commit
```

- 일반 구현은 일반개발자, Architecture·DB·Transaction·Recovery·RNG·Concurrency는 고급개발자가 맡는다.
- 일반개발자와 고급개발자는 구현과 자체 실행만 담당하며 별도 코드 리뷰를 수행하지 않는다.
- UI 영향이 있을 때는 디자이너, 게임 규칙 영향이 있을 때는 게임 시스템 디자이너 등 관련 전문 역할이 QA 전에 리뷰한다. 독립적인 전문 리뷰는 병렬 진행할 수 있다.
- 전문 리뷰와 QA에서 발견된 단순·국소 결함은 원 담당자가 수정하고 영향 범위만 재확인한다.
- 수석 기술 리뷰어는 QA 통합 검증 후 Phase 종료 직전에 전체 diff·코드·직접 실행 결과·P0~P3를 읽기 전용으로 최종 감사한다. 구현이나 수정은 하지 않는다.

### QA Gate

- QA는 Phase와 Release 범위의 전체 제품 품질을 코드 확인과 직접 실행으로 판정한다. 개발자 테스트 결과는 참고 자료다.
- Phase 범위에서는 실제 사용자 흐름, 화면 상태·문구, 접근성, 중복 조작, lifecycle, 오류 이해 가능성, 체감 성능과 Phase 목표를 검증한다.
- Release 범위에서는 여러 Phase 통합, Save/Load·Migration·Recovery, 장기 실행·성능·메모리, 기기 조합, 오프라인 설치·업데이트·롤백을 검증한다.
- Phase QA와 Release QA는 동일 QA 책임의 범위 차이이며 각각 `PASS / NOT VERIFIED / REJECTED`를 별도로 기록한다.

## 4. 작업 지시문 최소 형식

```text
[담당 역할]
[목적 / 관련 설계]
[사전 조건]
[Batch 범위와 비범위]
[수정 가능·금지 파일]
[Acceptance Criteria]
[focused test 또는 직접 실행 범위]
[완료 보고: 변경 파일, 코드 확인·직접 실행 결과, NOT VERIFIED, P0~P3]
```

다음 단계 안내와 작업 요청에는 반드시 담당 역할을 먼저 적는다. 일반개발자와 고급개발자는 구현·자체 실행, 디자이너는 UI/UX, 게임 시스템 디자이너는 규칙·밸런스, QA는 전체 제품 품질 직접 검증, 수석 기술 리뷰어는 QA 이후 Phase 최종 읽기 전용 감사를 맡는다. 개발보조는 사용자에게 전달할 요청문만 작성한다.

이전 대화나 설계 전문을 반복하지 않고 변경된 조건과 필요한 절·파일 링크만 전달한다.

## 5. 구현과 테스트

| 시점 | 실행 범위 |
|---|---|
| 구현 중 | 구현자가 변경 기능의 최소 Unit/Contract test |
| Batch 완료 | 공식 Test와 영향 Regression을 한 명령 또는 최소 실행 묶음으로 1회 실행 |
| 리뷰 수정 후 | 수정된 코드·테스트·fixture에 영향받는 test만 재실행 |
| Phase 완료 | Phase 전체 regression·runtime·lint·validator 1회 |

- 순수 Kotlin 변경은 JVM test를 우선하고 Android API 차이가 있을 때만 instrumentation을 실행한다.
- UI·Android runtime 변경이 없으면 emulator test를 반복하지 않는다.
- 코드·테스트·환경·fixture가 같으면 기존 실행 결과를 참고한다.
- 문서·관리데이터만 변경되면 코드 test를 실행하지 않고 validator만 실행한다.
- 실패는 product defect, test defect, environment, flaky, unrelated로 분류하고 같은 실패 명령을 의미 없이 반복하지 않는다.

### 5.1 중복 실행 방지

- 구현자는 Batch 최종 실행 결과를 작업 보고에 기록한다. XML·hash·source manifest·evidence bundle은 만들지 않는다.
- QA는 개발자 결과를 참고하되 최종 코드와 제품 흐름을 직접 실행하여 기능·제품 품질을 판정한다.
- 고급개발자와 수석 기술 리뷰어는 기본적으로 재실행하지 않는다. 전자는 위험 기반 요청 시, 후자는 Phase 종료 시에만 참여한다.
- 재실행 조건은 `production/test/fixture/environment/design` 변경, 공식 Test ID·매핑 변경, 실패 재현, QA의 구체적 요청으로 제한한다.

## 6. 리뷰 실패 처리

```text
REJECTED
→ 사용자가 Severity와 수정 담당 결정
→ 최소 수정
→ 영향 test
→ 실패한 Gate만 재리뷰
→ 다음 Gate
```

FAIL 보고에는 문제·근거·영향·기대 동작·Acceptance Criteria·필요한 재검증만 포함한다. 이미 승인된 범위는 새 변경이 영향을 주지 않으면 다시 열지 않는다.

### 국소 결함 즉시 수정

리뷰 또는 QA가 찾은 결함이 단일 담당 범위의 단순 수정이면 원 담당자가 바로 수정한다. 대상은 테스트 harness, fixture, 단일 UI 문구·상태 연결, 명확한 validation 누락처럼 설계·공통 계약·production authority를 바꾸지 않는 변경이다. 수정자는 영향받은 테스트와 실패 시나리오만 다시 실행하고 수정 내용과 결과를 한 번에 보고한다. 이 경우 새 역할 배정이나 전체 리뷰를 반복하지 않는다.

P0/P1, Architecture·DB·Transaction·Save/Recovery·공통 계약·Phase 경계 변경, 여러 모듈을 넘는 수정, 원인 불명 회귀는 즉시 중단하고 사용자에게 보고한 뒤 별도 리뷰를 요청한다.

## 7. 실행 결과 기록

- 완료 판단은 최종 코드 확인, 직접 실행 결과, Acceptance 충족, 리뷰 판정으로 한다.
- XML·로그·hash·manifest·evidence bundle 보존은 승인 조건이 아니다.
- skip, 0 test, early-return, 잘못된 fixture는 PASS로 기록하지 않는다.

## 8. Commit 정책

- Phase 구현·리뷰·QA·관리데이터 정리가 끝난 뒤 **Phase 종료 commit 한 번**만 만든다.
- Task·테스트·수정·리뷰 단위 commit을 만들지 않는다.
- PC 이관은 작업 폴더와 `.codex-team` 인수인계 문서를 우선 사용한다.
- 데이터 손실 방지를 위한 중간 commit은 사용자 사전 승인 후에만 허용하며 완료 판단 자료로 사용하지 않는다.

## 9. 상태 갱신

- Task별 상태는 실제 작업에 맞추되 관리데이터와 설계 문서는 Batch 종료 시 한 번에 갱신한다.
- `DONE`은 관련 전문 리뷰, 단일 QA Gate `PASS`, 수석 기술 리뷰어의 Phase 최종 감사, 사용자 승인 이후에만 설정한다.
- Phase 종료 시 Deferred, Technical Debt, `NOT VERIFIED`, 다음 Phase 선행 조건을 함께 기록한다.

## 10. 완료 보고

```text
Batch/Phase 범위
완료 Task
변경 파일
코드 확인 / 직접 실행 결과
생략한 Gate와 근거
P0/P1/Blocking P2/P3
NOT VERIFIED
다음 사용자 판단 지점
```
