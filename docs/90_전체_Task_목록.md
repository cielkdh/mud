# 90. 전체 Task 목록

> v31.0 · 2026-09-08 · 모든 구현상태 NOT_STARTED · 실제 담당자/PR/실행 증거 없음

## 1. 사용 방법
총 672개 항목은 구현 완료 약속이 아니라 요구사항 추적 후보 목록이다. 활성 범위는 `관리데이터/phases.json.active_function_ids`를 따르며 초기값은 P0 기준선·빌드 lock 기능이다. P0 물리 project는 `:app`, `:core:simulation` 두 개고 나머지 `:core:*`는 소유 Phase가 실제 필요할 때까지 논리 package다. 나머지 Task는 대상 파일·기존 API·재사용 지점·담당자/리뷰어·실행 명령·증거 경로와 관련 결정을 확인한 뒤 해당 Phase에서 필요한 범위만 활성화한다.

`depends`는 완료 선행관계, `decision_dependencies`는 검토 결정, `blocked_by`는 현재 설계 차단사유다. C14는 승인되었지만 실제 build/Room 증거는 각각 P0/P3에서 얻는다. 표의 공수는 코드·담당자·검증환경 확인 전 계획 가정이며 실제 일정으로 합산하지 않는다.

Phase 3 표의 `:core:database`·`:core:data`는 `:core:save` 내부 논리 package이며 별도 Gradle module이 아니다. UI·launcher Task에는 `:app`도 포함된다. 실제 대상은 Phase 3 상세설계서와 `tasks.json`을 따른다.

