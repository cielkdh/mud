# P0-TASK-001 작업 컨텍스트

> 자동 생성된 착수용 요약이다. 충돌 시 아래 원문 링크와 관리데이터가 우선한다.

## Task

- 기능: `FUNC-P0-001` 원문 기준선과 충돌 판정
- 단계/모듈: 계약 / docs/검증도구 / docs/관리데이터
- 선행 Task: 없음
- 상세: 원문 파일·document_manifest.json·decisions.json·추적 데이터의 입력/오류/검증 결과를 고정하고 FUNC-P0-001의 REQUIRED/DATA Assertion을 승인하거나 결정 ID 근거로 강도를 재분류한다. 별도 BaselineResolver production 타입은 만들지 않고 기존 validate_docs.py 검사를 확장한다.
- 완료 조건: 검증 계약·fixture 승인, FUNC-P0-001 미승인 REQUIRED/DATA 0건, 별도 production resolver 0

## 기능 계약

- 메소드: `py -3 docs/검증도구/validate_docs.py`
- 대상 schema: 없음
- 규칙: 원문 전체와 SHA-256을 고정하고 번호·행범위로 추적한다 / 명시적 교체/최종 조항을 우선하되 단순히 번호가 크다는 이유만으로 덮어쓰지 않는다 / 해결 근거가 없는 충돌은 DESIGN_DECISION_REQUIRED로 표시하고 영향 Task를 차단한다 / 본 문서의 보완 정책은 원문 규칙과 다른 namespace로 관리한다
- 정상: §28 6명, §1737 조직10/출전6 / 조직과 출전을 분리하고 R-PARTY-001에 원문 근거 2개 보존
- 경계: 같은 규칙의 모순이며 교체 문구 없음 / 결정 대기; 해당 기능의 운영 활성화 차단
- 실패: 요구사항 번호 하나 누락 / 문서 검증 실패; 릴리즈 범위에서 숨기지 않음

## 결정 의존

- C14: 승인·기준선 반영 / 빌드 PASS — Android-only greenfield: compileSdk37/targetSdk36/minSdk26, AGP9.4.0/Gradle9.6.0/JDK17/Kotlin2.4.20/KSP2.3.11/Compose BOM2026.08.00/Room3.0.2/Coil3.5.0 exact pin, SchemaBaselineMode=GREENFIELD_V1. 실제 출시 이력이 발견될 때만 재승인 후 LEGACY_CHAIN으로 전환한다. P0는 resolve/JVM·app compile·launch, P3는 Room compile/최초 v1 schema export를 검증한다.

## 관련 Test

- P0-UT-001: 원문 SHA-256, Phase 0 결정 집합 {C01,C02,C03,C14,C23}, active P0 REQUIRED/DATA Assertion 상태 → hash 일치, C01·C02·C03 승인 적용, C14 빌드검증 상태와 C23 Assertion 범위·소유 분류 일치, 잘못된 P0 결정 0건, 후속 구현 착수 시 미승인 active P0 REQUIRED/DATA 0건 [PASS]
- P0-BT-001: 같은 규칙의 모순이며 교체 문구 없음 → 결정 대기; 해당 기능의 운영 활성화 차단 [NOT_RUN]
- P0-FT-001: 요구사항 번호 하나 누락 → 문서 검증 실패; 릴리즈 범위에서 숨기지 않음 [NOT_RUN]
- P0-CT-001: §28 6명, §1737 조직10/출전6; 같은요청2회 → 조직과 출전을 분리하고 R-PARTY-001에 원문 근거 2개 보존; 같은입력2회 결과동일·live state/RNG쓰기0 [NOT_RUN]
- P0-IT-001: C01 fixture와 원문/결정/추적 JSON → C01 근거가 보존되고 같은 입력의 검사 결과가 동치이며 Android/DB 경로를 호출하지 않는다. [NOT_RUN]

## REQUIRED/DATA Atomic Assertions