## 2. 통합 Task 표
| Phase | Task ID / 상세 | Task | 선행 Task | 중요도 | 대상 모듈 | 병렬 가능 | Test | 완료 기준 | 기대 인일 |
|---|---|---|---|---|---|---|---|---|---|
| 0 | [P0-TASK-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-001) | 원문 기준선과 충돌 판정 — 계약·Fixture | - | 필수 | docs/검증도구 / docs/관리데이터 | 선행계약후;공통 schema 충돌은직렬 | P0-UT-001, P0-BT-001, P0-FT-001, P0-CT-001, P0-IT-001 | 검증 계약·fixture 승인, FUNC-P0-001 미승인 REQUIRED/DATA 0건, 별도 production resolver 0 | 1.06 |
| 0 | [P0-TASK-002](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-002) | 원문 기준선과 충돌 판정 — 핵심 규칙 | P0-TASK-001 | 필수 | docs/검증도구 / docs/관리데이터 | 선행계약후;공통 schema 충돌은직렬 | P0-UT-001, P0-BT-001, P0-FT-001 | 충돌 우선순위·차단 규칙과 C01 근거 추적 검사 PASS | 1.59 |
| 0 | [P0-TASK-003](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-003) | 원문 기준선과 충돌 판정 — 저장·연계 | P0-TASK-001 | 필수 | docs/검증도구 / docs/관리데이터 | 선행계약후;공통 schema 충돌은직렬 | P0-CT-001, P0-IT-001 | 검증보고서 원자 교체·실패 종료코드 1·game DB/앱 호출 0 | 1.59 |
| 0 | [P0-TASK-004](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-004) | 원문 기준선과 충돌 판정 — UI·호출 경로 | P0-TASK-001 | 필수 | docs/검증도구 | 선행계약후;공통 schema 충돌은직렬 | P0-CT-001, P0-IT-001 | CLI 종료코드·Markdown 보고서로 실패 관측, Android UI·SavePort 경로 0 | 1.06 |
| 0 | [P0-TASK-005](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-005) | 원문 기준선과 충돌 판정 — Test·리뷰 | P0-TASK-002, P0-TASK-003, P0-TASK-004 | 필수 | docs/검증도구 / docs/관리데이터 | 선행계약후;공통 schema 충돌은직렬 | P0-UT-001, P0-BT-001, P0-FT-001, P0-CT-001, P0-IT-001 | 대표5개Test와원문세부assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 0 | [P0-TASK-006](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-006) | 빌드·모듈·기술버전 고정 — 계약·Fixture | - | 필수 | Gradle root / :app / :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-UT-002, P0-BT-002, P0-FT-002, P0-CT-002, P0-IT-002 | P0 Build Manifest·두 project graph·fixture 승인, FUNC-P0-002 미승인 REQUIRED/DATA 0건, 채택 RECOMMENDED 결정 ID 연결 | 1.06 |
| 0 | [P0-TASK-007](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-007) | 빌드·모듈·기술버전 고정 — 핵심 규칙 | P0-TASK-006 | 필수 | Gradle root / :app / :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-UT-002, P0-BT-002, P0-FT-002 | 두 project graph·allowlist·금지 import·toolchain/lock 증거 검사 PASS | 1.59 |
| 0 | [P0-TASK-008](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-008) | 빌드·모듈·기술버전 고정 — 저장·연계 | P0-TASK-006 | 필수 | Gradle root / :app / :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-CT-002, P0-IT-002 | graph/import/lock·P3 인계 경로 검사 PASS, P0 schema/DB/headless 산출물 0 | 1.59 |
| 0 | [P0-TASK-009](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-009) | 빌드·모듈·기술버전 고정 — UI·호출 경로 | P0-TASK-006 | 필수 | :app / :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-CT-002, P0-IT-002 | :app → :core:simulation만으로 AppRoot assemble/smoke PASS, DB/save/headless 0 | 1.06 |
| 0 | [P0-TASK-010](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-010) | 빌드·모듈·기술버전 고정 — Test·리뷰 | P0-TASK-007, P0-TASK-008, P0-TASK-009 | 필수 | Gradle root / :app / :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-UT-002, P0-BT-002, P0-FT-002, P0-CT-002, P0-IT-002 | 대표5개Test와원문세부assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 0 | [P0-TASK-011](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-011) | 공통 타입·명령·오류·이벤트 계약 — 계약·Fixture | - | 필수 | :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-UT-003, P0-BT-003, P0-FT-003, P0-CT-003, P0-IT-003 | value object·Command/Event/Error·WorldSession/SavePort 계약과 fixture 승인 | 1.06 |
| 0 | [P0-TASK-012](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-012) | 공통 타입·명령·오류·이벤트 계약 — 핵심 규칙 | P0-TASK-011 | 필수 | :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-UT-003, P0-BT-003, P0-FT-003 | type 경계·overflow·UnsupportedFeature·Command/Event field 검사 PASS | 1.59 |
| 0 | [P0-TASK-013](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-013) | 공통 타입·명령·오류·이벤트 계약 — 저장·연계 | P0-TASK-011 | 필수 | :core:simulation test source | 선행계약후;공통 schema 충돌은직렬 | P0-CT-003, P0-IT-003 | codec golden·test-only in-memory SavePort PASS, production persistence 산출물 0 | 1.59 |
| 0 | [P0-TASK-014](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-014) | 공통 타입·명령·오류·이벤트 계약 — UI·호출 경로 | P0-TASK-011 | 필수 | :app / :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-CT-003, P0-IT-003 | :app 공개 API compile PASS, 저장 구현/DAO 직접 참조 0 | 1.06 |
| 0 | [P0-TASK-015](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-015) | 공통 타입·명령·오류·이벤트 계약 — Test·리뷰 | P0-TASK-012, P0-TASK-013, P0-TASK-014 | 필수 | :core:simulation / :app | 선행계약후;공통 schema 충돌은직렬 | P0-UT-003, P0-BT-003, P0-FT-003, P0-CT-003, P0-IT-003 | 대표5개Test와원문세부assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 0 | [P0-TASK-016](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-016) | 최소 검증 하네스·공통 UI 껍데기 — 계약·Fixture | - | 필수 | :app | 선행계약후;공통 schema 충돌은직렬 | P0-UT-004, P0-BT-004, P0-FT-004, P0-CT-004, P0-IT-004 | AppShellState·AppRoot·placeholder 계약과 fixture 승인 | 1.06 |
| 0 | [P0-TASK-017](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-017) | 최소 검증 하네스·공통 UI 껍데기 — 핵심 규칙 | P0-TASK-016 | 필수 | :app | 선행계약후;공통 schema 충돌은직렬 | P0-UT-004, P0-BT-004, P0-FT-004 | AppRoot 5-state·기존 Screen ID placeholder 구현, 실제 gameplay navigation 0 | 1.59 |
| 0 | [P0-TASK-018](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-018) | 최소 검증 하네스·공통 UI 껍데기 — 저장·연계 | P0-TASK-016 | 필수 | :core:simulation test source / :app test source | 선행계약후;공통 schema 충돌은직렬 | P0-CT-004, P0-IT-004 | 사용하는 test source에만 fixture 생성, production adapter/DB fixture 0 | 1.59 |
| 0 | [P0-TASK-019](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-019) | 최소 검증 하네스·공통 UI 껍데기 — UI·호출 경로 | P0-TASK-016 | 필수 | :app | 선행계약후;공통 schema 충돌은직렬 | P0-CT-004, P0-IT-004 | 5-state·retry·unknown route·접근성 Compose test PASS | 1.06 |
| 0 | [P0-TASK-020](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-020) | 최소 검증 하네스·공통 UI 껍데기 — Test·리뷰 | P0-TASK-017, P0-TASK-018, P0-TASK-019 | 필수 | :app / :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-UT-004, P0-BT-004, P0-FT-004, P0-CT-004, P0-IT-004 | 대표5개Test와원문세부assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 0 | [P0-TASK-021](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-task-021) | Phase 0 통합 검증·인계 Gate | P0-TASK-005, P0-TASK-010, P0-TASK-015, P0-TASK-020 | 필수 | docs/검증도구 / Gradle root / :app / :core:simulation | 선행계약후;공통 schema 충돌은직렬 | P0-UT-001, P0-UT-002, P0-BT-002, P0-BT-003, P0-CT-003, P0-CN-001, P0-CT-004, P0-IT-002 | Gate 8개 PASS·C01/C02/C03 적용·C14 빌드 증거·인계 contract/fixture·미해결 P0/P1 결함 0 | 1.59 |
| 1 | [P1-TASK-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-001) | canonical source·Assertion 계약 | P0-TASK-021 | 필수 | :core:content / :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-UT-001, P1-BT-001, P1-FT-001, P1-CT-001, P1-IT-001 | CSV/JSON dialect·canonical ID/kind·input 상한·source schema·diagnostic fixture 승인, FUNC-P1-001 미승인 REQUIRED/DATA 0건 | 1.06 |
| 1 | [P1-TASK-002](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-002) | Importer·ID/원문 필드 보존 | P1-TASK-001 | 필수 | :core:content | 선행계약후;공통 schema 충돌은직렬 | P1-UT-001, P1-BT-001, P1-FT-001 | 순수 importer·CSV/JSON dialect·중복/미정/effective display·logical row golden PASS | 2.12 |
| 1 | [P1-TASK-003](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-003) | bootstrap→canonical source 변환 | P1-TASK-001 | 필수 | :tools:content-builder / content/source | 선행계약후;공통 schema 충돌은직렬 | P1-CT-001, P1-IT-001 | canonical source manifest·provenance·roundtrip 승인, bootstrap 직접 build 0 | 1.59 |
| 1 | [P1-TASK-004](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-004) | Phase1 Gradle 경계·Build Spike | P1-TASK-001 | 필수 | Gradle root / :app / :core:content / :core:image / :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-CT-001, P1-IT-001 | 정확한 graph/allowlist/금지 import 검사와 Phase1 Build Spike PASS | 1.06 |
| 1 | [P1-TASK-005](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-005) | Importer Test·독립 리뷰 | P1-TASK-002, P1-TASK-003, P1-TASK-004 | 필수 | :core:content / :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-UT-001, P1-BT-001, P1-FT-001, P1-CT-001, P1-IT-001 | 5개 Test PASS·Assertion coverage·중대 결함 0·리뷰 승인 | 1.06 |
| 1 | [P1-TASK-006](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-006) | bundle schema·hash 계약 | P0-TASK-021 | 필수 | :core:content / :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-UT-002, P1-BT-002, P1-FT-002, P1-CT-002, P1-IT-002 | canonical ID/kind·fresh DDL/query inventory/journal·close·sidecar/idempotent publish/build pointer/report schema/hash/profile fixture 승인, FUNC-P1-002 미승인 REQUIRED/DATA 0건 | 1.06 |
| 1 | [P1-TASK-007](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-007) | Validator·canonical hash | P1-TASK-006 | 필수 | :core:content | 선행계약후;공통 schema 충돌은직렬 | P1-UT-002, P1-BT-002, P1-FT-002 | 검증 순서·오류 정렬·logical hash golden·독립 semantic audit·미정 Full 차단 PASS | 2.12 |
| 1 | [P1-TASK-008](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-008) | staging content.db writer | P1-TASK-006 | 필수 | :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-CT-002, P1-IT-002 | IF NOT EXISTS·N→N+1 migration·SQL 문자열 결합 0, STAGING_NOT_EMPTY 차단, FK/integrity/semantic audit·resource close·sidecar 0·read-only reopen/hash·동일 target idempotent/build-only pointer kill-safe publish PASS | 1.59 |
| 1 | [P1-TASK-009](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-009) | CLI·Gradle build entry | P1-TASK-006 | 필수 | :tools:content-builder / Gradle root | 선행계약후;공통 schema 충돌은직렬 | P1-CT-002, P1-IT-002 | 동일 JSON에서 Markdown/HTML 생성·logical hash 동치·실패 non-zero·부분 산출물 0 | 1.06 |
| 1 | [P1-TASK-010](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-010) | Content builder Test·독립 리뷰 | P1-TASK-007, P1-TASK-008, P1-TASK-009 | 필수 | :core:content / :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-UT-002, P1-BT-002, P1-FT-002, P1-CT-002, P1-IT-002 | 5개 Test PASS·artifact/FK/semantic audit/sealing/hash 증거·중대 결함 0 | 1.06 |
| 1 | [P1-TASK-011](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-011) | asset schema·path·crop 계약 | P0-TASK-021 | 필수 | :core:content / :core:image / :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-UT-003, P1-BT-003, P1-FT-003, P1-CT-003, P1-IT-003 | canonical type·license registry·manifest/path/input 상한/physical metadata·파생 category/crop/focal/화면별 fallback/TEXT/cache/UI state와 6 read API/query plan/lifecycle fixture 승인, FUNC-P1-003 미승인 REQUIRED/DATA 0건 | 1.06 |
| 1 | [P1-TASK-012](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-012) | Asset validator/compiler | P1-TASK-011 | 필수 | :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-UT-003, P1-BT-003, P1-FT-003 | 유효 fixture compile·경로/input 상한/license registry/focal/physical metadata negative case·coverage unresolved 0·stable diagnostics PASS | 2.12 |
| 1 | [P1-TASK-013](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-013) | asset row·manifest 산출 | P1-TASK-011 | 필수 | :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-CT-003, P1-IT-003 | asset DB row·manifest·파일 metadata 일치, 10,000 asset에서도 bounded DOM인 HTML escape 분할 정적 preview/coverage 생성, 외부 요청과 실물 부족 상태 은폐 0 | 1.59 |
| 1 | [P1-TASK-014](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-014) | Android 읽기 전용 AssetResolver | P1-TASK-011 | 필수 | :core:image / :app | 선행계약후;공통 schema 충돌은직렬 | P1-UT-003, P1-CT-003, P1-IT-003 | 6개 read method의 in-memory/builder JVM SQLite 결과와 CDB-Q01..Q06 query plan 동일, network/save write/Main-thread I/O 0, P3 Android adapter contract·owner lifecycle 승인, 유한 후보/TEXT/파생 category·crop/cache/debug gallery 접근성 smoke PASS, Android SQLite adapter·production route 0 | 1.06 |
| 1 | [P1-TASK-015](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-015) | Asset Test·독립 리뷰 | P1-TASK-012, P1-TASK-013, P1-TASK-014 | 필수 | :core:image / :tools:content-builder / :app | 선행계약후;공통 schema 충돌은직렬 | P1-UT-003, P1-BT-003, P1-FT-003, P1-CT-003, P1-IT-003 | 5개 Test PASS·6-query repository 동치/query plan·physical metadata·crop/fallback/TEXT/terminal layout screenshot·cache/decode/PSS·contentDescription 증거·중대 결함 0 | 1.06 |
| 1 | [P1-TASK-016](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-016) | BindingPlan·alias 계약 | P0-TASK-021 | 필수 | :core:content | 선행계약후;공통 schema 충돌은직렬 | P1-UT-004, P1-BT-004, P1-FT-004, P1-CT-004, P1-IT-004 | BindingPlan/alias/오류 fixture 승인, FUNC-P1-004 미승인 REQUIRED/DATA 0건 | 1.06 |
| 1 | [P1-TASK-017](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-017) | 순수 compatibility resolver | P1-TASK-016 | 필수 | :core:content | 선행계약후;공통 schema 충돌은직렬 | P1-UT-004, P1-BT-004, P1-FT-004 | compatibility matrix golden·determinism·I/O 0 PASS | 2.12 |
| 1 | [P1-TASK-018](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-018) | alias flatten·legacy plan | P1-TASK-016 | 필수 | :core:content / :tools:content-builder | 선행계약후;공통 schema 충돌은직렬 | P1-CT-004, P1-IT-004 | alias negative graph 전부 거절·runtime 최대 1회 lookup PASS | 1.59 |
| 1 | [P1-TASK-019](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-019) | P3/P22/P25 계약 인계 | P1-TASK-016 | 필수 | :core:content / docs | 선행계약후;공통 schema 충돌은직렬 | P1-CT-004, P1-IT-004 | P3/P22/P25 handoff contract/fixture 승인, P1 save/apply/editable UI 구현 0 | 1.06 |
| 1 | [P1-TASK-020](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-020) | Compatibility Test·독립 리뷰 | P1-TASK-017, P1-TASK-018, P1-TASK-019 | 필수 | :core:content | 선행계약후;공통 schema 충돌은직렬 | P1-UT-004, P1-BT-004, P1-FT-004, P1-CT-004, P1-IT-004 | 5개 Test PASS·save hash 불변·P3/P25 적용 경계 승인 | 1.06 |
| 1 | [P1-TASK-021](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-task-021) | Phase 1 통합 검증·인계 Gate | P1-TASK-005, P1-TASK-010, P1-TASK-015, P1-TASK-020 | 필수 | :app / :core:content / :core:image / :tools:content-builder / docs | 선행계약후;공통 schema 충돌은직렬 | P1-UT-001, P1-BT-001, P1-FT-001, P1-CT-001, P1-IT-001, P1-UT-002, P1-BT-002, P1-FT-002, P1-CT-002, P1-IT-002, P1-UT-003, P1-BT-003, P1-FT-003, P1-CT-003, P1-IT-003, P1-UT-004, P1-BT-004, P1-FT-004, P1-CT-004, P1-IT-004, P1-RT-001, P1-CN-001, P1-REC-001, P1-PT-001, P1-OP-001, P1-ET-001, P1-IT-005 | 27개 Test PASS·phase1Gate evidence·DB sealing/query plan·same-ID compatibility·save sentinel 불변·중대 결함 0·coverage unresolved 0·PROTOTYPE_ACCEPTED 또는 FULL_CONTENT_READY 명시·P3/P22/P25 인계 승인 | 1.59 |
| 2 | [P2-TASK-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-001) | 단일 작성자 명령 처리 — 계약·Fixture | P0-TASK-021 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-001, P2-BT-001, P2-FT-001, P2-CT-001, P2-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 2 | [P2-TASK-002](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-002) | 단일 작성자 명령 처리 — 핵심 규칙 | P2-TASK-001 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-001, P2-BT-001, P2-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 2 | [P2-TASK-003](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-003) | 단일 작성자 명령 처리 — 저장·연계 | P2-TASK-001 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-001, P2-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 2 | [P2-TASK-004](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-004) | 단일 작성자 명령 처리 — UI·호출 경로 | P2-TASK-001 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-001, P2-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 2 | [P2-TASK-005](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-005) | 단일 작성자 명령 처리 — Test·리뷰 | P2-TASK-002, P2-TASK-003, P2-TASK-004 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-001, P2-BT-001, P2-FT-001, P2-CT-001, P2-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 2 | [P2-TASK-006](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-006) | 게임 달력·잔여 밀리초·RNG 스트림 — 계약·Fixture | P0-TASK-021 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-002, P2-BT-002, P2-FT-002, P2-CT-002, P2-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 2 | [P2-TASK-007](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-007) | 게임 달력·잔여 밀리초·RNG 스트림 — 핵심 규칙 | P2-TASK-006 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-002, P2-BT-002, P2-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 2 | [P2-TASK-008](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-008) | 게임 달력·잔여 밀리초·RNG 스트림 — 저장·연계 | P2-TASK-006 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-002, P2-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 2 | [P2-TASK-009](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-009) | 게임 달력·잔여 밀리초·RNG 스트림 — UI·호출 경로 | P2-TASK-006 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-002, P2-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 2 | [P2-TASK-010](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-010) | 게임 달력·잔여 밀리초·RNG 스트림 — Test·리뷰 | P2-TASK-007, P2-TASK-008, P2-TASK-009 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-002, P2-BT-002, P2-FT-002, P2-CT-002, P2-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 2 | [P2-TASK-011](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-011) | 예약·점유·자원 선점 — 계약·Fixture | P0-TASK-021 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-003, P2-BT-003, P2-FT-003, P2-CT-003, P2-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 2 | [P2-TASK-012](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-012) | 예약·점유·자원 선점 — 핵심 규칙 | P2-TASK-011 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-003, P2-BT-003, P2-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 2 | [P2-TASK-013](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-013) | 예약·점유·자원 선점 — 저장·연계 | P2-TASK-011 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-003, P2-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 2 | [P2-TASK-014](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-014) | 예약·점유·자원 선점 — UI·호출 경로 | P2-TASK-011 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-003, P2-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 2 | [P2-TASK-015](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-015) | 예약·점유·자원 선점 — Test·리뷰 | P2-TASK-012, P2-TASK-013, P2-TASK-014 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-003, P2-BT-003, P2-FT-003, P2-CT-003, P2-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 2 | [P2-TASK-016](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-016) | 이벤트 경계 시간 진행·자동중단 — 계약·Fixture | P0-TASK-021 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-004, P2-BT-004, P2-FT-004, P2-CT-004, P2-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 2 | [P2-TASK-017](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-017) | 이벤트 경계 시간 진행·자동중단 — 핵심 규칙 | P2-TASK-016 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-004, P2-BT-004, P2-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 2 | [P2-TASK-018](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-018) | 이벤트 경계 시간 진행·자동중단 — 저장·연계 | P2-TASK-016 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-004, P2-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 2 | [P2-TASK-019](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-019) | 이벤트 경계 시간 진행·자동중단 — UI·호출 경로 | P2-TASK-016 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-004, P2-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 2 | [P2-TASK-020](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-020) | 이벤트 경계 시간 진행·자동중단 — Test·리뷰 | P2-TASK-017, P2-TASK-018, P2-TASK-019 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-004, P2-BT-004, P2-FT-004, P2-CT-004, P2-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 2 | [P2-TASK-021](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-021) | 세션·생명주기·작업 종료 — 계약·Fixture | P0-TASK-021 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-005, P2-BT-005, P2-FT-005, P2-CT-005, P2-IT-005 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 2 | [P2-TASK-022](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-022) | 세션·생명주기·작업 종료 — 핵심 규칙 | P2-TASK-021 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-005, P2-BT-005, P2-FT-005 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 2 | [P2-TASK-023](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-023) | 세션·생명주기·작업 종료 — 저장·연계 | P2-TASK-021 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-005, P2-IT-005 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 2 | [P2-TASK-024](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-024) | 세션·생명주기·작업 종료 — UI·호출 경로 | P2-TASK-021 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-CT-005, P2-IT-005 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 2 | [P2-TASK-025](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-025) | 세션·생명주기·작업 종료 — Test·리뷰 | P2-TASK-022, P2-TASK-023, P2-TASK-024 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-005, P2-BT-005, P2-FT-005, P2-CT-005, P2-IT-005 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 2 | [P2-TASK-026](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-task-026) | Phase 2 통합 검증·인계 Gate | P2-TASK-005, P2-TASK-010, P2-TASK-015, P2-TASK-020, P2-TASK-025 | 필수 | :core:simulation / :core:common | 선행계약후;공통 schema 충돌은직렬 | P2-UT-001, P2-BT-001, P2-FT-001, P2-CT-001, P2-IT-001, P2-UT-002, P2-BT-002, P2-FT-002, P2-CT-002, P2-IT-002, P2-UT-003, P2-BT-003, P2-FT-003, P2-CT-003, P2-IT-003, P2-UT-004, P2-BT-004, P2-FT-004, P2-CT-004, P2-IT-004, P2-UT-005, P2-BT-005, P2-FT-005, P2-CT-005, P2-IT-005, P2-RT-001, P2-CN-001, P2-REC-001, P2-PT-001, P2-OP-001, P2-ET-001, P2-IT-006 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 3 | [P3-TASK-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-001) | 현재상태 스키마·Dirty 단위 저장 — 계약·Fixture | P0-TASK-021, P1-TASK-021, P2-TASK-026 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-001, P3-BT-001, P3-FT-001, P3-CT-001, P3-IT-001 | 외부 command 1개당 receipt 1개·내부 SaveCoordinator 중첩 command 0 검증 | 1.06 |
| 3 | [P3-TASK-002](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-002) | 현재상태 스키마·Dirty 단위 저장 — 핵심 규칙 | P3-TASK-001 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-001, P3-BT-001, P3-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 3 | [P3-TASK-003](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-003) | 현재상태 스키마·Dirty 단위 저장 — 저장·연계 | P3-TASK-001 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-001, P3-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 3 | [P3-TASK-004](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-004) | 현재상태 스키마·Dirty 단위 저장 — UI·호출 경로 | P3-TASK-001 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-001, P3-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 3 | [P3-TASK-005](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-005) | 현재상태 스키마·Dirty 단위 저장 — Test·리뷰 | P3-TASK-002, P3-TASK-003, P3-TASK-004 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-001, P3-BT-001, P3-FT-001, P3-CT-001, P3-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 3 | [P3-TASK-006](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-006) | 복원 가능한 세대·슬롯·불변 청크 — 계약·Fixture | P0-TASK-021, P1-TASK-021, P2-TASK-026, P3-TASK-001 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-002, P3-BT-002, P3-FT-002, P3-CT-002, P3-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 3 | [P3-TASK-007](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-007) | 복원 가능한 세대·슬롯·불변 청크 — 핵심 규칙 | P3-TASK-006 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-002, P3-BT-002, P3-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 3 | [P3-TASK-008](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-008) | 복원 가능한 세대·슬롯·불변 청크 — 저장·연계 | P3-TASK-006 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-002, P3-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 3 | [P3-TASK-009](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-009) | 복원 가능한 세대·슬롯·불변 청크 — UI·호출 경로 | P3-TASK-006 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-002, P3-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 3 | [P3-TASK-010](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-010) | 복원 가능한 세대·슬롯·불변 청크 — Test·리뷰 | P3-TASK-007, P3-TASK-008, P3-TASK-009 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-002, P3-BT-002, P3-FT-002, P3-CT-002, P3-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 3 | [P3-TASK-011](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-011) | 장기진행·예약 복구 — 계약·Fixture | P0-TASK-021, P1-TASK-021, P2-TASK-026, P3-TASK-001 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-003, P3-BT-003, P3-FT-003, P3-CT-003, P3-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 3 | [P3-TASK-012](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-012) | 장기진행·예약 복구 — 핵심 규칙 | P3-TASK-011 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-003, P3-BT-003, P3-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 3 | [P3-TASK-013](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-013) | 장기진행·예약 복구 — 저장·연계 | P3-TASK-011 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-003, P3-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 3 | [P3-TASK-014](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-014) | 장기진행·예약 복구 — UI·호출 경로 | P3-TASK-011 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-003, P3-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 3 | [P3-TASK-015](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-015) | 장기진행·예약 복구 — Test·리뷰 | P3-TASK-012, P3-TASK-013, P3-TASK-014 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-003, P3-BT-003, P3-FT-003, P3-CT-003, P3-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 3 | [P3-TASK-016](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-016) | 마이그레이션·콘텐츠 호환 — 계약·Fixture | P0-TASK-021, P1-TASK-021, P2-TASK-026, P3-TASK-001 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-004, P3-BT-004, P3-FT-004, P3-CT-004, P3-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 3 | [P3-TASK-017](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-017) | 마이그레이션·콘텐츠 호환 — 핵심 규칙 | P3-TASK-016 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-004, P3-BT-004, P3-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 3 | [P3-TASK-018](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-018) | 마이그레이션·콘텐츠 호환 — 저장·연계 | P3-TASK-016 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-004, P3-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 3 | [P3-TASK-019](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-019) | 마이그레이션·콘텐츠 호환 — UI·호출 경로 | P3-TASK-016 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-004, P3-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 3 | [P3-TASK-020](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-020) | 마이그레이션·콘텐츠 호환 — Test·리뷰 | P3-TASK-017, P3-TASK-018, P3-TASK-019 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-004, P3-BT-004, P3-FT-004, P3-CT-004, P3-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 3 | [P3-TASK-021](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-021) | 오프라인 Export·Import·아카이브 보호 — 계약·Fixture | P0-TASK-021, P1-TASK-021, P2-TASK-026, P3-TASK-001 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-005, P3-BT-005, P3-FT-005, P3-CT-005, P3-IT-005 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 3 | [P3-TASK-022](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-022) | 오프라인 Export·Import·아카이브 보호 — 핵심 규칙 | P3-TASK-021 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-005, P3-BT-005, P3-FT-005 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 3 | [P3-TASK-023](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-023) | 오프라인 Export·Import·아카이브 보호 — 저장·연계 | P3-TASK-021 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-005, P3-IT-005 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 3 | [P3-TASK-024](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-024) | 오프라인 Export·Import·아카이브 보호 — UI·호출 경로 | P3-TASK-021 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-005, P3-IT-005 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 3 | [P3-TASK-025](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-025) | 오프라인 Export·Import·아카이브 보호 — Test·리뷰 | P3-TASK-022, P3-TASK-023, P3-TASK-024 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-005, P3-BT-005, P3-FT-005, P3-CT-005, P3-IT-005 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 3 | [P3-TASK-026](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-026) | 무결성 검사·복구·보존 GC — 계약·Fixture | P0-TASK-021, P1-TASK-021, P2-TASK-026, P3-TASK-001 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-006, P3-BT-006, P3-FT-006, P3-CT-006, P3-IT-006 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 3 | [P3-TASK-027](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-027) | 무결성 검사·복구·보존 GC — 핵심 규칙 | P3-TASK-026 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-006, P3-BT-006, P3-FT-006 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 3 | [P3-TASK-028](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-028) | 무결성 검사·복구·보존 GC — 저장·연계 | P3-TASK-026 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-006, P3-IT-006 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 3 | [P3-TASK-029](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-029) | 무결성 검사·복구·보존 GC — UI·호출 경로 | P3-TASK-026 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-CT-006, P3-IT-006 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 3 | [P3-TASK-030](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-030) | 무결성 검사·복구·보존 GC — Test·리뷰 | P3-TASK-027, P3-TASK-028, P3-TASK-029 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-006, P3-BT-006, P3-FT-006, P3-CT-006, P3-IT-006 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 3 | [P3-TASK-031](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-task-031) | Phase 3 통합 검증·인계 Gate | P3-TASK-005, P3-TASK-010, P3-TASK-015, P3-TASK-020, P3-TASK-025, P3-TASK-030 | 필수 | :core:database / :core:save / :core:data | 선행계약후;공통 schema 충돌은직렬 | P3-UT-001, P3-BT-001, P3-FT-001, P3-CT-001, P3-IT-001, P3-UT-002, P3-BT-002, P3-FT-002, P3-CT-002, P3-IT-002, P3-UT-003, P3-BT-003, P3-FT-003, P3-CT-003, P3-IT-003, P3-UT-004, P3-BT-004, P3-FT-004, P3-CT-004, P3-IT-004, P3-UT-005, P3-BT-005, P3-FT-005, P3-CT-005, P3-IT-005, P3-UT-006, P3-BT-006, P3-FT-006, P3-CT-006, P3-IT-006, P3-RT-001, P3-CN-001, P3-REC-001, P3-PT-001, P3-OP-001, P3-ET-001, P3-IT-007 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 4 | [P4-TASK-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-001) | NPC 생성·성별 이름·초상 일괄 확정 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-001, P4-BT-001, P4-FT-001, P4-CT-001, P4-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 4 | [P4-TASK-002](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-002) | NPC 생성·성별 이름·초상 일괄 확정 — 핵심 규칙 | P4-TASK-001 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-001, P4-BT-001, P4-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 4 | [P4-TASK-003](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-003) | NPC 생성·성별 이름·초상 일괄 확정 — 저장·연계 | P4-TASK-001 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-001, P4-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 4 | [P4-TASK-004](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-004) | NPC 생성·성별 이름·초상 일괄 확정 — UI·호출 경로 | P4-TASK-001 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-001, P4-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 4 | [P4-TASK-005](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-005) | NPC 생성·성별 이름·초상 일괄 확정 — Test·리뷰 | P4-TASK-002, P4-TASK-003, P4-TASK-004 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-001, P4-BT-001, P4-FT-001, P4-CT-001, P4-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 4 | [P4-TASK-006](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-006) | 기본스탯·경험치·성장원장 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-002, P4-BT-002, P4-FT-002, P4-CT-002, P4-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 4 | [P4-TASK-007](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-007) | 기본스탯·경험치·성장원장 — 핵심 규칙 | P4-TASK-006 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-002, P4-BT-002, P4-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 4 | [P4-TASK-008](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-008) | 기본스탯·경험치·성장원장 — 저장·연계 | P4-TASK-006 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-002, P4-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 4 | [P4-TASK-009](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-009) | 기본스탯·경험치·성장원장 — UI·호출 경로 | P4-TASK-006 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-002, P4-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 4 | [P4-TASK-010](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-010) | 기본스탯·경험치·성장원장 — Test·리뷰 | P4-TASK-007, P4-TASK-008, P4-TASK-009 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-002, P4-BT-002, P4-FT-002, P4-CT-002, P4-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 4 | [P4-TASK-011](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-011) | 잠재력·후천변화·숙련 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-003, P4-BT-003, P4-FT-003, P4-CT-003, P4-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 4 | [P4-TASK-012](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-012) | 잠재력·후천변화·숙련 — 핵심 규칙 | P4-TASK-011 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-003, P4-BT-003, P4-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 4 | [P4-TASK-013](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-013) | 잠재력·후천변화·숙련 — 저장·연계 | P4-TASK-011 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-003, P4-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 4 | [P4-TASK-014](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-014) | 잠재력·후천변화·숙련 — UI·호출 경로 | P4-TASK-011 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-003, P4-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 4 | [P4-TASK-015](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-015) | 잠재력·후천변화·숙련 — Test·리뷰 | P4-TASK-012, P4-TASK-013, P4-TASK-014 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-003, P4-BT-003, P4-FT-003, P4-CT-003, P4-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 4 | [P4-TASK-016](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-016) | 클래스 재훈련·스탯 재계산 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-004, P4-BT-004, P4-FT-004, P4-CT-004, P4-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 4 | [P4-TASK-017](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-017) | 클래스 재훈련·스탯 재계산 — 핵심 규칙 | P4-TASK-016 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-004, P4-BT-004, P4-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 4 | [P4-TASK-018](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-018) | 클래스 재훈련·스탯 재계산 — 저장·연계 | P4-TASK-016 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-004, P4-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 4 | [P4-TASK-019](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-019) | 클래스 재훈련·스탯 재계산 — UI·호출 경로 | P4-TASK-016 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-004, P4-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 4 | [P4-TASK-020](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-020) | 클래스 재훈련·스탯 재계산 — Test·리뷰 | P4-TASK-017, P4-TASK-018, P4-TASK-019 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-004, P4-BT-004, P4-FT-004, P4-CT-004, P4-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 4 | [P4-TASK-021](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-021) | 초기 정보 비대칭·인물 조회 계약 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-005, P4-BT-005, P4-FT-005, P4-CT-005, P4-IT-005 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 4 | [P4-TASK-022](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-022) | 초기 정보 비대칭·인물 조회 계약 — 핵심 규칙 | P4-TASK-021 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-005, P4-BT-005, P4-FT-005 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 4 | [P4-TASK-023](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-023) | 초기 정보 비대칭·인물 조회 계약 — 저장·연계 | P4-TASK-021 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-005, P4-IT-005 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 4 | [P4-TASK-024](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-024) | 초기 정보 비대칭·인물 조회 계약 — UI·호출 경로 | P4-TASK-021 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-CT-005, P4-IT-005 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 4 | [P4-TASK-025](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-025) | 초기 정보 비대칭·인물 조회 계약 — Test·리뷰 | P4-TASK-022, P4-TASK-023, P4-TASK-024 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-005, P4-BT-005, P4-FT-005, P4-CT-005, P4-IT-005 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 4 | [P4-TASK-026](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-task-026) | Phase 4 통합 검증·인계 Gate | P4-TASK-005, P4-TASK-010, P4-TASK-015, P4-TASK-020, P4-TASK-025 | 필수 | :core:simulation/character / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P4-UT-001, P4-BT-001, P4-FT-001, P4-CT-001, P4-IT-001, P4-UT-002, P4-BT-002, P4-FT-002, P4-CT-002, P4-IT-002, P4-UT-003, P4-BT-003, P4-FT-003, P4-CT-003, P4-IT-003, P4-UT-004, P4-BT-004, P4-FT-004, P4-CT-004, P4-IT-004, P4-UT-005, P4-BT-005, P4-FT-005, P4-CT-005, P4-IT-005, P4-RT-001, P4-CN-001, P4-REC-001, P4-PT-001, P4-OP-001, P4-ET-001, P4-IT-006 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 5 | [P5-TASK-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-001) | 인벤토리·장착·소유권 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031, P4-TASK-026 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-001, P5-BT-001, P5-FT-001, P5-CT-001, P5-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 5 | [P5-TASK-002](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-002) | 인벤토리·장착·소유권 — 핵심 규칙 | P5-TASK-001 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-001, P5-BT-001, P5-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 5 | [P5-TASK-003](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-003) | 인벤토리·장착·소유권 — 저장·연계 | P5-TASK-001 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-CT-001, P5-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 5 | [P5-TASK-004](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-004) | 인벤토리·장착·소유권 — UI·호출 경로 | P5-TASK-001 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-CT-001, P5-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 5 | [P5-TASK-005](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-005) | 인벤토리·장착·소유권 — Test·리뷰 | P5-TASK-002, P5-TASK-003, P5-TASK-004 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-001, P5-BT-001, P5-FT-001, P5-CT-001, P5-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 5 | [P5-TASK-006](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-006) | 스킬·접사·개인 상성 데이터 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031, P4-TASK-026 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-002, P5-BT-002, P5-FT-002, P5-CT-002, P5-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 5 | [P5-TASK-007](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-007) | 스킬·접사·개인 상성 데이터 — 핵심 규칙 | P5-TASK-006 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-002, P5-BT-002, P5-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 5 | [P5-TASK-008](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-008) | 스킬·접사·개인 상성 데이터 — 저장·연계 | P5-TASK-006 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-CT-002, P5-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 5 | [P5-TASK-009](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-009) | 스킬·접사·개인 상성 데이터 — UI·호출 경로 | P5-TASK-006 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-CT-002, P5-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 5 | [P5-TASK-010](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-010) | 스킬·접사·개인 상성 데이터 — Test·리뷰 | P5-TASK-007, P5-TASK-008, P5-TASK-009 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-002, P5-BT-002, P5-FT-002, P5-CT-002, P5-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 5 | [P5-TASK-011](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-011) | Loadout·프리셋·전술 조건식 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031, P4-TASK-026 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-003, P5-BT-003, P5-FT-003, P5-CT-003, P5-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 5 | [P5-TASK-012](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-012) | Loadout·프리셋·전술 조건식 — 핵심 규칙 | P5-TASK-011 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-003, P5-BT-003, P5-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 5 | [P5-TASK-013](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-013) | Loadout·프리셋·전술 조건식 — 저장·연계 | P5-TASK-011 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-CT-003, P5-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 5 | [P5-TASK-014](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-014) | Loadout·프리셋·전술 조건식 — UI·호출 경로 | P5-TASK-011 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-CT-003, P5-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 5 | [P5-TASK-015](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-015) | Loadout·프리셋·전술 조건식 — Test·리뷰 | P5-TASK-012, P5-TASK-013, P5-TASK-014 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-003, P5-BT-003, P5-FT-003, P5-CT-003, P5-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 5 | [P5-TASK-016](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-016) | 드롭·보상 예산·타겟파밍 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P3-TASK-031, P4-TASK-026 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-004, P5-BT-004, P5-FT-004, P5-CT-004, P5-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 5 | [P5-TASK-017](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-017) | 드롭·보상 예산·타겟파밍 — 핵심 규칙 | P5-TASK-016 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-004, P5-BT-004, P5-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 5 | [P5-TASK-018](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-018) | 드롭·보상 예산·타겟파밍 — 저장·연계 | P5-TASK-016 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-CT-004, P5-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 5 | [P5-TASK-019](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-019) | 드롭·보상 예산·타겟파밍 — UI·호출 경로 | P5-TASK-016 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-CT-004, P5-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 5 | [P5-TASK-020](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-020) | 드롭·보상 예산·타겟파밍 — Test·리뷰 | P5-TASK-017, P5-TASK-018, P5-TASK-019 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-004, P5-BT-004, P5-FT-004, P5-CT-004, P5-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 5 | [P5-TASK-021](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-task-021) | Phase 5 통합 검증·인계 Gate | P5-TASK-005, P5-TASK-010, P5-TASK-015, P5-TASK-020 | 필수 | :core:simulation/equipment,skill / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P5-UT-001, P5-BT-001, P5-FT-001, P5-CT-001, P5-IT-001, P5-UT-002, P5-BT-002, P5-FT-002, P5-CT-002, P5-IT-002, P5-UT-003, P5-BT-003, P5-FT-003, P5-CT-003, P5-IT-003, P5-UT-004, P5-BT-004, P5-FT-004, P5-CT-004, P5-IT-004, P5-RT-001, P5-CN-001, P5-REC-001, P5-PT-001, P5-OP-001, P5-ET-001, P5-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 6 | [P6-TASK-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-001) | 이벤트 큐·동시 해결 배치 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-001, P6-BT-001, P6-FT-001, P6-CT-001, P6-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 6 | [P6-TASK-002](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-002) | 이벤트 큐·동시 해결 배치 — 핵심 규칙 | P6-TASK-001 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-001, P6-BT-001, P6-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 6 | [P6-TASK-003](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-003) | 이벤트 큐·동시 해결 배치 — 저장·연계 | P6-TASK-001 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-001, P6-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 6 | [P6-TASK-004](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-004) | 이벤트 큐·동시 해결 배치 — UI·호출 경로 | P6-TASK-001 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-001, P6-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 6 | [P6-TASK-005](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-005) | 이벤트 큐·동시 해결 배치 — Test·리뷰 | P6-TASK-002, P6-TASK-003, P6-TASK-004 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-001, P6-BT-001, P6-FT-001, P6-CT-001, P6-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 6 | [P6-TASK-006](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-006) | 피해·치유·명중·보호막 공식 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-002, P6-BT-002, P6-FT-002, P6-CT-002, P6-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 6 | [P6-TASK-007](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-007) | 피해·치유·명중·보호막 공식 — 핵심 규칙 | P6-TASK-006 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-002, P6-BT-002, P6-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 6 | [P6-TASK-008](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-008) | 피해·치유·명중·보호막 공식 — 저장·연계 | P6-TASK-006 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-002, P6-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 6 | [P6-TASK-009](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-009) | 피해·치유·명중·보호막 공식 — UI·호출 경로 | P6-TASK-006 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-002, P6-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 6 | [P6-TASK-010](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-010) | 피해·치유·명중·보호막 공식 — Test·리뷰 | P6-TASK-007, P6-TASK-008, P6-TASK-009 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-002, P6-BT-002, P6-FT-002, P6-CT-002, P6-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 6 | [P6-TASK-011](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-011) | 액션 상태·자원·발사체 스냅샷 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-003, P6-BT-003, P6-FT-003, P6-CT-003, P6-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 6 | [P6-TASK-012](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-012) | 액션 상태·자원·발사체 스냅샷 — 핵심 규칙 | P6-TASK-011 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-003, P6-BT-003, P6-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 6 | [P6-TASK-013](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-013) | 액션 상태·자원·발사체 스냅샷 — 저장·연계 | P6-TASK-011 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-003, P6-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 6 | [P6-TASK-014](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-014) | 액션 상태·자원·발사체 스냅샷 — UI·호출 경로 | P6-TASK-011 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-003, P6-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 6 | [P6-TASK-015](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-015) | 액션 상태·자원·발사체 스냅샷 — Test·리뷰 | P6-TASK-012, P6-TASK-013, P6-TASK-014 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-003, P6-BT-003, P6-FT-003, P6-CT-003, P6-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 6 | [P6-TASK-016](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-016) | 상태이상·축적·Tick·점감 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-004, P6-BT-004, P6-FT-004, P6-CT-004, P6-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 6 | [P6-TASK-017](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-017) | 상태이상·축적·Tick·점감 — 핵심 규칙 | P6-TASK-016 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-004, P6-BT-004, P6-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 6 | [P6-TASK-018](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-018) | 상태이상·축적·Tick·점감 — 저장·연계 | P6-TASK-016 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-004, P6-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 6 | [P6-TASK-019](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-019) | 상태이상·축적·Tick·점감 — UI·호출 경로 | P6-TASK-016 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-004, P6-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 6 | [P6-TASK-020](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-020) | 상태이상·축적·Tick·점감 — Test·리뷰 | P6-TASK-017, P6-TASK-018, P6-TASK-019 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-004, P6-BT-004, P6-FT-004, P6-CT-004, P6-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 6 | [P6-TASK-021](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-021) | 후퇴·반응·전투 종료 정산 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-005, P6-BT-005, P6-FT-005, P6-CT-005, P6-IT-005 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 6 | [P6-TASK-022](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-022) | 후퇴·반응·전투 종료 정산 — 핵심 규칙 | P6-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-005, P6-BT-005, P6-FT-005 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 6 | [P6-TASK-023](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-023) | 후퇴·반응·전투 종료 정산 — 저장·연계 | P6-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-005, P6-IT-005 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 6 | [P6-TASK-024](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-024) | 후퇴·반응·전투 종료 정산 — UI·호출 경로 | P6-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-005, P6-IT-005 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 6 | [P6-TASK-025](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-025) | 후퇴·반응·전투 종료 정산 — Test·리뷰 | P6-TASK-022, P6-TASK-023, P6-TASK-024 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-005, P6-BT-005, P6-FT-005, P6-CT-005, P6-IT-005 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 6 | [P6-TASK-026](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-026) | 재생·로그·전투 버전 일치 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-006, P6-BT-006, P6-FT-006, P6-CT-006, P6-IT-006 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 6 | [P6-TASK-027](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-027) | 재생·로그·전투 버전 일치 — 핵심 규칙 | P6-TASK-026 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-006, P6-BT-006, P6-FT-006 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 6 | [P6-TASK-028](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-028) | 재생·로그·전투 버전 일치 — 저장·연계 | P6-TASK-026 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-006, P6-IT-006 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 6 | [P6-TASK-029](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-029) | 재생·로그·전투 버전 일치 — UI·호출 경로 | P6-TASK-026 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-CT-006, P6-IT-006 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 6 | [P6-TASK-030](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-030) | 재생·로그·전투 버전 일치 — Test·리뷰 | P6-TASK-027, P6-TASK-028, P6-TASK-029 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-006, P6-BT-006, P6-FT-006, P6-CT-006, P6-IT-006 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 6 | [P6-TASK-031](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-task-031) | Phase 6 통합 검증·인계 Gate | P6-TASK-005, P6-TASK-010, P6-TASK-015, P6-TASK-020, P6-TASK-025, P6-TASK-030 | 필수 | :core:simulation/combat / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P6-UT-001, P6-BT-001, P6-FT-001, P6-CT-001, P6-IT-001, P6-UT-002, P6-BT-002, P6-FT-002, P6-CT-002, P6-IT-002, P6-UT-003, P6-BT-003, P6-FT-003, P6-CT-003, P6-IT-003, P6-UT-004, P6-BT-004, P6-FT-004, P6-CT-004, P6-IT-004, P6-UT-005, P6-BT-005, P6-FT-005, P6-CT-005, P6-IT-005, P6-UT-006, P6-BT-006, P6-FT-006, P6-CT-006, P6-IT-006, P6-RT-001, P6-CN-001, P6-REC-001, P6-PT-001, P6-OP-001, P6-ET-001, P6-IT-007 | 필수 Test PASS·P6-IT-007 Early Playable Gate PASS·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 7 | [P7-TASK-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-001) | 몬스터 감지·Utility·역할 AI — 계약·Fixture | P1-TASK-021, P6-TASK-031 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-001, P7-BT-001, P7-FT-001, P7-CT-001, P7-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 7 | [P7-TASK-002](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-002) | 몬스터 감지·Utility·역할 AI — 핵심 규칙 | P7-TASK-001 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-001, P7-BT-001, P7-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 7 | [P7-TASK-003](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-003) | 몬스터 감지·Utility·역할 AI — 저장·연계 | P7-TASK-001 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-CT-001, P7-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 7 | [P7-TASK-004](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-004) | 몬스터 감지·Utility·역할 AI — UI·호출 경로 | P7-TASK-001 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-CT-001, P7-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 7 | [P7-TASK-005](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-005) | 몬스터 감지·Utility·역할 AI — Test·리뷰 | P7-TASK-002, P7-TASK-003, P7-TASK-004 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-001, P7-BT-001, P7-FT-001, P7-CT-001, P7-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 7 | [P7-TASK-006](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-006) | 그룹 Blackboard·순찰·학습 — 계약·Fixture | P1-TASK-021, P6-TASK-031 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-002, P7-BT-002, P7-FT-002, P7-CT-002, P7-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 7 | [P7-TASK-007](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-007) | 그룹 Blackboard·순찰·학습 — 핵심 규칙 | P7-TASK-006 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-002, P7-BT-002, P7-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 7 | [P7-TASK-008](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-008) | 그룹 Blackboard·순찰·학습 — 저장·연계 | P7-TASK-006 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-CT-002, P7-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 7 | [P7-TASK-009](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-009) | 그룹 Blackboard·순찰·학습 — UI·호출 경로 | P7-TASK-006 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-CT-002, P7-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 7 | [P7-TASK-010](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-010) | 그룹 Blackboard·순찰·학습 — Test·리뷰 | P7-TASK-007, P7-TASK-008, P7-TASK-009 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-002, P7-BT-002, P7-FT-002, P7-CT-002, P7-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 7 | [P7-TASK-011](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-011) | 보스 전조·페이즈·강인도 — 계약·Fixture | P1-TASK-021, P6-TASK-031 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-003, P7-BT-003, P7-FT-003, P7-CT-003, P7-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 7 | [P7-TASK-012](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-012) | 보스 전조·페이즈·강인도 — 핵심 규칙 | P7-TASK-011 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-003, P7-BT-003, P7-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 7 | [P7-TASK-013](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-013) | 보스 전조·페이즈·강인도 — 저장·연계 | P7-TASK-011 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-CT-003, P7-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 7 | [P7-TASK-014](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-014) | 보스 전조·페이즈·강인도 — UI·호출 경로 | P7-TASK-011 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-CT-003, P7-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 7 | [P7-TASK-015](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-015) | 보스 전조·페이즈·강인도 — Test·리뷰 | P7-TASK-012, P7-TASK-013, P7-TASK-014 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-003, P7-BT-003, P7-FT-003, P7-CT-003, P7-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 7 | [P7-TASK-016](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-016) | 보스 카탈로그·공략대·웨이브 — 계약·Fixture | P1-TASK-021, P6-TASK-031 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-004, P7-BT-004, P7-FT-004, P7-CT-004, P7-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 7 | [P7-TASK-017](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-017) | 보스 카탈로그·공략대·웨이브 — 핵심 규칙 | P7-TASK-016 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-004, P7-BT-004, P7-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 7 | [P7-TASK-018](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-018) | 보스 카탈로그·공략대·웨이브 — 저장·연계 | P7-TASK-016 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-CT-004, P7-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 7 | [P7-TASK-019](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-019) | 보스 카탈로그·공략대·웨이브 — UI·호출 경로 | P7-TASK-016 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-CT-004, P7-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 7 | [P7-TASK-020](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-020) | 보스 카탈로그·공략대·웨이브 — Test·리뷰 | P7-TASK-017, P7-TASK-018, P7-TASK-019 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-004, P7-BT-004, P7-FT-004, P7-CT-004, P7-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 7 | [P7-TASK-021](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-task-021) | Phase 7 통합 검증·인계 Gate | P7-TASK-005, P7-TASK-010, P7-TASK-015, P7-TASK-020 | 필수 | :core:simulation/ai,boss / :feature:combat | 선행계약후;공통 schema 충돌은직렬 | P7-UT-001, P7-BT-001, P7-FT-001, P7-CT-001, P7-IT-001, P7-UT-002, P7-BT-002, P7-FT-002, P7-CT-002, P7-IT-002, P7-UT-003, P7-BT-003, P7-FT-003, P7-CT-003, P7-IT-003, P7-UT-004, P7-BT-004, P7-FT-004, P7-CT-004, P7-IT-004, P7-RT-001, P7-CN-001, P7-REC-001, P7-PT-001, P7-OP-001, P7-ET-001, P7-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 8 | [P8-TASK-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-001) | 던전 그래프·열쇠/문 위상 생성 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P6-TASK-031, P7-TASK-021 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-001, P8-BT-001, P8-FT-001, P8-CT-001, P8-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 8 | [P8-TASK-002](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-002) | 던전 그래프·열쇠/문 위상 생성 — 핵심 규칙 | P8-TASK-001 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-001, P8-BT-001, P8-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 8 | [P8-TASK-003](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-003) | 던전 그래프·열쇠/문 위상 생성 — 저장·연계 | P8-TASK-001 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-CT-001, P8-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 8 | [P8-TASK-004](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-004) | 던전 그래프·열쇠/문 위상 생성 — UI·호출 경로 | P8-TASK-001 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-CT-001, P8-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 8 | [P8-TASK-005](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-005) | 던전 그래프·열쇠/문 위상 생성 — Test·리뷰 | P8-TASK-002, P8-TASK-003, P8-TASK-004 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-001, P8-BT-001, P8-FT-001, P8-CT-001, P8-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 8 | [P8-TASK-006](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-006) | 지형·구역·위험/보상 예산 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P6-TASK-031, P7-TASK-021 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-002, P8-BT-002, P8-FT-002, P8-CT-002, P8-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 8 | [P8-TASK-007](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-007) | 지형·구역·위험/보상 예산 — 핵심 규칙 | P8-TASK-006 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-002, P8-BT-002, P8-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 8 | [P8-TASK-008](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-008) | 지형·구역·위험/보상 예산 — 저장·연계 | P8-TASK-006 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-CT-002, P8-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 8 | [P8-TASK-009](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-009) | 지형·구역·위험/보상 예산 — UI·호출 경로 | P8-TASK-006 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-CT-002, P8-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 8 | [P8-TASK-010](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-010) | 지형·구역·위험/보상 예산 — Test·리뷰 | P8-TASK-007, P8-TASK-008, P8-TASK-009 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-002, P8-BT-002, P8-FT-002, P8-CT-002, P8-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 8 | [P8-TASK-011](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-011) | 몬스터·상자·함정·정복목표 배치 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P6-TASK-031, P7-TASK-021 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-003, P8-BT-003, P8-FT-003, P8-CT-003, P8-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 8 | [P8-TASK-012](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-012) | 몬스터·상자·함정·정복목표 배치 — 핵심 규칙 | P8-TASK-011 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-003, P8-BT-003, P8-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 8 | [P8-TASK-013](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-013) | 몬스터·상자·함정·정복목표 배치 — 저장·연계 | P8-TASK-011 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-CT-003, P8-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 8 | [P8-TASK-014](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-014) | 몬스터·상자·함정·정복목표 배치 — UI·호출 경로 | P8-TASK-011 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-CT-003, P8-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 8 | [P8-TASK-015](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-015) | 몬스터·상자·함정·정복목표 배치 — Test·리뷰 | P8-TASK-012, P8-TASK-013, P8-TASK-014 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-003, P8-BT-003, P8-FT-003, P8-CT-003, P8-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 8 | [P8-TASK-016](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-016) | 던전 생명주기·Seed·재방문 — 계약·Fixture | P1-TASK-021, P2-TASK-026, P6-TASK-031, P7-TASK-021 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-004, P8-BT-004, P8-FT-004, P8-CT-004, P8-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 8 | [P8-TASK-017](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-017) | 던전 생명주기·Seed·재방문 — 핵심 규칙 | P8-TASK-016 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-004, P8-BT-004, P8-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 8 | [P8-TASK-018](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-018) | 던전 생명주기·Seed·재방문 — 저장·연계 | P8-TASK-016 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-CT-004, P8-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 8 | [P8-TASK-019](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-019) | 던전 생명주기·Seed·재방문 — UI·호출 경로 | P8-TASK-016 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-CT-004, P8-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 8 | [P8-TASK-020](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-020) | 던전 생명주기·Seed·재방문 — Test·리뷰 | P8-TASK-017, P8-TASK-018, P8-TASK-019 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-004, P8-BT-004, P8-FT-004, P8-CT-004, P8-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 8 | [P8-TASK-021](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-task-021) | Phase 8 통합 검증·인계 Gate | P8-TASK-005, P8-TASK-010, P8-TASK-015, P8-TASK-020 | 필수 | :core:simulation/dungeon / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P8-UT-001, P8-BT-001, P8-FT-001, P8-CT-001, P8-IT-001, P8-UT-002, P8-BT-002, P8-FT-002, P8-CT-002, P8-IT-002, P8-UT-003, P8-BT-003, P8-FT-003, P8-CT-003, P8-IT-003, P8-UT-004, P8-BT-004, P8-FT-004, P8-CT-004, P8-IT-004, P8-RT-001, P8-CN-001, P8-REC-001, P8-PT-001, P8-OP-001, P8-ET-001, P8-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 9 | [P9-TASK-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-001) | MUD 이동·조사·점진 공개 — 계약·Fixture | P3-TASK-031, P5-TASK-021, P6-TASK-031, P7-TASK-021, P8-TASK-021 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-001, P9-BT-001, P9-FT-001, P9-CT-001, P9-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 9 | [P9-TASK-002](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-002) | MUD 이동·조사·점진 공개 — 핵심 규칙 | P9-TASK-001 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-001, P9-BT-001, P9-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 9 | [P9-TASK-003](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-003) | MUD 이동·조사·점진 공개 — 저장·연계 | P9-TASK-001 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-001, P9-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 9 | [P9-TASK-004](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-004) | MUD 이동·조사·점진 공개 — UI·호출 경로 | P9-TASK-001 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-001, P9-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 9 | [P9-TASK-005](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-005) | MUD 이동·조사·점진 공개 — Test·리뷰 | P9-TASK-002, P9-TASK-003, P9-TASK-004 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-001, P9-BT-001, P9-FT-001, P9-CT-001, P9-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 9 | [P9-TASK-006](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-006) | 지도·주석·안전 복귀 경로 — 계약·Fixture | P3-TASK-031, P5-TASK-021, P6-TASK-031, P7-TASK-021, P8-TASK-021 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-002, P9-BT-002, P9-FT-002, P9-CT-002, P9-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 9 | [P9-TASK-007](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-007) | 지도·주석·안전 복귀 경로 — 핵심 규칙 | P9-TASK-006 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-002, P9-BT-002, P9-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 9 | [P9-TASK-008](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-008) | 지도·주석·안전 복귀 경로 — 저장·연계 | P9-TASK-006 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-002, P9-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 9 | [P9-TASK-009](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-009) | 지도·주석·안전 복귀 경로 — UI·호출 경로 | P9-TASK-006 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-002, P9-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 9 | [P9-TASK-010](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-010) | 지도·주석·안전 복귀 경로 — Test·리뷰 | P9-TASK-007, P9-TASK-008, P9-TASK-009 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-002, P9-BT-002, P9-FT-002, P9-CT-002, P9-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 9 | [P9-TASK-011](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-011) | 야영·보급·경계·응급처치 — 계약·Fixture | P3-TASK-031, P5-TASK-021, P6-TASK-031, P7-TASK-021, P8-TASK-021 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-003, P9-BT-003, P9-FT-003, P9-CT-003, P9-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 9 | [P9-TASK-012](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-012) | 야영·보급·경계·응급처치 — 핵심 규칙 | P9-TASK-011 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-003, P9-BT-003, P9-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 9 | [P9-TASK-013](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-013) | 야영·보급·경계·응급처치 — 저장·연계 | P9-TASK-011 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-003, P9-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 9 | [P9-TASK-014](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-014) | 야영·보급·경계·응급처치 — UI·호출 경로 | P9-TASK-011 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-003, P9-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 9 | [P9-TASK-015](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-015) | 야영·보급·경계·응급처치 — Test·리뷰 | P9-TASK-012, P9-TASK-013, P9-TASK-014 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-003, P9-BT-003, P9-FT-003, P9-CT-003, P9-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 9 | [P9-TASK-016](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-016) | 패배·구조·SAFE_RECOVERY — 계약·Fixture | P3-TASK-031, P5-TASK-021, P6-TASK-031, P7-TASK-021, P8-TASK-021 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-004, P9-BT-004, P9-FT-004, P9-CT-004, P9-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 9 | [P9-TASK-017](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-017) | 패배·구조·SAFE_RECOVERY — 핵심 규칙 | P9-TASK-016 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-004, P9-BT-004, P9-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 9 | [P9-TASK-018](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-018) | 패배·구조·SAFE_RECOVERY — 저장·연계 | P9-TASK-016 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-004, P9-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 9 | [P9-TASK-019](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-019) | 패배·구조·SAFE_RECOVERY — UI·호출 경로 | P9-TASK-016 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-004, P9-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 9 | [P9-TASK-020](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-020) | 패배·구조·SAFE_RECOVERY — Test·리뷰 | P9-TASK-017, P9-TASK-018, P9-TASK-019 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-004, P9-BT-004, P9-FT-004, P9-CT-004, P9-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 9 | [P9-TASK-021](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-021) | 정복·보상·첫 완결 플레이 루프 — 계약·Fixture | P3-TASK-031, P5-TASK-021, P6-TASK-031, P7-TASK-021, P8-TASK-021 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-005, P9-BT-005, P9-FT-005, P9-CT-005, P9-IT-005 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 9 | [P9-TASK-022](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-022) | 정복·보상·첫 완결 플레이 루프 — 핵심 규칙 | P9-TASK-021 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-005, P9-BT-005, P9-FT-005 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 9 | [P9-TASK-023](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-023) | 정복·보상·첫 완결 플레이 루프 — 저장·연계 | P9-TASK-021 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-005, P9-IT-005 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 9 | [P9-TASK-024](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-024) | 정복·보상·첫 완결 플레이 루프 — UI·호출 경로 | P9-TASK-021 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-CT-005, P9-IT-005 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 9 | [P9-TASK-025](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-025) | 정복·보상·첫 완결 플레이 루프 — Test·리뷰 | P9-TASK-022, P9-TASK-023, P9-TASK-024 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-005, P9-BT-005, P9-FT-005, P9-CT-005, P9-IT-005 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 9 | [P9-TASK-026](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-task-026) | Phase 9 통합 검증·인계 Gate | P9-TASK-005, P9-TASK-010, P9-TASK-015, P9-TASK-020, P9-TASK-025 | 필수 | :core:simulation/exploration / :feature:dungeon | 선행계약후;공통 schema 충돌은직렬 | P9-UT-001, P9-BT-001, P9-FT-001, P9-CT-001, P9-IT-001, P9-UT-002, P9-BT-002, P9-FT-002, P9-CT-002, P9-IT-002, P9-UT-003, P9-BT-003, P9-FT-003, P9-CT-003, P9-IT-003, P9-UT-004, P9-BT-004, P9-FT-004, P9-CT-004, P9-IT-004, P9-UT-005, P9-BT-005, P9-FT-005, P9-CT-005, P9-IT-005, P9-RT-001, P9-CN-001, P9-REC-001, P9-PT-001, P9-OP-001, P9-ET-001, P9-IT-006 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 10 | [P10-TASK-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-001) | 도시 이동·시설·영업·대기 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021, P9-TASK-026 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-001, P10-BT-001, P10-FT-001, P10-CT-001, P10-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 10 | [P10-TASK-002](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-002) | 도시 이동·시설·영업·대기 — 핵심 규칙 | P10-TASK-001 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-001, P10-BT-001, P10-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 10 | [P10-TASK-003](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-003) | 도시 이동·시설·영업·대기 — 저장·연계 | P10-TASK-001 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-CT-001, P10-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 10 | [P10-TASK-004](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-004) | 도시 이동·시설·영업·대기 — UI·호출 경로 | P10-TASK-001 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-CT-001, P10-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 10 | [P10-TASK-005](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-005) | 도시 이동·시설·영업·대기 — Test·리뷰 | P10-TASK-002, P10-TASK-003, P10-TASK-004 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-001, P10-BT-001, P10-FT-001, P10-CT-001, P10-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 10 | [P10-TASK-006](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-006) | 부상·질병·치료·재활 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021, P9-TASK-026 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-002, P10-BT-002, P10-FT-002, P10-CT-002, P10-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 10 | [P10-TASK-007](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-007) | 부상·질병·치료·재활 — 핵심 규칙 | P10-TASK-006 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-002, P10-BT-002, P10-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 10 | [P10-TASK-008](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-008) | 부상·질병·치료·재활 — 저장·연계 | P10-TASK-006 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-CT-002, P10-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 10 | [P10-TASK-009](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-009) | 부상·질병·치료·재활 — UI·호출 경로 | P10-TASK-006 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-CT-002, P10-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 10 | [P10-TASK-010](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-010) | 부상·질병·치료·재활 — Test·리뷰 | P10-TASK-007, P10-TASK-008, P10-TASK-009 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-002, P10-BT-002, P10-FT-002, P10-CT-002, P10-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 10 | [P10-TASK-011](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-011) | 주거·숙식·유지비·시설 확장 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021, P9-TASK-026 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-003, P10-BT-003, P10-FT-003, P10-CT-003, P10-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 10 | [P10-TASK-012](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-012) | 주거·숙식·유지비·시설 확장 — 핵심 규칙 | P10-TASK-011 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-003, P10-BT-003, P10-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 10 | [P10-TASK-013](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-013) | 주거·숙식·유지비·시설 확장 — 저장·연계 | P10-TASK-011 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-CT-003, P10-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 10 | [P10-TASK-014](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-014) | 주거·숙식·유지비·시설 확장 — UI·호출 경로 | P10-TASK-011 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-CT-003, P10-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 10 | [P10-TASK-015](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-015) | 주거·숙식·유지비·시설 확장 — Test·리뷰 | P10-TASK-012, P10-TASK-013, P10-TASK-014 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-003, P10-BT-003, P10-FT-003, P10-CT-003, P10-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 10 | [P10-TASK-016](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-016) | 귀환 정비·생활 프리셋·도시 행사 — 계약·Fixture | P2-TASK-026, P3-TASK-031, P4-TASK-026, P5-TASK-021, P9-TASK-026 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-004, P10-BT-004, P10-FT-004, P10-CT-004, P10-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 10 | [P10-TASK-017](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-017) | 귀환 정비·생활 프리셋·도시 행사 — 핵심 규칙 | P10-TASK-016 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-004, P10-BT-004, P10-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 10 | [P10-TASK-018](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-018) | 귀환 정비·생활 프리셋·도시 행사 — 저장·연계 | P10-TASK-016 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-CT-004, P10-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 10 | [P10-TASK-019](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-019) | 귀환 정비·생활 프리셋·도시 행사 — UI·호출 경로 | P10-TASK-016 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-CT-004, P10-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 10 | [P10-TASK-020](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-020) | 귀환 정비·생활 프리셋·도시 행사 — Test·리뷰 | P10-TASK-017, P10-TASK-018, P10-TASK-019 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-004, P10-BT-004, P10-FT-004, P10-CT-004, P10-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 10 | [P10-TASK-021](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-task-021) | Phase 10 통합 검증·인계 Gate | P10-TASK-005, P10-TASK-010, P10-TASK-015, P10-TASK-020 | 필수 | :core:simulation/city,health / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P10-UT-001, P10-BT-001, P10-FT-001, P10-CT-001, P10-IT-001, P10-UT-002, P10-BT-002, P10-FT-002, P10-CT-002, P10-IT-002, P10-UT-003, P10-BT-003, P10-FT-003, P10-CT-003, P10-IT-003, P10-UT-004, P10-BT-004, P10-FT-004, P10-CT-004, P10-IT-004, P10-RT-001, P10-CN-001, P10-REC-001, P10-PT-001, P10-OP-001, P10-ET-001, P10-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 11 | [P11-TASK-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-001) | 보조 의뢰·생성·수락·정산 — 계약·Fixture | P2-TASK-026, P4-TASK-026, P5-TASK-021, P9-TASK-026, P10-TASK-021 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-001, P11-BT-001, P11-FT-001, P11-CT-001, P11-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 11 | [P11-TASK-002](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-002) | 보조 의뢰·생성·수락·정산 — 핵심 규칙 | P11-TASK-001 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-001, P11-BT-001, P11-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 11 | [P11-TASK-003](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-003) | 보조 의뢰·생성·수락·정산 — 저장·연계 | P11-TASK-001 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-CT-001, P11-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 11 | [P11-TASK-004](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-004) | 보조 의뢰·생성·수락·정산 — UI·호출 경로 | P11-TASK-001 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-CT-001, P11-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 11 | [P11-TASK-005](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-005) | 보조 의뢰·생성·수락·정산 — Test·리뷰 | P11-TASK-002, P11-TASK-003, P11-TASK-004 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-001, P11-BT-001, P11-FT-001, P11-CT-001, P11-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 11 | [P11-TASK-006](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-006) | 모집·협상·단기/상시 고용 — 계약·Fixture | P2-TASK-026, P4-TASK-026, P5-TASK-021, P9-TASK-026, P10-TASK-021 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-002, P11-BT-002, P11-FT-002, P11-CT-002, P11-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 11 | [P11-TASK-007](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-007) | 모집·협상·단기/상시 고용 — 핵심 규칙 | P11-TASK-006 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-002, P11-BT-002, P11-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 11 | [P11-TASK-008](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-008) | 모집·협상·단기/상시 고용 — 저장·연계 | P11-TASK-006 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-CT-002, P11-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 11 | [P11-TASK-009](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-009) | 모집·협상·단기/상시 고용 — UI·호출 경로 | P11-TASK-006 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-CT-002, P11-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 11 | [P11-TASK-010](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-010) | 모집·협상·단기/상시 고용 — Test·리뷰 | P11-TASK-007, P11-TASK-008, P11-TASK-009 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-002, P11-BT-002, P11-FT-002, P11-CT-002, P11-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 11 | [P11-TASK-011](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-011) | 계약 종료·퇴출·위약금·신뢰 — 계약·Fixture | P2-TASK-026, P4-TASK-026, P5-TASK-021, P9-TASK-026, P10-TASK-021 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-003, P11-BT-003, P11-FT-003, P11-CT-003, P11-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 11 | [P11-TASK-012](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-012) | 계약 종료·퇴출·위약금·신뢰 — 핵심 규칙 | P11-TASK-011 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-003, P11-BT-003, P11-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 11 | [P11-TASK-013](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-013) | 계약 종료·퇴출·위약금·신뢰 — 저장·연계 | P11-TASK-011 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-CT-003, P11-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 11 | [P11-TASK-014](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-014) | 계약 종료·퇴출·위약금·신뢰 — UI·호출 경로 | P11-TASK-011 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-CT-003, P11-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 11 | [P11-TASK-015](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-015) | 계약 종료·퇴출·위약금·신뢰 — Test·리뷰 | P11-TASK-012, P11-TASK-013, P11-TASK-014 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-003, P11-BT-003, P11-FT-003, P11-CT-003, P11-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 11 | [P11-TASK-016](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-016) | 선택형 대화·Topic·기억·말투 — 계약·Fixture | P2-TASK-026, P4-TASK-026, P5-TASK-021, P9-TASK-026, P10-TASK-021 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-004, P11-BT-004, P11-FT-004, P11-CT-004, P11-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 11 | [P11-TASK-017](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-017) | 선택형 대화·Topic·기억·말투 — 핵심 규칙 | P11-TASK-016 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-004, P11-BT-004, P11-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 11 | [P11-TASK-018](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-018) | 선택형 대화·Topic·기억·말투 — 저장·연계 | P11-TASK-016 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-CT-004, P11-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 11 | [P11-TASK-019](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-019) | 선택형 대화·Topic·기억·말투 — UI·호출 경로 | P11-TASK-016 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-CT-004, P11-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 11 | [P11-TASK-020](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-020) | 선택형 대화·Topic·기억·말투 — Test·리뷰 | P11-TASK-017, P11-TASK-018, P11-TASK-019 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-004, P11-BT-004, P11-FT-004, P11-CT-004, P11-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 11 | [P11-TASK-021](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-task-021) | Phase 11 통합 검증·인계 Gate | P11-TASK-005, P11-TASK-010, P11-TASK-015, P11-TASK-020 | 필수 | :core:simulation/contract,dialogue / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P11-UT-001, P11-BT-001, P11-FT-001, P11-CT-001, P11-IT-001, P11-UT-002, P11-BT-002, P11-FT-002, P11-CT-002, P11-IT-002, P11-UT-003, P11-BT-003, P11-FT-003, P11-CT-003, P11-IT-003, P11-UT-004, P11-BT-004, P11-FT-004, P11-CT-004, P11-IT-004, P11-RT-001, P11-CN-001, P11-REC-001, P11-PT-001, P11-OP-001, P11-ET-001, P11-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 12 | [P12-TASK-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-001) | 강화 확률·시도 원장·비파괴 — 계약·Fixture | P3-TASK-031, P5-TASK-021, P10-TASK-021 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-001, P12-BT-001, P12-FT-001, P12-CT-001, P12-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 12 | [P12-TASK-002](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-002) | 강화 확률·시도 원장·비파괴 — 핵심 규칙 | P12-TASK-001 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-001, P12-BT-001, P12-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 12 | [P12-TASK-003](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-003) | 강화 확률·시도 원장·비파괴 — 저장·연계 | P12-TASK-001 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-CT-001, P12-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 12 | [P12-TASK-004](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-004) | 강화 확률·시도 원장·비파괴 — UI·호출 경로 | P12-TASK-001 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-CT-001, P12-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 12 | [P12-TASK-005](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-005) | 강화 확률·시도 원장·비파괴 — Test·리뷰 | P12-TASK-002, P12-TASK-003, P12-TASK-004 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-001, P12-BT-001, P12-FT-001, P12-CT-001, P12-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 12 | [P12-TASK-006](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-006) | 강화 성장·안정도·각인 슬롯 — 계약·Fixture | P3-TASK-031, P5-TASK-021, P10-TASK-021 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-002, P12-BT-002, P12-FT-002, P12-CT-002, P12-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 12 | [P12-TASK-007](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-007) | 강화 성장·안정도·각인 슬롯 — 핵심 규칙 | P12-TASK-006 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-002, P12-BT-002, P12-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 12 | [P12-TASK-008](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-008) | 강화 성장·안정도·각인 슬롯 — 저장·연계 | P12-TASK-006 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-CT-002, P12-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 12 | [P12-TASK-009](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-009) | 강화 성장·안정도·각인 슬롯 — UI·호출 경로 | P12-TASK-006 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-CT-002, P12-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 12 | [P12-TASK-010](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-010) | 강화 성장·안정도·각인 슬롯 — Test·리뷰 | P12-TASK-007, P12-TASK-008, P12-TASK-009 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-002, P12-BT-002, P12-FT-002, P12-CT-002, P12-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 12 | [P12-TASK-011](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-011) | 계승·정련·재각성·유물복원 — 계약·Fixture | P3-TASK-031, P5-TASK-021, P10-TASK-021 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-003, P12-BT-003, P12-FT-003, P12-CT-003, P12-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 12 | [P12-TASK-012](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-012) | 계승·정련·재각성·유물복원 — 핵심 규칙 | P12-TASK-011 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-003, P12-BT-003, P12-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 12 | [P12-TASK-013](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-013) | 계승·정련·재각성·유물복원 — 저장·연계 | P12-TASK-011 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-CT-003, P12-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 12 | [P12-TASK-014](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-014) | 계승·정련·재각성·유물복원 — UI·호출 경로 | P12-TASK-011 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-CT-003, P12-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 12 | [P12-TASK-015](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-015) | 계승·정련·재각성·유물복원 — Test·리뷰 | P12-TASK-012, P12-TASK-013, P12-TASK-014 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-003, P12-BT-003, P12-FT-003, P12-CT-003, P12-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 12 | [P12-TASK-016](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-016) | 제작·연금·연구·분해 — 계약·Fixture | P3-TASK-031, P5-TASK-021, P10-TASK-021 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-004, P12-BT-004, P12-FT-004, P12-CT-004, P12-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 12 | [P12-TASK-017](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-017) | 제작·연금·연구·분해 — 핵심 규칙 | P12-TASK-016 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-004, P12-BT-004, P12-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 12 | [P12-TASK-018](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-018) | 제작·연금·연구·분해 — 저장·연계 | P12-TASK-016 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-CT-004, P12-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 12 | [P12-TASK-019](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-019) | 제작·연금·연구·분해 — UI·호출 경로 | P12-TASK-016 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-CT-004, P12-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 12 | [P12-TASK-020](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-020) | 제작·연금·연구·분해 — Test·리뷰 | P12-TASK-017, P12-TASK-018, P12-TASK-019 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-004, P12-BT-004, P12-FT-004, P12-CT-004, P12-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 12 | [P12-TASK-021](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-task-021) | Phase 12 통합 검증·인계 Gate | P12-TASK-005, P12-TASK-010, P12-TASK-015, P12-TASK-020 | 필수 | :core:simulation/enhancement,craft / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P12-UT-001, P12-BT-001, P12-FT-001, P12-CT-001, P12-IT-001, P12-UT-002, P12-BT-002, P12-FT-002, P12-CT-002, P12-IT-002, P12-UT-003, P12-BT-003, P12-FT-003, P12-CT-003, P12-IT-003, P12-UT-004, P12-BT-004, P12-FT-004, P12-CT-004, P12-IT-004, P12-RT-001, P12-CN-001, P12-REC-001, P12-PT-001, P12-OP-001, P12-ET-001, P12-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 13 | [P13-TASK-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-001) | 파티 구성·출전·교대·헌장 — 계약·Fixture | P6-TASK-031, P11-TASK-021, P12-TASK-021 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-001, P13-BT-001, P13-FT-001, P13-CT-001, P13-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 13 | [P13-TASK-002](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-002) | 파티 구성·출전·교대·헌장 — 핵심 규칙 | P13-TASK-001 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-001, P13-BT-001, P13-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 13 | [P13-TASK-003](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-003) | 파티 구성·출전·교대·헌장 — 저장·연계 | P13-TASK-001 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-CT-001, P13-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 13 | [P13-TASK-004](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-004) | 파티 구성·출전·교대·헌장 — UI·호출 경로 | P13-TASK-001 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-CT-001, P13-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 13 | [P13-TASK-005](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-005) | 파티 구성·출전·교대·헌장 — Test·리뷰 | P13-TASK-002, P13-TASK-003, P13-TASK-004 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-001, P13-BT-001, P13-FT-001, P13-CT-001, P13-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 13 | [P13-TASK-006](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-006) | 분배·공동자금·투표·공정성 — 계약·Fixture | P6-TASK-031, P11-TASK-021, P12-TASK-021 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-002, P13-BT-002, P13-FT-002, P13-CT-002, P13-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 13 | [P13-TASK-007](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-007) | 분배·공동자금·투표·공정성 — 핵심 규칙 | P13-TASK-006 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-002, P13-BT-002, P13-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 13 | [P13-TASK-008](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-008) | 분배·공동자금·투표·공정성 — 저장·연계 | P13-TASK-006 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-CT-002, P13-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 13 | [P13-TASK-009](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-009) | 분배·공동자금·투표·공정성 — UI·호출 경로 | P13-TASK-006 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-CT-002, P13-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 13 | [P13-TASK-010](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-010) | 분배·공동자금·투표·공정성 — Test·리뷰 | P13-TASK-007, P13-TASK-008, P13-TASK-009 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-002, P13-BT-002, P13-FT-002, P13-CT-002, P13-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 13 | [P13-TASK-011](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-011) | 만족·갈등·리더교체·분열·승계 — 계약·Fixture | P6-TASK-031, P11-TASK-021, P12-TASK-021 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-003, P13-BT-003, P13-FT-003, P13-CT-003, P13-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 13 | [P13-TASK-012](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-012) | 만족·갈등·리더교체·분열·승계 — 핵심 규칙 | P13-TASK-011 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-003, P13-BT-003, P13-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 13 | [P13-TASK-013](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-013) | 만족·갈등·리더교체·분열·승계 — 저장·연계 | P13-TASK-011 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-CT-003, P13-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 13 | [P13-TASK-014](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-014) | 만족·갈등·리더교체·분열·승계 — UI·호출 경로 | P13-TASK-011 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-CT-003, P13-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 13 | [P13-TASK-015](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-015) | 만족·갈등·리더교체·분열·승계 — Test·리뷰 | P13-TASK-012, P13-TASK-013, P13-TASK-014 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-003, P13-BT-003, P13-FT-003, P13-CT-003, P13-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 13 | [P13-TASK-016](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-016) | 파티 공식 랭킹·연속1 위 — 계약·Fixture | P6-TASK-031, P11-TASK-021, P12-TASK-021 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-004, P13-BT-004, P13-FT-004, P13-CT-004, P13-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 13 | [P13-TASK-017](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-017) | 파티 공식 랭킹·연속1 위 — 핵심 규칙 | P13-TASK-016 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-004, P13-BT-004, P13-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 13 | [P13-TASK-018](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-018) | 파티 공식 랭킹·연속1 위 — 저장·연계 | P13-TASK-016 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-CT-004, P13-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 13 | [P13-TASK-019](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-019) | 파티 공식 랭킹·연속1 위 — UI·호출 경로 | P13-TASK-016 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-CT-004, P13-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 13 | [P13-TASK-020](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-020) | 파티 공식 랭킹·연속1 위 — Test·리뷰 | P13-TASK-017, P13-TASK-018, P13-TASK-019 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-004, P13-BT-004, P13-FT-004, P13-CT-004, P13-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 13 | [P13-TASK-021](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-task-021) | Phase 13 통합 검증·인계 Gate | P13-TASK-005, P13-TASK-010, P13-TASK-015, P13-TASK-020 | 필수 | :core:simulation/party / :feature:party | 선행계약후;공통 schema 충돌은직렬 | P13-UT-001, P13-BT-001, P13-FT-001, P13-CT-001, P13-IT-001, P13-UT-002, P13-BT-002, P13-FT-002, P13-CT-002, P13-IT-002, P13-UT-003, P13-BT-003, P13-FT-003, P13-CT-003, P13-IT-003, P13-UT-004, P13-BT-004, P13-FT-004, P13-CT-004, P13-IT-004, P13-RT-001, P13-CN-001, P13-REC-001, P13-PT-001, P13-OP-001, P13-ET-001, P13-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 14 | [P14-TASK-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-001) | 도시 시장지수·재고·수요공급 — 계약·Fixture | P10-TASK-021, P12-TASK-021, P13-TASK-021 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-001, P14-BT-001, P14-FT-001, P14-CT-001, P14-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 14 | [P14-TASK-002](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-002) | 도시 시장지수·재고·수요공급 — 핵심 규칙 | P14-TASK-001 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-001, P14-BT-001, P14-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 14 | [P14-TASK-003](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-003) | 도시 시장지수·재고·수요공급 — 저장·연계 | P14-TASK-001 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-CT-001, P14-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 14 | [P14-TASK-004](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-004) | 도시 시장지수·재고·수요공급 — UI·호출 경로 | P14-TASK-001 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-CT-001, P14-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 14 | [P14-TASK-005](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-005) | 도시 시장지수·재고·수요공급 — Test·리뷰 | P14-TASK-002, P14-TASK-003, P14-TASK-004 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-001, P14-BT-001, P14-FT-001, P14-CT-001, P14-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 14 | [P14-TASK-006](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-006) | 구매·판매·흥정·경매 — 계약·Fixture | P10-TASK-021, P12-TASK-021, P13-TASK-021 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-002, P14-BT-002, P14-FT-002, P14-CT-002, P14-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 14 | [P14-TASK-007](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-007) | 구매·판매·흥정·경매 — 핵심 규칙 | P14-TASK-006 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-002, P14-BT-002, P14-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 14 | [P14-TASK-008](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-008) | 구매·판매·흥정·경매 — 저장·연계 | P14-TASK-006 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-CT-002, P14-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 14 | [P14-TASK-009](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-009) | 구매·판매·흥정·경매 — UI·호출 경로 | P14-TASK-006 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-CT-002, P14-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 14 | [P14-TASK-010](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-010) | 구매·판매·흥정·경매 — Test·리뷰 | P14-TASK-007, P14-TASK-008, P14-TASK-009 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-002, P14-BT-002, P14-FT-002, P14-CT-002, P14-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 14 | [P14-TASK-011](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-011) | 장비 대여·회수·손상·보험 — 계약·Fixture | P10-TASK-021, P12-TASK-021, P13-TASK-021 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-003, P14-BT-003, P14-FT-003, P14-CT-003, P14-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 14 | [P14-TASK-012](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-012) | 장비 대여·회수·손상·보험 — 핵심 규칙 | P14-TASK-011 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-003, P14-BT-003, P14-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 14 | [P14-TASK-013](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-013) | 장비 대여·회수·손상·보험 — 저장·연계 | P14-TASK-011 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-CT-003, P14-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 14 | [P14-TASK-014](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-014) | 장비 대여·회수·손상·보험 — UI·호출 경로 | P14-TASK-011 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-CT-003, P14-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 14 | [P14-TASK-015](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-015) | 장비 대여·회수·손상·보험 — Test·리뷰 | P14-TASK-012, P14-TASK-013, P14-TASK-014 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-003, P14-BT-003, P14-FT-003, P14-CT-003, P14-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 14 | [P14-TASK-016](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-016) | 창고·운송·원정보급·분실복구 — 계약·Fixture | P10-TASK-021, P12-TASK-021, P13-TASK-021 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-004, P14-BT-004, P14-FT-004, P14-CT-004, P14-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 14 | [P14-TASK-017](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-017) | 창고·운송·원정보급·분실복구 — 핵심 규칙 | P14-TASK-016 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-004, P14-BT-004, P14-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 14 | [P14-TASK-018](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-018) | 창고·운송·원정보급·분실복구 — 저장·연계 | P14-TASK-016 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-CT-004, P14-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 14 | [P14-TASK-019](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-019) | 창고·운송·원정보급·분실복구 — UI·호출 경로 | P14-TASK-016 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-CT-004, P14-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 14 | [P14-TASK-020](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-020) | 창고·운송·원정보급·분실복구 — Test·리뷰 | P14-TASK-017, P14-TASK-018, P14-TASK-019 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-004, P14-BT-004, P14-FT-004, P14-CT-004, P14-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 14 | [P14-TASK-021](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-task-021) | Phase 14 통합 검증·인계 Gate | P14-TASK-005, P14-TASK-010, P14-TASK-015, P14-TASK-020 | 필수 | :core:simulation/economy,logistics / :feature:city | 선행계약후;공통 schema 충돌은직렬 | P14-UT-001, P14-BT-001, P14-FT-001, P14-CT-001, P14-IT-001, P14-UT-002, P14-BT-002, P14-FT-002, P14-CT-002, P14-IT-002, P14-UT-003, P14-BT-003, P14-FT-003, P14-CT-003, P14-IT-003, P14-UT-004, P14-BT-004, P14-FT-004, P14-CT-004, P14-IT-004, P14-RT-001, P14-CN-001, P14-REC-001, P14-PT-001, P14-OP-001, P14-ET-001, P14-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 15 | [P15-TASK-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-001) | 지식·관측·소문·정보 공개 — 계약·Fixture | P4-TASK-026, P11-TASK-021, P13-TASK-021, P14-TASK-021 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-001, P15-BT-001, P15-FT-001, P15-CT-001, P15-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 15 | [P15-TASK-002](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-002) | 지식·관측·소문·정보 공개 — 핵심 규칙 | P15-TASK-001 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-001, P15-BT-001, P15-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 15 | [P15-TASK-003](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-003) | 지식·관측·소문·정보 공개 — 저장·연계 | P15-TASK-001 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-CT-001, P15-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 15 | [P15-TASK-004](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-004) | 지식·관측·소문·정보 공개 — UI·호출 경로 | P15-TASK-001 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-CT-001, P15-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 15 | [P15-TASK-005](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-005) | 지식·관측·소문·정보 공개 — Test·리뷰 | P15-TASK-002, P15-TASK-003, P15-TASK-004 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-001, P15-BT-001, P15-FT-001, P15-CT-001, P15-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 15 | [P15-TASK-006](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-006) | 평판·법률·계약 신뢰·지역기여 — 계약·Fixture | P4-TASK-026, P11-TASK-021, P13-TASK-021, P14-TASK-021 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-002, P15-BT-002, P15-FT-002, P15-CT-002, P15-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 15 | [P15-TASK-007](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-007) | 평판·법률·계약 신뢰·지역기여 — 핵심 규칙 | P15-TASK-006 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-002, P15-BT-002, P15-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 15 | [P15-TASK-008](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-008) | 평판·법률·계약 신뢰·지역기여 — 저장·연계 | P15-TASK-006 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-CT-002, P15-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 15 | [P15-TASK-009](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-009) | 평판·법률·계약 신뢰·지역기여 — UI·호출 경로 | P15-TASK-006 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-CT-002, P15-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 15 | [P15-TASK-010](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-010) | 평판·법률·계약 신뢰·지역기여 — Test·리뷰 | P15-TASK-007, P15-TASK-008, P15-TASK-009 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-002, P15-BT-002, P15-FT-002, P15-CT-002, P15-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 15 | [P15-TASK-011](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-011) | 다축 관계·기억·호흡·연애 — 계약·Fixture | P4-TASK-026, P11-TASK-021, P13-TASK-021, P14-TASK-021 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-003, P15-BT-003, P15-FT-003, P15-CT-003, P15-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 15 | [P15-TASK-012](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-012) | 다축 관계·기억·호흡·연애 — 핵심 규칙 | P15-TASK-011 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-003, P15-BT-003, P15-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 15 | [P15-TASK-013](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-013) | 다축 관계·기억·호흡·연애 — 저장·연계 | P15-TASK-011 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-CT-003, P15-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 15 | [P15-TASK-014](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-014) | 다축 관계·기억·호흡·연애 — UI·호출 경로 | P15-TASK-011 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-CT-003, P15-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 15 | [P15-TASK-015](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-015) | 다축 관계·기억·호흡·연애 — Test·리뷰 | P15-TASK-012, P15-TASK-013, P15-TASK-014 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-003, P15-BT-003, P15-FT-003, P15-CT-003, P15-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 15 | [P15-TASK-016](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-016) | 성격·특성·매력·개인 목표 — 계약·Fixture | P4-TASK-026, P11-TASK-021, P13-TASK-021, P14-TASK-021 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-004, P15-BT-004, P15-FT-004, P15-CT-004, P15-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 15 | [P15-TASK-017](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-017) | 성격·특성·매력·개인 목표 — 핵심 규칙 | P15-TASK-016 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-004, P15-BT-004, P15-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 15 | [P15-TASK-018](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-018) | 성격·특성·매력·개인 목표 — 저장·연계 | P15-TASK-016 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-CT-004, P15-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 15 | [P15-TASK-019](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-019) | 성격·특성·매력·개인 목표 — UI·호출 경로 | P15-TASK-016 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-CT-004, P15-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 15 | [P15-TASK-020](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-020) | 성격·특성·매력·개인 목표 — Test·리뷰 | P15-TASK-017, P15-TASK-018, P15-TASK-019 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-004, P15-BT-004, P15-FT-004, P15-CT-004, P15-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 15 | [P15-TASK-021](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-task-021) | Phase 15 통합 검증·인계 Gate | P15-TASK-005, P15-TASK-010, P15-TASK-015, P15-TASK-020 | 필수 | :core:simulation/social,knowledge / :feature:mercenary | 선행계약후;공통 schema 충돌은직렬 | P15-UT-001, P15-BT-001, P15-FT-001, P15-CT-001, P15-IT-001, P15-UT-002, P15-BT-002, P15-FT-002, P15-CT-002, P15-IT-002, P15-UT-003, P15-BT-003, P15-FT-003, P15-CT-003, P15-IT-003, P15-UT-004, P15-BT-004, P15-FT-004, P15-CT-004, P15-IT-004, P15-RT-001, P15-CN-001, P15-REC-001, P15-PT-001, P15-OP-001, P15-ET-001, P15-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 16 | [P16-TASK-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-001) | 길드 가입·직위·승계·권한 — 계약·Fixture | P13-TASK-021, P14-TASK-021, P15-TASK-021 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-001, P16-BT-001, P16-FT-001, P16-CT-001, P16-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 16 | [P16-TASK-002](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-002) | 길드 가입·직위·승계·권한 — 핵심 규칙 | P16-TASK-001 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-001, P16-BT-001, P16-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 16 | [P16-TASK-003](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-003) | 길드 가입·직위·승계·권한 — 저장·연계 | P16-TASK-001 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-CT-001, P16-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 16 | [P16-TASK-004](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-004) | 길드 가입·직위·승계·권한 — UI·호출 경로 | P16-TASK-001 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-CT-001, P16-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 16 | [P16-TASK-005](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-005) | 길드 가입·직위·승계·권한 — Test·리뷰 | P16-TASK-002, P16-TASK-003, P16-TASK-004 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-001, P16-BT-001, P16-FT-001, P16-CT-001, P16-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 16 | [P16-TASK-006](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-006) | 길드 재정·시설·인재·간부 운영 — 계약·Fixture | P13-TASK-021, P14-TASK-021, P15-TASK-021 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-002, P16-BT-002, P16-FT-002, P16-CT-002, P16-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 16 | [P16-TASK-007](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-007) | 길드 재정·시설·인재·간부 운영 — 핵심 규칙 | P16-TASK-006 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-002, P16-BT-002, P16-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 16 | [P16-TASK-008](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-008) | 길드 재정·시설·인재·간부 운영 — 저장·연계 | P16-TASK-006 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-CT-002, P16-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 16 | [P16-TASK-009](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-009) | 길드 재정·시설·인재·간부 운영 — UI·호출 경로 | P16-TASK-006 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-CT-002, P16-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 16 | [P16-TASK-010](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-010) | 길드 재정·시설·인재·간부 운영 — Test·리뷰 | P16-TASK-007, P16-TASK-008, P16-TASK-009 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-002, P16-BT-002, P16-FT-002, P16-CT-002, P16-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 16 | [P16-TASK-011](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-011) | 파벌·정책·정당성·길드 공략대 — 계약·Fixture | P13-TASK-021, P14-TASK-021, P15-TASK-021 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-003, P16-BT-003, P16-FT-003, P16-CT-003, P16-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 16 | [P16-TASK-012](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-012) | 파벌·정책·정당성·길드 공략대 — 핵심 규칙 | P16-TASK-011 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-003, P16-BT-003, P16-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 16 | [P16-TASK-013](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-013) | 파벌·정책·정당성·길드 공략대 — 저장·연계 | P16-TASK-011 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-CT-003, P16-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 16 | [P16-TASK-014](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-014) | 파벌·정책·정당성·길드 공략대 — UI·호출 경로 | P16-TASK-011 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-CT-003, P16-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 16 | [P16-TASK-015](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-015) | 파벌·정책·정당성·길드 공략대 — Test·리뷰 | P16-TASK-012, P16-TASK-013, P16-TASK-014 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-003, P16-BT-003, P16-FT-003, P16-CT-003, P16-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 16 | [P16-TASK-016](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-016) | 길드 공식10000 점·일마감·기여 — 계약·Fixture | P13-TASK-021, P14-TASK-021, P15-TASK-021 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-004, P16-BT-004, P16-FT-004, P16-CT-004, P16-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 16 | [P16-TASK-017](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-017) | 길드 공식10000 점·일마감·기여 — 핵심 규칙 | P16-TASK-016 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-004, P16-BT-004, P16-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 16 | [P16-TASK-018](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-018) | 길드 공식10000 점·일마감·기여 — 저장·연계 | P16-TASK-016 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-CT-004, P16-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 16 | [P16-TASK-019](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-019) | 길드 공식10000 점·일마감·기여 — UI·호출 경로 | P16-TASK-016 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-CT-004, P16-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 16 | [P16-TASK-020](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-020) | 길드 공식10000 점·일마감·기여 — Test·리뷰 | P16-TASK-017, P16-TASK-018, P16-TASK-019 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-004, P16-BT-004, P16-FT-004, P16-CT-004, P16-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 16 | [P16-TASK-021](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-task-021) | Phase 16 통합 검증·인계 Gate | P16-TASK-005, P16-TASK-010, P16-TASK-015, P16-TASK-020 | 필수 | :core:simulation/guild,ranking / :feature:guild | 선행계약후;공통 schema 충돌은직렬 | P16-UT-001, P16-BT-001, P16-FT-001, P16-CT-001, P16-IT-001, P16-UT-002, P16-BT-002, P16-FT-002, P16-CT-002, P16-IT-002, P16-UT-003, P16-BT-003, P16-FT-003, P16-CT-003, P16-IT-003, P16-UT-004, P16-BT-004, P16-FT-004, P16-CT-004, P16-IT-004, P16-RT-001, P16-CN-001, P16-REC-001, P16-PT-001, P16-OP-001, P16-ET-001, P16-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 17 | [P17-TASK-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-001) | NPC 목표·행동 Utility·경제 의사결정 — 계약·Fixture | P4-TASK-026, P11-TASK-021, P12-TASK-021, P13-TASK-021, P14-TASK-021, P15-TASK-021, P16-TASK-021 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-001, P17-BT-001, P17-FT-001, P17-CT-001, P17-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 17 | [P17-TASK-002](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-002) | NPC 목표·행동 Utility·경제 의사결정 — 핵심 규칙 | P17-TASK-001 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-001, P17-BT-001, P17-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 17 | [P17-TASK-003](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-003) | NPC 목표·행동 Utility·경제 의사결정 — 저장·연계 | P17-TASK-001 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-CT-001, P17-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 17 | [P17-TASK-004](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-004) | NPC 목표·행동 Utility·경제 의사결정 — UI·호출 경로 | P17-TASK-001 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-CT-001, P17-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 17 | [P17-TASK-005](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-005) | NPC 목표·행동 Utility·경제 의사결정 — Test·리뷰 | P17-TASK-002, P17-TASK-003, P17-TASK-004 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-001, P17-BT-001, P17-FT-001, P17-CT-001, P17-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 17 | [P17-TASK-006](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-006) | 상세/축약 시뮬레이션·승격·강등 — 계약·Fixture | P4-TASK-026, P11-TASK-021, P12-TASK-021, P13-TASK-021, P14-TASK-021, P15-TASK-021, P16-TASK-021 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-002, P17-BT-002, P17-FT-002, P17-CT-002, P17-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 17 | [P17-TASK-007](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-007) | 상세/축약 시뮬레이션·승격·강등 — 핵심 규칙 | P17-TASK-006 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-002, P17-BT-002, P17-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 17 | [P17-TASK-008](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-008) | 상세/축약 시뮬레이션·승격·강등 — 저장·연계 | P17-TASK-006 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-CT-002, P17-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 17 | [P17-TASK-009](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-009) | 상세/축약 시뮬레이션·승격·강등 — UI·호출 경로 | P17-TASK-006 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-CT-002, P17-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 17 | [P17-TASK-010](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-010) | 상세/축약 시뮬레이션·승격·강등 — Test·리뷰 | P17-TASK-007, P17-TASK-008, P17-TASK-009 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-002, P17-BT-002, P17-FT-002, P17-CT-002, P17-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 17 | [P17-TASK-011](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-011) | 용병 유입·은퇴·복귀·직업전환 — 계약·Fixture | P4-TASK-026, P11-TASK-021, P12-TASK-021, P13-TASK-021, P14-TASK-021, P15-TASK-021, P16-TASK-021 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-003, P17-BT-003, P17-FT-003, P17-CT-003, P17-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 17 | [P17-TASK-012](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-012) | 용병 유입·은퇴·복귀·직업전환 — 핵심 규칙 | P17-TASK-011 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-003, P17-BT-003, P17-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 17 | [P17-TASK-013](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-013) | 용병 유입·은퇴·복귀·직업전환 — 저장·연계 | P17-TASK-011 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-CT-003, P17-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 17 | [P17-TASK-014](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-014) | 용병 유입·은퇴·복귀·직업전환 — UI·호출 경로 | P17-TASK-011 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-CT-003, P17-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 17 | [P17-TASK-015](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-015) | 용병 유입·은퇴·복귀·직업전환 — Test·리뷰 | P17-TASK-012, P17-TASK-013, P17-TASK-014 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-003, P17-BT-003, P17-FT-003, P17-CT-003, P17-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 17 | [P17-TASK-016](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-016) | 장기 정체성·압축·사회 순환 검증 — 계약·Fixture | P4-TASK-026, P11-TASK-021, P12-TASK-021, P13-TASK-021, P14-TASK-021, P15-TASK-021, P16-TASK-021 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-004, P17-BT-004, P17-FT-004, P17-CT-004, P17-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 17 | [P17-TASK-017](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-017) | 장기 정체성·압축·사회 순환 검증 — 핵심 규칙 | P17-TASK-016 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-004, P17-BT-004, P17-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 17 | [P17-TASK-018](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-018) | 장기 정체성·압축·사회 순환 검증 — 저장·연계 | P17-TASK-016 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-CT-004, P17-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 17 | [P17-TASK-019](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-019) | 장기 정체성·압축·사회 순환 검증 — UI·호출 경로 | P17-TASK-016 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-CT-004, P17-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 17 | [P17-TASK-020](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-020) | 장기 정체성·압축·사회 순환 검증 — Test·리뷰 | P17-TASK-017, P17-TASK-018, P17-TASK-019 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-004, P17-BT-004, P17-FT-004, P17-CT-004, P17-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 17 | [P17-TASK-021](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-task-021) | Phase 17 통합 검증·인계 Gate | P17-TASK-005, P17-TASK-010, P17-TASK-015, P17-TASK-020 | 필수 | :core:simulation/npc,population | 선행계약후;공통 schema 충돌은직렬 | P17-UT-001, P17-BT-001, P17-FT-001, P17-CT-001, P17-IT-001, P17-UT-002, P17-BT-002, P17-FT-002, P17-CT-002, P17-IT-002, P17-UT-003, P17-BT-003, P17-FT-003, P17-CT-003, P17-IT-003, P17-UT-004, P17-BT-004, P17-FT-004, P17-CT-004, P17-IT-004, P17-RT-001, P17-CN-001, P17-REC-001, P17-PT-001, P17-OP-001, P17-ET-001, P17-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 18 | [P18-TASK-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-001) | 가족·출생·입양·성장·교육 — 계약·Fixture | P3-TASK-031, P15-TASK-021, P17-TASK-021 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-001, P18-BT-001, P18-FT-001, P18-CT-001, P18-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 18 | [P18-TASK-002](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-002) | 가족·출생·입양·성장·교육 — 핵심 규칙 | P18-TASK-001 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-001, P18-BT-001, P18-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 18 | [P18-TASK-003](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-003) | 가족·출생·입양·성장·교육 — 저장·연계 | P18-TASK-001 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-CT-001, P18-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 18 | [P18-TASK-004](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-004) | 가족·출생·입양·성장·교육 — UI·호출 경로 | P18-TASK-001 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-CT-001, P18-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 18 | [P18-TASK-005](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-005) | 가족·출생·입양·성장·교육 — Test·리뷰 | P18-TASK-002, P18-TASK-003, P18-TASK-004 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-001, P18-BT-001, P18-FT-001, P18-CT-001, P18-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 18 | [P18-TASK-006](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-006) | 후계자 후보·의사·지정·부재 안전장치 — 계약·Fixture | P3-TASK-031, P15-TASK-021, P17-TASK-021 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-002, P18-BT-002, P18-FT-002, P18-CT-002, P18-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 18 | [P18-TASK-007](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-007) | 후계자 후보·의사·지정·부재 안전장치 — 핵심 규칙 | P18-TASK-006 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-002, P18-BT-002, P18-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 18 | [P18-TASK-008](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-008) | 후계자 후보·의사·지정·부재 안전장치 — 저장·연계 | P18-TASK-006 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-CT-002, P18-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 18 | [P18-TASK-009](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-009) | 후계자 후보·의사·지정·부재 안전장치 — UI·호출 경로 | P18-TASK-006 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-CT-002, P18-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 18 | [P18-TASK-010](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-010) | 후계자 후보·의사·지정·부재 안전장치 — Test·리뷰 | P18-TASK-007, P18-TASK-008, P18-TASK-009 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-002, P18-BT-002, P18-FT-002, P18-CT-002, P18-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 18 | [P18-TASK-011](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-011) | 원자적 세대 교체·자산·증표 유지 — 계약·Fixture | P3-TASK-031, P15-TASK-021, P17-TASK-021 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-003, P18-BT-003, P18-FT-003, P18-CT-003, P18-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 18 | [P18-TASK-012](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-012) | 원자적 세대 교체·자산·증표 유지 — 핵심 규칙 | P18-TASK-011 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-003, P18-BT-003, P18-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 18 | [P18-TASK-013](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-013) | 원자적 세대 교체·자산·증표 유지 — 저장·연계 | P18-TASK-011 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-CT-003, P18-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 18 | [P18-TASK-014](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-014) | 원자적 세대 교체·자산·증표 유지 — UI·호출 경로 | P18-TASK-011 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-CT-003, P18-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 18 | [P18-TASK-015](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-015) | 원자적 세대 교체·자산·증표 유지 — Test·리뷰 | P18-TASK-012, P18-TASK-013, P18-TASK-014 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-003, P18-BT-003, P18-FT-003, P18-CT-003, P18-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 18 | [P18-TASK-016](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-016) | 가문 목표·유물·세대 기여·기록 UI — 계약·Fixture | P3-TASK-031, P15-TASK-021, P17-TASK-021 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-004, P18-BT-004, P18-FT-004, P18-CT-004, P18-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 18 | [P18-TASK-017](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-017) | 가문 목표·유물·세대 기여·기록 UI — 핵심 규칙 | P18-TASK-016 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-004, P18-BT-004, P18-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 18 | [P18-TASK-018](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-018) | 가문 목표·유물·세대 기여·기록 UI — 저장·연계 | P18-TASK-016 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-CT-004, P18-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 18 | [P18-TASK-019](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-019) | 가문 목표·유물·세대 기여·기록 UI — UI·호출 경로 | P18-TASK-016 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-CT-004, P18-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 18 | [P18-TASK-020](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-020) | 가문 목표·유물·세대 기여·기록 UI — Test·리뷰 | P18-TASK-017, P18-TASK-018, P18-TASK-019 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-004, P18-BT-004, P18-FT-004, P18-CT-004, P18-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 18 | [P18-TASK-021](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-task-021) | Phase 18 통합 검증·인계 Gate | P18-TASK-005, P18-TASK-010, P18-TASK-015, P18-TASK-020 | 필수 | :core:simulation/lineage / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P18-UT-001, P18-BT-001, P18-FT-001, P18-CT-001, P18-IT-001, P18-UT-002, P18-BT-002, P18-FT-002, P18-CT-002, P18-IT-002, P18-UT-003, P18-BT-003, P18-FT-003, P18-CT-003, P18-IT-003, P18-UT-004, P18-BT-004, P18-FT-004, P18-CT-004, P18-IT-004, P18-RT-001, P18-CN-001, P18-REC-001, P18-PT-001, P18-OP-001, P18-ET-001, P18-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 19 | [P19-TASK-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-001) | NPC 사건·등장인물·자동 해결 — 계약·Fixture | P9-TASK-026, P15-TASK-021, P17-TASK-021, P18-TASK-021 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-001, P19-BT-001, P19-FT-001, P19-CT-001, P19-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 19 | [P19-TASK-002](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-002) | NPC 사건·등장인물·자동 해결 — 핵심 규칙 | P19-TASK-001 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-001, P19-BT-001, P19-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 19 | [P19-TASK-003](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-003) | NPC 사건·등장인물·자동 해결 — 저장·연계 | P19-TASK-001 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-CT-001, P19-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 19 | [P19-TASK-004](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-004) | NPC 사건·등장인물·자동 해결 — UI·호출 경로 | P19-TASK-001 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-CT-001, P19-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 19 | [P19-TASK-005](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-005) | NPC 사건·등장인물·자동 해결 — Test·리뷰 | P19-TASK-002, P19-TASK-003, P19-TASK-004 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-001, P19-BT-001, P19-FT-001, P19-CT-001, P19-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 19 | [P19-TASK-006](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-006) | 연쇄 사건·조건·쿨다운·대안 경로 — 계약·Fixture | P9-TASK-026, P15-TASK-021, P17-TASK-021, P18-TASK-021 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-002, P19-BT-002, P19-FT-002, P19-CT-002, P19-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 19 | [P19-TASK-007](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-007) | 연쇄 사건·조건·쿨다운·대안 경로 — 핵심 규칙 | P19-TASK-006 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-002, P19-BT-002, P19-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 19 | [P19-TASK-008](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-008) | 연쇄 사건·조건·쿨다운·대안 경로 — 저장·연계 | P19-TASK-006 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-CT-002, P19-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 19 | [P19-TASK-009](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-009) | 연쇄 사건·조건·쿨다운·대안 경로 — UI·호출 경로 | P19-TASK-006 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-CT-002, P19-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 19 | [P19-TASK-010](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-010) | 연쇄 사건·조건·쿨다운·대안 경로 — Test·리뷰 | P19-TASK-007, P19-TASK-008, P19-TASK-009 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-002, P19-BT-002, P19-FT-002, P19-CT-002, P19-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 19 | [P19-TASK-011](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-011) | AdventureDirector·장기 목표·전설화 — 계약·Fixture | P9-TASK-026, P15-TASK-021, P17-TASK-021, P18-TASK-021 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-003, P19-BT-003, P19-FT-003, P19-CT-003, P19-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 19 | [P19-TASK-012](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-012) | AdventureDirector·장기 목표·전설화 — 핵심 규칙 | P19-TASK-011 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-003, P19-BT-003, P19-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 19 | [P19-TASK-013](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-013) | AdventureDirector·장기 목표·전설화 — 저장·연계 | P19-TASK-011 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-CT-003, P19-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 19 | [P19-TASK-014](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-014) | AdventureDirector·장기 목표·전설화 — UI·호출 경로 | P19-TASK-011 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-CT-003, P19-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 19 | [P19-TASK-015](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-015) | AdventureDirector·장기 목표·전설화 — Test·리뷰 | P19-TASK-012, P19-TASK-013, P19-TASK-014 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-003, P19-BT-003, P19-FT-003, P19-CT-003, P19-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 19 | [P19-TASK-016](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-016) | 전술 실험실·전략 회의·행동 자동화 — 계약·Fixture | P9-TASK-026, P15-TASK-021, P17-TASK-021, P18-TASK-021 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-004, P19-BT-004, P19-FT-004, P19-CT-004, P19-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 19 | [P19-TASK-017](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-017) | 전술 실험실·전략 회의·행동 자동화 — 핵심 규칙 | P19-TASK-016 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-004, P19-BT-004, P19-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 19 | [P19-TASK-018](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-018) | 전술 실험실·전략 회의·행동 자동화 — 저장·연계 | P19-TASK-016 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-CT-004, P19-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 19 | [P19-TASK-019](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-019) | 전술 실험실·전략 회의·행동 자동화 — UI·호출 경로 | P19-TASK-016 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-CT-004, P19-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 19 | [P19-TASK-020](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-020) | 전술 실험실·전략 회의·행동 자동화 — Test·리뷰 | P19-TASK-017, P19-TASK-018, P19-TASK-019 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-004, P19-BT-004, P19-FT-004, P19-CT-004, P19-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 19 | [P19-TASK-021](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-task-021) | Phase 19 통합 검증·인계 Gate | P19-TASK-005, P19-TASK-010, P19-TASK-015, P19-TASK-020 | 필수 | :core:simulation/event / :feature:dialogue | 선행계약후;공통 schema 충돌은직렬 | P19-UT-001, P19-BT-001, P19-FT-001, P19-CT-001, P19-IT-001, P19-UT-002, P19-BT-002, P19-FT-002, P19-CT-002, P19-IT-002, P19-UT-003, P19-BT-003, P19-FT-003, P19-CT-003, P19-IT-003, P19-UT-004, P19-BT-004, P19-FT-004, P19-CT-004, P19-IT-004, P19-RT-001, P19-CN-001, P19-REC-001, P19-PT-001, P19-OP-001, P19-ET-001, P19-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 20 | [P20-TASK-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-001) | 균열 탐사·7 핵 봉인·발생원 차단 — 계약·Fixture | P9-TASK-026, P16-TASK-021, P18-TASK-021, P19-TASK-021 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-001, P20-BT-001, P20-FT-001, P20-CT-001, P20-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 20 | [P20-TASK-002](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-002) | 균열 탐사·7 핵 봉인·발생원 차단 — 핵심 규칙 | P20-TASK-001 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-001, P20-BT-001, P20-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 20 | [P20-TASK-003](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-003) | 균열 탐사·7 핵 봉인·발생원 차단 — 저장·연계 | P20-TASK-001 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-CT-001, P20-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 20 | [P20-TASK-004](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-004) | 균열 탐사·7 핵 봉인·발생원 차단 — UI·호출 경로 | P20-TASK-001 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-CT-001, P20-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 20 | [P20-TASK-005](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-005) | 균열 탐사·7 핵 봉인·발생원 차단 — Test·리뷰 | P20-TASK-002, P20-TASK-003, P20-TASK-004 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-001, P20-BT-001, P20-FT-001, P20-CT-001, P20-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 20 | [P20-TASK-006](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-006) | 악마 전쟁·잔존세력·90 일 종전 — 계약·Fixture | P9-TASK-026, P16-TASK-021, P18-TASK-021, P19-TASK-021 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-002, P20-BT-002, P20-FT-002, P20-CT-002, P20-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 20 | [P20-TASK-007](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-007) | 악마 전쟁·잔존세력·90 일 종전 — 핵심 규칙 | P20-TASK-006 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-002, P20-BT-002, P20-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 20 | [P20-TASK-008](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-008) | 악마 전쟁·잔존세력·90 일 종전 — 저장·연계 | P20-TASK-006 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-CT-002, P20-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 20 | [P20-TASK-009](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-009) | 악마 전쟁·잔존세력·90 일 종전 — UI·호출 경로 | P20-TASK-006 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-CT-002, P20-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 20 | [P20-TASK-010](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-010) | 악마 전쟁·잔존세력·90 일 종전 — Test·리뷰 | P20-TASK-007, P20-TASK-008, P20-TASK-009 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-002, P20-BT-002, P20-FT-002, P20-CT-002, P20-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 20 | [P20-TASK-011](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-011) | 영구증표·랭킹·잔존던전·귀환 판정 — 계약·Fixture | P9-TASK-026, P16-TASK-021, P18-TASK-021, P19-TASK-021 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-003, P20-BT-003, P20-FT-003, P20-CT-003, P20-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 20 | [P20-TASK-012](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-012) | 영구증표·랭킹·잔존던전·귀환 판정 — 핵심 규칙 | P20-TASK-011 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-003, P20-BT-003, P20-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 20 | [P20-TASK-013](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-013) | 영구증표·랭킹·잔존던전·귀환 판정 — 저장·연계 | P20-TASK-011 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-CT-003, P20-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 20 | [P20-TASK-014](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-014) | 영구증표·랭킹·잔존던전·귀환 판정 — UI·호출 경로 | P20-TASK-011 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-CT-003, P20-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 20 | [P20-TASK-015](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-015) | 영구증표·랭킹·잔존던전·귀환 판정 — Test·리뷰 | P20-TASK-012, P20-TASK-013, P20-TASK-014 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-003, P20-BT-003, P20-FT-003, P20-CT-003, P20-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 20 | [P20-TASK-016](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-016) | 귀환·잔류·후일담·캠페인 종료 — 계약·Fixture | P9-TASK-026, P16-TASK-021, P18-TASK-021, P19-TASK-021 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-004, P20-BT-004, P20-FT-004, P20-CT-004, P20-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 20 | [P20-TASK-017](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-017) | 귀환·잔류·후일담·캠페인 종료 — 핵심 규칙 | P20-TASK-016 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-004, P20-BT-004, P20-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 20 | [P20-TASK-018](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-018) | 귀환·잔류·후일담·캠페인 종료 — 저장·연계 | P20-TASK-016 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-CT-004, P20-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 20 | [P20-TASK-019](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-019) | 귀환·잔류·후일담·캠페인 종료 — UI·호출 경로 | P20-TASK-016 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-CT-004, P20-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 20 | [P20-TASK-020](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-020) | 귀환·잔류·후일담·캠페인 종료 — Test·리뷰 | P20-TASK-017, P20-TASK-018, P20-TASK-019 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-004, P20-BT-004, P20-FT-004, P20-CT-004, P20-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 20 | [P20-TASK-021](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-task-021) | Phase 20 통합 검증·인계 Gate | P20-TASK-005, P20-TASK-010, P20-TASK-015, P20-TASK-020 | 필수 | :core:simulation/campaign / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P20-UT-001, P20-BT-001, P20-FT-001, P20-CT-001, P20-IT-001, P20-UT-002, P20-BT-002, P20-FT-002, P20-CT-002, P20-IT-002, P20-UT-003, P20-BT-003, P20-FT-003, P20-CT-003, P20-IT-003, P20-UT-004, P20-BT-004, P20-FT-004, P20-CT-004, P20-IT-004, P20-RT-001, P20-CN-001, P20-REC-001, P20-PT-001, P20-OP-001, P20-ET-001, P20-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 21 | [P21-TASK-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-001) | 용병·장비·파티·세계 연대기 — 계약·Fixture | P15-TASK-021, P16-TASK-021, P17-TASK-021, P18-TASK-021, P19-TASK-021, P20-TASK-021 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-001, P21-BT-001, P21-FT-001, P21-CT-001, P21-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 21 | [P21-TASK-002](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-002) | 용병·장비·파티·세계 연대기 — 핵심 규칙 | P21-TASK-001 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-001, P21-BT-001, P21-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 21 | [P21-TASK-003](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-003) | 용병·장비·파티·세계 연대기 — 저장·연계 | P21-TASK-001 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-CT-001, P21-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 21 | [P21-TASK-004](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-004) | 용병·장비·파티·세계 연대기 — UI·호출 경로 | P21-TASK-001 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-CT-001, P21-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 21 | [P21-TASK-005](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-005) | 용병·장비·파티·세계 연대기 — Test·리뷰 | P21-TASK-002, P21-TASK-003, P21-TASK-004 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-001, P21-BT-001, P21-FT-001, P21-CT-001, P21-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 21 | [P21-TASK-006](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-006) | 일월연 통계·기여·추세·랭킹 이력 — 계약·Fixture | P15-TASK-021, P16-TASK-021, P17-TASK-021, P18-TASK-021, P19-TASK-021, P20-TASK-021 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-002, P21-BT-002, P21-FT-002, P21-CT-002, P21-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 21 | [P21-TASK-007](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-007) | 일월연 통계·기여·추세·랭킹 이력 — 핵심 규칙 | P21-TASK-006 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-002, P21-BT-002, P21-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 21 | [P21-TASK-008](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-008) | 일월연 통계·기여·추세·랭킹 이력 — 저장·연계 | P21-TASK-006 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-CT-002, P21-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 21 | [P21-TASK-009](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-009) | 일월연 통계·기여·추세·랭킹 이력 — UI·호출 경로 | P21-TASK-006 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-CT-002, P21-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 21 | [P21-TASK-010](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-010) | 일월연 통계·기여·추세·랭킹 이력 — Test·리뷰 | P21-TASK-007, P21-TASK-008, P21-TASK-009 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-002, P21-BT-002, P21-FT-002, P21-CT-002, P21-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 21 | [P21-TASK-011](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-011) | 공개 정보 검색·필터·페이지·북마크 — 계약·Fixture | P15-TASK-021, P16-TASK-021, P17-TASK-021, P18-TASK-021, P19-TASK-021, P20-TASK-021 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-003, P21-BT-003, P21-FT-003, P21-CT-003, P21-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 21 | [P21-TASK-012](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-012) | 공개 정보 검색·필터·페이지·북마크 — 핵심 규칙 | P21-TASK-011 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-003, P21-BT-003, P21-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 21 | [P21-TASK-013](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-013) | 공개 정보 검색·필터·페이지·북마크 — 저장·연계 | P21-TASK-011 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-CT-003, P21-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 21 | [P21-TASK-014](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-014) | 공개 정보 검색·필터·페이지·북마크 — UI·호출 경로 | P21-TASK-011 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-CT-003, P21-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 21 | [P21-TASK-015](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-015) | 공개 정보 검색·필터·페이지·북마크 — Test·리뷰 | P21-TASK-012, P21-TASK-013, P21-TASK-014 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-003, P21-BT-003, P21-FT-003, P21-CT-003, P21-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 21 | [P21-TASK-016](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-016) | 보존·압축·뉴스·정보 알림 — 계약·Fixture | P15-TASK-021, P16-TASK-021, P17-TASK-021, P18-TASK-021, P19-TASK-021, P20-TASK-021 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-004, P21-BT-004, P21-FT-004, P21-CT-004, P21-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 21 | [P21-TASK-017](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-017) | 보존·압축·뉴스·정보 알림 — 핵심 규칙 | P21-TASK-016 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-004, P21-BT-004, P21-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 2.12 |
| 21 | [P21-TASK-018](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-018) | 보존·압축·뉴스·정보 알림 — 저장·연계 | P21-TASK-016 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-CT-004, P21-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 21 | [P21-TASK-019](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-019) | 보존·압축·뉴스·정보 알림 — UI·호출 경로 | P21-TASK-016 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-CT-004, P21-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 21 | [P21-TASK-020](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-020) | 보존·압축·뉴스·정보 알림 — Test·리뷰 | P21-TASK-017, P21-TASK-018, P21-TASK-019 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-004, P21-BT-004, P21-FT-004, P21-CT-004, P21-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 21 | [P21-TASK-021](22_Phase21_연대기_통계_검색_상세설계서.md#p21-task-021) | Phase 21 통합 검증·인계 Gate | P21-TASK-005, P21-TASK-010, P21-TASK-015, P21-TASK-020 | 필수 | :core:data / :core:simulation/chronicle / :feature:records | 선행계약후;공통 schema 충돌은직렬 | P21-UT-001, P21-BT-001, P21-FT-001, P21-CT-001, P21-IT-001, P21-UT-002, P21-BT-002, P21-FT-002, P21-CT-002, P21-IT-002, P21-UT-003, P21-BT-003, P21-FT-003, P21-CT-003, P21-IT-003, P21-UT-004, P21-BT-004, P21-FT-004, P21-CT-004, P21-IT-004, P21-RT-001, P21-CN-001, P21-REC-001, P21-PT-001, P21-OP-001, P21-ET-001, P21-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 22 | [P22-TASK-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-001) | 정보구조·화면 계약·Navigation — 계약·Fixture | P9-TASK-026, P10-TASK-021, P11-TASK-021, P12-TASK-021, P13-TASK-021, P14-TASK-021, P15-TASK-021, P16-TASK-021, P18-TASK-021, P19-TASK-021, P20-TASK-021, P21-TASK-021 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-001, P22-BT-001, P22-FT-001, P22-CT-001, P22-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 22 | [P22-TASK-002](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-002) | 정보구조·화면 계약·Navigation — 핵심 규칙 | P22-TASK-001 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-001, P22-BT-001, P22-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 22 | [P22-TASK-003](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-003) | 정보구조·화면 계약·Navigation — 저장·연계 | P22-TASK-001 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-CT-001, P22-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 22 | [P22-TASK-004](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-004) | 정보구조·화면 계약·Navigation — UI·호출 경로 | P22-TASK-001 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-CT-001, P22-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 22 | [P22-TASK-005](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-005) | 정보구조·화면 계약·Navigation — Test·리뷰 | P22-TASK-002, P22-TASK-003, P22-TASK-004 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-001, P22-BT-001, P22-FT-001, P22-CT-001, P22-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 22 | [P22-TASK-006](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-006) | 디자인 토큰·컴포넌트·이미지 — 계약·Fixture | P9-TASK-026, P10-TASK-021, P11-TASK-021, P12-TASK-021, P13-TASK-021, P14-TASK-021, P15-TASK-021, P16-TASK-021, P18-TASK-021, P19-TASK-021, P20-TASK-021, P21-TASK-021 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-002, P22-BT-002, P22-FT-002, P22-CT-002, P22-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 22 | [P22-TASK-007](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-007) | 디자인 토큰·컴포넌트·이미지 — 핵심 규칙 | P22-TASK-006 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-002, P22-BT-002, P22-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 22 | [P22-TASK-008](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-008) | 디자인 토큰·컴포넌트·이미지 — 저장·연계 | P22-TASK-006 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-CT-002, P22-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 22 | [P22-TASK-009](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-009) | 디자인 토큰·컴포넌트·이미지 — UI·호출 경로 | P22-TASK-006 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-CT-002, P22-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 22 | [P22-TASK-010](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-010) | 디자인 토큰·컴포넌트·이미지 — Test·리뷰 | P22-TASK-007, P22-TASK-008, P22-TASK-009 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-002, P22-BT-002, P22-FT-002, P22-CT-002, P22-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 22 | [P22-TASK-011](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-011) | Adaptive·큰글자·TalkBack·터치 — 계약·Fixture | P9-TASK-026, P10-TASK-021, P11-TASK-021, P12-TASK-021, P13-TASK-021, P14-TASK-021, P15-TASK-021, P16-TASK-021, P18-TASK-021, P19-TASK-021, P20-TASK-021, P21-TASK-021 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-003, P22-BT-003, P22-FT-003, P22-CT-003, P22-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 22 | [P22-TASK-012](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-012) | Adaptive·큰글자·TalkBack·터치 — 핵심 규칙 | P22-TASK-011 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-003, P22-BT-003, P22-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 22 | [P22-TASK-013](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-013) | Adaptive·큰글자·TalkBack·터치 — 저장·연계 | P22-TASK-011 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-CT-003, P22-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 22 | [P22-TASK-014](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-014) | Adaptive·큰글자·TalkBack·터치 — UI·호출 경로 | P22-TASK-011 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-CT-003, P22-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 22 | [P22-TASK-015](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-015) | Adaptive·큰글자·TalkBack·터치 — Test·리뷰 | P22-TASK-012, P22-TASK-013, P22-TASK-014 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-003, P22-BT-003, P22-FT-003, P22-CT-003, P22-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 22 | [P22-TASK-016](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-016) | 화면 생명주기·일회성 효과·유저 여정 — 계약·Fixture | P9-TASK-026, P10-TASK-021, P11-TASK-021, P12-TASK-021, P13-TASK-021, P14-TASK-021, P15-TASK-021, P16-TASK-021, P18-TASK-021, P19-TASK-021, P20-TASK-021, P21-TASK-021 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-004, P22-BT-004, P22-FT-004, P22-CT-004, P22-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 22 | [P22-TASK-017](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-017) | 화면 생명주기·일회성 효과·유저 여정 — 핵심 규칙 | P22-TASK-016 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-004, P22-BT-004, P22-FT-004 | epoch/version 순서 경계와 일회성 효과 비재생 golden 결과 구현 | 1.59 |
| 22 | [P22-TASK-018](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-018) | 화면 생명주기·일회성 효과·유저 여정 — 저장·연계 | P22-TASK-016 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-CT-004, P22-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 22 | [P22-TASK-019](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-019) | 화면 생명주기·일회성 효과·유저 여정 — UI·호출 경로 | P22-TASK-016 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-CT-004, P22-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 22 | [P22-TASK-020](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-020) | 화면 생명주기·일회성 효과·유저 여정 — Test·리뷰 | P22-TASK-017, P22-TASK-018, P22-TASK-019 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-004, P22-BT-004, P22-FT-004, P22-CT-004, P22-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 22 | [P22-TASK-021](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-task-021) | Phase 22 통합 검증·인계 Gate | P22-TASK-005, P22-TASK-010, P22-TASK-015, P22-TASK-020 | 필수 | :core:designsystem / :feature:* / :app | 선행계약후;공통 schema 충돌은직렬 | P22-UT-001, P22-BT-001, P22-FT-001, P22-CT-001, P22-IT-001, P22-UT-002, P22-BT-002, P22-FT-002, P22-CT-002, P22-IT-002, P22-UT-003, P22-BT-003, P22-FT-003, P22-CT-003, P22-IT-003, P22-UT-004, P22-BT-004, P22-FT-004, P22-CT-004, P22-IT-004, P22-RT-001, P22-CN-001, P22-REC-001, P22-PT-001, P22-OP-001, P22-ET-001, P22-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 23 | [P23-TASK-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-001) | Validation Center·공통 검사 실행 — 계약·Fixture | P17-TASK-021, P19-TASK-021, P20-TASK-021, P21-TASK-021, P22-TASK-021 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-001, P23-BT-001, P23-FT-001, P23-CT-001, P23-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 23 | [P23-TASK-002](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-002) | Validation Center·공통 검사 실행 — 핵심 규칙 | P23-TASK-001 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-001, P23-BT-001, P23-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 23 | [P23-TASK-003](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-003) | Validation Center·공통 검사 실행 — 저장·연계 | P23-TASK-001 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-CT-001, P23-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 23 | [P23-TASK-004](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-004) | Validation Center·공통 검사 실행 — UI·호출 경로 | P23-TASK-001 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-CT-001, P23-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 23 | [P23-TASK-005](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-005) | Validation Center·공통 검사 실행 — Test·리뷰 | P23-TASK-002, P23-TASK-003, P23-TASK-004 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-001, P23-BT-001, P23-FT-001, P23-CT-001, P23-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 23 | [P23-TASK-006](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-006) | 불변식·속성·퍼즈·장기 월드 — 계약·Fixture | P17-TASK-021, P19-TASK-021, P20-TASK-021, P21-TASK-021, P22-TASK-021 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-002, P23-BT-002, P23-FT-002, P23-CT-002, P23-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 23 | [P23-TASK-007](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-007) | 불변식·속성·퍼즈·장기 월드 — 핵심 규칙 | P23-TASK-006 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-002, P23-BT-002, P23-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 23 | [P23-TASK-008](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-008) | 불변식·속성·퍼즈·장기 월드 — 저장·연계 | P23-TASK-006 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-CT-002, P23-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 23 | [P23-TASK-009](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-009) | 불변식·속성·퍼즈·장기 월드 — UI·호출 경로 | P23-TASK-006 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-CT-002, P23-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 23 | [P23-TASK-010](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-010) | 불변식·속성·퍼즈·장기 월드 — Test·리뷰 | P23-TASK-007, P23-TASK-008, P23-TASK-009 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-002, P23-BT-002, P23-FT-002, P23-CT-002, P23-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 23 | [P23-TASK-011](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-011) | 확률·수치·드롭·경제 밸런스 비교 — 계약·Fixture | P17-TASK-021, P19-TASK-021, P20-TASK-021, P21-TASK-021, P22-TASK-021 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 23 | [P23-TASK-012](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-012) | 확률·수치·드롭·경제 밸런스 비교 — 핵심 규칙 | P23-TASK-011 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 23 | [P23-TASK-013](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-013) | 확률·수치·드롭·경제 밸런스 비교 — 저장·연계 | P23-TASK-011 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-CT-003, P23-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 23 | [P23-TASK-014](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-014) | 확률·수치·드롭·경제 밸런스 비교 — UI·호출 경로 | P23-TASK-011 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-CT-003, P23-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 23 | [P23-TASK-015](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-015) | 확률·수치·드롭·경제 밸런스 비교 — Test·리뷰 | P23-TASK-012, P23-TASK-013, P23-TASK-014 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 23 | [P23-TASK-016](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-016) | 재현 패키지·리뷰·결함·승인 — 계약·Fixture | P17-TASK-021, P19-TASK-021, P20-TASK-021, P21-TASK-021, P22-TASK-021 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-004, P23-BT-004, P23-FT-004, P23-CT-004, P23-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 23 | [P23-TASK-017](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-017) | 재현 패키지·리뷰·결함·승인 — 핵심 규칙 | P23-TASK-016 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-004, P23-BT-004, P23-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 23 | [P23-TASK-018](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-018) | 재현 패키지·리뷰·결함·승인 — 저장·연계 | P23-TASK-016 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-CT-004, P23-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 23 | [P23-TASK-019](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-019) | 재현 패키지·리뷰·결함·승인 — UI·호출 경로 | P23-TASK-016 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-CT-004, P23-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 23 | [P23-TASK-020](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-020) | 재현 패키지·리뷰·결함·승인 — Test·리뷰 | P23-TASK-017, P23-TASK-018, P23-TASK-019 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-004, P23-BT-004, P23-FT-004, P23-CT-004, P23-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 23 | [P23-TASK-021](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-021) | Phase 23 통합 검증·인계 Gate | P23-TASK-005, P23-TASK-010, P23-TASK-015, P23-TASK-020, P23-TASK-022, P23-TASK-023, P23-TASK-024, P23-TASK-025, P23-TASK-026, P23-TASK-027, P23-TASK-028, P23-TASK-029, P23-TASK-030, P23-TASK-031, P23-TASK-032, P23-TASK-033, P23-TASK-034, P23-TASK-035, P23-TASK-036, P23-TASK-037, P23-TASK-038, P23-TASK-039, P23-TASK-040, P23-TASK-041, P23-TASK-042, P23-TASK-043, P23-TASK-044, P23-TASK-045, P23-TASK-046, P23-TASK-047, P23-TASK-048, P23-TASK-049, P23-TASK-050, P23-TASK-051, P23-TASK-052, P23-TASK-053, P23-TASK-054, P23-TASK-055, P23-TASK-056, P23-TASK-057, P23-TASK-058, P23-TASK-059, P23-TASK-060, P23-TASK-061, P23-TASK-062, P23-TASK-063, P23-TASK-064, P23-TASK-065, P23-TASK-066, P23-TASK-067, P23-TASK-068, P23-TASK-069, P23-TASK-070, P23-TASK-071, P23-TASK-072, P23-TASK-073, P23-TASK-074, P23-TASK-075, P23-TASK-076, P23-TASK-077, P23-TASK-078, P23-TASK-079, P23-TASK-080, P23-TASK-081, P23-TASK-082, P23-TASK-083, P23-TASK-084, P23-TASK-085, P23-TASK-086, P23-TASK-087, P23-TASK-088, P23-TASK-089, P23-TASK-090, P23-TASK-091, P23-TASK-092, P23-TASK-093, P23-TASK-094, P23-TASK-095, P23-TASK-096, P23-TASK-097, P23-TASK-098, P23-TASK-099, P23-TASK-100, P23-TASK-101, P23-TASK-102, P23-TASK-103, P23-TASK-104, P23-TASK-105, P23-TASK-106, P23-TASK-107, P23-TASK-108, P23-TASK-109, P23-TASK-110, P23-TASK-111, P23-TASK-112 | 필수 | :core:testing / :feature:validation / tools:validation-cli | 선행계약후;공통 schema 충돌은직렬 | P23-UT-001, P23-BT-001, P23-FT-001, P23-CT-001, P23-IT-001, P23-UT-002, P23-BT-002, P23-FT-002, P23-CT-002, P23-IT-002, P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003, P23-UT-004, P23-BT-004, P23-FT-004, P23-CT-004, P23-IT-004, P23-RT-001, P23-CN-001, P23-REC-001, P23-PT-001, P23-OP-001, P23-ET-001, P23-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 24 | [P24-TASK-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-001) | 실측 성능·이미지·목록·DB 예산 — 계약·Fixture | P23-TASK-021 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-001, P24-BT-001, P24-FT-001, P24-CT-001, P24-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 24 | [P24-TASK-002](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-002) | 실측 성능·이미지·목록·DB 예산 — 핵심 규칙 | P24-TASK-001 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-001, P24-BT-001, P24-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 24 | [P24-TASK-003](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-003) | 실측 성능·이미지·목록·DB 예산 — 저장·연계 | P24-TASK-001 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-CT-001, P24-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 24 | [P24-TASK-004](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-004) | 실측 성능·이미지·목록·DB 예산 — UI·호출 경로 | P24-TASK-001 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-CT-001, P24-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 24 | [P24-TASK-005](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-005) | 실측 성능·이미지·목록·DB 예산 — Test·리뷰 | P24-TASK-002, P24-TASK-003, P24-TASK-004 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-001, P24-BT-001, P24-FT-001, P24-CT-001, P24-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 24 | [P24-TASK-006](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-006) | 누수·취소·배터리·앱 중단 — 계약·Fixture | P23-TASK-021 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-002, P24-BT-002, P24-FT-002, P24-CT-002, P24-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 24 | [P24-TASK-007](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-007) | 누수·취소·배터리·앱 중단 — 핵심 규칙 | P24-TASK-006 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-002, P24-BT-002, P24-FT-002 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 24 | [P24-TASK-008](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-008) | 누수·취소·배터리·앱 중단 — 저장·연계 | P24-TASK-006 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-CT-002, P24-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 24 | [P24-TASK-009](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-009) | 누수·취소·배터리·앱 중단 — UI·호출 경로 | P24-TASK-006 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-CT-002, P24-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 24 | [P24-TASK-010](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-010) | 누수·취소·배터리·앱 중단 — Test·리뷰 | P24-TASK-007, P24-TASK-008, P24-TASK-009 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-002, P24-BT-002, P24-FT-002, P24-CT-002, P24-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 24 | [P24-TASK-011](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-011) | 저장공간·장기 압축·실패 격리 — 계약·Fixture | P23-TASK-021 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-003, P24-BT-003, P24-FT-003, P24-CT-003, P24-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 24 | [P24-TASK-012](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-012) | 저장공간·장기 압축·실패 격리 — 핵심 규칙 | P24-TASK-011 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-003, P24-BT-003, P24-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 24 | [P24-TASK-013](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-013) | 저장공간·장기 압축·실패 격리 — 저장·연계 | P24-TASK-011 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-CT-003, P24-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 24 | [P24-TASK-014](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-014) | 저장공간·장기 압축·실패 격리 — UI·호출 경로 | P24-TASK-011 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-CT-003, P24-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 24 | [P24-TASK-015](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-015) | 저장공간·장기 압축·실패 격리 — Test·리뷰 | P24-TASK-012, P24-TASK-013, P24-TASK-014 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-003, P24-BT-003, P24-FT-003, P24-CT-003, P24-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 24 | [P24-TASK-016](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-016) | 최적화 동치·인덱스·R8·프로필 — 계약·Fixture | P23-TASK-021 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-004, P24-BT-004, P24-FT-004, P24-CT-004, P24-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 24 | [P24-TASK-017](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-017) | 최적화 동치·인덱스·R8·프로필 — 핵심 규칙 | P24-TASK-016 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-004, P24-BT-004, P24-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 24 | [P24-TASK-018](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-018) | 최적화 동치·인덱스·R8·프로필 — 저장·연계 | P24-TASK-016 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-CT-004, P24-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 24 | [P24-TASK-019](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-019) | 최적화 동치·인덱스·R8·프로필 — UI·호출 경로 | P24-TASK-016 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-CT-004, P24-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 24 | [P24-TASK-020](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-020) | 최적화 동치·인덱스·R8·프로필 — Test·리뷰 | P24-TASK-017, P24-TASK-018, P24-TASK-019 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-004, P24-BT-004, P24-FT-004, P24-CT-004, P24-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 24 | [P24-TASK-021](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-task-021) | Phase 24 통합 검증·인계 Gate | P24-TASK-005, P24-TASK-010, P24-TASK-015, P24-TASK-020 | 필수 | :benchmark / :core:* | 선행계약후;공통 schema 충돌은직렬 | P24-UT-001, P24-BT-001, P24-FT-001, P24-CT-001, P24-IT-001, P24-UT-002, P24-BT-002, P24-FT-002, P24-CT-002, P24-IT-002, P24-UT-003, P24-BT-003, P24-FT-003, P24-CT-003, P24-IT-003, P24-UT-004, P24-BT-004, P24-FT-004, P24-CT-004, P24-IT-004, P24-RT-001, P24-CN-001, P24-REC-001, P24-PT-001, P24-OP-001, P24-ET-001, P24-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 25 | [P25-TASK-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-001) | 전체 End-to-End·회귀·출시 범위 검수 — 계약·Fixture | P24-TASK-021 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-001, P25-BT-001, P25-FT-001, P25-CT-001, P25-IT-001 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 25 | [P25-TASK-002](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-002) | 전체 End-to-End·회귀·출시 범위 검수 — 핵심 규칙 | P25-TASK-001 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-001, P25-BT-001, P25-FT-001 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 25 | [P25-TASK-003](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-003) | 전체 End-to-End·회귀·출시 범위 검수 — 저장·연계 | P25-TASK-001 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-CT-001, P25-IT-001 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 25 | [P25-TASK-004](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-004) | 전체 End-to-End·회귀·출시 범위 검수 — UI·호출 경로 | P25-TASK-001 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-CT-001, P25-IT-001 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 25 | [P25-TASK-005](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-005) | 전체 End-to-End·회귀·출시 범위 검수 — Test·리뷰 | P25-TASK-002, P25-TASK-003, P25-TASK-004 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-001, P25-BT-001, P25-FT-001, P25-CT-001, P25-IT-001 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 25 | [P25-TASK-006](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-006) | 업그레이드·마이그레이션·다운그레이드 — 계약·Fixture | P24-TASK-021 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-002, P25-BT-002, P25-FT-002, P25-CT-002, P25-IT-002 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 25 | [P25-TASK-007](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-007) | 업그레이드·마이그레이션·다운그레이드 — 핵심 규칙 | P25-TASK-006 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-002, P25-BT-002, P25-FT-002 | schema 체인·구 EventCodec decode/upcast·projection rebuild 검증 | 1.59 |
| 25 | [P25-TASK-008](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-008) | 업그레이드·마이그레이션·다운그레이드 — 저장·연계 | P25-TASK-006 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-CT-002, P25-IT-002 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 25 | [P25-TASK-009](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-009) | 업그레이드·마이그레이션·다운그레이드 — UI·호출 경로 | P25-TASK-006 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-CT-002, P25-IT-002 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 25 | [P25-TASK-010](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-010) | 업그레이드·마이그레이션·다운그레이드 — Test·리뷰 | P25-TASK-007, P25-TASK-008, P25-TASK-009 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-002, P25-BT-002, P25-FT-002, P25-CT-002, P25-IT-002 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 25 | [P25-TASK-011](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-011) | 서명 Release·오프라인 설치·배포 — 계약·Fixture | P24-TASK-021 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-003, P25-BT-003, P25-FT-003, P25-CT-003, P25-IT-003 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 25 | [P25-TASK-012](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-012) | 서명 Release·오프라인 설치·배포 — 핵심 규칙 | P25-TASK-011 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-003, P25-BT-003, P25-FT-003 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 25 | [P25-TASK-013](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-013) | 서명 Release·오프라인 설치·배포 — 저장·연계 | P25-TASK-011 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-CT-003, P25-IT-003 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 25 | [P25-TASK-014](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-014) | 서명 Release·오프라인 설치·배포 — UI·호출 경로 | P25-TASK-011 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-CT-003, P25-IT-003 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 25 | [P25-TASK-015](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-015) | 서명 Release·오프라인 설치·배포 — Test·리뷰 | P25-TASK-012, P25-TASK-013, P25-TASK-014 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-003, P25-BT-003, P25-FT-003, P25-CT-003, P25-IT-003 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 25 | [P25-TASK-016](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-016) | 릴리즈 운영·결함 대응·문서 인계 — 계약·Fixture | P24-TASK-021 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-004, P25-BT-004, P25-FT-004, P25-CT-004, P25-IT-004 | DTO schema·source assertion manifest·3 종 fixture 를 리뷰 승인 | 1.06 |
| 25 | [P25-TASK-017](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-017) | 릴리즈 운영·결함 대응·문서 인계 — 핵심 규칙 | P25-TASK-016 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-004, P25-BT-004, P25-FT-004 | 순수핵심 메소드·경계검사·결정론 golden 결과 구현; 미정규칙 활성금지 | 1.59 |
| 25 | [P25-TASK-018](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-018) | 릴리즈 운영·결함 대응·문서 인계 — 저장·연계 | P25-TASK-016 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-CT-004, P25-IT-004 | 실제 adapter 통합·필요 migration/codec·FK/취소경계 검증 | 1.59 |
| 25 | [P25-TASK-019](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-019) | 릴리즈 운영·결함 대응·문서 인계 — UI·호출 경로 | P25-TASK-016 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-CT-004, P25-IT-004 | 정상·경계·실패가관측가능한최소진입점과접근성 labels; 핵심권한우회0 | 1.06 |
| 25 | [P25-TASK-020](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-020) | 릴리즈 운영·결함 대응·문서 인계 — Test·리뷰 | P25-TASK-017, P25-TASK-018, P25-TASK-019 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-004, P25-BT-004, P25-FT-004, P25-CT-004, P25-IT-004 | 대표5 개 Test 와원문세부 assertion coverage 검토완료·관련중대결함0·리뷰승인 | 1.06 |
| 25 | [P25-TASK-021](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-task-021) | Phase 25 통합 검증·인계 Gate | P25-TASK-005, P25-TASK-010, P25-TASK-015, P25-TASK-020 | 필수 | :app / release / CI | 선행계약후;공통 schema 충돌은직렬 | P25-UT-001, P25-BT-001, P25-FT-001, P25-CT-001, P25-IT-001, P25-UT-002, P25-BT-002, P25-FT-002, P25-CT-002, P25-IT-002, P25-UT-003, P25-BT-003, P25-FT-003, P25-CT-003, P25-IT-003, P25-UT-004, P25-BT-004, P25-FT-004, P25-CT-004, P25-IT-004, P25-RT-001, P25-CN-001, P25-REC-001, P25-PT-001, P25-OP-001, P25-ET-001, P25-IT-005 | 필수 Test PASS·Gate 승인·인계 DTO/codec/fixture·미해결중대결함0 | 1.59 |
| 23 | [P23-TASK-022](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-022) | Full 콘텐츠 ACC ACC-0001~ACC-0050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-023](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-023) | Full 콘텐츠 ACC ACC-0051~ACC-0100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-024](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-024) | Full 콘텐츠 ACC ACC-0101~ACC-0150 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-025](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-025) | Full 콘텐츠 ACC ACC-0151~ACC-0200 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-026](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-026) | Full 콘텐츠 ACC ACC-0201~ACC-0250 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-027](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-027) | Full 콘텐츠 ACC ACC-0251~ACC-0300 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-028](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-028) | Full 콘텐츠 ACC ACC-0301~ACC-0350 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-029](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-029) | Full 콘텐츠 ACC ACC-0351~ACC-0400 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-030](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-030) | Full 콘텐츠 ACC ACC-0401~ACC-0420 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-031](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-031) | Full 콘텐츠 ARM ARM-0001~ARM-0050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-032](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-032) | Full 콘텐츠 ARM ARM-0051~ARM-0100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-033](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-033) | Full 콘텐츠 ARM ARM-0101~ARM-0150 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-034](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-034) | Full 콘텐츠 ARM ARM-0151~ARM-0200 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-035](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-035) | Full 콘텐츠 ARM ARM-0201~ARM-0250 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-036](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-036) | Full 콘텐츠 ARM ARM-0251~ARM-0300 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-037](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-037) | Full 콘텐츠 ARM ARM-0301~ARM-0350 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-038](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-038) | Full 콘텐츠 ARM ARM-0351~ARM-0400 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-039](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-039) | Full 콘텐츠 ARM ARM-0401~ARM-0450 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-040](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-040) | Full 콘텐츠 ARM ARM-0451~ARM-0480 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-041](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-041) | Full 콘텐츠 BOS BOS-001~BOS-025 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-042](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-042) | Full 콘텐츠 BOS BOS-026~BOS-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-043](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-043) | Full 콘텐츠 BOS BOS-051~BOS-075 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-044](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-044) | Full 콘텐츠 BOS BOS-076~BOS-100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-045](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-045) | Full 콘텐츠 CHAIN CHAIN-001~CHAIN-025 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-046](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-046) | Full 콘텐츠 CHAIN CHAIN-026~CHAIN-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-047](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-047) | Full 콘텐츠 CHAIN CHAIN-051~CHAIN-060 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-048](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-048) | Full 콘텐츠 CTR CTR-001~CTR-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-049](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-049) | Full 콘텐츠 CTR CTR-051~CTR-080 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-050](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-050) | Full 콘텐츠 DNG-EVT DNG-EVT-001~DNG-EVT-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-051](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-051) | Full 콘텐츠 DNG-EVT DNG-EVT-051~DNG-EVT-100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-052](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-052) | Full 콘텐츠 DNG-EVT DNG-EVT-101~DNG-EVT-150 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-053](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-053) | Full 콘텐츠 DNG-EVT DNG-EVT-151~DNG-EVT-200 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-054](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-054) | Full 콘텐츠 DNG-EVT DNG-EVT-201~DNG-EVT-204 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-055](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-055) | Full 콘텐츠 EPRE EPRE-001~EPRE-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-056](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-056) | Full 콘텐츠 EPRE EPRE-051~EPRE-100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-057](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-057) | Full 콘텐츠 EPRE EPRE-101~EPRE-120 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-058](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-058) | Full 콘텐츠 ESUF ESUF-001~ESUF-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-059](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-059) | Full 콘텐츠 ESUF ESUF-051~ESUF-100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-060](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-060) | Full 콘텐츠 ESUF ESUF-101~ESUF-120 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-061](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-061) | Full 콘텐츠 EVT EVT-001~EVT-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-062](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-062) | Full 콘텐츠 EVT EVT-051~EVT-100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-063](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-063) | Full 콘텐츠 EVT EVT-101~EVT-130 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-064](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-064) | Full 콘텐츠 ITM ITM-0001~ITM-0050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-065](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-065) | Full 콘텐츠 ITM ITM-0051~ITM-0100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-066](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-066) | Full 콘텐츠 ITM ITM-0101~ITM-0150 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-067](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-067) | Full 콘텐츠 ITM ITM-0151~ITM-0180 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-068](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-068) | Full 콘텐츠 LEG LEG-001~LEG-024 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-069](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-069) | Full 콘텐츠 MON MON-0001~MON-0050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-070](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-070) | Full 콘텐츠 MON MON-0051~MON-0100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-071](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-071) | Full 콘텐츠 MON MON-0101~MON-0150 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-072](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-072) | Full 콘텐츠 MON MON-0151~MON-0200 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-073](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-073) | Full 콘텐츠 MON MON-0201~MON-0250 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-074](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-074) | Full 콘텐츠 MON MON-0251~MON-0300 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-075](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-075) | Full 콘텐츠 MPRE MPRE-001~MPRE-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-076](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-076) | Full 콘텐츠 MPRE MPRE-051~MPRE-100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-077](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-077) | Full 콘텐츠 MPRE MPRE-101~MPRE-120 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-078](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-078) | Full 콘텐츠 MSUF MSUF-001~MSUF-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-079](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-079) | Full 콘텐츠 MSUF MSUF-051~MSUF-100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-080](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-080) | Full 콘텐츠 MSUF MSUF-101~MSUF-120 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-081](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-081) | Full 콘텐츠 REL REL-001~REL-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-082](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-082) | Full 콘텐츠 SET SET-001~SET-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-083](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-083) | Full 콘텐츠 SET SET-051~SET-060 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-084](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-084) | Full 콘텐츠 SKL SKL-0001~SKL-0025 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-085](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-085) | Full 콘텐츠 SKL SKL-0026~SKL-0050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-086](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-086) | Full 콘텐츠 SKL SKL-0051~SKL-0075 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-087](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-087) | Full 콘텐츠 SKL SKL-0076~SKL-0100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-088](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-088) | Full 콘텐츠 SKL SKL-0101~SKL-0125 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-089](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-089) | Full 콘텐츠 SKL SKL-0126~SKL-0150 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-090](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-090) | Full 콘텐츠 SKL SKL-0151~SKL-0175 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-091](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-091) | Full 콘텐츠 SKL SKL-0176~SKL-0200 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-092](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-092) | Full 콘텐츠 SKL SKL-0201~SKL-0225 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-093](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-093) | Full 콘텐츠 SKL SKL-0226~SKL-0250 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-094](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-094) | Full 콘텐츠 SKL SKL-0251~SKL-0275 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-095](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-095) | Full 콘텐츠 SKL SKL-0276~SKL-0300 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-096](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-096) | Full 콘텐츠 SPRE SPRE-001~SPRE-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-097](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-097) | Full 콘텐츠 SPRE SPRE-051~SPRE-080 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-098](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-098) | Full 콘텐츠 SSUF SSUF-001~SSUF-050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-099](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-099) | Full 콘텐츠 SSUF SSUF-051~SSUF-080 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-100](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-100) | Full 콘텐츠 WPN WPN-0001~WPN-0050 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-101](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-101) | Full 콘텐츠 WPN WPN-0051~WPN-0100 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-102](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-102) | Full 콘텐츠 WPN WPN-0101~WPN-0150 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-103](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-103) | Full 콘텐츠 WPN WPN-0151~WPN-0200 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-104](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-104) | Full 콘텐츠 WPN WPN-0201~WPN-0250 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-105](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-105) | Full 콘텐츠 WPN WPN-0251~WPN-0300 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-106](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-106) | Full 콘텐츠 WPN WPN-0301~WPN-0350 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-107](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-107) | Full 콘텐츠 WPN WPN-0351~WPN-0400 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-108](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-108) | Full 콘텐츠 WPN WPN-0401~WPN-0420 검수 | P23-TASK-011, P23-TASK-012 | 필수 | :core:content / content-source | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 해당배치전체 ID source roundtrip·효과상한/참조검사·파라미터시험 PASS, 검토승인 | 1.59 |
| 23 | [P23-TASK-109](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-109) | 10,000 개 실물 초상·모든 필수자산 검수 | P1-TASK-021, P4-TASK-026 | 필수 | :core:content / QA | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 실물/데이터검증증거·확정수량·의미적누락점검승인. 미제공/미검증은별도차단상태유지 | 3.17 |
| 23 | [P23-TASK-110](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-110) | 이름 Pool·문화권·성씨 실제데이터 완성 | P4-TASK-026 | 필수 | :core:content / QA | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 실물/데이터검증증거·확정수량·의미적누락점검승인. 미제공/미검증은별도차단상태유지 | 3.17 |
| 23 | [P23-TASK-111](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-111) | 미수록 목표카탈로그 보완·정확수량 승인 | P1-TASK-021 | 필수 | :core:content / QA | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 실물/데이터검증증거·확정수량·의미적누락점검승인. 미제공/미검증은별도차단상태유지 | 3.17 |
| 23 | [P23-TASK-112](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-task-112) | Full 전체콘텐츠 조립·원문 세부 assertion 감사 | P23-TASK-022, P23-TASK-023, P23-TASK-024, P23-TASK-025, P23-TASK-026, P23-TASK-027, P23-TASK-028, P23-TASK-029, P23-TASK-030, P23-TASK-031, P23-TASK-032, P23-TASK-033, P23-TASK-034, P23-TASK-035, P23-TASK-036, P23-TASK-037, P23-TASK-038, P23-TASK-039, P23-TASK-040, P23-TASK-041, P23-TASK-042, P23-TASK-043, P23-TASK-044, P23-TASK-045, P23-TASK-046, P23-TASK-047, P23-TASK-048, P23-TASK-049, P23-TASK-050, P23-TASK-051, P23-TASK-052, P23-TASK-053, P23-TASK-054, P23-TASK-055, P23-TASK-056, P23-TASK-057, P23-TASK-058, P23-TASK-059, P23-TASK-060, P23-TASK-061, P23-TASK-062, P23-TASK-063, P23-TASK-064, P23-TASK-065, P23-TASK-066, P23-TASK-067, P23-TASK-068, P23-TASK-069, P23-TASK-070, P23-TASK-071, P23-TASK-072, P23-TASK-073, P23-TASK-074, P23-TASK-075, P23-TASK-076, P23-TASK-077, P23-TASK-078, P23-TASK-079, P23-TASK-080, P23-TASK-081, P23-TASK-082, P23-TASK-083, P23-TASK-084, P23-TASK-085, P23-TASK-086, P23-TASK-087, P23-TASK-088, P23-TASK-089, P23-TASK-090, P23-TASK-091, P23-TASK-092, P23-TASK-093, P23-TASK-094, P23-TASK-095, P23-TASK-096, P23-TASK-097, P23-TASK-098, P23-TASK-099, P23-TASK-100, P23-TASK-101, P23-TASK-102, P23-TASK-103, P23-TASK-104, P23-TASK-105, P23-TASK-106, P23-TASK-107, P23-TASK-108, P23-TASK-109, P23-TASK-110, P23-TASK-111 | 필수 | :core:content / QA | 선행계약후;공통 schema 충돌은직렬 | P23-UT-003, P23-BT-003, P23-FT-003, P23-CT-003, P23-IT-003 | 실물/데이터검증증거·확정수량·의미적누락점검승인. 미제공/미검증은별도차단상태유지 | 3.17 |

## 3. 전체 구현 순서

아래는선행관계를만족하는한가지 topological order 이다. 같은레벨의 Task 는담당자/공통파일충돌을확인한뒤병렬화할수있다. 이것을한사람이꼭직렬로수행해야하는순서로해석하지않는다.

```text
001. P0-TASK-001 원문 기준선과 충돌 판정 — 계약·Fixture
002. P0-TASK-002 원문 기준선과 충돌 판정 — 핵심 규칙
003. P0-TASK-003 원문 기준선과 충돌 판정 — 저장·연계
004. P0-TASK-004 원문 기준선과 충돌 판정 — UI·호출 경로
005. P0-TASK-005 원문 기준선과 충돌 판정 — Test·리뷰
006. P0-TASK-006 빌드·모듈·기술버전 고정 — 계약·Fixture
007. P0-TASK-007 빌드·모듈·기술버전 고정 — 핵심 규칙
008. P0-TASK-008 빌드·모듈·기술버전 고정 — 저장·연계
009. P0-TASK-009 빌드·모듈·기술버전 고정 — UI·호출 경로
010. P0-TASK-010 빌드·모듈·기술버전 고정 — Test·리뷰
011. P0-TASK-011 공통 타입·명령·오류·이벤트 계약 — 계약·Fixture
012. P0-TASK-012 공통 타입·명령·오류·이벤트 계약 — 핵심 규칙
013. P0-TASK-013 공통 타입·명령·오류·이벤트 계약 — 저장·연계
014. P0-TASK-014 공통 타입·명령·오류·이벤트 계약 — UI·호출 경로
015. P0-TASK-015 공통 타입·명령·오류·이벤트 계약 — Test·리뷰
016. P0-TASK-016 최소 검증 하네스·공통 UI 껍데기 — 계약·Fixture
017. P0-TASK-017 최소 검증 하네스·공통 UI 껍데기 — 핵심 규칙
018. P0-TASK-018 최소 검증 하네스·공통 UI 껍데기 — 저장·연계
019. P0-TASK-019 최소 검증 하네스·공통 UI 껍데기 — UI·호출 경로
020. P0-TASK-020 최소 검증 하네스·공통 UI 껍데기 — Test·리뷰
021. P0-TASK-021 Phase 0 통합 검증·인계 Gate
022. P1-TASK-001 canonical source·Assertion 계약
023. P1-TASK-002 Importer·ID/원문 필드 보존
024. P1-TASK-003 bootstrap→canonical source 변환
025. P1-TASK-004 Phase1 Gradle 경계·Build Spike
026. P1-TASK-005 Importer Test·독립 리뷰
027. P1-TASK-006 bundle schema·hash 계약
028. P1-TASK-007 Validator·canonical hash
029. P1-TASK-008 staging content.db writer
030. P1-TASK-009 CLI·Gradle build entry
031. P1-TASK-010 Content builder Test·독립 리뷰
032. P1-TASK-011 asset schema·path·crop 계약
033. P1-TASK-012 Asset validator/compiler
034. P1-TASK-013 asset row·manifest 산출
035. P1-TASK-014 Android 읽기 전용 AssetResolver
036. P1-TASK-015 Asset Test·독립 리뷰
037. P1-TASK-016 BindingPlan·alias 계약
038. P1-TASK-017 순수 compatibility resolver
039. P1-TASK-018 alias flatten·legacy plan
040. P1-TASK-019 P3/P22/P25 계약 인계
041. P1-TASK-020 Compatibility Test·독립 리뷰
042. P1-TASK-021 Phase 1 통합 검증·인계 Gate
043. P2-TASK-001 단일 작성자 명령 처리 — 계약·Fixture
044. P2-TASK-002 단일 작성자 명령 처리 — 핵심 규칙
045. P2-TASK-003 단일 작성자 명령 처리 — 저장·연계
046. P2-TASK-004 단일 작성자 명령 처리 — UI·호출 경로
047. P2-TASK-005 단일 작성자 명령 처리 — Test·리뷰
048. P2-TASK-006 게임 달력·잔여 밀리초·RNG 스트림 — 계약·Fixture
049. P2-TASK-007 게임 달력·잔여 밀리초·RNG 스트림 — 핵심 규칙
050. P2-TASK-008 게임 달력·잔여 밀리초·RNG 스트림 — 저장·연계
051. P2-TASK-009 게임 달력·잔여 밀리초·RNG 스트림 — UI·호출 경로
052. P2-TASK-010 게임 달력·잔여 밀리초·RNG 스트림 — Test·리뷰
053. P2-TASK-011 예약·점유·자원 선점 — 계약·Fixture
054. P2-TASK-012 예약·점유·자원 선점 — 핵심 규칙
055. P2-TASK-013 예약·점유·자원 선점 — 저장·연계
056. P2-TASK-014 예약·점유·자원 선점 — UI·호출 경로
057. P2-TASK-015 예약·점유·자원 선점 — Test·리뷰
058. P2-TASK-016 이벤트 경계 시간진행·자동중단 — 계약·Fixture
059. P2-TASK-017 이벤트 경계 시간진행·자동중단 — 핵심 규칙
060. P2-TASK-018 이벤트 경계 시간진행·자동중단 — 저장·연계
061. P2-TASK-019 이벤트 경계 시간진행·자동중단 — UI·호출 경로
062. P2-TASK-020 이벤트 경계 시간진행·자동중단 — Test·리뷰
063. P2-TASK-021 세션·생명주기·작업 종료 — 계약·Fixture
064. P2-TASK-022 세션·생명주기·작업 종료 — 핵심 규칙
065. P2-TASK-023 세션·생명주기·작업 종료 — 저장·연계
066. P2-TASK-024 세션·생명주기·작업 종료 — UI·호출 경로
067. P2-TASK-025 세션·생명주기·작업 종료 — Test·리뷰
068. P2-TASK-026 Phase 2 통합 검증·인계 Gate
069. P23-TASK-111 미수록 목표카탈로그 보완·정확수량 승인
070. P3-TASK-001 현재상태 스키마·Dirty 단위 저장 — 계약·Fixture
071. P3-TASK-002 현재상태 스키마·Dirty 단위 저장 — 핵심 규칙
072. P3-TASK-003 현재상태 스키마·Dirty 단위 저장 — 저장·연계
073. P3-TASK-004 현재상태 스키마·Dirty 단위 저장 — UI·호출 경로
074. P3-TASK-005 현재상태 스키마·Dirty 단위 저장 — Test·리뷰
075. P3-TASK-006 복원 가능한 세대·슬롯·불변 청크 — 계약·Fixture
076. P3-TASK-007 복원 가능한 세대·슬롯·불변 청크 — 핵심 규칙
077. P3-TASK-008 복원 가능한 세대·슬롯·불변 청크 — 저장·연계
078. P3-TASK-009 복원 가능한 세대·슬롯·불변 청크 — UI·호출 경로
079. P3-TASK-010 복원 가능한 세대·슬롯·불변 청크 — Test·리뷰
080. P3-TASK-011 장기진행·예약 복구 — 계약·Fixture
081. P3-TASK-012 장기진행·예약 복구 — 핵심 규칙
082. P3-TASK-013 장기진행·예약 복구 — 저장·연계
083. P3-TASK-014 장기진행·예약 복구 — UI·호출 경로
084. P3-TASK-015 장기진행·예약 복구 — Test·리뷰
085. P3-TASK-016 마이그레이션·콘텐츠 호환 — 계약·Fixture
086. P3-TASK-017 마이그레이션·콘텐츠 호환 — 핵심 규칙
087. P3-TASK-018 마이그레이션·콘텐츠 호환 — 저장·연계
088. P3-TASK-019 마이그레이션·콘텐츠 호환 — UI·호출 경로
089. P3-TASK-020 마이그레이션·콘텐츠 호환 — Test·리뷰
090. P3-TASK-021 오프라인 Export·Import·아카이브 보호 — 계약·Fixture
091. P3-TASK-022 오프라인 Export·Import·아카이브 보호 — 핵심 규칙
092. P3-TASK-023 오프라인 Export·Import·아카이브 보호 — 저장·연계
093. P3-TASK-024 오프라인 Export·Import·아카이브 보호 — UI·호출 경로
094. P3-TASK-025 오프라인 Export·Import·아카이브 보호 — Test·리뷰
095. P3-TASK-026 무결성 검사·복구·보존 GC — 계약·Fixture
096. P3-TASK-027 무결성 검사·복구·보존 GC — 핵심 규칙
097. P3-TASK-028 무결성 검사·복구·보존 GC — 저장·연계
098. P3-TASK-029 무결성 검사·복구·보존 GC — UI·호출 경로
099. P3-TASK-030 무결성 검사·복구·보존 GC — Test·리뷰
100. P3-TASK-031 Phase 3 통합 검증·인계 Gate
101. P4-TASK-001 NPC 생성·성별 이름·초상 일괄 확정 — 계약·Fixture
102. P4-TASK-002 NPC 생성·성별 이름·초상 일괄 확정 — 핵심 규칙
103. P4-TASK-003 NPC 생성·성별 이름·초상 일괄 확정 — 저장·연계
104. P4-TASK-004 NPC 생성·성별 이름·초상 일괄 확정 — UI·호출 경로
105. P4-TASK-005 NPC 생성·성별 이름·초상 일괄 확정 — Test·리뷰
106. P4-TASK-006 기본스탯·경험치·성장원장 — 계약·Fixture
107. P4-TASK-007 기본스탯·경험치·성장원장 — 핵심 규칙
108. P4-TASK-008 기본스탯·경험치·성장원장 — 저장·연계
109. P4-TASK-009 기본스탯·경험치·성장원장 — UI·호출 경로
110. P4-TASK-010 기본스탯·경험치·성장원장 — Test·리뷰
111. P4-TASK-011 잠재력·후천변화·숙련 — 계약·Fixture
112. P4-TASK-012 잠재력·후천변화·숙련 — 핵심 규칙
113. P4-TASK-013 잠재력·후천변화·숙련 — 저장·연계
114. P4-TASK-014 잠재력·후천변화·숙련 — UI·호출 경로
115. P4-TASK-015 잠재력·후천변화·숙련 — Test·리뷰
116. P4-TASK-016 클래스 재훈련·스탯 재계산 — 계약·Fixture
117. P4-TASK-017 클래스 재훈련·스탯 재계산 — 핵심 규칙
118. P4-TASK-018 클래스 재훈련·스탯 재계산 — 저장·연계
119. P4-TASK-019 클래스 재훈련·스탯 재계산 — UI·호출 경로
120. P4-TASK-020 클래스 재훈련·스탯 재계산 — Test·리뷰
121. P4-TASK-021 초기 정보 비대칭·인물 조회 계약 — 계약·Fixture
122. P4-TASK-022 초기 정보 비대칭·인물 조회 계약 — 핵심 규칙
123. P4-TASK-023 초기 정보 비대칭·인물 조회 계약 — 저장·연계
124. P4-TASK-024 초기 정보 비대칭·인물 조회 계약 — UI·호출 경로
125. P4-TASK-025 초기 정보 비대칭·인물 조회 계약 — Test·리뷰
126. P4-TASK-026 Phase 4 통합 검증·인계 Gate
127. P23-TASK-109 10,000개 실물 초상·모든 필수자산 검수
128. P23-TASK-110 이름 Pool·문화권·성씨 실제데이터 완성
129. P5-TASK-001 인벤토리·장착·소유권 — 계약·Fixture
130. P5-TASK-002 인벤토리·장착·소유권 — 핵심 규칙
131. P5-TASK-003 인벤토리·장착·소유권 — 저장·연계
132. P5-TASK-004 인벤토리·장착·소유권 — UI·호출 경로
133. P5-TASK-005 인벤토리·장착·소유권 — Test·리뷰
134. P5-TASK-006 스킬·접사·개인 상성 데이터 — 계약·Fixture
135. P5-TASK-007 스킬·접사·개인 상성 데이터 — 핵심 규칙
136. P5-TASK-008 스킬·접사·개인 상성 데이터 — 저장·연계
137. P5-TASK-009 스킬·접사·개인 상성 데이터 — UI·호출 경로
138. P5-TASK-010 스킬·접사·개인 상성 데이터 — Test·리뷰
139. P5-TASK-011 Loadout·프리셋·전술 조건식 — 계약·Fixture
140. P5-TASK-012 Loadout·프리셋·전술 조건식 — 핵심 규칙
141. P5-TASK-013 Loadout·프리셋·전술 조건식 — 저장·연계
142. P5-TASK-014 Loadout·프리셋·전술 조건식 — UI·호출 경로
143. P5-TASK-015 Loadout·프리셋·전술 조건식 — Test·리뷰
144. P5-TASK-016 드롭·보상 예산·타겟파밍 — 계약·Fixture
145. P5-TASK-017 드롭·보상 예산·타겟파밍 — 핵심 규칙
146. P5-TASK-018 드롭·보상 예산·타겟파밍 — 저장·연계
147. P5-TASK-019 드롭·보상 예산·타겟파밍 — UI·호출 경로
148. P5-TASK-020 드롭·보상 예산·타겟파밍 — Test·리뷰
149. P5-TASK-021 Phase 5 통합 검증·인계 Gate
150. P6-TASK-001 이벤트 큐·동시 해결 배치 — 계약·Fixture
151. P6-TASK-002 이벤트 큐·동시 해결 배치 — 핵심 규칙
152. P6-TASK-003 이벤트 큐·동시 해결 배치 — 저장·연계
153. P6-TASK-004 이벤트 큐·동시 해결 배치 — UI·호출 경로
154. P6-TASK-005 이벤트 큐·동시 해결 배치 — Test·리뷰
155. P6-TASK-006 피해·치유·명중·보호막 공식 — 계약·Fixture
156. P6-TASK-007 피해·치유·명중·보호막 공식 — 핵심 규칙
157. P6-TASK-008 피해·치유·명중·보호막 공식 — 저장·연계
158. P6-TASK-009 피해·치유·명중·보호막 공식 — UI·호출 경로
159. P6-TASK-010 피해·치유·명중·보호막 공식 — Test·리뷰
160. P6-TASK-011 액션 상태·자원·발사체 스냅샷 — 계약·Fixture
161. P6-TASK-012 액션 상태·자원·발사체 스냅샷 — 핵심 규칙
162. P6-TASK-013 액션 상태·자원·발사체 스냅샷 — 저장·연계
163. P6-TASK-014 액션 상태·자원·발사체 스냅샷 — UI·호출 경로
164. P6-TASK-015 액션 상태·자원·발사체 스냅샷 — Test·리뷰
165. P6-TASK-016 상태이상·축적·Tick·점감 — 계약·Fixture
166. P6-TASK-017 상태이상·축적·Tick·점감 — 핵심 규칙
167. P6-TASK-018 상태이상·축적·Tick·점감 — 저장·연계
168. P6-TASK-019 상태이상·축적·Tick·점감 — UI·호출 경로
169. P6-TASK-020 상태이상·축적·Tick·점감 — Test·리뷰
170. P6-TASK-021 후퇴·반응·전투 종료 정산 — 계약·Fixture
171. P6-TASK-022 후퇴·반응·전투 종료 정산 — 핵심 규칙
172. P6-TASK-023 후퇴·반응·전투 종료 정산 — 저장·연계
173. P6-TASK-024 후퇴·반응·전투 종료 정산 — UI·호출 경로
174. P6-TASK-025 후퇴·반응·전투 종료 정산 — Test·리뷰
175. P6-TASK-026 재생·로그·전투 버전 일치 — 계약·Fixture
176. P6-TASK-027 재생·로그·전투 버전 일치 — 핵심 규칙
177. P6-TASK-028 재생·로그·전투 버전 일치 — 저장·연계
178. P6-TASK-029 재생·로그·전투 버전 일치 — UI·호출 경로
179. P6-TASK-030 재생·로그·전투 버전 일치 — Test·리뷰
180. P6-TASK-031 Phase 6 통합 검증·인계 Gate
181. P7-TASK-001 몬스터 감지·Utility·역할 AI — 계약·Fixture
182. P7-TASK-002 몬스터 감지·Utility·역할 AI — 핵심 규칙
183. P7-TASK-003 몬스터 감지·Utility·역할 AI — 저장·연계
184. P7-TASK-004 몬스터 감지·Utility·역할 AI — UI·호출 경로
185. P7-TASK-005 몬스터 감지·Utility·역할 AI — Test·리뷰
186. P7-TASK-006 그룹 Blackboard·순찰·학습 — 계약·Fixture
187. P7-TASK-007 그룹 Blackboard·순찰·학습 — 핵심 규칙
188. P7-TASK-008 그룹 Blackboard·순찰·학습 — 저장·연계
189. P7-TASK-009 그룹 Blackboard·순찰·학습 — UI·호출 경로
190. P7-TASK-010 그룹 Blackboard·순찰·학습 — Test·리뷰
191. P7-TASK-011 보스 전조·페이즈·강인도 — 계약·Fixture
192. P7-TASK-012 보스 전조·페이즈·강인도 — 핵심 규칙
193. P7-TASK-013 보스 전조·페이즈·강인도 — 저장·연계
194. P7-TASK-014 보스 전조·페이즈·강인도 — UI·호출 경로
195. P7-TASK-015 보스 전조·페이즈·강인도 — Test·리뷰
196. P7-TASK-016 보스 카탈로그·공략대·웨이브 — 계약·Fixture
197. P7-TASK-017 보스 카탈로그·공략대·웨이브 — 핵심 규칙
198. P7-TASK-018 보스 카탈로그·공략대·웨이브 — 저장·연계
199. P7-TASK-019 보스 카탈로그·공략대·웨이브 — UI·호출 경로
200. P7-TASK-020 보스 카탈로그·공략대·웨이브 — Test·리뷰
201. P7-TASK-021 Phase 7 통합 검증·인계 Gate
202. P8-TASK-001 던전 그래프·열쇠/문 위상 생성 — 계약·Fixture
203. P8-TASK-002 던전 그래프·열쇠/문 위상 생성 — 핵심 규칙
204. P8-TASK-003 던전 그래프·열쇠/문 위상 생성 — 저장·연계
205. P8-TASK-004 던전 그래프·열쇠/문 위상 생성 — UI·호출 경로
206. P8-TASK-005 던전 그래프·열쇠/문 위상 생성 — Test·리뷰
207. P8-TASK-006 지형·구역·위험/보상 예산 — 계약·Fixture
208. P8-TASK-007 지형·구역·위험/보상 예산 — 핵심 규칙
209. P8-TASK-008 지형·구역·위험/보상 예산 — 저장·연계
210. P8-TASK-009 지형·구역·위험/보상 예산 — UI·호출 경로
211. P8-TASK-010 지형·구역·위험/보상 예산 — Test·리뷰
212. P8-TASK-011 몬스터·상자·함정·정복목표 배치 — 계약·Fixture
213. P8-TASK-012 몬스터·상자·함정·정복목표 배치 — 핵심 규칙
214. P8-TASK-013 몬스터·상자·함정·정복목표 배치 — 저장·연계
215. P8-TASK-014 몬스터·상자·함정·정복목표 배치 — UI·호출 경로
216. P8-TASK-015 몬스터·상자·함정·정복목표 배치 — Test·리뷰
217. P8-TASK-016 던전 생명주기·Seed·재방문 — 계약·Fixture
218. P8-TASK-017 던전 생명주기·Seed·재방문 — 핵심 규칙
219. P8-TASK-018 던전 생명주기·Seed·재방문 — 저장·연계
220. P8-TASK-019 던전 생명주기·Seed·재방문 — UI·호출 경로
221. P8-TASK-020 던전 생명주기·Seed·재방문 — Test·리뷰
222. P8-TASK-021 Phase 8 통합 검증·인계 Gate
223. P9-TASK-001 MUD 이동·조사·점진 공개 — 계약·Fixture
224. P9-TASK-002 MUD 이동·조사·점진 공개 — 핵심 규칙
225. P9-TASK-003 MUD 이동·조사·점진 공개 — 저장·연계
226. P9-TASK-004 MUD 이동·조사·점진 공개 — UI·호출 경로
227. P9-TASK-005 MUD 이동·조사·점진 공개 — Test·리뷰
228. P9-TASK-006 지도·주석·안전 복귀 경로 — 계약·Fixture
229. P9-TASK-007 지도·주석·안전 복귀 경로 — 핵심 규칙
230. P9-TASK-008 지도·주석·안전 복귀 경로 — 저장·연계
231. P9-TASK-009 지도·주석·안전 복귀 경로 — UI·호출 경로
232. P9-TASK-010 지도·주석·안전 복귀 경로 — Test·리뷰
233. P9-TASK-011 야영·보급·경계·응급처치 — 계약·Fixture
234. P9-TASK-012 야영·보급·경계·응급처치 — 핵심 규칙
235. P9-TASK-013 야영·보급·경계·응급처치 — 저장·연계
236. P9-TASK-014 야영·보급·경계·응급처치 — UI·호출 경로
237. P9-TASK-015 야영·보급·경계·응급처치 — Test·리뷰
238. P9-TASK-016 패배·구조·SAFE_RECOVERY — 계약·Fixture
239. P9-TASK-017 패배·구조·SAFE_RECOVERY — 핵심 규칙
240. P9-TASK-018 패배·구조·SAFE_RECOVERY — 저장·연계
241. P9-TASK-019 패배·구조·SAFE_RECOVERY — UI·호출 경로
242. P9-TASK-020 패배·구조·SAFE_RECOVERY — Test·리뷰
243. P9-TASK-021 정복·보상·첫 완결 플레이 루프 — 계약·Fixture
244. P9-TASK-022 정복·보상·첫 완결 플레이 루프 — 핵심 규칙
245. P9-TASK-023 정복·보상·첫 완결 플레이 루프 — 저장·연계
246. P9-TASK-024 정복·보상·첫 완결 플레이 루프 — UI·호출 경로
247. P9-TASK-025 정복·보상·첫 완결 플레이 루프 — Test·리뷰
248. P9-TASK-026 Phase 9 통합 검증·인계 Gate
249. P10-TASK-001 도시 이동·시설·영업·대기 — 계약·Fixture
250. P10-TASK-002 도시 이동·시설·영업·대기 — 핵심 규칙
251. P10-TASK-003 도시 이동·시설·영업·대기 — 저장·연계
252. P10-TASK-004 도시 이동·시설·영업·대기 — UI·호출 경로
253. P10-TASK-005 도시 이동·시설·영업·대기 — Test·리뷰
254. P10-TASK-006 부상·질병·치료·재활 — 계약·Fixture
255. P10-TASK-007 부상·질병·치료·재활 — 핵심 규칙
256. P10-TASK-008 부상·질병·치료·재활 — 저장·연계
257. P10-TASK-009 부상·질병·치료·재활 — UI·호출 경로
258. P10-TASK-010 부상·질병·치료·재활 — Test·리뷰
259. P10-TASK-011 주거·숙식·유지비·시설 확장 — 계약·Fixture
260. P10-TASK-012 주거·숙식·유지비·시설 확장 — 핵심 규칙
261. P10-TASK-013 주거·숙식·유지비·시설 확장 — 저장·연계
262. P10-TASK-014 주거·숙식·유지비·시설 확장 — UI·호출 경로
263. P10-TASK-015 주거·숙식·유지비·시설 확장 — Test·리뷰
264. P10-TASK-016 귀환 정비·생활 프리셋·도시 행사 — 계약·Fixture
265. P10-TASK-017 귀환 정비·생활 프리셋·도시 행사 — 핵심 규칙
266. P10-TASK-018 귀환 정비·생활 프리셋·도시 행사 — 저장·연계
267. P10-TASK-019 귀환 정비·생활 프리셋·도시 행사 — UI·호출 경로
268. P10-TASK-020 귀환 정비·생활 프리셋·도시 행사 — Test·리뷰
269. P10-TASK-021 Phase 10 통합 검증·인계 Gate
270. P11-TASK-001 보조 의뢰·생성·수락·정산 — 계약·Fixture
271. P11-TASK-002 보조 의뢰·생성·수락·정산 — 핵심 규칙
272. P11-TASK-003 보조 의뢰·생성·수락·정산 — 저장·연계
273. P11-TASK-004 보조 의뢰·생성·수락·정산 — UI·호출 경로
274. P11-TASK-005 보조 의뢰·생성·수락·정산 — Test·리뷰
275. P11-TASK-006 모집·협상·단기/상시 고용 — 계약·Fixture
276. P11-TASK-007 모집·협상·단기/상시 고용 — 핵심 규칙
277. P11-TASK-008 모집·협상·단기/상시 고용 — 저장·연계
278. P11-TASK-009 모집·협상·단기/상시 고용 — UI·호출 경로
279. P11-TASK-010 모집·협상·단기/상시 고용 — Test·리뷰
280. P11-TASK-011 계약 종료·퇴출·위약금·신뢰 — 계약·Fixture
281. P11-TASK-012 계약 종료·퇴출·위약금·신뢰 — 핵심 규칙
282. P11-TASK-013 계약 종료·퇴출·위약금·신뢰 — 저장·연계
283. P11-TASK-014 계약 종료·퇴출·위약금·신뢰 — UI·호출 경로
284. P11-TASK-015 계약 종료·퇴출·위약금·신뢰 — Test·리뷰
285. P11-TASK-016 선택형 대화·Topic·기억·말투 — 계약·Fixture
286. P11-TASK-017 선택형 대화·Topic·기억·말투 — 핵심 규칙
287. P11-TASK-018 선택형 대화·Topic·기억·말투 — 저장·연계
288. P11-TASK-019 선택형 대화·Topic·기억·말투 — UI·호출 경로
289. P11-TASK-020 선택형 대화·Topic·기억·말투 — Test·리뷰
290. P11-TASK-021 Phase 11 통합 검증·인계 Gate
291. P12-TASK-001 강화 확률·시도 원장·비파괴 — 계약·Fixture
292. P12-TASK-002 강화 확률·시도 원장·비파괴 — 핵심 규칙
293. P12-TASK-003 강화 확률·시도 원장·비파괴 — 저장·연계
294. P12-TASK-004 강화 확률·시도 원장·비파괴 — UI·호출 경로
295. P12-TASK-005 강화 확률·시도 원장·비파괴 — Test·리뷰
296. P12-TASK-006 강화 성장·안정도·각인 슬롯 — 계약·Fixture
297. P12-TASK-007 강화 성장·안정도·각인 슬롯 — 핵심 규칙
298. P12-TASK-008 강화 성장·안정도·각인 슬롯 — 저장·연계
299. P12-TASK-009 강화 성장·안정도·각인 슬롯 — UI·호출 경로
300. P12-TASK-010 강화 성장·안정도·각인 슬롯 — Test·리뷰
301. P12-TASK-011 계승·정련·재각성·유물복원 — 계약·Fixture
302. P12-TASK-012 계승·정련·재각성·유물복원 — 핵심 규칙
303. P12-TASK-013 계승·정련·재각성·유물복원 — 저장·연계
304. P12-TASK-014 계승·정련·재각성·유물복원 — UI·호출 경로
305. P12-TASK-015 계승·정련·재각성·유물복원 — Test·리뷰
306. P12-TASK-016 제작·연금·연구·분해 — 계약·Fixture
307. P12-TASK-017 제작·연금·연구·분해 — 핵심 규칙
308. P12-TASK-018 제작·연금·연구·분해 — 저장·연계
309. P12-TASK-019 제작·연금·연구·분해 — UI·호출 경로
310. P12-TASK-020 제작·연금·연구·분해 — Test·리뷰
311. P12-TASK-021 Phase 12 통합 검증·인계 Gate
312. P13-TASK-001 파티 구성·출전·교대·헌장 — 계약·Fixture
313. P13-TASK-002 파티 구성·출전·교대·헌장 — 핵심 규칙
314. P13-TASK-003 파티 구성·출전·교대·헌장 — 저장·연계
315. P13-TASK-004 파티 구성·출전·교대·헌장 — UI·호출 경로
316. P13-TASK-005 파티 구성·출전·교대·헌장 — Test·리뷰
317. P13-TASK-006 분배·공동자금·투표·공정성 — 계약·Fixture
318. P13-TASK-007 분배·공동자금·투표·공정성 — 핵심 규칙
319. P13-TASK-008 분배·공동자금·투표·공정성 — 저장·연계
320. P13-TASK-009 분배·공동자금·투표·공정성 — UI·호출 경로
321. P13-TASK-010 분배·공동자금·투표·공정성 — Test·리뷰
322. P13-TASK-011 만족·갈등·리더교체·분열·승계 — 계약·Fixture
323. P13-TASK-012 만족·갈등·리더교체·분열·승계 — 핵심 규칙
324. P13-TASK-013 만족·갈등·리더교체·분열·승계 — 저장·연계
325. P13-TASK-014 만족·갈등·리더교체·분열·승계 — UI·호출 경로
326. P13-TASK-015 만족·갈등·리더교체·분열·승계 — Test·리뷰
327. P13-TASK-016 파티 공식 랭킹·연속1위 — 계약·Fixture
328. P13-TASK-017 파티 공식 랭킹·연속1위 — 핵심 규칙
329. P13-TASK-018 파티 공식 랭킹·연속1위 — 저장·연계
330. P13-TASK-019 파티 공식 랭킹·연속1위 — UI·호출 경로
331. P13-TASK-020 파티 공식 랭킹·연속1위 — Test·리뷰
332. P13-TASK-021 Phase 13 통합 검증·인계 Gate
333. P14-TASK-001 도시 시장지수·재고·수요공급 — 계약·Fixture
334. P14-TASK-002 도시 시장지수·재고·수요공급 — 핵심 규칙
335. P14-TASK-003 도시 시장지수·재고·수요공급 — 저장·연계
336. P14-TASK-004 도시 시장지수·재고·수요공급 — UI·호출 경로
337. P14-TASK-005 도시 시장지수·재고·수요공급 — Test·리뷰
338. P14-TASK-006 구매·판매·흥정·경매 — 계약·Fixture
339. P14-TASK-007 구매·판매·흥정·경매 — 핵심 규칙
340. P14-TASK-008 구매·판매·흥정·경매 — 저장·연계
341. P14-TASK-009 구매·판매·흥정·경매 — UI·호출 경로
342. P14-TASK-010 구매·판매·흥정·경매 — Test·리뷰
343. P14-TASK-011 장비 대여·회수·손상·보험 — 계약·Fixture
344. P14-TASK-012 장비 대여·회수·손상·보험 — 핵심 규칙
345. P14-TASK-013 장비 대여·회수·손상·보험 — 저장·연계
346. P14-TASK-014 장비 대여·회수·손상·보험 — UI·호출 경로
347. P14-TASK-015 장비 대여·회수·손상·보험 — Test·리뷰
348. P14-TASK-016 창고·운송·원정보급·분실복구 — 계약·Fixture
349. P14-TASK-017 창고·운송·원정보급·분실복구 — 핵심 규칙
350. P14-TASK-018 창고·운송·원정보급·분실복구 — 저장·연계
351. P14-TASK-019 창고·운송·원정보급·분실복구 — UI·호출 경로
352. P14-TASK-020 창고·운송·원정보급·분실복구 — Test·리뷰
353. P14-TASK-021 Phase 14 통합 검증·인계 Gate
354. P15-TASK-001 지식·관측·소문·정보 공개 — 계약·Fixture
355. P15-TASK-002 지식·관측·소문·정보 공개 — 핵심 규칙
356. P15-TASK-003 지식·관측·소문·정보 공개 — 저장·연계
357. P15-TASK-004 지식·관측·소문·정보 공개 — UI·호출 경로
358. P15-TASK-005 지식·관측·소문·정보 공개 — Test·리뷰
359. P15-TASK-006 평판·법률·계약 신뢰·지역기여 — 계약·Fixture
360. P15-TASK-007 평판·법률·계약 신뢰·지역기여 — 핵심 규칙
361. P15-TASK-008 평판·법률·계약 신뢰·지역기여 — 저장·연계
362. P15-TASK-009 평판·법률·계약 신뢰·지역기여 — UI·호출 경로
363. P15-TASK-010 평판·법률·계약 신뢰·지역기여 — Test·리뷰
364. P15-TASK-011 다축 관계·기억·호흡·연애 — 계약·Fixture
365. P15-TASK-012 다축 관계·기억·호흡·연애 — 핵심 규칙
366. P15-TASK-013 다축 관계·기억·호흡·연애 — 저장·연계
367. P15-TASK-014 다축 관계·기억·호흡·연애 — UI·호출 경로
368. P15-TASK-015 다축 관계·기억·호흡·연애 — Test·리뷰
369. P15-TASK-016 성격·특성·매력·개인 목표 — 계약·Fixture
370. P15-TASK-017 성격·특성·매력·개인 목표 — 핵심 규칙
371. P15-TASK-018 성격·특성·매력·개인 목표 — 저장·연계
372. P15-TASK-019 성격·특성·매력·개인 목표 — UI·호출 경로
373. P15-TASK-020 성격·특성·매력·개인 목표 — Test·리뷰
374. P15-TASK-021 Phase 15 통합 검증·인계 Gate
375. P16-TASK-001 길드 가입·직위·승계·권한 — 계약·Fixture
376. P16-TASK-002 길드 가입·직위·승계·권한 — 핵심 규칙
377. P16-TASK-003 길드 가입·직위·승계·권한 — 저장·연계
378. P16-TASK-004 길드 가입·직위·승계·권한 — UI·호출 경로
379. P16-TASK-005 길드 가입·직위·승계·권한 — Test·리뷰
380. P16-TASK-006 길드 재정·시설·인재·간부 운영 — 계약·Fixture
381. P16-TASK-007 길드 재정·시설·인재·간부 운영 — 핵심 규칙
382. P16-TASK-008 길드 재정·시설·인재·간부 운영 — 저장·연계
383. P16-TASK-009 길드 재정·시설·인재·간부 운영 — UI·호출 경로
384. P16-TASK-010 길드 재정·시설·인재·간부 운영 — Test·리뷰
385. P16-TASK-011 파벌·정책·정당성·길드 공략대 — 계약·Fixture
386. P16-TASK-012 파벌·정책·정당성·길드 공략대 — 핵심 규칙
387. P16-TASK-013 파벌·정책·정당성·길드 공략대 — 저장·연계
388. P16-TASK-014 파벌·정책·정당성·길드 공략대 — UI·호출 경로
389. P16-TASK-015 파벌·정책·정당성·길드 공략대 — Test·리뷰
390. P16-TASK-016 길드 공식10000점·일마감·기여 — 계약·Fixture
391. P16-TASK-017 길드 공식10000점·일마감·기여 — 핵심 규칙
392. P16-TASK-018 길드 공식10000점·일마감·기여 — 저장·연계
393. P16-TASK-019 길드 공식10000점·일마감·기여 — UI·호출 경로
394. P16-TASK-020 길드 공식10000점·일마감·기여 — Test·리뷰
395. P16-TASK-021 Phase 16 통합 검증·인계 Gate
396. P17-TASK-001 NPC 목표·행동 Utility·경제 의사결정 — 계약·Fixture
397. P17-TASK-002 NPC 목표·행동 Utility·경제 의사결정 — 핵심 규칙
398. P17-TASK-003 NPC 목표·행동 Utility·경제 의사결정 — 저장·연계
399. P17-TASK-004 NPC 목표·행동 Utility·경제 의사결정 — UI·호출 경로
400. P17-TASK-005 NPC 목표·행동 Utility·경제 의사결정 — Test·리뷰
401. P17-TASK-006 상세/축약 시뮬레이션·승격·강등 — 계약·Fixture
402. P17-TASK-007 상세/축약 시뮬레이션·승격·강등 — 핵심 규칙
403. P17-TASK-008 상세/축약 시뮬레이션·승격·강등 — 저장·연계
404. P17-TASK-009 상세/축약 시뮬레이션·승격·강등 — UI·호출 경로
405. P17-TASK-010 상세/축약 시뮬레이션·승격·강등 — Test·리뷰
406. P17-TASK-011 용병 유입·은퇴·복귀·직업전환 — 계약·Fixture
407. P17-TASK-012 용병 유입·은퇴·복귀·직업전환 — 핵심 규칙
408. P17-TASK-013 용병 유입·은퇴·복귀·직업전환 — 저장·연계
409. P17-TASK-014 용병 유입·은퇴·복귀·직업전환 — UI·호출 경로
410. P17-TASK-015 용병 유입·은퇴·복귀·직업전환 — Test·리뷰
411. P17-TASK-016 장기 정체성·압축·사회 순환 검증 — 계약·Fixture
412. P17-TASK-017 장기 정체성·압축·사회 순환 검증 — 핵심 규칙
413. P17-TASK-018 장기 정체성·압축·사회 순환 검증 — 저장·연계
414. P17-TASK-019 장기 정체성·압축·사회 순환 검증 — UI·호출 경로
415. P17-TASK-020 장기 정체성·압축·사회 순환 검증 — Test·리뷰
416. P17-TASK-021 Phase 17 통합 검증·인계 Gate
417. P18-TASK-001 가족·출생·입양·성장·교육 — 계약·Fixture
418. P18-TASK-002 가족·출생·입양·성장·교육 — 핵심 규칙
419. P18-TASK-003 가족·출생·입양·성장·교육 — 저장·연계
420. P18-TASK-004 가족·출생·입양·성장·교육 — UI·호출 경로
421. P18-TASK-005 가족·출생·입양·성장·교육 — Test·리뷰
422. P18-TASK-006 후계자 후보·의사·지정·부재 안전장치 — 계약·Fixture
423. P18-TASK-007 후계자 후보·의사·지정·부재 안전장치 — 핵심 규칙
424. P18-TASK-008 후계자 후보·의사·지정·부재 안전장치 — 저장·연계
425. P18-TASK-009 후계자 후보·의사·지정·부재 안전장치 — UI·호출 경로
426. P18-TASK-010 후계자 후보·의사·지정·부재 안전장치 — Test·리뷰
427. P18-TASK-011 원자적 세대 교체·자산·증표 유지 — 계약·Fixture
428. P18-TASK-012 원자적 세대 교체·자산·증표 유지 — 핵심 규칙
429. P18-TASK-013 원자적 세대 교체·자산·증표 유지 — 저장·연계
430. P18-TASK-014 원자적 세대 교체·자산·증표 유지 — UI·호출 경로
431. P18-TASK-015 원자적 세대 교체·자산·증표 유지 — Test·리뷰
432. P18-TASK-016 가문 목표·유물·세대 기여·기록 UI — 계약·Fixture
433. P18-TASK-017 가문 목표·유물·세대 기여·기록 UI — 핵심 규칙
434. P18-TASK-018 가문 목표·유물·세대 기여·기록 UI — 저장·연계
435. P18-TASK-019 가문 목표·유물·세대 기여·기록 UI — UI·호출 경로
436. P18-TASK-020 가문 목표·유물·세대 기여·기록 UI — Test·리뷰
437. P18-TASK-021 Phase 18 통합 검증·인계 Gate
438. P19-TASK-001 NPC 사건·등장인물·자동 해결 — 계약·Fixture
439. P19-TASK-002 NPC 사건·등장인물·자동 해결 — 핵심 규칙
440. P19-TASK-003 NPC 사건·등장인물·자동 해결 — 저장·연계
441. P19-TASK-004 NPC 사건·등장인물·자동 해결 — UI·호출 경로
442. P19-TASK-005 NPC 사건·등장인물·자동 해결 — Test·리뷰
443. P19-TASK-006 연쇄 사건·조건·쿨다운·대안 경로 — 계약·Fixture
444. P19-TASK-007 연쇄 사건·조건·쿨다운·대안 경로 — 핵심 규칙
445. P19-TASK-008 연쇄 사건·조건·쿨다운·대안 경로 — 저장·연계
446. P19-TASK-009 연쇄 사건·조건·쿨다운·대안 경로 — UI·호출 경로
447. P19-TASK-010 연쇄 사건·조건·쿨다운·대안 경로 — Test·리뷰
448. P19-TASK-011 AdventureDirector·장기 목표·전설화 — 계약·Fixture
449. P19-TASK-012 AdventureDirector·장기 목표·전설화 — 핵심 규칙
450. P19-TASK-013 AdventureDirector·장기 목표·전설화 — 저장·연계
451. P19-TASK-014 AdventureDirector·장기 목표·전설화 — UI·호출 경로
452. P19-TASK-015 AdventureDirector·장기 목표·전설화 — Test·리뷰
453. P19-TASK-016 전술 실험실·전략 회의·행동 자동화 — 계약·Fixture
454. P19-TASK-017 전술 실험실·전략 회의·행동 자동화 — 핵심 규칙
455. P19-TASK-018 전술 실험실·전략 회의·행동 자동화 — 저장·연계
456. P19-TASK-019 전술 실험실·전략 회의·행동 자동화 — UI·호출 경로
457. P19-TASK-020 전술 실험실·전략 회의·행동 자동화 — Test·리뷰
458. P19-TASK-021 Phase 19 통합 검증·인계 Gate
459. P20-TASK-001 균열 탐사·7핵 봉인·발생원 차단 — 계약·Fixture
460. P20-TASK-002 균열 탐사·7핵 봉인·발생원 차단 — 핵심 규칙
461. P20-TASK-003 균열 탐사·7핵 봉인·발생원 차단 — 저장·연계
462. P20-TASK-004 균열 탐사·7핵 봉인·발생원 차단 — UI·호출 경로
463. P20-TASK-005 균열 탐사·7핵 봉인·발생원 차단 — Test·리뷰
464. P20-TASK-006 악마 전쟁·잔존세력·90일 종전 — 계약·Fixture
465. P20-TASK-007 악마 전쟁·잔존세력·90일 종전 — 핵심 규칙
466. P20-TASK-008 악마 전쟁·잔존세력·90일 종전 — 저장·연계
467. P20-TASK-009 악마 전쟁·잔존세력·90일 종전 — UI·호출 경로
468. P20-TASK-010 악마 전쟁·잔존세력·90일 종전 — Test·리뷰
469. P20-TASK-011 영구증표·랭킹·잔존던전·귀환 판정 — 계약·Fixture
470. P20-TASK-012 영구증표·랭킹·잔존던전·귀환 판정 — 핵심 규칙
471. P20-TASK-013 영구증표·랭킹·잔존던전·귀환 판정 — 저장·연계
472. P20-TASK-014 영구증표·랭킹·잔존던전·귀환 판정 — UI·호출 경로
473. P20-TASK-015 영구증표·랭킹·잔존던전·귀환 판정 — Test·리뷰
474. P20-TASK-016 귀환·잔류·후일담·캠페인 종료 — 계약·Fixture
475. P20-TASK-017 귀환·잔류·후일담·캠페인 종료 — 핵심 규칙
476. P20-TASK-018 귀환·잔류·후일담·캠페인 종료 — 저장·연계
477. P20-TASK-019 귀환·잔류·후일담·캠페인 종료 — UI·호출 경로
478. P20-TASK-020 귀환·잔류·후일담·캠페인 종료 — Test·리뷰
479. P20-TASK-021 Phase 20 통합 검증·인계 Gate
480. P21-TASK-001 용병·장비·파티·세계 연대기 — 계약·Fixture
481. P21-TASK-002 용병·장비·파티·세계 연대기 — 핵심 규칙
482. P21-TASK-003 용병·장비·파티·세계 연대기 — 저장·연계
483. P21-TASK-004 용병·장비·파티·세계 연대기 — UI·호출 경로
484. P21-TASK-005 용병·장비·파티·세계 연대기 — Test·리뷰
485. P21-TASK-006 일월연 통계·기여·추세·랭킹 이력 — 계약·Fixture
486. P21-TASK-007 일월연 통계·기여·추세·랭킹 이력 — 핵심 규칙
487. P21-TASK-008 일월연 통계·기여·추세·랭킹 이력 — 저장·연계
488. P21-TASK-009 일월연 통계·기여·추세·랭킹 이력 — UI·호출 경로
489. P21-TASK-010 일월연 통계·기여·추세·랭킹 이력 — Test·리뷰
490. P21-TASK-011 공개정보 검색·필터·페이지·북마크 — 계약·Fixture
491. P21-TASK-012 공개정보 검색·필터·페이지·북마크 — 핵심 규칙
492. P21-TASK-013 공개정보 검색·필터·페이지·북마크 — 저장·연계
493. P21-TASK-014 공개정보 검색·필터·페이지·북마크 — UI·호출 경로
494. P21-TASK-015 공개정보 검색·필터·페이지·북마크 — Test·리뷰
495. P21-TASK-016 보존·압축·뉴스·정보 알림 — 계약·Fixture
496. P21-TASK-017 보존·압축·뉴스·정보 알림 — 핵심 규칙
497. P21-TASK-018 보존·압축·뉴스·정보 알림 — 저장·연계
498. P21-TASK-019 보존·압축·뉴스·정보 알림 — UI·호출 경로
499. P21-TASK-020 보존·압축·뉴스·정보 알림 — Test·리뷰
500. P21-TASK-021 Phase 21 통합 검증·인계 Gate
501. P22-TASK-001 정보구조·화면 계약·Navigation — 계약·Fixture
502. P22-TASK-002 정보구조·화면 계약·Navigation — 핵심 규칙
503. P22-TASK-003 정보구조·화면 계약·Navigation — 저장·연계
504. P22-TASK-004 정보구조·화면 계약·Navigation — UI·호출 경로
505. P22-TASK-005 정보구조·화면 계약·Navigation — Test·리뷰
506. P22-TASK-006 디자인 토큰·컴포넌트·이미지 — 계약·Fixture
507. P22-TASK-007 디자인 토큰·컴포넌트·이미지 — 핵심 규칙
508. P22-TASK-008 디자인 토큰·컴포넌트·이미지 — 저장·연계
509. P22-TASK-009 디자인 토큰·컴포넌트·이미지 — UI·호출 경로
510. P22-TASK-010 디자인 토큰·컴포넌트·이미지 — Test·리뷰
511. P22-TASK-011 Adaptive·큰글자·TalkBack·터치 — 계약·Fixture
512. P22-TASK-012 Adaptive·큰글자·TalkBack·터치 — 핵심 규칙
513. P22-TASK-013 Adaptive·큰글자·TalkBack·터치 — 저장·연계
514. P22-TASK-014 Adaptive·큰글자·TalkBack·터치 — UI·호출 경로
515. P22-TASK-015 Adaptive·큰글자·TalkBack·터치 — Test·리뷰
516. P22-TASK-016 화면 생명주기·일회성 효과·유저 여정 — 계약·Fixture
517. P22-TASK-017 화면 생명주기·일회성 효과·유저 여정 — 핵심 규칙
518. P22-TASK-018 화면 생명주기·일회성 효과·유저 여정 — 저장·연계
519. P22-TASK-019 화면 생명주기·일회성 효과·유저 여정 — UI·호출 경로
520. P22-TASK-020 화면 생명주기·일회성 효과·유저 여정 — Test·리뷰
521. P22-TASK-021 Phase 22 통합 검증·인계 Gate
522. P23-TASK-001 Validation Center·공통 검사 실행 — 계약·Fixture
523. P23-TASK-002 Validation Center·공통 검사 실행 — 핵심 규칙
524. P23-TASK-003 Validation Center·공통 검사 실행 — 저장·연계
525. P23-TASK-004 Validation Center·공통 검사 실행 — UI·호출 경로
526. P23-TASK-005 Validation Center·공통 검사 실행 — Test·리뷰
527. P23-TASK-006 불변식·속성·퍼즈·장기 월드 — 계약·Fixture
528. P23-TASK-007 불변식·속성·퍼즈·장기 월드 — 핵심 규칙
529. P23-TASK-008 불변식·속성·퍼즈·장기 월드 — 저장·연계
530. P23-TASK-009 불변식·속성·퍼즈·장기 월드 — UI·호출 경로
531. P23-TASK-010 불변식·속성·퍼즈·장기 월드 — Test·리뷰
532. P23-TASK-011 확률·수치·드롭·경제 밸런스 비교 — 계약·Fixture
533. P23-TASK-012 확률·수치·드롭·경제 밸런스 비교 — 핵심 규칙
534. P23-TASK-013 확률·수치·드롭·경제 밸런스 비교 — 저장·연계
535. P23-TASK-014 확률·수치·드롭·경제 밸런스 비교 — UI·호출 경로
536. P23-TASK-015 확률·수치·드롭·경제 밸런스 비교 — Test·리뷰
537. P23-TASK-016 재현 패키지·리뷰·결함·승인 — 계약·Fixture
538. P23-TASK-017 재현 패키지·리뷰·결함·승인 — 핵심 규칙
539. P23-TASK-018 재현 패키지·리뷰·결함·승인 — 저장·연계
540. P23-TASK-019 재현 패키지·리뷰·결함·승인 — UI·호출 경로
541. P23-TASK-020 재현 패키지·리뷰·결함·승인 — Test·리뷰
542. P23-TASK-022 Full 콘텐츠 ACC ACC-0001~ACC-0050 검수
543. P23-TASK-023 Full 콘텐츠 ACC ACC-0051~ACC-0100 검수
544. P23-TASK-024 Full 콘텐츠 ACC ACC-0101~ACC-0150 검수
545. P23-TASK-025 Full 콘텐츠 ACC ACC-0151~ACC-0200 검수
546. P23-TASK-026 Full 콘텐츠 ACC ACC-0201~ACC-0250 검수
547. P23-TASK-027 Full 콘텐츠 ACC ACC-0251~ACC-0300 검수
548. P23-TASK-028 Full 콘텐츠 ACC ACC-0301~ACC-0350 검수
549. P23-TASK-029 Full 콘텐츠 ACC ACC-0351~ACC-0400 검수
550. P23-TASK-030 Full 콘텐츠 ACC ACC-0401~ACC-0420 검수
551. P23-TASK-031 Full 콘텐츠 ARM ARM-0001~ARM-0050 검수
552. P23-TASK-032 Full 콘텐츠 ARM ARM-0051~ARM-0100 검수
553. P23-TASK-033 Full 콘텐츠 ARM ARM-0101~ARM-0150 검수
554. P23-TASK-034 Full 콘텐츠 ARM ARM-0151~ARM-0200 검수
555. P23-TASK-035 Full 콘텐츠 ARM ARM-0201~ARM-0250 검수
556. P23-TASK-036 Full 콘텐츠 ARM ARM-0251~ARM-0300 검수
557. P23-TASK-037 Full 콘텐츠 ARM ARM-0301~ARM-0350 검수
558. P23-TASK-038 Full 콘텐츠 ARM ARM-0351~ARM-0400 검수
559. P23-TASK-039 Full 콘텐츠 ARM ARM-0401~ARM-0450 검수
560. P23-TASK-040 Full 콘텐츠 ARM ARM-0451~ARM-0480 검수
561. P23-TASK-041 Full 콘텐츠 BOS BOS-001~BOS-025 검수
562. P23-TASK-042 Full 콘텐츠 BOS BOS-026~BOS-050 검수
563. P23-TASK-043 Full 콘텐츠 BOS BOS-051~BOS-075 검수
564. P23-TASK-044 Full 콘텐츠 BOS BOS-076~BOS-100 검수
565. P23-TASK-045 Full 콘텐츠 CHAIN CHAIN-001~CHAIN-025 검수
566. P23-TASK-046 Full 콘텐츠 CHAIN CHAIN-026~CHAIN-050 검수
567. P23-TASK-047 Full 콘텐츠 CHAIN CHAIN-051~CHAIN-060 검수
568. P23-TASK-048 Full 콘텐츠 CTR CTR-001~CTR-050 검수
569. P23-TASK-049 Full 콘텐츠 CTR CTR-051~CTR-080 검수
570. P23-TASK-050 Full 콘텐츠 DNG-EVT DNG-EVT-001~DNG-EVT-050 검수
571. P23-TASK-051 Full 콘텐츠 DNG-EVT DNG-EVT-051~DNG-EVT-100 검수
572. P23-TASK-052 Full 콘텐츠 DNG-EVT DNG-EVT-101~DNG-EVT-150 검수
573. P23-TASK-053 Full 콘텐츠 DNG-EVT DNG-EVT-151~DNG-EVT-200 검수
574. P23-TASK-054 Full 콘텐츠 DNG-EVT DNG-EVT-201~DNG-EVT-204 검수
575. P23-TASK-055 Full 콘텐츠 EPRE EPRE-001~EPRE-050 검수
576. P23-TASK-056 Full 콘텐츠 EPRE EPRE-051~EPRE-100 검수
577. P23-TASK-057 Full 콘텐츠 EPRE EPRE-101~EPRE-120 검수
578. P23-TASK-058 Full 콘텐츠 ESUF ESUF-001~ESUF-050 검수
579. P23-TASK-059 Full 콘텐츠 ESUF ESUF-051~ESUF-100 검수
580. P23-TASK-060 Full 콘텐츠 ESUF ESUF-101~ESUF-120 검수
581. P23-TASK-061 Full 콘텐츠 EVT EVT-001~EVT-050 검수
582. P23-TASK-062 Full 콘텐츠 EVT EVT-051~EVT-100 검수
583. P23-TASK-063 Full 콘텐츠 EVT EVT-101~EVT-130 검수
584. P23-TASK-064 Full 콘텐츠 ITM ITM-0001~ITM-0050 검수
585. P23-TASK-065 Full 콘텐츠 ITM ITM-0051~ITM-0100 검수
586. P23-TASK-066 Full 콘텐츠 ITM ITM-0101~ITM-0150 검수
587. P23-TASK-067 Full 콘텐츠 ITM ITM-0151~ITM-0180 검수
588. P23-TASK-068 Full 콘텐츠 LEG LEG-001~LEG-024 검수
589. P23-TASK-069 Full 콘텐츠 MON MON-0001~MON-0050 검수
590. P23-TASK-070 Full 콘텐츠 MON MON-0051~MON-0100 검수
591. P23-TASK-071 Full 콘텐츠 MON MON-0101~MON-0150 검수
592. P23-TASK-072 Full 콘텐츠 MON MON-0151~MON-0200 검수
593. P23-TASK-073 Full 콘텐츠 MON MON-0201~MON-0250 검수
594. P23-TASK-074 Full 콘텐츠 MON MON-0251~MON-0300 검수
595. P23-TASK-075 Full 콘텐츠 MPRE MPRE-001~MPRE-050 검수
596. P23-TASK-076 Full 콘텐츠 MPRE MPRE-051~MPRE-100 검수
597. P23-TASK-077 Full 콘텐츠 MPRE MPRE-101~MPRE-120 검수
598. P23-TASK-078 Full 콘텐츠 MSUF MSUF-001~MSUF-050 검수
599. P23-TASK-079 Full 콘텐츠 MSUF MSUF-051~MSUF-100 검수
600. P23-TASK-080 Full 콘텐츠 MSUF MSUF-101~MSUF-120 검수
601. P23-TASK-081 Full 콘텐츠 REL REL-001~REL-050 검수
602. P23-TASK-082 Full 콘텐츠 SET SET-001~SET-050 검수
603. P23-TASK-083 Full 콘텐츠 SET SET-051~SET-060 검수
604. P23-TASK-084 Full 콘텐츠 SKL SKL-0001~SKL-0025 검수
605. P23-TASK-085 Full 콘텐츠 SKL SKL-0026~SKL-0050 검수
606. P23-TASK-086 Full 콘텐츠 SKL SKL-0051~SKL-0075 검수
607. P23-TASK-087 Full 콘텐츠 SKL SKL-0076~SKL-0100 검수
608. P23-TASK-088 Full 콘텐츠 SKL SKL-0101~SKL-0125 검수
609. P23-TASK-089 Full 콘텐츠 SKL SKL-0126~SKL-0150 검수
610. P23-TASK-090 Full 콘텐츠 SKL SKL-0151~SKL-0175 검수
611. P23-TASK-091 Full 콘텐츠 SKL SKL-0176~SKL-0200 검수
612. P23-TASK-092 Full 콘텐츠 SKL SKL-0201~SKL-0225 검수
613. P23-TASK-093 Full 콘텐츠 SKL SKL-0226~SKL-0250 검수
614. P23-TASK-094 Full 콘텐츠 SKL SKL-0251~SKL-0275 검수
615. P23-TASK-095 Full 콘텐츠 SKL SKL-0276~SKL-0300 검수
616. P23-TASK-096 Full 콘텐츠 SPRE SPRE-001~SPRE-050 검수
617. P23-TASK-097 Full 콘텐츠 SPRE SPRE-051~SPRE-080 검수
618. P23-TASK-098 Full 콘텐츠 SSUF SSUF-001~SSUF-050 검수
619. P23-TASK-099 Full 콘텐츠 SSUF SSUF-051~SSUF-080 검수
620. P23-TASK-100 Full 콘텐츠 WPN WPN-0001~WPN-0050 검수
621. P23-TASK-101 Full 콘텐츠 WPN WPN-0051~WPN-0100 검수
622. P23-TASK-102 Full 콘텐츠 WPN WPN-0101~WPN-0150 검수
623. P23-TASK-103 Full 콘텐츠 WPN WPN-0151~WPN-0200 검수
624. P23-TASK-104 Full 콘텐츠 WPN WPN-0201~WPN-0250 검수
625. P23-TASK-105 Full 콘텐츠 WPN WPN-0251~WPN-0300 검수
626. P23-TASK-106 Full 콘텐츠 WPN WPN-0301~WPN-0350 검수
627. P23-TASK-107 Full 콘텐츠 WPN WPN-0351~WPN-0400 검수
628. P23-TASK-108 Full 콘텐츠 WPN WPN-0401~WPN-0420 검수
629. P23-TASK-112 Full 전체콘텐츠 조립·원문 세부assertion 감사
630. P23-TASK-021 Phase 23 통합 검증·인계 Gate
631. P24-TASK-001 실측 성능·이미지·목록·DB 예산 — 계약·Fixture
632. P24-TASK-002 실측 성능·이미지·목록·DB 예산 — 핵심 규칙
633. P24-TASK-003 실측 성능·이미지·목록·DB 예산 — 저장·연계
634. P24-TASK-004 실측 성능·이미지·목록·DB 예산 — UI·호출 경로
635. P24-TASK-005 실측 성능·이미지·목록·DB 예산 — Test·리뷰
636. P24-TASK-006 누수·취소·배터리·앱 중단 — 계약·Fixture
637. P24-TASK-007 누수·취소·배터리·앱 중단 — 핵심 규칙
638. P24-TASK-008 누수·취소·배터리·앱 중단 — 저장·연계
639. P24-TASK-009 누수·취소·배터리·앱 중단 — UI·호출 경로
640. P24-TASK-010 누수·취소·배터리·앱 중단 — Test·리뷰
641. P24-TASK-011 저장공간·장기 압축·실패 격리 — 계약·Fixture
642. P24-TASK-012 저장공간·장기 압축·실패 격리 — 핵심 규칙
643. P24-TASK-013 저장공간·장기 압축·실패 격리 — 저장·연계
644. P24-TASK-014 저장공간·장기 압축·실패 격리 — UI·호출 경로
645. P24-TASK-015 저장공간·장기 압축·실패 격리 — Test·리뷰
646. P24-TASK-016 최적화 동치·인덱스·R8·프로필 — 계약·Fixture
647. P24-TASK-017 최적화 동치·인덱스·R8·프로필 — 핵심 규칙
648. P24-TASK-018 최적화 동치·인덱스·R8·프로필 — 저장·연계
649. P24-TASK-019 최적화 동치·인덱스·R8·프로필 — UI·호출 경로
650. P24-TASK-020 최적화 동치·인덱스·R8·프로필 — Test·리뷰
651. P24-TASK-021 Phase 24 통합 검증·인계 Gate
652. P25-TASK-001 전체 End-to-End·회귀·출시 범위 검수 — 계약·Fixture
653. P25-TASK-002 전체 End-to-End·회귀·출시 범위 검수 — 핵심 규칙
654. P25-TASK-003 전체 End-to-End·회귀·출시 범위 검수 — 저장·연계
655. P25-TASK-004 전체 End-to-End·회귀·출시 범위 검수 — UI·호출 경로
656. P25-TASK-005 전체 End-to-End·회귀·출시 범위 검수 — Test·리뷰
657. P25-TASK-006 업그레이드·마이그레이션·다운그레이드 — 계약·Fixture
658. P25-TASK-007 업그레이드·마이그레이션·다운그레이드 — 핵심 규칙
659. P25-TASK-008 업그레이드·마이그레이션·다운그레이드 — 저장·연계
660. P25-TASK-009 업그레이드·마이그레이션·다운그레이드 — UI·호출 경로
661. P25-TASK-010 업그레이드·마이그레이션·다운그레이드 — Test·리뷰
662. P25-TASK-011 서명 Release·오프라인 설치·배포 — 계약·Fixture
663. P25-TASK-012 서명 Release·오프라인 설치·배포 — 핵심 규칙
664. P25-TASK-013 서명 Release·오프라인 설치·배포 — 저장·연계
665. P25-TASK-014 서명 Release·오프라인 설치·배포 — UI·호출 경로
666. P25-TASK-015 서명 Release·오프라인 설치·배포 — Test·리뷰
667. P25-TASK-016 릴리즈 운영·결함 대응·문서 인계 — 계약·Fixture
668. P25-TASK-017 릴리즈 운영·결함 대응·문서 인계 — 핵심 규칙
669. P25-TASK-018 릴리즈 운영·결함 대응·문서 인계 — 저장·연계
670. P25-TASK-019 릴리즈 운영·결함 대응·문서 인계 — UI·호출 경로
671. P25-TASK-020 릴리즈 운영·결함 대응·문서 인계 — Test·리뷰
672. P25-TASK-021 Phase 25 통합 검증·인계 Gate
```