- AR-S0123-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0123` §123 개발 단계 L3772: 처음부터 모든 콘텐츠를 만들지 않는다.
- AR-S0124-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3819: 게임 시작 직후에도 어딘가에:
- AR-S0124-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3822: Lv.127 고대의 동굴곰
- AR-S0124-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3825: 이 존재할 수 있다.
- AR-S0124-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3827: 잘못 들어가면 후퇴해야 한다.
- AR-S0124-005 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3831: `Lv.50 슬라임`과 `Lv.50 드래곤`은 같은 강함이 아니다.
- AR-S0124-006 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3833: 종족, 개체 등급, 접사, 스킬, 지형이 중요하다.
- AR-S0124-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3837: 어떤 던전을 고르는가
- AR-S0124-008 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3838: 누구와 가는가
- AR-S0124-009 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3839: 어떤 스킬을 장착하는가
- AR-S0124-010 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3840: 어떤 장비를 사용하는가
- AR-S0124-011 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3841: 어떤 전술을 설정하는가
- AR-S0124-012 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3842: 언제 후퇴하는가
- AR-S0124-013 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3844: 가 결과를 크게 좌우해야 한다.
- AR-S0124-014 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3848: 빠른 보스 공략과 완전 탐사는 서로 다른 플레이 방식이다.
- AR-S0124-015 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3852: 플레이어가 없는 동안에도 NPC는:
- AR-S0124-016 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3854: 성장
- AR-S0124-017 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3855: 실패
- AR-S0124-018 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3856: 결혼
- AR-S0124-019 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3857: 은퇴
- AR-S0124-020 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3858: 길드 이동
- AR-S0124-021 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3859: 파티 구성
- AR-S0124-022 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3860: 던전 공략
- AR-S0124-023 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3862: 을 계속한다.
- AR-S0124-024 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3866: 이전 세대가 만든:
- AR-S0124-025 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3868: 집
- AR-S0124-026 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3869: 길드
- AR-S0124-027 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3870: 인맥
- AR-S0124-028 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3871: 적
- AR-S0124-029 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3872: 연대기
- AR-S0124-030 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3873: 가문 명성
- AR-S0124-031 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3874: 세계 변화
- AR-S0124-032 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0124` §124 핵심 밸런스 원칙 L3876: 가 그대로 남아야 한다.
- AR-S0127-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S0127` §127 현재 통합 설계 결론 L3955: 던전 현황 확인 / ↓ / 적합한 던전 존재 / → 던전 선택 / → 파티 구성 / → 장비/스킬/전술 준비 / → MUD 탐색 / → 자동 전투 / → 보물 획득 / → 후퇴 또는 정복 / 적합한 던전 없음 / 준비 필요 / → 보조 의뢰 / → 금화·정보·관계·재료 확보 / → 정비/휴식 / → 다시 던전 탐색
- AR-S3046-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3046` §3046 DataStore 사용 영역 L62970: 금지:
- AR-S3135-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64633: Android 전용이므로 Native Kotlin을 사용한다.
- AR-S3135-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64634: UI는 Compose로 통일한다.
- AR-S3135-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64635: 게임 핵심 로직은 Android Framework에 의존하지 않는 순수 Kotlin으로 작성한다.
- AR-S3135-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64636: WorldEngine은 단일 writer 구조로 결정론을 유지한다.
- AR-S3135-005 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64637: Combat/NPC simulation 중 Room을 직접 반복 조회하지 않는다.
- AR-S3135-006 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64638: 정적 콘텐츠 content.db와 런타임 save.db를 분리한다.
- AR-S3135-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64639: save.db는 Room transaction/migration으로 관리한다.
- AR-S3135-008 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64640: 검색은 Room FTS5를 활용할 수 있게 설계한다.
- AR-S3135-009 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64641: 세이브가 아닌 사용자 설정은 DataStore로 분리한다.
- AR-S3135-010 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64642: 이미지 로딩은 Coil + AssetResolver로 통일한다.
- AR-S3135-011 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64643: Portrait/Monster/Item 이미지 경로는 Domain이 알지 않는다.
- AR-S3135-012 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64644: RNG는 자체 versioned interface와 독립 stream을 사용한다.
- AR-S3135-013 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64645: GameTime은 커스텀 Long 기반 value object를 사용한다.
- AR-S3135-014 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64646: UI는 ViewModel + StateFlow + UDF를 기본으로 한다.
- AR-S3135-015 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64647: Hilt를 사용해 Repository/Engine/Test Fake를 주입한다.
- AR-S3135-016 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64648: WorkManager는 게임시간 진행에 사용하지 않는다.
- AR-S3135-017 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64649: 콘텐츠는 CSV/JSON → Validation → content.db build pipeline을 사용한다.
- AR-S3135-018 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64650: Simulation Validation은 실제 core:simulation을 그대로 실행한다.
- AR-S3135-019 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64651: JVM 테스트를 최대한 많이 돌릴 수 있게 Android 의존을 경계 밖으로 밀어낸다.
- AR-S3135-020 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64652: Release에는 Macrobenchmark/Baseline Profile/R8 검증을 포함한다.
- AR-S3135-021 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64653: NDK/C++은 초기 도입하지 않고 실제 profiler 결과가 필요할 때만 검토한다.
- AR-S3135-022 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64654: Backend/서버/로그인을 게임 핵심 구조에 넣지 않는다.
- AR-S3135-023 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64655: 첫 구현은 전체 기능이 아니라 Vertical Slice 하나를 끝까지 완성한다.
- AR-S3135-024 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3135` §3135 구현 기술 최종 원칙 L64656: 이후 기능은 같은 Engine/Repository/UI 패턴으로 확장한다.

## Command/Event 계약

| `FUNC-P0-001` 원문 기준선과 충돌 판정 | `tool` | 아니오 | 문서/관리 JSON 읽기 전용; 검증 artifact만 기록 |

## 권위 문서

- [Phase 상세](../01_Phase0_기준선_아키텍처_개발기반_상세설계서.md)
- [Atomic Assertions](../82_원자_요구사항_및_Assertion_추적표.md)
- [Command/Event](../84_전체_Command_Event_계약서.md)
- [Data Dictionary](../81_전체_데이터사전.md)