## 4. 작업 규모 / 공수
| Phase | Task 수 | 기능 Test 수 | O 인일 | 기대 인일 | P 인일 | Gate |
|---|---|---|---|---|---|---|
| 0 | 21 | 27 | 16.62 | 27.03 | 43.35 | P0-TASK-021 |
| 1 | 21 | 27 | 17.9 | 29.15 | 46.75 | P1-TASK-021 |
| 2 | 26 | 32 | 22.13 | 36.04 | 57.8 | P2-TASK-026 |
| 3 | 31 | 37 | 26.36 | 42.93 | 68.85 | P3-TASK-031 |
| 4 | 26 | 32 | 22.13 | 36.04 | 57.8 | P4-TASK-026 |
| 5 | 21 | 27 | 17.9 | 29.15 | 46.75 | P5-TASK-021 |
| 6 | 31 | 37 | 26.36 | 42.93 | 68.85 | P6-TASK-031 |
| 7 | 21 | 27 | 17.9 | 29.15 | 46.75 | P7-TASK-021 |
| 8 | 21 | 27 | 17.9 | 29.15 | 46.75 | P8-TASK-021 |
| 9 | 26 | 32 | 22.13 | 36.04 | 57.8 | P9-TASK-026 |
| 10 | 21 | 27 | 17.9 | 29.15 | 46.75 | P10-TASK-021 |
| 11 | 21 | 27 | 17.9 | 29.15 | 46.75 | P11-TASK-021 |
| 12 | 21 | 27 | 17.9 | 29.15 | 46.75 | P12-TASK-021 |
| 13 | 21 | 27 | 17.9 | 29.15 | 46.75 | P13-TASK-021 |
| 14 | 21 | 27 | 17.9 | 29.15 | 46.75 | P14-TASK-021 |
| 15 | 21 | 27 | 17.9 | 29.15 | 46.75 | P15-TASK-021 |
| 16 | 21 | 27 | 17.9 | 29.15 | 46.75 | P16-TASK-021 |
| 17 | 21 | 27 | 17.9 | 29.15 | 46.75 | P17-TASK-021 |
| 18 | 21 | 27 | 17.9 | 29.15 | 46.75 | P18-TASK-021 |
| 19 | 21 | 27 | 17.9 | 29.15 | 46.75 | P19-TASK-021 |
| 20 | 21 | 27 | 17.9 | 29.15 | 46.75 | P20-TASK-021 |
| 21 | 21 | 27 | 17.9 | 29.15 | 46.75 | P21-TASK-021 |
| 22 | 21 | 27 | 16.62 | 27.03 | 43.35 | P22-TASK-021 |
| 23 | 112 | 27 | 109.68 | 178.04 | 285.6 | P23-TASK-021 |
| 24 | 21 | 27 | 16.62 | 27.03 | 43.35 | P24-TASK-021 |
| 25 | 21 | 27 | 16.62 | 27.03 | 43.35 | P25-TASK-021 |

예상공수는완료증거나일정약속이아니다. 상세가정과실적갱신은95 문서를사용한다. 마스터/이표는설계기준 snapshot,관리데이터 tasks.json 과98 대시보드는진행관리원본이다.
