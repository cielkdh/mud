# 91. 전체 Test 계획서

> v31.0 · 테스트 설계 737 개 + 시스템 통합 14 개 · 실제 게임 테스트 **NOT_RUN**

## 1. 테스트 계층과 실행 정책
Unit:순수규칙의독립 oracle. Component:validator/usecase/repository port 와멱등성. Integration:실제 Room/파일/codec/모듈연결. UI:화면실제행동/정보공개/생명주기. Regression:선행승인 fixture. Failure/Recovery:쓰기 cut/손상/종료. Performance:실기기 release. 운영:비행기모드/기기이동/업데이트/세이브복구.

## 2. Fixture·Oracle·증거
기본 seed42 와강제 RNG 분기를함께사용한다. 일반분포시험 seed 는 seed0..N-1 로명시한다. 테스트전후 canonical stateHash,분야별 checksum,행수/원장합계,게임시간/RNGcounter 를기록한다. 테스트한로직자신을그대로다시호출하여 expected 를만들지않는다. 숫자공식은손계산 golden,소유권은합/집합,일정은구간,콘텐츠는원문 ID/행 roundtrip 을독립 oracle 로사용한다.

각 test 결과에는 app/schema/content/balance/combat/rng/generator 버전,기기/JVM,fixtureHash,seed,input,expected,actual,시작종료시각,evidencePath 를포함한다. 설계파일과 Python 문서검사만으로 Android 게임 Test 를 PASS 처리하지않는다.

751개 ID는 요구 추적 목록이며 모두가 즉시 실행 가능한 test script라는 뜻이 아니다. Phase를 활성화하기 전에 해당 Gate 필수 case와 기능별 대표 정상/경계/실패 case를 실제 fixture 경로, 조립할 구현/adapter, 순서가 있는 실행 절차, 독립 oracle, DB/파일 확인 SQL, timeout/종료조건, 증거 경로까지 구체화한다. 공통 템플릿 문구만 남은 case는 `NOT_RUN`을 유지하고 Gate 증거로 사용할 수 없다.

P0 Gate 실행 계약은 `P0-UT-001`, `P0-UT-002`, `P0-BT-002`, `P0-BT-003`, `P0-CT-003`, `P0-CN-001`, `P0-CT-004`, `P0-IT-002` 정확히 8개다. 각 case는 실제 test source path, 최소 fixture, 실행 명령, 독립 oracle, 파일 확인, timeout, evidence path를 갖춘다. 나머지 P0 case는 비차단 후속 계획이고, P2/P3/P6/P22/P25의 실행 계약은 해당 Phase 진입 직전 실제 코드와 schema를 기준으로 구체화한다. 751개를 선행 일괄 구현하지 않는다.

## 3. Phase별 전략
| Phase | 주요 초점 | Test 수 | 대표 회귀 |
|---|---|---|---|
| 0 | C01·C02·C03 적용, C14 빌드 검증과 P0 Gate 8개 통과 | 27 | P0-RT-001 |
| 1 | 원문 ID 수량 일치·필수 참조 0 건 오류·자산 검증 리포트 | 27 | P1-RT-001 |
| 2 | 시간 역행·중복 경계 처리 0 건·배속과 RNG 독립 | 32 | P2-RT-001 |
| 3 | 모든 crash cut 에서 이전 또는 다음 완전 세대만 로드 | 37 | P3-RT-001 |
| 4 | 동일 NPC 이름·얼굴 유지·성장 원장 재계산·잠재력 비공개 | 32 | P4-RT-001 |
| 5 | 스킬 2+3·각인 3 제한·아이템 단일 위치·중복 지급 차단 | 27 | P5-RT-001 |
| 6 | 즉시결과·배속·복구 hash 일치·동시 전투불능 재현·Early Playable Gate 통과 | 37 | P6-RT-001 |
| 7 | 비공개 정보 비참조·페이즈 한 번 전환·증원 상한 준수 | 27 | P7-RT-001 |
| 8 | 필수목표·퇴로 접근성 및 생성 재시도 상한 보장 | 27 | P8-RT-001 |
| 9 | 첫 Vertical Slice E2E 와 안전 회귀 비되감기 통과 | 32 | P9-RT-001 |
| 10 | 치료 예약/비용 정합성과 앱 종료 중 진척 없음 | 27 | P10-RT-001 |
| 11 | 대화/직접 버튼 결과 일치·서버 없이 모든 Topic 처리 | 27 | P11-RT-001 |
| 12 | 강화 원자성·재료 선점·+20 확률과 비파괴 규칙 | 27 | P12-RT-001 |
| 13 | 10/6 경계·원정 중 변경 제한·분열 재산 보존 | 27 | P13-RT-001 |
| 14 | 즉시 순환거래 차익 차단·대여 소유권 유지·운송 1 회 인도 | 27 | P14-RT-001 |
| 15 | 검색·추천·대화·통계까지 비공개 정보 누출 없음 | 27 | P15-RT-001 |
| 16 | 플레이어 신규 창설 금지·30 일 마감·기여조건·예산 보존 | 27 | P16-RT-001 |
| 17 | 10/50/100/300 년 시나리오 정의 및 승격 시 이중처리 없음 | 27 | P17-RT-001 |
| 18 | 개인 성장 비복사·가문 자산/증표 보존·후계 부재 안전장치 | 27 | P18-RT-001 |
| 19 | 예산·반복감쇠·가시성·체인 실패 후 대안 경로 | 27 | P19-RT-001 |
| 20 | 순서/세대 독립 증표·90 일 리셋·잔존 미정리 귀환 불가 | 27 | P20-RT-001 |
| 21 | 집계 원본 일치·압축 보존·검색 누출 없음·페이지 안정 | 27 | P21-RT-001 |
| 22 | 대표 사용자 여정·회전/재생성·큰 글자·TalkBack 완료 | 27 | P22-RT-001 |
| 23 | baseline 비교·seed 재현·실제 세이브와 테스트 저장소 분리 | 27 | P23-RT-001 |
| 24 | 성능 예산 실측·최적화 전후 결정론·복구 재검증 | 27 | P24-RT-001 |
| 25 | 출시 차단 결함 0·서명 release 오프라인 설치·데이터 복구 검증 | 27 | P25-RT-001 |

## 4. 전체 Test 인덱스

모든 case 의11 개상세필드는각 Phase 문서에있다. 원문절추적표가대표 Test 를가리킨다는것만으로그절의모든하위조건을검증했다고판정하지않는다. 기능별검증 Task 에서 assertion manifest 를작성하고하위조건누락을리뷰한다.

| Test ID / 상세 | 종류 | 대상 기능 | 검증 입력 | 예상 결과 | 상태 |
|---|---|---|---|---|---|
| [P0-UT-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ut-001) | UT | FUNC-P0-001 | 원문 SHA-256, Phase 0 결정 집합 `{C01,C02,C03,C14,C23}`, active P0 REQUIRED/DATA Assertion 상태 | hash 일치, C01·C02·C03 승인 적용, C14 빌드검증 상태와 C23 Assertion 범위·소유 분류 일치, 잘못된 P0 결정 0건, 후속 구현 착수 시 미승인 active P0 REQUIRED/DATA 0건 | PASS |
| [P0-BT-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-bt-001) | BT | FUNC-P0-001 | 같은 규칙의 모순이며 교체 문구 없음 | 결정 대기; 해당 기능의 운영 활성화 차단 | NOT_RUN |
| [P0-FT-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ft-001) | FT | FUNC-P0-001 | 요구사항 번호 하나 누락 | 문서 검증 실패; 릴리즈 범위에서 숨기지 않음 | NOT_RUN |
| [P0-CT-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ct-001) | CT | FUNC-P0-001 | §28 6 명, §1737 조직10/출전6; 같은요청2 회 | 조직과 출전을 분리하고 R-PARTY-001 에 원문 근거 2 개 보존; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P0-IT-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-it-001) | IT | FUNC-P0-001 | C01 fixture와 원문/결정/추적 JSON | C01 근거가 보존되고 같은 입력의 검사 결과가 동치이며 Android/DB 경로를 호출하지 않는다. | NOT_RUN |
| [P0-UT-002](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ut-002) | UT | FUNC-P0-002 | P0 Build Manifest와 `gradlew.bat :core:simulation:test` | 물리 project가 `:app`, `:core:simulation`뿐이고 JVM test가 성공한다. | PASS |
| [P0-BT-002](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-bt-002) | BT | FUNC-P0-002 | 정상 edge `:app→:core:simulation`; 금지 edge `:core:simulation→:app`; simulation의 Android/Room/Compose/네트워크 import; 미선언 P0 module/plugin/dependency | 모든 금지 fixture는 실패하고 정상 `:app→:core:simulation` graph와 JVM test는 통과 | PASS |
| [P0-FT-002](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ft-002) | FT | FUNC-P0-002 | 잠금 버전 의존성 resolve 실패 | 빌드 차단; 자동 최신 버전으로 변경하지 않음 | NOT_RUN |
| [P0-CT-002](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ct-002) | CT | FUNC-P0-002 | simulation 소스에 Android import 없음; 같은요청2 회 | 순수 JVM test 태스크 단독 성공; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P0-IT-002](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-it-002) | IT | FUNC-P0-002 | `gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease`와 AppRoot launch smoke | 네 Gradle task와 AppRoot smoke가 성공하고 built-in Kotlin 중복 plugin, 미선언 module, 실제 DB 생성이 없다. | PASS |
| [P0-UT-003](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ut-003) | UT | FUNC-P0-003 | Money(100), debit=40 | Money(60), 원본 값은 불변 | NOT_RUN |
| [P0-BT-003](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-bt-003) | BT | FUNC-P0-003 | `Money(Long.MAX_VALUE).plus(Money(1))`, `BasisPoint(10_001)`, `ProbabilityPpm(1_000_001)` | ArithmeticOverflow 오류·상태 변경 없음 | PASS |
| [P0-FT-003](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ft-003) | FT | FUNC-P0-003 | 잘못된 sessionEpoch 명령 | StaleSession; 다른 슬롯 변경 없음 | NOT_RUN |
| [P0-CT-003](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ct-003) | CT | FUNC-P0-003 | 동일 `CommandEnvelope`/`DomainEvent` 객체와 canonical golden bytes | 동일 입력의 bytes가 완전히 같고 roundtrip field loss 0건이며 unknown codec을 성공 처리하지 않는다. | PASS |
| [P0-IT-003](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-it-003) | IT | FUNC-P0-003 | `:app`의 WorldSession 공개 API 사용과 저장 구현 타입 직접 참조 fixture | 공개 API 사용만 compile되고 저장 구현 직접 참조는 실패하며 in-memory 결과는 정확히 1회 반환된다. | NOT_RUN |
| [P0-UT-004](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ut-004) | UT | FUNC-P0-004 | fixture=EMPTY_WORLD, seed=42 | 같은 초기 stateHash 와 홈 empty state | NOT_RUN |
| [P0-BT-004](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-bt-004) | BT | FUNC-P0-004 | 선행 기능 port 가 UnsupportedFeature | 기능 준비 안 됨 표시; 앱 crash 없음 | NOT_RUN |
| [P0-FT-004](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ft-004) | FT | FUNC-P0-004 | unknown Screen ID와 실패하는 retry callback | Blocked/Error가 유지되고 crash·가짜 성공·navigation 실행이 없다. | NOT_RUN |
| [P0-CT-004](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-ct-004) | CT | FUNC-P0-004 | 5개 AppShellState, unknown Screen ID, retry callback | 각 상태가 구분되고 미구현 기능은 Blocked이며 crash·가짜 성공·중복 callback 0건 | PASS |
| [P0-IT-004](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-it-004) | IT | FUNC-P0-004 | 앱 최초 실행·process recreation과 5-state fixture | shell이 crash 없이 재생성되고 권위 저장 성공을 주장하지 않으며 DB 파일을 만들지 않는다. | NOT_RUN |
| [P0-RT-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-rt-001) | RT | PHASE-0 | §28 6 명, §1737 조직10/출전6; 선행 Phase 의승인 fixture 전체 | 조직과 출전을 분리하고 R-PARTY-001 에 원문 근거 2 개 보존; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P0-CN-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-cn-001) | CN | PHASE-0 | command A/B 동시 enqueue, stale epoch C, enqueue 전 취소 D, enqueue 후 caller 취소 E, close 중 F | A→B 수락 순서와 stateVersion이 일치하고 stale epoch/close 이후 쓰기 0, enqueue 전 취소 효과 0, 수락 후 E는 정확히 1회 완료 | PASS |
| [P0-REC-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-rec-001) | REC | PHASE-0 | 보고서 쓰기 전·staging 완료 후·rename 직전 프로세스 중단 | 기존 또는 새 완전한 보고서만 존재하고 부분 파일·깨진 JSON 0건 | NOT_RUN |
| [P0-PT-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-pt-001) | PT | PHASE-0 | `:core:simulation:test`와 `:app:assembleDebug` 각 3회 | 측정치와 환경이 보고서에 남는다. P0에는 장기 시뮬레이션 성능 합격 임계치를 두지 않으며 이 Test는 Gate 비차단이다. | NOT_RUN |
| [P0-OP-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-op-001) | OP | PHASE-0 | 앱 최초 실행·재실행, 네트워크 차단, AppShellState.Empty/Blocked | 동일 shell 상태, 필수 네트워크 요청 0, 현실시간 catch-up 0, DB 생성 0 | NOT_RUN |
| [P0-ET-001](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-et-001) | ET | PHASE-0 | source hash mismatch, Gradle task 실패, unknown Screen ID, UnsupportedFeature | 문서/빌드 오류는 non-zero로 차단되고 UI는 Error/Blocked를 구분하며 crash·가짜 성공·DB 생성이 없다. | NOT_RUN |
| [P0-IT-005](01_Phase0_기준선_아키텍처_개발기반_상세설계서.md#p0-it-005) | IT | PHASE-0 | Phase 0 Gate evidence index | Gate evidence 누락 0, C01/C02/C03 적용, C14 build PASS, P3 저장 책임 인계 완료 | NOT_RUN |
| [P1-UT-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ut-001) | UT | FUNC-P1-001 | 20개 ContentKind.v1과 WPN-0001의 sourceId/sourceDisplayName/수치/단위/displayNameOverride | ContentId와 content_template.id가 sourceId와 byte-for-byte 같고 별도 source_id 저장 없이 closed ContentKind.v1, 원문 필드와 source locator를 보존하며 effective displayName만 override를 반영 | NOT_RUN |
| [P1-BT-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-bt-001) | BT | FUNC-P1-001 | 같은 WPN-0001을 가진 서로 다른 두 source row | DuplicateContentId가 두 source locator를 모두 포함 | NOT_RUN |
| [P1-FT-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ft-001) | FT | FUNC-P1-001 | 존재하지 않는 MON ID를 참조하는 loot row | VALIDATION_FAILED이며 builder/writer 호출과 발행 artifact가 0 | NOT_RUN |
| [P1-CT-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ct-001) | CT | FUNC-P1-001 | LF/CRLF·UTF-8 BOM·quoted empty/unquoted null CSV와 key 순서·duplicate key·unknown field·NaN/Infinity JSON fixture를 두 번 import | CSV dialect v1/JSON v1 허용 입력은 같은 CatalogDraft/hash, 금지 입력은 같은 diagnostic code와 source locator | NOT_RUN |
| [P1-IT-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-it-001) | IT | FUNC-P1-001 | content/source/catalog-manifest.json과 CSV dialect v1/JSON v1 대표 source set, 64MiB/100,000행 경계와 초과 fixture | BOM/newline/null 경계를 포함해 manifest rowCount=import count이며 모든 sourceId/원문 필드가 roundtrip하고 파일·행 상한 초과는 전체 parse 전 SOURCE_INVALID | NOT_RUN |
| [P1-UT-002](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ut-002) | UT | FUNC-P1-002 | 같은 CatalogDraft를 두 번, wall-clock/절대경로만 다르게 제공 | logicalContentHash와 정렬 row가 같고 진단 metadata만 다를 수 있음 | NOT_RUN |
| [P1-BT-002](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-bt-002) | BT | FUNC-P1-002 | 허용/금지 tag 동시 지정과 recipe A→B→A | 안정 정렬된 TAG_CONFLICT/RECIPE_CYCLE ERROR | NOT_RUN |
| [P1-FT-002](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ft-002) | FT | FUNC-P1-002 | 기존 파일/schema가 있는 staging root, WAL 요청·열린 DB handle·가짜 content.db-wal/shm/journal, staging row write, bundle/current.json ATOMIC_MOVE 직전 fault와 atomic move 미지원 | 기존 staging은 STAGING_NOT_EMPTY, journal은 DELETE로 정규화되고 열린 handle/sidecar 잔존은 안전 실패, current.json은 완전 이전/다음 bundle만 가리키며 미지원은 PUBLISH_ATOMIC_UNSUPPORTED, 혼합 active 0 | NOT_RUN |
| [P1-CT-002](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ct-002) | CT | FUNC-P1-002 | ERROR 1개와 WARN-only CatalogDraft | ERROR면 writer 0회, WARN-only면 staging writer 1회 | NOT_RUN |
| [P1-IT-002](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-it-002) | IT | FUNC-P1-002 | 동일 source/rules/tool/license registry 2회와 같은 logical content·서로 다른 asset/registry, O'Brien'); DROP TABLE content_template;--·줄바꿈·Unicode 표시명, dangling template/alias·미지원 ContentKind·usage/category mismatch와 display/JSON/asset metadata corruption fault | PreparedStatement 경계 문자열 roundtrip과 schema 유지, dangling FK·미지원 kind·usage 파생 category 불일치·display/JSON/definition/byte-size/SHA sealing 전 거절. 동일 build는 같은 artifact/bundleId이며 기존 target 검증 뒤 idempotent success, 참조 asset 또는 license registry entry 차이는 서로 다른 assetManifestSha256/bundleId. 각 DB는 IF NOT EXISTS 없는 fresh DDL, foreign_keys=1, journal_mode=delete, content_manifest 1행, foreign_key_check 0행, integrity_check=ok, semantic audit PASS, sidecar 0, read-only 재오픈/외부 hash 일치 | NOT_RUN |
| [P1-UT-003](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ut-003) | UT | FUNC-P1-003 | InstalledBundle의 findTemplate/listTemplates/findAlias와 MON-0001/BATTLE_TOKEN findAssetBindings(templateId,usage)·findAsset·listAssetFallbacks(usage), templateId=MON-0001, exactAssetKeys=[], entityKind=MONSTER, fallbackContext, targetPx=320, qualityMode=FULL AssetResolveRequest | 6개 read method가 template id 순·terminal alias 한 hop·binding/fallback priority 순과 미존재 empty/null을 반환하고 Exact ResolvedAsset, usage 파생 PORTRAIT/SQUARE_CENTER geometry, decode 256px 상한, 정규화 focal·원래 key 목록·cache identity가 동일 | NOT_RUN |
| [P1-BT-003](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-bt-003) | BT | FUNC-P1-003 | 용병 LIST/DETAIL/DIALOG/BATTLE/CHRONICLE, monster family, dungeon/room region/theme, item type, facility category별 missing/corrupt exact와 첫 fallback 표 | 용병은 LIST/DETAIL/DIALOG=SEX, BATTLE=CLASS, CHRONICLE=CATEGORY_DEFAULT 우선이고 다른 kind도 usage별 첫 승인 matcher를 사용하며 후보 assetId 1회·재귀 0·source key/identity 불변·유한 Fallback 또는 AssetUnavailable | NOT_RUN |
| [P1-FT-003](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ft-003) | FT | FUNC-P1-003 | ../save.db, C:/save.db, https://host/a.png, backslash, root 밖 symlink, 32MiB/8,192px/16,777,216px 초과, 미등록·BLOCKED licenseId와 MIME/animated WebP/EXIF/ICC/alpha 불일치 | 경로 공격은 INVALID_ASSET_PATH와 root 밖 read 0, input 상한·license registry·physical metadata 불일치는 VALIDATION_FAILED build ERROR이며 과대 decode·artifact 발행 0 | NOT_RUN |
| [P1-CT-003](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ct-003) | CT | FUNC-P1-003 | FACE/BUST/BATTLE/ROOM_BG/KEYART legacy code, 10개 usage→category/crop 표, 홀짝 source 크기·경계 focal crop golden, TEXT 허용/생략과 Exact/Fallback/Skipped/Loading fixture | builder만 canonical usage로 변환하고 runtime legacy 거절, category/crop은 usage에서 유일 파생, 정수 floor/clamp crop golden 일치, TEXT 허용 usage 정상 decode·금지 usage repository/file/decode 0, Loading/Fallback/terminal text layout 구분 | NOT_RUN |
| [P1-IT-003](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-it-003) | IT | FUNC-P1-003 | 승인 licenseId와 물리 metadata가 검증된 PNG/WebP, CDB-Q01..Q06, usage 파생 crop, Exact/Fallback/TEXT/Loading/terminal layout, HTML 특수문자 공개 이름과 분할 asset-preview | asset-preview.html index/category별 정적 page·manifest·builder SQLite query oracle·in-memory repository·gallery의 binding/category/crop/focal/physical metadata가 일치하고 공개 이름 contentDescription, 외부 요청·DOM 주입·stale cache·Main-thread I/O 0 | NOT_RUN |
| [P1-UT-004](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ut-004) | UT | FUNC-P1-004 | OLD-WPN→WPN-0001 terminal REMAP | MigrationRequired step이 old/new ID와 provenance를 보존 | NOT_RUN |
| [P1-BT-004](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-bt-004) | BT | FUNC-P1-004 | logicalContentHash 동일, assetManifestSha256/artifactFileSha256/bundleId만 다른 두 InstalledBundle | Compatible plan과 runtime fallback이며 save logical_content_hash/content identity 불변 | NOT_RUN |
| [P1-FT-004](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ft-004) | FT | FUNC-P1-004 | 필수 SKL ID에 alias와 legacy snapshot 없음 | Unsupported(missingIds), 원본 save bytes/hash 불변 | NOT_RUN |
| [P1-CT-004](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-ct-004) | CT | FUNC-P1-004 | 동일 saved/installed/alias 입력을 두 instance에 제공 | 동일 BindingPlan이고 파일/DB open·write 0 | NOT_RUN |
| [P1-IT-004](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-it-004) | IT | FUNC-P1-004 | logicalContentHash 동일·변경, asset-only 변경, migration/unsupported version matrix | 세 BindingPlan DTO가 P3/P25 handoff codec roundtrip하며 적용·save write는 0 | NOT_RUN |
| [P1-RT-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-rt-001) | RT | PHASE-1 | 기존 P0 graph와 확장된 Phase1 exact graph | P0 금지 import가 유지되고 선언된 Phase1 project/edge만 허용 | NOT_RUN |
| [P1-CN-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-cn-001) | CN | PHASE-1 | 같은 output root/current.json으로 두 builder 동시 publish와 하나의 OPEN ContentRepository에 병렬 read 후 owner close | publish 하나 성공/하나 PublishConflict와 active 혼합 0; OPEN 동시 read 성공, owner cancel→join→idempotent close, 경합 새 read/close 뒤 read는 ContentRepositoryClosed | NOT_RUN |
| [P1-REC-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-rec-001) | REC | PHASE-1 | transaction/DB close/sidecar 검사, DB hash·bundle manifest 확정/fsync 전후, bundle move 전/후, current.json 교체 전/후 강제 종료 fixture | 재실행 시 published bundle의 SQLite sidecar와 열린 resource는 0이고 current.json이 완전 이전 또는 완전 다음 bundle만 선택하며 orphan staging/bundle은 진단됨 | NOT_RUN |
| [P1-PT-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-pt-001) | PT | PHASE-1 | 최대 profile source와 10,000 asset, 분할 preview, CDB-Q01..Q06, FULL/LOW/TEXT, target bucket, bundle switch, 대표 PNG/WebP와 C19 단말 | 6개 query의 PK/UNIQUE/index와 허용되지 않은 full scan 0, query/build/preview/resolve/decode p50/p95·DB/heap/PSS 기록. preview page당 500/한 DOM 10,000 asset 0, bounded Coil cache, TEXT 금지 usage repository/file/decode 0·허용 usage 정상 decode, stale asset 0, C19 판정 명시 | NOT_RUN |
| [P1-OP-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-op-001) | OP | PHASE-1 | 네트워크 차단 상태의 build, app 재실행, resolver decode | 필수 네트워크 요청 0이며 같은 local bundle/hash/result 유지 | NOT_RUN |
| [P1-ET-001](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-et-001) | ET | PHASE-1 | SOURCE_INVALID, STAGING_NOT_EMPTY, PUBLISH_ATOMIC_UNSUPPORTED, INVALID_ASSET_PATH, ASSET_DECODE_FAILED, MISSING_REQUIRED_ASSET, 모든 후보 손상, INCOMPATIBLE_CONTENT fixture | 고정 error/CLI exit code와 build 차단·runtime 유한 fallback WARN·AssetUnavailable terminal failure·Blocked가 계약대로 분리되고 후보 assetId별 open 1회·기존 InstalledBundle 불변 | NOT_RUN |
| [P1-IT-005](02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md#p1-it-005) | IT | PHASE-1 | canonical ID/kind source→sealed content.db/license asset manifest/분할 asset-preview→InstalledBundle→ContentSnapshot/6-query Android resolver→logicalContentHash BindingPlan/P3·P22·P25 handoff | artifact/sealing/idempotent hash/6-query read-only lifecycle/coverage/10,000 asset UX/handoff가 일치하고 Gate는 PROTOTYPE_ACCEPTED와 BLOCKED_ASSET 또는 FULL_CONTENT_READY를 명시 | NOT_RUN |
| [P2-UT-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ut-001) | UT | FUNC-P2-001 | 동일 commandId 로 금화40 지출을 2 회 요청, 잔액100 | 잔액60·receipt 1 개·stateVersion 1 회 증가 | NOT_RUN |
| [P2-BT-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-bt-001) | BT | FUNC-P2-001 | 과거 expectedVersion | Conflict, 재조회 안내·변경0 | NOT_RUN |
| [P2-FT-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ft-001) | FT | FUNC-P2-001 | commit 실패 | 이전 stateHash/RNG/receipt 유지 | NOT_RUN |
| [P2-CT-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ct-001) | CT | FUNC-P2-001 | 동일 commandId 로 금화40 지출을 2 회 요청, 잔액100; 같은요청2 회 | 잔액60·receipt 1 개·stateVersion 1 회 증가; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P2-IT-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-it-001) | IT | FUNC-P2-001 | 동일 commandId 로 금화40 지출을 2 회 요청, 잔액100; 모듈 adapter 를실제 구현으로교체 | 잔액60·receipt 1 개·stateVersion 1 회 증가; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P2-UT-002](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ut-002) | UT | FUNC-P2-002 | clock=(분0,잔여0), 10 초 전투를6 회 | clock=(분1,잔여0); 1 회60 초와 동일 | NOT_RUN |
| [P2-BT-002](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-bt-002) | BT | FUNC-P2-002 | 23:59 +1 분, 12 월30 일 | 다음 연도1 월1 일00:00 | NOT_RUN |
| [P2-FT-002](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ft-002) | FT | FUNC-P2-002 | 미지원 rngAlgorithmVersion | 복구 중단; 다른 난수기로 자동 대체 금지 | NOT_RUN |
| [P2-CT-002](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ct-002) | CT | FUNC-P2-002 | clock=(분0,잔여0), 10 초 전투를6 회; 같은요청2 회 | clock=(분1,잔여0); 1 회60 초와 동일; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P2-IT-002](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-it-002) | IT | FUNC-P2-002 | clock=(분0,잔여0), 10 초 전투를6 회; 모듈 adapter 를실제 구현으로교체 | clock=(분1,잔여0); 1 회60 초와 동일; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P2-UT-003](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ut-003) | UT | FUNC-P2-003 | 리아 [14:00,18:00) 치료 후 [18:00,20:00) 훈련 | 경계 접점은 충돌 없음 | NOT_RUN |
| [P2-BT-003](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-bt-003) | BT | FUNC-P2-003 | 동일 인물 [17:59,19:00) | ScheduleConflict; 선점 생성0 | NOT_RUN |
| [P2-FT-003](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ft-003) | FT | FUNC-P2-003 | 재료예약 뒤 일정저장 실패 | 자원 예약/일정 모두 rollback | NOT_RUN |
| [P2-CT-003](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ct-003) | CT | FUNC-P2-003 | 리아 [14:00,18:00) 치료 후 [18:00,20:00) 훈련; 같은요청2 회 | 경계 접점은 충돌 없음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P2-IT-003](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-it-003) | IT | FUNC-P2-003 | 리아 [14:00,18:00) 치료 후 [18:00,20:00) 훈련; 모듈 adapter 를실제 구현으로교체 | 경계 접점은 충돌 없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P2-UT-004](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ut-004) | UT | FUNC-P2-004 | 08:00→내일08:00,10:30 치료완료 중단 설정 | 10:30 에서 정지·치료1 회완료·남은 목표 유지 | NOT_RUN |
| [P2-BT-004](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-bt-004) | BT | FUNC-P2-004 | target=current, due event 없음 | NoOp·시간/RNG 불변 | NOT_RUN |
| [P2-FT-004](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ft-004) | FT | FUNC-P2-004 | 3 일째 처리 실패 | 마지막 성공 경계에서 정지; 다음 실행에서 이미 처리한 일마감 재실행 없음 | NOT_RUN |
| [P2-CT-004](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ct-004) | CT | FUNC-P2-004 | 08:00→내일08:00,10:30 치료완료 중단 설정; 같은요청2 회 | 10:30 에서 정지·치료1 회완료·남은 목표 유지; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P2-IT-004](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-it-004) | IT | FUNC-P2-004 | 08:00→내일08:00,10:30 치료완료 중단 설정; 모듈 adapter 를실제 구현으로교체 | 10:30 에서 정지·치료1 회완료·남은 목표 유지; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P2-UT-005](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ut-005) | UT | FUNC-P2-005 | 앱 종료 후 현실24 시간 경과 후 재실행 | worldTime·치료 잔여시간 동일 | NOT_RUN |
| [P2-BT-005](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-bt-005) | BT | FUNC-P2-005 | 슬롯 A 이미지/DB 응답 지연 중 슬롯 B 로드 | epoch A 응답을 버려 B 에 쓰기0 | NOT_RUN |
| [P2-FT-005](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ft-005) | FT | FUNC-P2-005 | 프로세스 즉시 kill 로 onStop 미실행 | 마지막 committed 상태만 복원 | NOT_RUN |
| [P2-CT-005](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-ct-005) | CT | FUNC-P2-005 | 앱 종료 후 현실24 시간 경과 후 재실행; 같은요청2 회 | worldTime·치료 잔여시간 동일; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P2-IT-005](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-it-005) | IT | FUNC-P2-005 | 앱 종료 후 현실24 시간 경과 후 재실행; 모듈 adapter 를실제 구현으로교체 | worldTime·치료 잔여시간 동일; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P2-RT-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-rt-001) | RT | PHASE-2 | 동일 commandId 로 금화40 지출을 2 회 요청, 잔액100; 선행 Phase 의승인 fixture 전체 | 잔액60·receipt 1 개·stateVersion 1 회 증가; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P2-CN-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-cn-001) | CN | PHASE-2 | 동일 commandId 로 금화40 지출을 2 회 요청, 잔액100; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P2-REC-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-rec-001) | REC | PHASE-2 | commit 실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P2-PT-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-pt-001) | PT | PHASE-2 | mailbox 64+1개 burst, UI command 10개와 30일 AdvanceTime(최소 10,000 경계), 중간 process kill; PCG32 reference/golden vector | mailbox 무한증가·drop 0, segment별 transaction 상한 준수, UI starvation 없음, 외부 receipt 1개, 이미 완료한 boundary 재실행 0, 단일 실행과 terminal stateHash/RNG counter 동일 | NOT_RUN |
| [P2-OP-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-op-001) | OP | PHASE-2 | 앱 종료 후 현실24 시간 경과 후 재실행; 네트워크차단·앱재실행/도구재실행 | worldTime·치료 잔여시간 동일; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P2-ET-001](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-et-001) | ET | PHASE-2 | 프로세스 즉시 kill 로 onStop 미실행 | 마지막 committed 상태만 복원; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P2-IT-006](03_Phase2_월드명령_시간_예약_RNG_상세설계서.md#p2-it-006) | IT | PHASE-2 | 동일 commandId 로 금화40 지출을 2 회 요청, 잔액100→앱 종료 후 현실24 시간 경과 후 재실행 | 잔액60·receipt 1 개·stateVersion 1 회 증가 및 worldTime·치료 잔여시간 동일; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P3-UT-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ut-001) | UT | FUNC-P3-001 | 아이템 이동+금화지출+RNG 1 회 | 세 요소와 receipt 가 같은 generation 에서 보임 | NOT_RUN |
| [P3-BT-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-bt-001) | BT | FUNC-P3-001 | Dirty set 공집합 | 필요 metadata 변경 없으면 NoOp | NOT_RUN |
| [P3-FT-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ft-001) | FT | FUNC-P3-001 | RNG row 쓰기 단계에서 예외 | 아이템·금화·RNG 전부 이전 값 | NOT_RUN |
| [P3-CT-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ct-001) | CT | FUNC-P3-001 | CheckpointWorld 1회가 내부 SaveCoordinator.commit을 호출; 같은 commandId/payload 2회 | 바깥 command_receipt 정확히 1개·내부 P3 receipt/Event 0개; 세 요소가 같은 generation에서 보임; 재호출 효과 1회, 같은 ID/다른 payload는 IdempotencyKeyReuse | NOT_RUN |
| [P3-IT-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-it-001) | IT | FUNC-P3-001 | 아이템 이동+금화지출+RNG 1 회; 모듈 adapter 를실제 구현으로교체 | 세 요소와 receipt 가 같은 generation 에서 보임; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P3-UT-002](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ut-002) | UT | FUNC-P3-002 | g1 금화100, g2 금화60 이후 g1 로드 | 금화100 복원; g2 내용도 보존 | NOT_RUN |
| [P3-BT-002](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-bt-002) | BT | FUNC-P3-002 | 수동슬롯 이름 동일2 개 | slotId 가 달라 충돌 없음 | NOT_RUN |
| [P3-FT-002](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ft-002) | FT | FUNC-P3-002 | 새 청크 완료 전 종료 | g1 의 완전 manifest 로 복구; g2 조각 혼합 없음 | NOT_RUN |
| [P3-CT-002](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ct-002) | CT | FUNC-P3-002 | g1 금화100, g2 금화60 이후 g1 로드; 같은요청2 회 | 금화100 복원; g2 내용도 보존; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P3-IT-002](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-it-002) | IT | FUNC-P3-002 | g1 금화100, g2 금화60 이후 g1 로드; 모듈 adapter 를실제 구현으로교체 | 금화100 복원; g2 내용도 보존; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P3-UT-003](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ut-003) | UT | FUNC-P3-003 | 투사체 발사 후 시전자 전투불능 checkpoint | 유효 투사체만 이어지고 기존 발사 이벤트 재생성0 | NOT_RUN |
| [P3-BT-003](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-bt-003) | BT | FUNC-P3-003 | 전투 시작 상태로 복귀 선택 | start checkpoint 의 상태·RNG 전체 복원 | NOT_RUN |
| [P3-FT-003](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ft-003) | FT | FUNC-P3-003 | checkpoint checksum 불일치 | 직전 완전 generation 제안·손상 원본 유지 | NOT_RUN |
| [P3-CT-003](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ct-003) | CT | FUNC-P3-003 | 투사체 발사 후 시전자 전투불능 checkpoint; 같은요청2 회 | 유효 투사체만 이어지고 기존 발사 이벤트 재생성0; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P3-IT-003](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-it-003) | IT | FUNC-P3-003 | 투사체 발사 후 시전자 전투불능 checkpoint; 모듈 adapter 를실제 구현으로교체 | 유효 투사체만 이어지고 기존 발사 이벤트 재생성0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P3-UT-004](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ut-004) | UT | FUNC-P3-004 | GREENFIELD_V1, exported current schema v1, releasedFixtures=[] | migrationSteps=[]·가상 v2/v3 없음·fresh v1 왕복 검증 계획 생성 | NOT_RUN |
| [P3-BT-004](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-bt-004) | BT | FUNC-P3-004 | 지원보다 새로운 schema | UnsupportedSaveVersion·원본 hash 동일 | NOT_RUN |
| [P3-FT-004](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ft-004) | FT | FUNC-P3-004 | 실제 N→N+1 체인의 한 단계에 FaultInjector로 중간 migration 실패 주입 | 실패 복제본 격리·원본으로 복귀 | NOT_RUN |
| [P3-CT-004](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ct-004) | CT | FUNC-P3-004 | GREENFIELD_V1 fresh-open 요청 2회; LEGACY_CHAIN이면 실제 동일 archive migration 요청 2회 | GREENFIELD_V1은 migration 0개와 동치 open; LEGACY_CHAIN은 실제 단계별 이력 1회씩; 반복 효과 1회, 다른 payload는 IdempotencyKeyReuse | NOT_RUN |
| [P3-IT-004](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-it-004) | IT | FUNC-P3-004 | GREENFIELD_V1: exported v1 fresh DB; LEGACY_CHAIN: oldest supported 실제 DB fixture→current schema | GREENFIELD_V1은 v1 fresh create/reopen·migration 0개; LEGACY_CHAIN은 실제 단계만 적용·대표 권위값 보존; 앱/JVM test 재조회 결과 동일 | NOT_RUN |
| [P3-UT-005](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ut-005) | UT | FUNC-P3-005 | 정상 archive 가져오기 | 새 slotId·기존 슬롯 hash 불변·동일 상태 복원 | NOT_RUN |
| [P3-BT-005](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-bt-005) | BT | FUNC-P3-005 | archive 에 ../save.db 경로 | UnsafeArchive 오류·외부 파일 생성0 | NOT_RUN |
| [P3-FT-005](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ft-005) | FT | FUNC-P3-005 | export 중 공간 부족 | 완성 표시하지 않음·부분파일 제거·기존 save 보존 | NOT_RUN |
| [P3-CT-005](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ct-005) | CT | FUNC-P3-005 | 정상 archive 가져오기; 같은요청2 회 | 새 slotId·기존 슬롯 hash 불변·동일 상태 복원; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P3-IT-005](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-it-005) | IT | FUNC-P3-005 | 정상 archive 가져오기; 모듈 adapter 를실제 구현으로교체 | 새 slotId·기존 슬롯 hash 불변·동일 상태 복원; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P3-UT-006](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ut-006) | UT | FUNC-P3-006 | 현재 g3 손상, g2 정상 | g2 복원 제안·손실 경계와 시간 표시 | NOT_RUN |
| [P3-BT-006](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-bt-006) | BT | FUNC-P3-006 | 미참조 checkpoint chunk 1 개 | 보존 root 검증 후 그 chunk 만 GC | NOT_RUN |
| [P3-FT-006](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ft-006) | FT | FUNC-P3-006 | GC 중단 | 트랜잭션 rollback·모든 보존 root 로드 가능 | NOT_RUN |
| [P3-CT-006](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-ct-006) | CT | FUNC-P3-006 | 현재 g3 손상, g2 정상; 같은요청2 회 | g2 복원 제안·손실 경계와 시간 표시; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P3-IT-006](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-it-006) | IT | FUNC-P3-006 | 현재 g3 손상, g2 정상; 모듈 adapter 를실제 구현으로교체 | g2 복원 제안·손실 경계와 시간 표시; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P3-RT-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-rt-001) | RT | PHASE-3 | 아이템 이동+금화지출+RNG 1 회; 선행 Phase 의승인 fixture 전체 | 세 요소와 receipt 가 같은 generation 에서 보임; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P3-CN-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-cn-001) | CN | PHASE-3 | 아이템 이동+금화지출+RNG 1 회; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P3-REC-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-rec-001) | REC | PHASE-3 | RNG row 쓰기 단계에서 예외; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P3-PT-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-pt-001) | PT | PHASE-3 | 10년/100년 synthetic current rows, dirty shard 1%/50%, 일반 command 1,000회, checkpoint 10회, g1→g2→g1 restore swap | 일반 command마다 SaveGeneration이 생기지 않음, checkpoint 크기는 변경 shard에 비례, restore 중 live DB 혼합 0, kill 후 이전 또는 새 완전 DB만 열림 | NOT_RUN |
| [P3-OP-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-op-001) | OP | PHASE-3 | 현재 g3 손상, g2 정상; 네트워크차단·앱재실행/도구재실행 | g2 복원 제안·손실 경계와 시간 표시; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P3-ET-001](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-et-001) | ET | PHASE-3 | GC 중단 | 트랜잭션 rollback·모든 보존 root 로드 가능; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P3-IT-007](04_Phase3_로컬DB_세이브_복구_상세설계서.md#p3-it-007) | IT | PHASE-3 | 아이템 이동+금화지출+RNG 1 회→현재 g3 손상, g2 정상 | 세 요소와 receipt 가 같은 generation 에서 보임 및 g2 복원 제안·손실 경계와 시간 표시; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P4-UT-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ut-001) | UT | FUNC-P4-001 | 여성 NPC-1, 성씨 발렌, 남성 pool 도 존재 | NPC-W 범위 portrait·발렌 성씨·세 레코드 동일 commit | NOT_RUN |
| [P4-BT-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-bt-001) | BT | FUNC-P4-001 | 여성 pool 전부 일반 ACTIVE, 보호키 제외 가능 | 일반 shared fallback 허용·collision metric 기록 | NOT_RUN |
| [P4-FT-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ft-001) | FT | FUNC-P4-001 | portrait 예약 후 NPC insert 실패 | 이름예약·초상예약·NPC 모두0 | NOT_RUN |
| [P4-CT-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ct-001) | CT | FUNC-P4-001 | 여성 NPC-1, 성씨 발렌, 남성 pool 도 존재; 같은요청2 회 | NPC-W 범위 portrait·발렌 성씨·세 레코드 동일 commit; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P4-IT-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-it-001) | IT | FUNC-P4-001 | 여성 NPC-1, 성씨 발렌, 남성 pool 도 존재; 모듈 adapter 를실제 구현으로교체 | NPC-W 범위 portrait·발렌 성씨·세 레코드 동일 commit; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P4-UT-002](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ut-002) | UT | FUNC-P4-002 | 레벨9→10 1 회 상승 | 자동성장1·자유포인트3 지급, ledger 중복0 | NOT_RUN |
| [P4-BT-002](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-bt-002) | BT | FUNC-P4-002 | 현재스탯30 에서1 투자,31 에서1 투자 | 각 비용1,2; 포인트 부족 시 두 번째만 거절 | NOT_RUN |
| [P4-FT-002](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ft-002) | FT | FUNC-P4-002 | 같은 level event 재수신 | 성장 보상 추가0 | NOT_RUN |
| [P4-CT-002](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ct-002) | CT | FUNC-P4-002 | 레벨9→10 1 회 상승; 같은요청2 회 | 자동성장1·자유포인트3 지급, ledger 중복0; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P4-IT-002](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-it-002) | IT | FUNC-P4-002 | 레벨9→10 1 회 상승; 모듈 adapter 를실제 구현으로교체 | 자동성장1·자유포인트3 지급, ledger 중복0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P4-UT-003](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ut-003) | UT | FUNC-P4-003 | 부상 modifier -3 뒤 동일 injury 의 재활 +3 | basePotential 불변·해당 손상만0 으로 복원 | NOT_RUN |
| [P4-BT-003](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-bt-003) | BT | FUNC-P4-003 | 이미 복원된 injury 재활 반복 요청 | 추가 잠재력+3 없음 | NOT_RUN |
| [P4-FT-003](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ft-003) | FT | FUNC-P4-003 | 원문 수치가 없는 변화효과 | 콘텐츠 승인 전 활성 차단·임의 영구상승 금지 | NOT_RUN |
| [P4-CT-003](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ct-003) | CT | FUNC-P4-003 | 부상 modifier -3 뒤 동일 injury 의 재활 +3; 같은요청2 회 | basePotential 불변·해당 손상만0 으로 복원; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P4-IT-003](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-it-003) | IT | FUNC-P4-003 | 부상 modifier -3 뒤 동일 injury 의 재활 +3; 모듈 adapter 를실제 구현으로교체 | basePotential 불변·해당 손상만0 으로 복원; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P4-UT-004](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ut-004) | UT | FUNC-P4-004 | Lv50 검사→창병, 테스트계수0.90 | Lv45, 초기/영구분 보존·감소 성장분만 재산정 | NOT_RUN |
| [P4-BT-004](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-bt-004) | BT | FUNC-P4-004 | Lv1 재훈련 | 하한1 유지·음수 자유포인트 없음 | NOT_RUN |
| [P4-FT-004](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ft-004) | FT | FUNC-P4-004 | 시작비용 부족 | 클래스/예약/금화 변경0 | NOT_RUN |
| [P4-CT-004](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ct-004) | CT | FUNC-P4-004 | Lv50 검사→창병, 테스트계수0.90; 같은요청2 회 | Lv45, 초기/영구분 보존·감소 성장분만 재산정; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P4-IT-004](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-it-004) | IT | FUNC-P4-004 | Lv50 검사→창병, 테스트계수0.90; 모듈 adapter 를실제 구현으로교체 | Lv45, 초기/영구분 보존·감소 성장분만 재산정; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P4-UT-005](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ut-005) | UT | FUNC-P4-005 | 낯선 NPC potential=99, observer knowledge 없음 | view 와 semantics/export/search 에99 없음 | NOT_RUN |
| [P4-BT-005](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-bt-005) | BT | FUNC-P4-005 | 같은파티 current HP 필요 | 운영 HP 공개·exactPotential 비공개 유지 | NOT_RUN |
| [P4-FT-005](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ft-005) | FT | FUNC-P4-005 | 미정 visibility rule | 기본 숨김·권한오류 audit | NOT_RUN |
| [P4-CT-005](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-ct-005) | CT | FUNC-P4-005 | 낯선 NPC potential=99, observer knowledge 없음; 같은요청2 회 | view 와 semantics/export/search 에99 없음; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P4-IT-005](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-it-005) | IT | FUNC-P4-005 | 낯선 NPC potential=99, observer knowledge 없음; 모듈 adapter 를실제 구현으로교체 | view 와 semantics/export/search 에99 없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P4-RT-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-rt-001) | RT | PHASE-4 | 여성 NPC-1, 성씨 발렌, 남성 pool 도 존재; 선행 Phase 의승인 fixture 전체 | NPC-W 범위 portrait·발렌 성씨·세 레코드 동일 commit; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P4-CN-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-cn-001) | CN | PHASE-4 | 여성 NPC-1, 성씨 발렌, 남성 pool 도 존재; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P4-REC-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-rec-001) | REC | PHASE-4 | portrait 예약 후 NPC insert 실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P4-PT-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-pt-001) | PT | PHASE-4 | 여성 NPC-1, 성씨 발렌, 남성 pool 도 존재; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P4-OP-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-op-001) | OP | PHASE-4 | 낯선 NPC potential=99, observer knowledge 없음; 네트워크차단·앱재실행/도구재실행 | view 와 semantics/export/search 에99 없음; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P4-ET-001](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-et-001) | ET | PHASE-4 | 미정 visibility rule | 기본 숨김·권한오류 audit; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P4-IT-006](05_Phase4_용병생성_성장_잠재력_이름_상세설계서.md#p4-it-006) | IT | PHASE-4 | 여성 NPC-1, 성씨 발렌, 남성 pool 도 존재→낯선 NPC potential=99, observer knowledge 없음 | NPC-W 범위 portrait·발렌 성씨·세 레코드 동일 commit 및 view 와 semantics/export/search 에99 없음; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P5-UT-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ut-001) | UT | FUNC-P5-001 | 보관장비 I1 을 주무기 장착 | storage→equipment 원자 이동·동시 위치1 개 | NOT_RUN |
| [P5-BT-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-bt-001) | BT | FUNC-P5-001 | 같은 item 을 두 NPC 장착 | 두 번째 Conflict·소유권 불변 | NOT_RUN |
| [P5-FT-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ft-001) | FT | FUNC-P5-001 | 이동 대상창고 FK 없음 | 정합성오류·출발위치 보존 | NOT_RUN |
| [P5-CT-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ct-001) | CT | FUNC-P5-001 | 보관장비 I1 을 주무기 장착; 같은요청2 회 | storage→equipment 원자 이동·동시 위치1 개; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P5-IT-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-it-001) | IT | FUNC-P5-001 | 보관장비 I1 을 주무기 장착; 모듈 adapter 를실제 구현으로교체 | storage→equipment 원자 이동·동시 위치1 개; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P5-UT-002](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ut-002) | UT | FUNC-P5-002 | 범용 skill S1 에 허용 prefix1/suffix1 | 기본등급 유지·variation count2 | NOT_RUN |
| [P5-BT-002](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-bt-002) | BT | FUNC-P5-002 | 같은 prefix2 개 | AffixConflict·보유스킬 불변 | NOT_RUN |
| [P5-FT-002](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ft-002) | FT | FUNC-P5-002 | 제한 클래스 전용 스킬 사용권 없음 | 장착 불가·배우기 규칙과 사용 규칙 분리 | NOT_RUN |
| [P5-CT-002](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ct-002) | CT | FUNC-P5-002 | 범용 skill S1 에 허용 prefix1/suffix1; 같은요청2 회 | 기본등급 유지·variation count2; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P5-IT-002](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-it-002) | IT | FUNC-P5-002 | 범용 skill S1 에 허용 prefix1/suffix1; 모듈 adapter 를실제 구현으로교체 | 기본등급 유지·variation count2; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P5-UT-003](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ut-003) | UT | FUNC-P5-003 | 3 액티브 중1 개 장비스킬 장착 | 정상; 해당 장비 해제시 그 슬롯만 비활성 | NOT_RUN |
| [P5-BT-003](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-bt-003) | BT | FUNC-P5-003 | 4 번째 액티브 또는 각인4 개 | 검증 거절·원본 preset 유지 | NOT_RUN |
| [P5-FT-003](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ft-003) | FT | FUNC-P5-003 | 조건식에 파일/Reflection 호출 | UnsupportedExpression·평가하지 않음 | NOT_RUN |
| [P5-CT-003](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ct-003) | CT | FUNC-P5-003 | 3 액티브 중1 개 장비스킬 장착; 같은요청2 회 | 정상; 해당 장비 해제시 그 슬롯만 비활성; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P5-IT-003](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-it-003) | IT | FUNC-P5-003 | 3 액티브 중1 개 장비스킬 장착; 모듈 adapter 를실제 구현으로교체 | 정상; 해당 장비 해제시 그 슬롯만 비활성; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P5-UT-004](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ut-004) | UT | FUNC-P5-004 | 동일 chestId 첫 개봉 보상 I1, 두 번째개봉 | 첫1 회 지급·두 번째 AlreadyClaimed | NOT_RUN |
| [P5-BT-004](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-bt-004) | BT | FUNC-P5-004 | 인벤토리 용량0 | overflow loot storage 에 동일 보상 보존 | NOT_RUN |
| [P5-FT-004](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ft-004) | FT | FUNC-P5-004 | 아이템생성 후 보상 receipt 저장 실패 | 아이템과 receipt 모두 rollback | NOT_RUN |
| [P5-CT-004](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-ct-004) | CT | FUNC-P5-004 | 동일 chestId 첫 개봉 보상 I1, 두 번째개봉; 같은요청2 회 | 첫1 회 지급·두 번째 AlreadyClaimed; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P5-IT-004](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-it-004) | IT | FUNC-P5-004 | 동일 chestId 첫 개봉 보상 I1, 두 번째개봉; 모듈 adapter 를실제 구현으로교체 | 첫1 회 지급·두 번째 AlreadyClaimed; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P5-RT-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-rt-001) | RT | PHASE-5 | 보관장비 I1 을 주무기 장착; 선행 Phase 의승인 fixture 전체 | storage→equipment 원자 이동·동시 위치1 개; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P5-CN-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-cn-001) | CN | PHASE-5 | 보관장비 I1 을 주무기 장착; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P5-REC-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-rec-001) | REC | PHASE-5 | 이동 대상창고 FK 없음; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P5-PT-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-pt-001) | PT | PHASE-5 | 보관장비 I1 을 주무기 장착; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P5-OP-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-op-001) | OP | PHASE-5 | 동일 chestId 첫 개봉 보상 I1, 두 번째개봉; 네트워크차단·앱재실행/도구재실행 | 첫1 회 지급·두 번째 AlreadyClaimed; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P5-ET-001](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-et-001) | ET | PHASE-5 | 아이템생성 후 보상 receipt 저장 실패 | 아이템과 receipt 모두 rollback; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P5-IT-005](06_Phase5_장비_인벤토리_스킬_전술설정_상세설계서.md#p5-it-005) | IT | PHASE-5 | 보관장비 I1 을 주무기 장착→동일 chestId 첫 개봉 보상 I1, 두 번째개봉 | storage→equipment 원자 이동·동시 위치1 개 및 첫1 회 지급·두 번째 AlreadyClaimed; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P6-UT-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ut-001) | UT | FUNC-P6-001 | T=1000 에 A/B 각 HP10, 서로10 피해 확정 | A/B 동시 전투불능·먼저 정렬된 ID 우대 없음 | NOT_RUN |
| [P6-BT-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-bt-001) | BT | FUNC-P6-001 | T=5000 만료 버프와 신규공격 | 버프 미적용 | NOT_RUN |
| [P6-FT-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ft-001) | FT | FUNC-P6-001 | eventTime 이 현재보다 작음 | InvariantViolation·세이브복구 안내·피해 적용0 | NOT_RUN |
| [P6-CT-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ct-001) | CT | FUNC-P6-001 | T=1000 에 A/B 각 HP10, 서로10 피해 확정; 같은요청2 회 | A/B 동시 전투불능·먼저 정렬된 ID 우대 없음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P6-IT-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-it-001) | IT | FUNC-P6-001 | T=1000 에 A/B 각 HP10, 서로10 피해 확정; 모듈 adapter 를실제 구현으로교체 | A/B 동시 전투불능·먼저 정렬된 ID 우대 없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P6-UT-002](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ut-002) | UT | FUNC-P6-002 | 명중90 회피30 | 확률0.935384615…; 표시93.5%, 내부 clamp 전 정밀값 | NOT_RUN |
| [P6-BT-002](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-bt-002) | BT | FUNC-P6-002 | 원시피해100,방어0,저항0,shield30,HP100 | shield0·HP30·실 HP 피해70 | NOT_RUN |
| [P6-FT-002](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ft-002) | FT | FUNC-P6-002 | NaN/음수 damage definition | 콘텐츠/계산 오류로 차단·HP 변경0 | NOT_RUN |
| [P6-CT-002](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ct-002) | CT | FUNC-P6-002 | 명중90 회피30; 같은요청2 회 | 확률0.935384615…; 표시93.5%, 내부 clamp 전 정밀값; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P6-IT-002](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-it-002) | IT | FUNC-P6-002 | 명중90 회피30; 모듈 adapter 를실제 구현으로교체 | 확률0.935384615…; 표시93.5%, 내부 clamp 전 정밀값; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P6-UT-003](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ut-003) | UT | FUNC-P6-003 | MP100,cost20,refundRate0.5,시전중단 | MP90,환급1 회·발사체 생성0 | NOT_RUN |
| [P6-BT-003](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-bt-003) | BT | FUNC-P6-003 | 시전중단 이벤트2 번 | 두 번째 환급0 | NOT_RUN |
| [P6-FT-003](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ft-003) | FT | FUNC-P6-003 | 이미 발사한 시전자 전투불능 | 발사체는 유효타겟에 도착·미래 새 행동만 취소 | NOT_RUN |
| [P6-CT-003](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ct-003) | CT | FUNC-P6-003 | MP100,cost20,refundRate0.5,시전중단; 같은요청2 회 | MP90,환급1 회·발사체 생성0; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P6-IT-003](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-it-003) | IT | FUNC-P6-003 | MP100,cost20,refundRate0.5,시전중단; 모듈 adapter 를실제 구현으로교체 | MP90,환급1 회·발사체 생성0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P6-UT-004](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ut-004) | UT | FUNC-P6-004 | 독9 스택에2 스택 재적용 | 최대10 스택·source policy 적용 | NOT_RUN |
| [P6-BT-004](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-bt-004) | BT | FUNC-P6-004 | expire=4000,ticks=2000,4000 | 2000 만실행·4000 제외 | NOT_RUN |
| [P6-FT-004](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ft-004) | FT | FUNC-P6-004 | 알 수 없는 status enum | 로드 compatibility 오류 또는 명시 alias; 조용한 삭제 금지 | NOT_RUN |
| [P6-CT-004](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ct-004) | CT | FUNC-P6-004 | 독9 스택에2 스택 재적용; 같은요청2 회 | 최대10 스택·source policy 적용; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P6-IT-004](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-it-004) | IT | FUNC-P6-004 | 독9 스택에2 스택 재적용; 모듈 adapter 를실제 구현으로교체 | 최대10 스택·source policy 적용; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P6-UT-005](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ut-005) | UT | FUNC-P6-005 | 마지막 몹 HP0,200ms 뒤 사망폭발 대기 | 폭발 처리까지 승리확정 유보 | NOT_RUN |
| [P6-BT-005](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-bt-005) | BT | FUNC-P6-005 | 쌍방 반사/반격 능력 | 추가반응 연쇄 무한 발생 없음 | NOT_RUN |
| [P6-FT-005](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ft-005) | FT | FUNC-P6-005 | 정산 receipt 중복 | XP/보상 요청 중복0 | NOT_RUN |
| [P6-CT-005](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ct-005) | CT | FUNC-P6-005 | 마지막 몹 HP0,200ms 뒤 사망폭발 대기; 같은요청2 회 | 폭발 처리까지 승리확정 유보; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P6-IT-005](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-it-005) | IT | FUNC-P6-005 | 마지막 몹 HP0,200ms 뒤 사망폭발 대기; 모듈 adapter 를실제 구현으로교체 | 폭발 처리까지 승리확정 유보; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P6-UT-006](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ut-006) | UT | FUNC-P6-006 | 동일 seed/입력 네 재생모드 | stateHash·worldDuration·loot seed 동일 | NOT_RUN |
| [P6-BT-006](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-bt-006) | BT | FUNC-P6-006 | 로그상세 OFF | 결과/난수 counter 동일 | NOT_RUN |
| [P6-FT-006](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ft-006) | FT | FUNC-P6-006 | 미지원 combatVersion replay | 요약 표시·현재엔진으로 다른 결과를 재연하지 않음 | NOT_RUN |
| [P6-CT-006](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-ct-006) | CT | FUNC-P6-006 | 동일 seed/입력 네 재생모드; 같은요청2 회 | stateHash·worldDuration·loot seed 동일; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P6-IT-006](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-it-006) | IT | FUNC-P6-006 | 동일 seed/입력 네 재생모드; 모듈 adapter 를실제 구현으로교체 | stateHash·worldDuration·loot seed 동일; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P6-RT-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-rt-001) | RT | PHASE-6 | T=1000 에 A/B 각 HP10, 서로10 피해 확정; 선행 Phase 의승인 fixture 전체 | A/B 동시 전투불능·먼저 정렬된 ID 우대 없음; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P6-CN-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-cn-001) | CN | PHASE-6 | T=1000 에 A/B 각 HP10, 서로10 피해 확정; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P6-REC-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-rec-001) | REC | PHASE-6 | eventTime 이 현재보다 작음; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P6-PT-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-pt-001) | PT | PHASE-6 | 소형/최대 파티 fixture, 일반/상태이상 과밀/보스 전투 각 1,000회, 동일 PCG32 seed pack, UI render 없음 | 모든 전투 bounded 종료, event/reaction budget 초과는 typed 실패, 반복에 따른 heap 선형 증가 없음, 동일 seed/명령의 상세·배속 stateHash/RNG counter 동일 | NOT_RUN |
| [P6-OP-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-op-001) | OP | PHASE-6 | 동일 seed/입력 네 재생모드; 네트워크차단·앱재실행/도구재실행 | stateHash·worldDuration·loot seed 동일; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P6-ET-001](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-et-001) | ET | PHASE-6 | 미지원 combatVersion replay | 요약 표시·현재엔진으로 다른 결과를 재연하지 않음; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P6-IT-007](07_Phase6_전투시간축_수치_상태이상_상세설계서.md#p6-it-007) | IT | PHASE-6 | 고정 파티/장비/몬스터/전리품 fixture로 파티 준비→자동 전투→전리품 정산→save/load; T=1000 에 A/B 각 HP10, 서로10 피해 확정→동일 seed/입력 네 재생모드 | Early Playable Gate에서 실제 WorldEngine/SavePort로 전투·정산·복원하고 Fake/Mock/UnsupportedFeature 성공0건; load 후 stateHash·전리품 소유권·worldDuration 동일; A/B 동시 전투불능·먼저 정렬된 ID 우대 없음; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P7-UT-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ut-001) | UT | FUNC-P7-001 | 후열 사제 존재를 아직 감지 못한 몹 | 미관측 사제를 이름/직업 근거로 우선공격하지 않음 | NOT_RUN |
| [P7-BT-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-bt-001) | BT | FUNC-P7-001 | 가능한 스킬후보0 | 기본공격 또는 방어대기 | NOT_RUN |
| [P7-FT-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ft-001) | FT | FUNC-P7-001 | AI profile 파손 | 직전 상태 보존·콘텐츠 오류; 승리 자동처리 금지 | NOT_RUN |
| [P7-CT-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ct-001) | CT | FUNC-P7-001 | 후열 사제 존재를 아직 감지 못한 몹; 같은요청2 회 | 미관측 사제를 이름/직업 근거로 우선공격하지 않음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P7-IT-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-it-001) | IT | FUNC-P7-001 | 후열 사제 존재를 아직 감지 못한 몹; 모듈 adapter 를실제 구현으로교체 | 미관측 사제를 이름/직업 근거로 우선공격하지 않음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P7-UT-002](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ut-002) | UT | FUNC-P7-002 | A-B-C 통로,문 B 닫힘,소음전파차단 | C 의 그룹이 소음을 감지하지 않음 | NOT_RUN |
| [P7-BT-002](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-bt-002) | BT | FUNC-P7-002 | 리더가 전투불능 | 정의된 fallback leader 로만 이전 | NOT_RUN |
| [P7-FT-002](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ft-002) | FT | FUNC-P7-002 | blackboard 타겟 소멸 | 후보 재평가·null dereference 없음 | NOT_RUN |
| [P7-CT-002](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ct-002) | CT | FUNC-P7-002 | A-B-C 통로,문 B 닫힘,소음전파차단; 같은요청2 회 | C 의 그룹이 소음을 감지하지 않음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P7-IT-002](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-it-002) | IT | FUNC-P7-002 | A-B-C 통로,문 B 닫힘,소음전파차단; 모듈 adapter 를실제 구현으로교체 | C 의 그룹이 소음을 감지하지 않음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P7-UT-003](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ut-003) | UT | FUNC-P7-003 | HP60→20 으로 한 배치,50/25 phase 경계 | 정책상 도달최종 phase 로1 회 전환·보상 중복0 | NOT_RUN |
| [P7-BT-003](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-bt-003) | BT | FUNC-P7-003 | BREAK 게이지 정확0 | 해당 phase BREAK event1 개 | NOT_RUN |
| [P7-FT-003](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ft-003) | FT | FUNC-P7-003 | 무적 phase 에 해제조건 없음 | 콘텐츠 검증에서 차단·원정 생성금지 | NOT_RUN |
| [P7-CT-003](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ct-003) | CT | FUNC-P7-003 | HP60→20 으로 한 배치,50/25 phase 경계; 같은요청2 회 | 정책상 도달최종 phase 로1 회 전환·보상 중복0; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P7-IT-003](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-it-003) | IT | FUNC-P7-003 | HP60→20 으로 한 배치,50/25 phase 경계; 모듈 adapter 를실제 구현으로교체 | 정책상 도달최종 phase 로1 회 전환·보상 중복0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P7-UT-004](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ut-004) | UT | FUNC-P7-004 | 2 개 파티 각6 인 공략대 | 공유 시간축12 인,파티당6 제한 유지 | NOT_RUN |
| [P7-BT-004](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-bt-004) | BT | FUNC-P7-004 | 활성적8 과 증원3 | 3 개는 wave 대기·즉시 actor 증식없음 | NOT_RUN |
| [P7-FT-004](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ft-004) | FT | FUNC-P7-004 | 최초보스보상 receipt 이미있음 | 재공략 일반보상만·최초보상0 | NOT_RUN |
| [P7-CT-004](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-ct-004) | CT | FUNC-P7-004 | 2 개 파티 각6 인 공략대; 같은요청2 회 | 공유 시간축12 인,파티당6 제한 유지; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P7-IT-004](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-it-004) | IT | FUNC-P7-004 | 2 개 파티 각6 인 공략대; 모듈 adapter 를실제 구현으로교체 | 공유 시간축12 인,파티당6 제한 유지; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P7-RT-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-rt-001) | RT | PHASE-7 | 후열 사제 존재를 아직 감지 못한 몹; 선행 Phase 의승인 fixture 전체 | 미관측 사제를 이름/직업 근거로 우선공격하지 않음; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P7-CN-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-cn-001) | CN | PHASE-7 | 후열 사제 존재를 아직 감지 못한 몹; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P7-REC-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-rec-001) | REC | PHASE-7 | AI profile 파손; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P7-PT-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-pt-001) | PT | PHASE-7 | 후열 사제 존재를 아직 감지 못한 몹; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P7-OP-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-op-001) | OP | PHASE-7 | 2 개 파티 각6 인 공략대; 네트워크차단·앱재실행/도구재실행 | 공유 시간축12 인,파티당6 제한 유지; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P7-ET-001](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-et-001) | ET | PHASE-7 | 최초보스보상 receipt 이미있음 | 재공략 일반보상만·최초보상0; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P7-IT-005](08_Phase7_몬스터AI_보스_공략대전투_상세설계서.md#p7-it-005) | IT | PHASE-7 | 후열 사제 존재를 아직 감지 못한 몹→2 개 파티 각6 인 공략대 | 미관측 사제를 이름/직업 근거로 우선공격하지 않음 및 공유 시간축12 인,파티당6 제한 유지; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P8-UT-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ut-001) | UT | FUNC-P8-001 | 문 K 뒤에만 열쇠 K 가 있음 | 토폴로지 실패,검증된 다른 구조로 재생성 | NOT_RUN |
| [P8-BT-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-bt-001) | BT | FUNC-P8-001 | 보스없는 F 자연동굴 | 목표 room 도달성 검증으로 정상 | NOT_RUN |
| [P8-FT-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ft-001) | FT | FUNC-P8-001 | 8 회 연속 위상 실패 | 검증 fallback 또는 생성보류; 끝없는 retry 없음 | NOT_RUN |
| [P8-CT-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ct-001) | CT | FUNC-P8-001 | 문 K 뒤에만 열쇠 K 가 있음; 같은요청2 회 | 토폴로지 실패,검증된 다른 구조로 재생성; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P8-IT-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-it-001) | IT | FUNC-P8-001 | 문 K 뒤에만 열쇠 K 가 있음; 모듈 adapter 를실제 구현으로교체 | 토폴로지 실패,검증된 다른 구조로 재생성; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P8-UT-002](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ut-002) | UT | FUNC-P8-002 | C 중형 예산560,배분280/90/70/90/30 | 합계560·불일치0 | NOT_RUN |
| [P8-BT-002](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-bt-002) | BT | FUNC-P8-002 | 초거대300+ 방 | 상한은 profile 의 explicit maxRooms 로 유한화 | NOT_RUN |
| [P8-FT-002](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ft-002) | FT | FUNC-P8-002 | 예산 음수/NaN | InvalidDungeonBudget·상태저장0 | NOT_RUN |
| [P8-CT-002](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ct-002) | CT | FUNC-P8-002 | C 중형 예산560,배분280/90/70/90/30; 같은요청2 회 | 합계560·불일치0; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P8-IT-002](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-it-002) | IT | FUNC-P8-002 | C 중형 예산560,배분280/90/70/90/30; 모듈 adapter 를실제 구현으로교체 | 합계560·불일치0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P8-UT-003](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ut-003) | UT | FUNC-P8-003 | 전부알려진방 조사완료·보스미처치 | 탐색100% 가능·정복미완료 | NOT_RUN |
| [P8-BT-003](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-bt-003) | BT | FUNC-P8-003 | 상자0 개인 극소형 | empty loot 정상·최소상자 강제생성없음 | NOT_RUN |
| [P8-FT-003](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ft-003) | FT | FUNC-P8-003 | 필수열쇠 드롭그룹 배치불가 | 생성실패·잠긴던전 발행없음 | NOT_RUN |
| [P8-CT-003](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ct-003) | CT | FUNC-P8-003 | 전부알려진방 조사완료·보스미처치; 같은요청2 회 | 탐색100% 가능·정복미완료; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P8-IT-003](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-it-003) | IT | FUNC-P8-003 | 전부알려진방 조사완료·보스미처치; 모듈 adapter 를실제 구현으로교체 | 탐색100% 가능·정복미완료; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P8-UT-004](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ut-004) | UT | FUNC-P8-004 | 던전 D 저장 후로드 같은버전 | 연결·상자·보스·Seed hash 동일 | NOT_RUN |
| [P8-BT-004](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-bt-004) | BT | FUNC-P8-004 | deadline 정확도달 | 브레이크 event1 개 | NOT_RUN |
| [P8-FT-004](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ft-004) | FT | FUNC-P8-004 | 기존 generatorVersion 미지원 | 보존된 map 사용·없으면 compatibility 차단 | NOT_RUN |
| [P8-CT-004](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-ct-004) | CT | FUNC-P8-004 | 던전 D 저장 후로드 같은버전; 같은요청2 회 | 연결·상자·보스·Seed hash 동일; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P8-IT-004](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-it-004) | IT | FUNC-P8-004 | 던전 D 저장 후로드 같은버전; 모듈 adapter 를실제 구현으로교체 | 연결·상자·보스·Seed hash 동일; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P8-RT-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-rt-001) | RT | PHASE-8 | 문 K 뒤에만 열쇠 K 가 있음; 선행 Phase 의승인 fixture 전체 | 토폴로지 실패,검증된 다른 구조로 재생성; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P8-CN-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-cn-001) | CN | PHASE-8 | 문 K 뒤에만 열쇠 K 가 있음; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P8-REC-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-rec-001) | REC | PHASE-8 | 8 회 연속 위상 실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P8-PT-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-pt-001) | PT | PHASE-8 | 문 K 뒤에만 열쇠 K 가 있음; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P8-OP-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-op-001) | OP | PHASE-8 | 던전 D 저장 후로드 같은버전; 네트워크차단·앱재실행/도구재실행 | 연결·상자·보스·Seed hash 동일; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P8-ET-001](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-et-001) | ET | PHASE-8 | 기존 generatorVersion 미지원 | 보존된 map 사용·없으면 compatibility 차단; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P8-IT-005](09_Phase8_던전생성_그래프_위험예산_상세설계서.md#p8-it-005) | IT | PHASE-8 | 문 K 뒤에만 열쇠 K 가 있음→던전 D 저장 후로드 같은버전 | 토폴로지 실패,검증된 다른 구조로 재생성 및 연결·상자·보스·Seed hash 동일; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P9-UT-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ut-001) | UT | FUNC-P9-001 | 조사점수100/100 에서 비밀방 점수10 발견 | 100/110=90.9%로 감소·새 조사대상 안내 | NOT_RUN |
| [P9-BT-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-bt-001) | BT | FUNC-P9-001 | 연결되지 않은 방으로 이동 명령 | InvalidConnection·시간/식량 소비0 | NOT_RUN |
| [P9-FT-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ft-001) | FT | FUNC-P9-001 | 조사 중 저장실패 | 발견·시간·RNG 모두 직전 커밋 상태 | NOT_RUN |
| [P9-CT-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ct-001) | CT | FUNC-P9-001 | 조사점수100/100 에서 비밀방 점수10 발견; 같은요청2 회 | 100/110=90.9%로 감소·새 조사대상 안내; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P9-IT-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-it-001) | IT | FUNC-P9-001 | 조사점수100/100 에서 비밀방 점수10 발견; 모듈 adapter 를실제 구현으로교체 | 100/110=90.9%로 감소·새 조사대상 안내; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P9-UT-002](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ut-002) | UT | FUNC-P9-002 | 알려진 A-B 10 분/B-C20 분 경로 | 30 분 진행 후 C·중간 사건은 해당 경계 처리 | NOT_RUN |
| [P9-BT-002](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-bt-002) | BT | FUNC-P9-002 | 유일 통로 붕괴 | 경로불가 표시·정복 성공으로 처리하지 않음 | NOT_RUN |
| [P9-FT-002](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ft-002) | FT | FUNC-P9-002 | map annotation 손상 | 지도 원본은 유지·손상 주석 격리·경고 | NOT_RUN |
| [P9-CT-002](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ct-002) | CT | FUNC-P9-002 | 같은 주석 저장 CommandEnvelope 2 회; 같은 ID/다른 주석 1 회 | map_annotation 1 행·receipt/Event 1 세트; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P9-IT-002](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-it-002) | IT | FUNC-P9-002 | 주석 저장 후 A-B 10 분/B-C20 분 안전 복귀; 모듈 adapter 를실제 구현으로교체 | 주석과 도착 C/30 분/중간 Event/receipt가 새세션에도 동일하고 route 조회만 수행하면 save hash 불변 | NOT_RUN |
| [P9-UT-003](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ut-003) | UT | FUNC-P9-003 | 8 시간야영 예약 중3 시간에 습격 | 3 시간 회복·소비만 정산 후전투·남은5 시간 취소/재개선택 | NOT_RUN |
| [P9-BT-003](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-bt-003) | BT | FUNC-P9-003 | 경계 가능한 인원0 | 위험도 증가 명시·허용여부 profile 판정 | NOT_RUN |
| [P9-FT-003](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ft-003) | FT | FUNC-P9-003 | 야영중 앱종료 후현실24 시간 | 게임시간/회복 추가0·체크포인트부터 재개 | NOT_RUN |
| [P9-CT-003](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ct-003) | CT | FUNC-P9-003 | 8 시간야영 예약 중3 시간에 습격; 같은요청2 회 | 3 시간 회복·소비만 정산 후전투·남은5 시간 취소/재개선택; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P9-IT-003](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-it-003) | IT | FUNC-P9-003 | 8 시간야영 예약 중3 시간에 습격; 모듈 adapter 를실제 구현으로교체 | 3 시간 회복·소비만 정산 후전투·남은5 시간 취소/재개선택; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P9-UT-004](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ut-004) | UT | FUNC-P9-004 | 휴대금1000,내구80,최대 HP100,구조실패,추가0 | 금900·내구72·HP30·12 시간 증가·패배기록 유지 | NOT_RUN |
| [P9-BT-004](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-bt-004) | BT | FUNC-P9-004 | 휴대금9,정책=floor(gold*10%) | 금9 유지·손실0; 창고금화 미변경 | NOT_RUN |
| [P9-FT-004](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ft-004) | FT | FUNC-P9-004 | 같은 defeatId 두 번 복귀 | 두 번째 금/내구/시간 추가손실0 | NOT_RUN |
| [P9-CT-004](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ct-004) | CT | FUNC-P9-004 | 휴대금1000,내구80,최대 HP100,구조실패,추가0; 같은요청2 회 | 금900·내구72·HP30·12 시간 증가·패배기록 유지; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P9-IT-004](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-it-004) | IT | FUNC-P9-004 | 휴대금1000,내구80,최대 HP100,구조실패,추가0; 모듈 adapter 를실제 구현으로교체 | 금900·내구72·HP30·12 시간 증가·패배기록 유지; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P9-UT-005](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ut-005) | UT | FUNC-P9-005 | 시작→F 던전→승리→상자→후퇴→저장로드 | 아이템1 회·시간 증가·같은 방/금/XP 복원 | NOT_RUN |
| [P9-BT-005](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-bt-005) | BT | FUNC-P9-005 | 보스처치 탐색71% | 조건충족이면 정복완료·탐색71% 보존 | NOT_RUN |
| [P9-FT-005](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ft-005) | FT | FUNC-P9-005 | 보상생성 후 DB commit 실패 | 지급/정복 모두 rollback·동일정산 재시도 가능 | NOT_RUN |
| [P9-CT-005](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-ct-005) | CT | FUNC-P9-005 | 시작→F 던전→승리→상자→후퇴→저장로드; 같은요청2 회 | 아이템1 회·시간 증가·같은 방/금/XP 복원; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P9-IT-005](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-it-005) | IT | FUNC-P9-005 | 시작→F 던전→승리→상자→후퇴→저장로드; 모듈 adapter 를실제 구현으로교체 | 아이템1 회·시간 증가·같은 방/금/XP 복원; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P9-RT-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-rt-001) | RT | PHASE-9 | 조사점수100/100 에서 비밀방 점수10 발견; 선행 Phase 의승인 fixture 전체 | 100/110=90.9%로 감소·새 조사대상 안내; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P9-CN-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-cn-001) | CN | PHASE-9 | 조사점수100/100 에서 비밀방 점수10 발견; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P9-REC-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-rec-001) | REC | PHASE-9 | 조사 중 저장실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P9-PT-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-pt-001) | PT | PHASE-9 | 조사점수100/100 에서 비밀방 점수10 발견; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P9-OP-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-op-001) | OP | PHASE-9 | 시작→F 던전→승리→상자→후퇴→저장로드; 네트워크차단·앱재실행/도구재실행 | 아이템1 회·시간 증가·같은 방/금/XP 복원; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P9-ET-001](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-et-001) | ET | PHASE-9 | 보상생성 후 DB commit 실패 | 지급/정복 모두 rollback·동일정산 재시도 가능; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P9-IT-006](10_Phase9_탐색_야영_패배_핵심플레이_상세설계서.md#p9-it-006) | IT | PHASE-9 | 조사점수100/100 에서 비밀방 점수10 발견→시작→F 던전→승리→상자→후퇴→저장로드 | 100/110=90.9%로 감소·새 조사대상 안내 및 아이템1 회·시간 증가·같은 방/금/XP 복원; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P10-UT-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ut-001) | UT | FUNC-P10-001 | 18 시폐점 시설에17:50 도착·서비스5 분 | 서비스성공17:55 | NOT_RUN |
| [P10-BT-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-bt-001) | BT | FUNC-P10-001 | 18:00 도착,마감 exclusive | 폐점·금소비0·대기선택 | NOT_RUN |
| [P10-FT-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ft-001) | FT | FUNC-P10-001 | 시설 파괴 이벤트와 동시방문 | 새 version 확인 후 FacilityUnavailable·기존예약 보상정책 적용 | NOT_RUN |
| [P10-CT-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ct-001) | CT | FUNC-P10-001 | 18 시폐점 시설에17:50 도착·서비스5 분; 같은요청2 회 | 서비스성공17:55; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P10-IT-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-it-001) | IT | FUNC-P10-001 | 18 시폐점 시설에17:50 도착·서비스5 분; 모듈 adapter 를실제 구현으로교체 | 서비스성공17:55; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P10-UT-002](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ut-002) | UT | FUNC-P10-002 | 비용100·치료6 시간·금200 | 금100·치료예약1 개·6 게임시간 후완료 | NOT_RUN |
| [P10-BT-002](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-bt-002) | BT | FUNC-P10-002 | 5 시간59 분 상태조회 | 완료아님·HP 회복이 부상소멸을 의미하지 않음 | NOT_RUN |
| [P10-FT-002](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ft-002) | FT | FUNC-P10-002 | 치료완료 경계 재처리 | 부상제거/환급/연대기 이벤트 각1 회 | NOT_RUN |
| [P10-CT-002](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ct-002) | CT | FUNC-P10-002 | 비용100·치료6 시간·금200; 같은요청2 회 | 금100·치료예약1 개·6 게임시간 후완료; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P10-IT-002](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-it-002) | IT | FUNC-P10-002 | 비용100·치료6 시간·금200; 모듈 adapter 를실제 구현으로교체 | 금100·치료예약1 개·6 게임시간 후완료; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P10-UT-003](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ut-003) | UT | FUNC-P10-003 | 같은 월 경계 두 번 처리·월세30 | 30 금1 회 차감·두 번째0 | NOT_RUN |
| [P10-BT-003](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-bt-003) | BT | FUNC-P10-003 | 이사후 이전창고에보호유물 | 자동소실없음·이관대기 보관 유지 | NOT_RUN |
| [P10-FT-003](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ft-003) | FT | FUNC-P10-003 | 주택구매중잔액부족 | 주택/자금/창고 모두변경0 | NOT_RUN |
| [P10-CT-003](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ct-003) | CT | FUNC-P10-003 | 같은 월 경계 두 번 처리·월세30; 같은요청2 회 | 30 금1 회 차감·두 번째0; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P10-IT-003](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-it-003) | IT | FUNC-P10-003 | 같은 월 경계 두 번 처리·월세30; 모듈 adapter 를실제 구현으로교체 | 30 금1 회 차감·두 번째0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P10-UT-004](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ut-004) | UT | FUNC-P10-004 | 예산50,수리20·포션40 순서 | 수리성공·포션예산부족·실지출20 | NOT_RUN |
| [P10-BT-004](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-bt-004) | BT | FUNC-P10-004 | 항목0 개 프리셋 | 정상 empty report·시간소비0 | NOT_RUN |
| [P10-FT-004](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ft-004) | FT | FUNC-P10-004 | 두번째항목실패후 batch 재시도 | 첫수리 재청구0·실패항목만 재검증 | NOT_RUN |
| [P10-CT-004](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-ct-004) | CT | FUNC-P10-004 | 예산50,수리20·포션40 순서; 같은요청2 회 | 수리성공·포션예산부족·실지출20; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P10-IT-004](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-it-004) | IT | FUNC-P10-004 | 예산50,수리20·포션40 순서; 모듈 adapter 를실제 구현으로교체 | 수리성공·포션예산부족·실지출20; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P10-RT-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-rt-001) | RT | PHASE-10 | 18 시폐점 시설에17:50 도착·서비스5 분; 선행 Phase 의승인 fixture 전체 | 서비스성공17:55; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P10-CN-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-cn-001) | CN | PHASE-10 | 18 시폐점 시설에17:50 도착·서비스5 분; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P10-REC-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-rec-001) | REC | PHASE-10 | 시설 파괴 이벤트와 동시방문; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P10-PT-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-pt-001) | PT | PHASE-10 | 18 시폐점 시설에17:50 도착·서비스5 분; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P10-OP-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-op-001) | OP | PHASE-10 | 예산50,수리20·포션40 순서; 네트워크차단·앱재실행/도구재실행 | 수리성공·포션예산부족·실지출20; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P10-ET-001](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-et-001) | ET | PHASE-10 | 두번째항목실패후 batch 재시도 | 첫수리 재청구0·실패항목만 재검증; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P10-IT-005](11_Phase10_도시_시설_주거_치료_상세설계서.md#p10-it-005) | IT | PHASE-10 | 18 시폐점 시설에17:50 도착·서비스5 분→예산50,수리20·포션40 순서 | 서비스성공17:55 및 수리성공·포션예산부족·실지출20; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P11-UT-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ut-001) | UT | FUNC-P11-001 | 몬스터3 처치 의뢰에 동일 kill event2 번+서로다른2 개 | 진행3/3·완료보상1 회 | NOT_RUN |
| [P11-BT-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-bt-001) | BT | FUNC-P11-001 | 기한시각과 완료시각같음 | 정의된 inclusive 기한 정책으로 판정·수행순서와무관 | NOT_RUN |
| [P11-FT-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ft-001) | FT | FUNC-P11-001 | 보상중인벤토리초과 | overflow 보관·보상분실/중복없음 | NOT_RUN |
| [P11-CT-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ct-001) | CT | FUNC-P11-001 | 몬스터3 처치 의뢰에 동일 kill event2 번+서로다른2 개; 같은요청2 회 | 진행3/3·완료보상1 회; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P11-IT-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-it-001) | IT | FUNC-P11-001 | 몬스터3 처치 의뢰에 동일 kill event2 번+서로다른2 개; 모듈 adapter 를실제 구현으로교체 | 진행3/3·완료보상1 회; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P11-UT-002](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ut-002) | UT | FUNC-P11-002 | 지원자 가용·계약금50·금100·수락 | 금50·유효계약1·출전예약아직없음 | NOT_RUN |
| [P11-BT-002](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-bt-002) | BT | FUNC-P11-002 | 이미 타원정중 NPC 수락 | Unavailable·계약금차감0 | NOT_RUN |
| [P11-FT-002](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ft-002) | FT | FUNC-P11-002 | 수락 버튼동시2 회 | 하나의 계약·하나의 금차감 | NOT_RUN |
| [P11-CT-002](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ct-002) | CT | FUNC-P11-002 | 지원자 가용·계약금50·금100·수락; 같은요청2 회 | 금50·유효계약1·출전예약아직없음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P11-IT-002](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-it-002) | IT | FUNC-P11-002 | 지원자 가용·계약금50·금100·수락; 모듈 adapter 를실제 구현으로교체 | 금50·유효계약1·출전예약아직없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P11-UT-003](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ut-003) | UT | FUNC-P11-003 | 만료·미지급급여20·대여장비 I1 | 급여정산·I1 반환/회수대기·부당해지페널티0 | NOT_RUN |
| [P11-BT-003](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-bt-003) | BT | FUNC-P11-003 | 던전내 일방퇴출 | 안전지역까지보류·NPC 삭제없음 | NOT_RUN |
| [P11-FT-003](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ft-003) | FT | FUNC-P11-003 | 위약금차감중실패 | 계약종료와차감 모두 rollback | NOT_RUN |
| [P11-CT-003](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ct-003) | CT | FUNC-P11-003 | 만료·미지급급여20·대여장비 I1; 같은요청2 회 | 급여정산·I1 반환/회수대기·부당해지페널티0; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P11-IT-003](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-it-003) | IT | FUNC-P11-003 | 만료·미지급급여20·대여장비 I1; 모듈 adapter 를실제 구현으로교체 | 급여정산·I1 반환/회수대기·부당해지페널티0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P11-UT-004](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ut-004) | UT | FUNC-P11-004 | 상점 NPC 대화에서구입→동일 BuyCommand | 직접구입과동일금/재고변화·대화비용정책1 회 | NOT_RUN |
| [P11-BT-004](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-bt-004) | BT | FUNC-P11-004 | 선택후예전 node 재전송 | StaleDialogueChoice·추가보상0 | NOT_RUN |
| [P11-FT-004](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ft-004) | FT | FUNC-P11-004 | 필수대사 template 누락 | 안내문+되돌아가기·핵심거래 use case 는직접화면으로가능 | NOT_RUN |
| [P11-CT-004](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-ct-004) | CT | FUNC-P11-004 | 상점 NPC 대화에서구입→동일 BuyCommand; 같은요청2 회 | 직접구입과동일금/재고변화·대화비용정책1 회; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P11-IT-004](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-it-004) | IT | FUNC-P11-004 | 상점 NPC 대화에서구입→동일 BuyCommand; 모듈 adapter 를실제 구현으로교체 | 직접구입과동일금/재고변화·대화비용정책1 회; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P11-RT-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-rt-001) | RT | PHASE-11 | 몬스터3 처치 의뢰에 동일 kill event2 번+서로다른2 개; 선행 Phase 의승인 fixture 전체 | 진행3/3·완료보상1 회; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P11-CN-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-cn-001) | CN | PHASE-11 | 몬스터3 처치 의뢰에 동일 kill event2 번+서로다른2 개; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P11-REC-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-rec-001) | REC | PHASE-11 | 보상중인벤토리초과; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P11-PT-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-pt-001) | PT | PHASE-11 | 몬스터3 처치 의뢰에 동일 kill event2 번+서로다른2 개; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P11-OP-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-op-001) | OP | PHASE-11 | 상점 NPC 대화에서구입→동일 BuyCommand; 네트워크차단·앱재실행/도구재실행 | 직접구입과동일금/재고변화·대화비용정책1 회; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P11-ET-001](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-et-001) | ET | PHASE-11 | 필수대사 template 누락 | 안내문+되돌아가기·핵심거래 use case 는직접화면으로가능; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P11-IT-005](12_Phase11_의뢰_영입_대화_상세설계서.md#p11-it-005) | IT | PHASE-11 | 몬스터3 처치 의뢰에 동일 kill event2 번+서로다른2 개→상점 NPC 대화에서구입→동일 BuyCommand | 진행3/3·완료보상1 회 및 직접구입과동일금/재고변화·대화비용정책1 회; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P12-UT-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ut-001) | UT | FUNC-P12-001 | +19→20 기본1%·scripted draw0.005·보정0 | +20 성공·비용1 회·attemptNo+1 | NOT_RUN |
| [P12-BT-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-bt-001) | BT | FUNC-P12-001 | +20 장비에강화명령 | MaxEnhancement·비용/시도증가0 | NOT_RUN |
| [P12-FT-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ft-001) | FT | FUNC-P12-001 | 비용차감뒤쓰기실패 | 금/재료/시도/강화 상태 전체 rollback | NOT_RUN |
| [P12-CT-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ct-001) | CT | FUNC-P12-001 | +19→20 기본1%·scripted draw0.005·보정0; 같은요청2 회 | +20 성공·비용1 회·attemptNo+1; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P12-IT-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-it-001) | IT | FUNC-P12-001 | +19→20 기본1%·scripted draw0.005·보정0; 모듈 adapter 를실제 구현으로교체 | +20 성공·비용1 회·attemptNo+1; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P12-UT-002](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ut-002) | UT | FUNC-P12-002 | 기본위력100·대성공없는+20 | 기본강화적용위력180 | NOT_RUN |
| [P12-BT-002](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-bt-002) | BT | FUNC-P12-002 | 각인활성3 상태에서네번째활성 | 비활성보관·장비각인자체소실없음 | NOT_RUN |
| [P12-FT-002](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ft-002) | FT | FUNC-P12-002 | +15 획득후하락14→15 반복 | 승인정책상15 단계추가성장재추첨0 | NOT_RUN |
| [P12-CT-002](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ct-002) | CT | FUNC-P12-002 | 기본위력100·대성공없는+20; 같은요청2 회 | 기본강화적용위력180; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P12-IT-002](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-it-002) | IT | FUNC-P12-002 | 기본위력100·대성공없는+20; 모듈 adapter 를실제 구현으로교체 | 기본강화적용위력180; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P12-UT-003](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ut-003) | UT | FUNC-P12-003 | 잠금1 줄+대상1 줄정련 | 잠금값동일·대상줄만변경·비용1 회 | NOT_RUN |
| [P12-BT-003](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-bt-003) | BT | FUNC-P12-003 | 대여장비를계승소재로선택 | ProtectedOwnership·변경0 | NOT_RUN |
| [P12-FT-003](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ft-003) | FT | FUNC-P12-003 | 계승목표/소재동일 ID | InvalidTarget·아이템소실없음 | NOT_RUN |
| [P12-CT-003](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ct-003) | CT | FUNC-P12-003 | 잠금1 줄+대상1 줄정련; 같은요청2 회 | 잠금값동일·대상줄만변경·비용1 회; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P12-IT-003](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-it-003) | IT | FUNC-P12-003 | 잠금1 줄+대상1 줄정련; 모듈 adapter 를실제 구현으로교체 | 잠금값동일·대상줄만변경·비용1 회; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P12-UT-004](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ut-004) | UT | FUNC-P12-004 | 철괴3 필요·현재5·제작2 시간 | 가용2/예약3·2 게임시간후산출1 개 | NOT_RUN |
| [P12-BT-004](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-bt-004) | BT | FUNC-P12-004 | 같은재료로동시2 주문각3 | 두번째 MaterialUnavailable | NOT_RUN |
| [P12-FT-004](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ft-004) | FT | FUNC-P12-004 | 완료직전프로세스종료 | 로드후완료 receipt 기준산출1 개·재료중복차감0 | NOT_RUN |
| [P12-CT-004](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-ct-004) | CT | FUNC-P12-004 | 철괴3 필요·현재5·제작2 시간; 같은요청2 회 | 가용2/예약3·2 게임시간후산출1 개; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P12-IT-004](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-it-004) | IT | FUNC-P12-004 | 철괴3 필요·현재5·제작2 시간; 모듈 adapter 를실제 구현으로교체 | 가용2/예약3·2 게임시간후산출1 개; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P12-RT-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-rt-001) | RT | PHASE-12 | +19→20 기본1%·scripted draw0.005·보정0; 선행 Phase 의승인 fixture 전체 | +20 성공·비용1 회·attemptNo+1; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P12-CN-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-cn-001) | CN | PHASE-12 | +19→20 기본1%·scripted draw0.005·보정0; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P12-REC-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-rec-001) | REC | PHASE-12 | 비용차감뒤쓰기실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P12-PT-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-pt-001) | PT | PHASE-12 | +19→20 기본1%·scripted draw0.005·보정0; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P12-OP-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-op-001) | OP | PHASE-12 | 철괴3 필요·현재5·제작2 시간; 네트워크차단·앱재실행/도구재실행 | 가용2/예약3·2 게임시간후산출1 개; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P12-ET-001](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-et-001) | ET | PHASE-12 | 완료직전프로세스종료 | 로드후완료 receipt 기준산출1 개·재료중복차감0; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P12-IT-005](13_Phase12_강화_제작_정련_유물복원_상세설계서.md#p12-it-005) | IT | PHASE-12 | +19→20 기본1%·scripted draw0.005·보정0→철괴3 필요·현재5·제작2 시간 | +20 성공·비용1 회·attemptNo+1 및 가용2/예약3·2 게임시간후산출1 개; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P13-UT-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ut-001) | UT | FUNC-P13-001 | 정식8+고용2·출전6 | 유효조직10·출전6 | NOT_RUN |
| [P13-BT-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-bt-001) | BT | FUNC-P13-001 | 조직11 명또는출전7 명 | CapacityExceeded·원본명단유지 | NOT_RUN |
| [P13-FT-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ft-001) | FT | FUNC-P13-001 | 교대도중저장실패 | 기존출전6 명그대로·중복출전0 | NOT_RUN |
| [P13-CT-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ct-001) | CT | FUNC-P13-001 | 정식8+고용2·출전6; 같은요청2 회 | 유효조직10·출전6; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P13-IT-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-it-001) | IT | FUNC-P13-001 | 정식8+고용2·출전6; 모듈 adapter 를실제 구현으로교체 | 유효조직10·출전6; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P13-UT-002](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ut-002) | UT | FUNC-P13-002 | 100 금3 인균등·잔액공동기금정책 | 각33 금·공동기금1 금·합100 | NOT_RUN |
| [P13-BT-002](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-bt-002) | BT | FUNC-P13-002 | 동일멤버투표2 번 | 정책상수정또는거절·유권자수증가0 | NOT_RUN |
| [P13-FT-002](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ft-002) | FT | FUNC-P13-002 | 분배중한수령자없음 | 전체분배보류·다른멤버만지급하지않음 | NOT_RUN |
| [P13-CT-002](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ct-002) | CT | FUNC-P13-002 | 100 금3 인균등·잔액공동기금정책; 같은요청2 회 | 각33 금·공동기금1 금·합100; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P13-IT-002](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-it-002) | IT | FUNC-P13-002 | 100 금3 인균등·잔액공동기금정책; 모듈 adapter 를실제 구현으로교체 | 각33 금·공동기금1 금·합100; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P13-UT-003](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ut-003) | UT | FUNC-P13-003 | 구성원10 명파티가6/4 로합의분열 | 구성원총10·자산총액/부채총액보존·history 연결 | NOT_RUN |
| [P13-BT-003](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-bt-003) | BT | FUNC-P13-003 | 리더퇴임·적격후보0 | 대행/선거대기·파티삭제없음 | NOT_RUN |
| [P13-FT-003](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ft-003) | FT | FUNC-P13-003 | 분열후자산합원본과불일치 | InvariantViolation·분열 rollback | NOT_RUN |
| [P13-CT-003](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ct-003) | CT | FUNC-P13-003 | 구성원10 명파티가6/4 로합의분열; 같은요청2 회 | 구성원총10·자산총액/부채총액보존·history 연결; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P13-IT-003](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-it-003) | IT | FUNC-P13-003 | 구성원10 명파티가6/4 로합의분열; 모듈 adapter 를실제 구현으로교체 | 구성원총10·자산총액/부채총액보존·history 연결; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P13-UT-004](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ut-004) | UT | FUNC-P13-004 | 29 일연속1 위·다음날마감1 위 | streak30·후보 event1 개 | NOT_RUN |
| [P13-BT-004](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-bt-004) | BT | FUNC-P13-004 | 30 일도중하루2 위 | streak0;순간1 위복귀만으로증표없음 | NOT_RUN |
| [P13-FT-004](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ft-004) | FT | FUNC-P13-004 | 같은 dayCloseId 재실행 | 점수/연속일/후보 event 추가0 | NOT_RUN |
| [P13-CT-004](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-ct-004) | CT | FUNC-P13-004 | 29 일연속1 위·다음날마감1 위; 같은요청2 회 | streak30·후보 event1 개; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P13-IT-004](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-it-004) | IT | FUNC-P13-004 | 29 일연속1 위·다음날마감1 위; 모듈 adapter 를실제 구현으로교체 | streak30·후보 event1 개; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P13-RT-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-rt-001) | RT | PHASE-13 | 정식8+고용2·출전6; 선행 Phase 의승인 fixture 전체 | 유효조직10·출전6; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P13-CN-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-cn-001) | CN | PHASE-13 | 정식8+고용2·출전6; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P13-REC-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-rec-001) | REC | PHASE-13 | 교대도중저장실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P13-PT-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-pt-001) | PT | PHASE-13 | 정식8+고용2·출전6; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P13-OP-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-op-001) | OP | PHASE-13 | 29 일연속1 위·다음날마감1 위; 네트워크차단·앱재실행/도구재실행 | streak30·후보 event1 개; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P13-ET-001](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-et-001) | ET | PHASE-13 | 같은 dayCloseId 재실행 | 점수/연속일/후보 event 추가0; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P13-IT-005](14_Phase13_파티운영_정치_랭킹_상세설계서.md#p13-it-005) | IT | PHASE-13 | 정식8+고용2·출전6→29 일연속1 위·다음날마감1 위 | 유효조직10·출전6 및 streak30·후보 event1 개; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P14-UT-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ut-001) | UT | FUNC-P14-001 | 포션재고10·구매2·금100·단가10 | 재고8·금80·상인매출20 | NOT_RUN |
| [P14-BT-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-bt-001) | BT | FUNC-P14-001 | 재고0 구매1 | OutOfStock·금변경0 | NOT_RUN |
| [P14-FT-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ft-001) | FT | FUNC-P14-001 | 같은도시일마감2 회 | 시장지수1 회갱신·원장중복0 | NOT_RUN |
| [P14-CT-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ct-001) | CT | FUNC-P14-001 | 포션재고10·구매2·금100·단가10; 같은요청2 회 | 재고8·금80·상인매출20; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P14-IT-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-it-001) | IT | FUNC-P14-001 | 포션재고10·구매2·금100·단가10; 모듈 adapter 를실제 구현으로교체 | 재고8·금80·상인매출20; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P14-UT-002](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ut-002) | UT | FUNC-P14-002 | 입찰 A100/B120·마감 | B 만소유권획득·A 예약금반환·판매자순대금 | NOT_RUN |
| [P14-BT-002](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-bt-002) | BT | FUNC-P14-002 | 마감시각과같은입찰 | 마감 exclusive 정책으로거절 | NOT_RUN |
| [P14-FT-002](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ft-002) | FT | FUNC-P14-002 | 낙찰중프로세스종료 | 이전경매미정산또는완전정산만복원 | NOT_RUN |
| [P14-CT-002](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ct-002) | CT | FUNC-P14-002 | 입찰 A100/B120·마감; 같은요청2 회 | B 만소유권획득·A 예약금반환·판매자순대금; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P14-IT-002](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-it-002) | IT | FUNC-P14-002 | 입찰 A100/B120·마감; 모듈 adapter 를실제 구현으로교체 | B 만소유권획득·A 예약금반환·판매자순대금; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P14-UT-003](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ut-003) | UT | FUNC-P14-003 | 길드소유 I1 을 NPC N1 대여 | owner=길드 유지·custodian=N1 | NOT_RUN |
| [P14-BT-003](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-bt-003) | BT | FUNC-P14-003 | 대여 I1 판매시도 | OwnershipDenied·금/아이템변경0 | NOT_RUN |
| [P14-FT-003](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ft-003) | FT | FUNC-P14-003 | 반환 receipt 재시도 | 소유권/보증금반환1 회 | NOT_RUN |
| [P14-CT-003](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ct-003) | CT | FUNC-P14-003 | 길드소유 I1 을 NPC N1 대여; 같은요청2 회 | owner=길드 유지·custodian=N1; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P14-IT-003](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-it-003) | IT | FUNC-P14-003 | 길드소유 I1 을 NPC N1 대여; 모듈 adapter 를실제 구현으로교체 | owner=길드 유지·custodian=N1; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P14-UT-004](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ut-004) | UT | FUNC-P14-004 | 창고 A I1→B 운송6 시간 | 이동중사용불가·6 게임시간후 B 단일위치 | NOT_RUN |
| [P14-BT-004](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-bt-004) | BT | FUNC-P14-004 | 같은운송도착2 번 | 물품/스택1 회인도 | NOT_RUN |
| [P14-FT-004](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ft-004) | FT | FUNC-P14-004 | 도착창고용량부족 | 도착임시보관함유지·아이템삭제0 | NOT_RUN |
| [P14-CT-004](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-ct-004) | CT | FUNC-P14-004 | 창고 A I1→B 운송6 시간; 같은요청2 회 | 이동중사용불가·6 게임시간후 B 단일위치; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P14-IT-004](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-it-004) | IT | FUNC-P14-004 | 창고 A I1→B 운송6 시간; 모듈 adapter 를실제 구현으로교체 | 이동중사용불가·6 게임시간후 B 단일위치; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P14-RT-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-rt-001) | RT | PHASE-14 | 포션재고10·구매2·금100·단가10; 선행 Phase 의승인 fixture 전체 | 재고8·금80·상인매출20; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P14-CN-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-cn-001) | CN | PHASE-14 | 포션재고10·구매2·금100·단가10; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P14-REC-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-rec-001) | REC | PHASE-14 | 같은도시일마감2 회; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P14-PT-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-pt-001) | PT | PHASE-14 | 포션재고10·구매2·금100·단가10; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P14-OP-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-op-001) | OP | PHASE-14 | 창고 A I1→B 운송6 시간; 네트워크차단·앱재실행/도구재실행 | 이동중사용불가·6 게임시간후 B 단일위치; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P14-ET-001](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-et-001) | ET | PHASE-14 | 도착창고용량부족 | 도착임시보관함유지·아이템삭제0; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P14-IT-005](15_Phase14_경제_경매_대여_물류_상세설계서.md#p14-it-005) | IT | PHASE-14 | 포션재고10·구매2·금100·단가10→창고 A I1→B 운송6 시간 | 재고8·금80·상인매출20 및 이동중사용불가·6 게임시간후 B 단일위치; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P15-UT-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ut-001) | UT | FUNC-P15-001 | 숨은잠재력20/90 두 NPC 의공개 정보동일 | 공개 DTO·검색정렬결과동일 | NOT_RUN |
| [P15-BT-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-bt-001) | BT | FUNC-P15-001 | 정보유효시각만료 | 추정/오래된정보표시·정확값으로승격없음 | NOT_RUN |
| [P15-FT-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ft-001) | FT | FUNC-P15-001 | 관측원인 ID 없음 | 관측거절·진실데이터변경0 | NOT_RUN |
| [P15-CT-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ct-001) | CT | FUNC-P15-001 | 숨은잠재력20/90 두 NPC 의공개 정보동일; 같은요청2 회 | 공개 DTO·검색정렬결과동일; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P15-IT-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-it-001) | IT | FUNC-P15-001 | 숨은잠재력20/90 두 NPC 의공개 정보동일; 모듈 adapter 를실제 구현으로교체 | 공개 DTO·검색정렬결과동일; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P15-UT-002](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ut-002) | UT | FUNC-P15-002 | 동일구조 event 를조합과도시에서중복수신 | 각평판 dimension 의정해진효과1 회 | NOT_RUN |
| [P15-BT-002](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-bt-002) | BT | FUNC-P15-002 | 평판최대치추가보너스 | cap 이상증가없음·원사건기록유지 | NOT_RUN |
| [P15-FT-002](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ft-002) | FT | FUNC-P15-002 | 판결증거누락 | 미확정 case·벌금자동차감0 | NOT_RUN |
| [P15-CT-002](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ct-002) | CT | FUNC-P15-002 | 동일구조 event 를조합과도시에서중복수신; 같은요청2 회 | 각평판 dimension 의정해진효과1 회; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P15-IT-002](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-it-002) | IT | FUNC-P15-002 | 동일구조 event 를조합과도시에서중복수신; 모듈 adapter 를실제 구현으로교체 | 각평판 dimension 의정해진효과1 회; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P15-UT-003](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ut-003) | UT | FUNC-P15-003 | 동일약속이행 event2 회 | 신뢰상승1 회·기억1 개 | NOT_RUN |
| [P15-BT-003](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-bt-003) | BT | FUNC-P15-003 | 대상관계거절플래그 | 연애제안거절·정상동료기능유지 | NOT_RUN |
| [P15-FT-003](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ft-003) | FT | FUNC-P15-003 | 기억압축중실패 | 원본기억유지·관계총합불변 | NOT_RUN |
| [P15-CT-003](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ct-003) | CT | FUNC-P15-003 | 동일약속이행 event2 회; 같은요청2 회 | 신뢰상승1 회·기억1 개; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P15-IT-003](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-it-003) | IT | FUNC-P15-003 | 동일약속이행 event2 회; 모듈 adapter 를실제 구현으로교체 | 신뢰상승1 회·기억1 개; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P15-UT-004](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ut-004) | UT | FUNC-P15-004 | 같은성격·다른 portraitKey 두 NPC | 동일상황의성격점수동일 | NOT_RUN |
| [P15-BT-004](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-bt-004) | BT | FUNC-P15-004 | 배타 trait 동시선택 | 생성검증 실패또는후보재선택기록 | NOT_RUN |
| [P15-FT-004](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ft-004) | FT | FUNC-P15-004 | 목표대상던전소멸 | 목표를실패/대안상태로전환·영구대기없음 | NOT_RUN |
| [P15-CT-004](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-ct-004) | CT | FUNC-P15-004 | 같은성격·다른 portraitKey 두 NPC; 같은요청2 회 | 동일상황의성격점수동일; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P15-IT-004](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-it-004) | IT | FUNC-P15-004 | 같은성격·다른 portraitKey 두 NPC; 모듈 adapter 를실제 구현으로교체 | 동일상황의성격점수동일; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P15-RT-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-rt-001) | RT | PHASE-15 | 숨은잠재력20/90 두 NPC 의공개 정보동일; 선행 Phase 의승인 fixture 전체 | 공개 DTO·검색정렬결과동일; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P15-CN-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-cn-001) | CN | PHASE-15 | 숨은잠재력20/90 두 NPC 의공개 정보동일; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P15-REC-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-rec-001) | REC | PHASE-15 | 관측원인 ID 없음; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P15-PT-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-pt-001) | PT | PHASE-15 | 숨은잠재력20/90 두 NPC 의공개 정보동일; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P15-OP-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-op-001) | OP | PHASE-15 | 같은성격·다른 portraitKey 두 NPC; 네트워크차단·앱재실행/도구재실행 | 동일상황의성격점수동일; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P15-ET-001](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-et-001) | ET | PHASE-15 | 목표대상던전소멸 | 목표를실패/대안상태로전환·영구대기없음; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P15-IT-005](16_Phase15_정보_평판_관계_인격_상세설계서.md#p15-it-005) | IT | PHASE-15 | 숨은잠재력20/90 두 NPC 의공개 정보동일→같은성격·다른 portraitKey 두 NPC | 공개 DTO·검색정렬결과동일 및 동일상황의성격점수동일; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P16-UT-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ut-001) | UT | FUNC-P16-001 | 간부자격충족플레이어가길드장선거승리 | 직위변경·길드 ID/역사유지 | NOT_RUN |
| [P16-BT-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-bt-001) | BT | FUNC-P16-001 | 플레이어 CreateGuild 직접명령 | UnsupportedPlayerAction·금/길드생성0 | NOT_RUN |
| [P16-FT-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ft-001) | FT | FUNC-P16-001 | 선출확정중 DB 실패 | 이전길드장또는완전새길드장·공백중간상태없음 | NOT_RUN |
| [P16-CT-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ct-001) | CT | FUNC-P16-001 | 간부자격충족플레이어가길드장선거승리; 같은요청2 회 | 직위변경·길드 ID/역사유지; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P16-IT-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-it-001) | IT | FUNC-P16-001 | 간부자격충족플레이어가길드장선거승리; 모듈 adapter 를실제 구현으로교체 | 직위변경·길드 ID/역사유지; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P16-UT-002](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ut-002) | UT | FUNC-P16-002 | 시설예산1000·공사600·기금900 | 600 예약·잔여가용300·게임시간후시설완료 | NOT_RUN |
| [P16-BT-002](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-bt-002) | BT | FUNC-P16-002 | 예산한도초과공사 | BudgetDenied·자금변경0 | NOT_RUN |
| [P16-FT-002](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ft-002) | FT | FUNC-P16-002 | 간부교체중기존미완료업무 | assignment ID 유지·새담당자에게한번인계 | NOT_RUN |
| [P16-CT-002](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ct-002) | CT | FUNC-P16-002 | 시설예산1000·공사600·기금900; 같은요청2 회 | 600 예약·잔여가용300·게임시간후시설완료; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P16-IT-002](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-it-002) | IT | FUNC-P16-002 | 시설예산1000·공사600·기금900; 모듈 adapter 를실제 구현으로교체 | 600 예약·잔여가용300·게임시간후시설완료; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P16-UT-003](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ut-003) | UT | FUNC-P16-003 | 정책찬성6/반대4·과반규약 | 가결1 회·집행은예산재확인 후진행 | NOT_RUN |
| [P16-BT-003](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-bt-003) | BT | FUNC-P16-003 | 투표마감뒤자격신규획득 | 해당회차투표권자동추가없음 | NOT_RUN |
| [P16-FT-003](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ft-003) | FT | FUNC-P16-003 | 공략대파티한곳준비실패 | 출발보류/명시부분출정선택·몰래전력제외없음 | NOT_RUN |
| [P16-CT-003](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ct-003) | CT | FUNC-P16-003 | 정책찬성6/반대4·과반규약; 같은요청2 회 | 가결1 회·집행은예산재확인 후진행; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P16-IT-003](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-it-003) | IT | FUNC-P16-003 | 정책찬성6/반대4·과반규약; 모듈 adapter 를실제 구현으로교체 | 가결1 회·집행은예산재확인 후진행; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P16-UT-004](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ut-004) | UT | FUNC-P16-004 | 모든항목각상한 | 총점10000·10001 불가 | NOT_RUN |
| [P16-BT-004](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-bt-004) | BT | FUNC-P16-004 | 인원만2 배·핵심12/성과동일 | 단순인원합전력보너스없음 | NOT_RUN |
| [P16-FT-004](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ft-004) | FT | FUNC-P16-004 | 30 일1 위지만플레이어기여부족 | 랭킹1 위유지·증표미발행·부족근거공개범위표시 | NOT_RUN |
| [P16-CT-004](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-ct-004) | CT | FUNC-P16-004 | 모든항목각상한; 같은요청2 회 | 총점10000·10001 불가; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P16-IT-004](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-it-004) | IT | FUNC-P16-004 | 모든항목각상한; 모듈 adapter 를실제 구현으로교체 | 총점10000·10001 불가; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P16-RT-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-rt-001) | RT | PHASE-16 | 간부자격충족플레이어가길드장선거승리; 선행 Phase 의승인 fixture 전체 | 직위변경·길드 ID/역사유지; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P16-CN-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-cn-001) | CN | PHASE-16 | 간부자격충족플레이어가길드장선거승리; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P16-REC-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-rec-001) | REC | PHASE-16 | 선출확정중 DB 실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P16-PT-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-pt-001) | PT | PHASE-16 | 간부자격충족플레이어가길드장선거승리; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P16-OP-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-op-001) | OP | PHASE-16 | 모든항목각상한; 네트워크차단·앱재실행/도구재실행 | 총점10000·10001 불가; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P16-ET-001](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-et-001) | ET | PHASE-16 | 30 일1 위지만플레이어기여부족 | 랭킹1 위유지·증표미발행·부족근거공개범위표시; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P16-IT-005](17_Phase16_길드운영_정치_공식랭킹_상세설계서.md#p16-it-005) | IT | PHASE-16 | 간부자격충족플레이어가길드장선거승리→모든항목각상한 | 직위변경·길드 ID/역사유지 및 총점10000·10001 불가; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P17-UT-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ut-001) | UT | FUNC-P17-001 | 중상 NPC·가용치료비충분 | 치료후보우선·위험원정강제선택없음 | NOT_RUN |
| [P17-BT-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-bt-001) | BT | FUNC-P17-001 | 금0·필수비용활동만존재 | 무료합법대안·잔액음수없음 | NOT_RUN |
| [P17-FT-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ft-001) | FT | FUNC-P17-001 | 목표대상아이템삭제 | 재계획·무한재시도없이목표상태 변경 | NOT_RUN |
| [P17-CT-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ct-001) | CT | FUNC-P17-001 | 중상 NPC·가용치료비충분; 같은요청2 회 | 치료후보우선·위험원정강제선택없음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P17-IT-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-it-001) | IT | FUNC-P17-001 | 중상 NPC·가용치료비충분; 모듈 adapter 를실제 구현으로교체 | 치료후보우선·위험원정강제선택없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P17-UT-002](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ut-002) | UT | FUNC-P17-002 | 축약던전완료 후동일시각플레이어조우로승격 | 보상1 회·진행중상태동일 | NOT_RUN |
| [P17-BT-002](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-bt-002) | BT | FUNC-P17-002 | NPC2000 명이같은일경계 | 안정된 entityId 순서·batch 크기와결과무관 | NOT_RUN |
| [P17-FT-002](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ft-002) | FT | FUNC-P17-002 | 특정 NPC 에잘못된핵심 상태 | 해당경계 commit 중단·오류 NPC 식별·정상행동인척 skip 금지 | NOT_RUN |
| [P17-CT-002](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ct-002) | CT | FUNC-P17-002 | 축약던전완료 후동일시각플레이어조우로승격; 같은요청2 회 | 보상1 회·진행중상태동일; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P17-IT-002](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-it-002) | IT | FUNC-P17-002 | 축약던전완료 후동일시각플레이어조우로승격; 모듈 adapter 를실제 구현으로교체 | 보상1 회·진행중상태동일; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P17-UT-003](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ut-003) | UT | FUNC-P17-003 | 현역1900+등록20-은퇴15-이주5 | 현역1900·각흐름원장40 건 | NOT_RUN |
| [P17-BT-003](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-bt-003) | BT | FUNC-P17-003 | 장기위기로현역1450 | 1450 그대로·원인/대응 event;50 명순간강제생성금지 | NOT_RUN |
| [P17-FT-003](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ft-003) | FT | FUNC-P17-003 | 동일출생코호트성인편입2 회 | 성인입력중복0 | NOT_RUN |
| [P17-CT-003](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ct-003) | CT | FUNC-P17-003 | 현역1900+등록20-은퇴15-이주5; 같은요청2 회 | 현역1900·각흐름원장40 건; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P17-IT-003](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-it-003) | IT | FUNC-P17-003 | 현역1900+등록20-은퇴15-이주5; 모듈 adapter 를실제 구현으로교체 | 현역1900·각흐름원장40 건; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P17-UT-004](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ut-004) | UT | FUNC-P17-004 | 은퇴 NPC 가가계도/전설장비원소유자로참조됨 | 상세일지압축가능·인물/핵심참조보존 | NOT_RUN |
| [P17-BT-004](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-bt-004) | BT | FUNC-P17-004 | portrait free pool0 | 명시 reuse 규칙·신규 NPC 생성실패없음 | NOT_RUN |
| [P17-FT-004](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ft-004) | FT | FUNC-P17-004 | 압축중 checksum 실패 | 원본남김·참조변경0 | NOT_RUN |
| [P17-CT-004](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-ct-004) | CT | FUNC-P17-004 | 은퇴 NPC 가가계도/전설장비원소유자로참조됨; 같은요청2 회 | 상세일지압축가능·인물/핵심참조보존; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P17-IT-004](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-it-004) | IT | FUNC-P17-004 | 은퇴 NPC 가가계도/전설장비원소유자로참조됨; 모듈 adapter 를실제 구현으로교체 | 상세일지압축가능·인물/핵심참조보존; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P17-RT-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-rt-001) | RT | PHASE-17 | 중상 NPC·가용치료비충분; 선행 Phase 의승인 fixture 전체 | 치료후보우선·위험원정강제선택없음; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P17-CN-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-cn-001) | CN | PHASE-17 | 중상 NPC·가용치료비충분; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P17-REC-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-rec-001) | REC | PHASE-17 | 목표대상아이템삭제; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P17-PT-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-pt-001) | PT | PHASE-17 | 활성 NPC 2,200명, S1~S4 비율 고정, 1개월/1년/10년 진행, seed0..9, 상세↔축약 승격 경계 포함 | NPC별 Thread/DAO tick 0, 처리량은 이벤트 수에 bounded, 활동/보상 이중 처리 0, PSS/DB bytes가 설명 없는 선형 폭증 없음, 축약 편차는 승인된 통계 구간 내 | NOT_RUN |
| [P17-OP-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-op-001) | OP | PHASE-17 | 은퇴 NPC 가가계도/전설장비원소유자로참조됨; 네트워크차단·앱재실행/도구재실행 | 상세일지압축가능·인물/핵심참조보존; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P17-ET-001](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-et-001) | ET | PHASE-17 | 압축중 checksum 실패 | 원본남김·참조변경0; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P17-IT-005](18_Phase17_NPC장기AI_인구순환_상세설계서.md#p17-it-005) | IT | PHASE-17 | 중상 NPC·가용치료비충분→은퇴 NPC 가가계도/전설장비원소유자로참조됨 | 치료후보우선·위험원정강제선택없음 및 상세일지압축가능·인물/핵심참조보존; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P18-UT-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ut-001) | UT | FUNC-P18-001 | 17 세359 일→18 세0 일·360 일달력 | 성인단계전환1 회·진로선택가능 | NOT_RUN |
| [P18-BT-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-bt-001) | BT | FUNC-P18-001 | 부모스탯100·자녀생성 | 스탯직접100 복사없음·승인성장보정만 | NOT_RUN |
| [P18-FT-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ft-001) | FT | FUNC-P18-001 | 교육비납부중실패 | 교육시작/차감 rollback·기존진로유지 | NOT_RUN |
| [P18-CT-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ct-001) | CT | FUNC-P18-001 | 17 세359 일→18 세0 일·360 일달력; 같은요청2 회 | 성인단계전환1 회·진로선택가능; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P18-IT-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-it-001) | IT | FUNC-P18-001 | 17 세359 일→18 세0 일·360 일달력; 모듈 adapter 를실제 구현으로교체 | 성인단계전환1 회·진로선택가능; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P18-UT-002](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ut-002) | UT | FUNC-P18-002 | 성인22 세제자·승계의사있음 | 적격후계지정·아직플레이어교체없음 | NOT_RUN |
| [P18-BT-002](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-bt-002) | BT | FUNC-P18-002 | 17 세후보또는수락거절 | IneligibleSuccessor·지정불가 | NOT_RUN |
| [P18-FT-002](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ft-002) | FT | FUNC-P18-002 | 현재후계이주/부적격변경 | 계획 NEEDS_REVIEW·시간 진행강제승계전에중단 | NOT_RUN |
| [P18-CT-002](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ct-002) | CT | FUNC-P18-002 | 성인22 세제자·승계의사있음; 같은요청2 회 | 적격후계지정·아직플레이어교체없음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P18-IT-002](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-it-002) | IT | FUNC-P18-002 | 성인22 세제자·승계의사있음; 모듈 adapter 를실제 구현으로교체 | 적격후계지정·아직플레이어교체없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P18-UT-003](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ut-003) | UT | FUNC-P18-003 | 1 대 Lv80→후계 Lv20·가문금1000·증표2 | 2 대 Lv20·가문금1000·증표2·구주인공 NPC 유지 | NOT_RUN |
| [P18-BT-003](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-bt-003) | BT | FUNC-P18-003 | 같은 successionId2 회 | 세대증가1·개인자산중복이전0 | NOT_RUN |
| [P18-FT-003](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ft-003) | FT | FUNC-P18-003 | 플레이어 ID 변경뒤쓰기실패 | 1 대그대로복원·가문금/증표일치 | NOT_RUN |
| [P18-CT-003](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ct-003) | CT | FUNC-P18-003 | 1 대 Lv80→후계 Lv20·가문금1000·증표2; 같은요청2 회 | 2 대 Lv20·가문금1000·증표2·구주인공 NPC 유지; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P18-IT-003](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-it-003) | IT | FUNC-P18-003 | 1 대 Lv80→후계 Lv20·가문금1000·증표2; 모듈 adapter 를실제 구현으로교체 | 2 대 Lv20·가문금1000·증표2·구주인공 NPC 유지; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P18-UT-004](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ut-004) | UT | FUNC-P18-004 | 3 대가1 대획득유물조회 | 원소유자/획득던전/현재보관위치연결 | NOT_RUN |
| [P18-BT-004](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-bt-004) | BT | FUNC-P18-004 | 친가족이아닌지정후계자 | 관계유형명시·가문기록에서제외하지않음 | NOT_RUN |
| [P18-FT-004](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ft-004) | FT | FUNC-P18-004 | 참조된 NPC 상세압축됨 | 요약생애표시·링크 crash 없음 | NOT_RUN |
| [P18-CT-004](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-ct-004) | CT | FUNC-P18-004 | 3 대가1 대획득유물조회; 같은요청2 회 | 원소유자/획득던전/현재보관위치연결; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P18-IT-004](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-it-004) | IT | FUNC-P18-004 | 3 대가1 대획득유물조회; 모듈 adapter 를실제 구현으로교체 | 원소유자/획득던전/현재보관위치연결; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P18-RT-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-rt-001) | RT | PHASE-18 | 17 세359 일→18 세0 일·360 일달력; 선행 Phase 의승인 fixture 전체 | 성인단계전환1 회·진로선택가능; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P18-CN-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-cn-001) | CN | PHASE-18 | 17 세359 일→18 세0 일·360 일달력; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P18-REC-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-rec-001) | REC | PHASE-18 | 교육비납부중실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P18-PT-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-pt-001) | PT | PHASE-18 | 17 세359 일→18 세0 일·360 일달력; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P18-OP-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-op-001) | OP | PHASE-18 | 3 대가1 대획득유물조회; 네트워크차단·앱재실행/도구재실행 | 원소유자/획득던전/현재보관위치연결; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P18-ET-001](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-et-001) | ET | PHASE-18 | 참조된 NPC 상세압축됨 | 요약생애표시·링크 crash 없음; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P18-IT-005](19_Phase18_가문_교육_후계_세대계승_상세설계서.md#p18-it-005) | IT | PHASE-18 | 17 세359 일→18 세0 일·360 일달력→3 대가1 대획득유물조회 | 성인단계전환1 회·진로선택가능 및 원소유자/획득던전/현재보관위치연결; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P19-UT-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ut-001) | UT | FUNC-P19-001 | 플레이어미관측도시의 NPC 이직사건 | 세계에는이직반영·플레이어즉시전지적뉴스없음 | NOT_RUN |
| [P19-BT-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-bt-001) | BT | FUNC-P19-001 | 적격참가자0 | 선택안함·기한없는미완료 event 생성0 | NOT_RUN |
| [P19-FT-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ft-001) | FT | FUNC-P19-001 | effect3 개중2 번째실패 | 동일사건 effect 전체 rollback·부분관계변경없음 | NOT_RUN |
| [P19-CT-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ct-001) | CT | FUNC-P19-001 | 플레이어미관측도시의 NPC 이직사건; 같은요청2 회 | 세계에는이직반영·플레이어즉시전지적뉴스없음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P19-IT-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-it-001) | IT | FUNC-P19-001 | 플레이어미관측도시의 NPC 이직사건; 모듈 adapter 를실제 구현으로교체 | 세계에는이직반영·플레이어즉시전지적뉴스없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P19-UT-002](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ut-002) | UT | FUNC-P19-002 | step2 NPC 이주·대체기록단서정의있음 | 대체 step 로이동·메인귀환단서획득가능 | NOT_RUN |
| [P19-BT-002](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-bt-002) | BT | FUNC-P19-002 | 완료 step 선택재전송 | AlreadyResolved·보상0 | NOT_RUN |
| [P19-FT-002](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ft-002) | FT | FUNC-P19-002 | 분기조건순환·중단조건없음 | validator 오류·배포차단 | NOT_RUN |
| [P19-CT-002](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ct-002) | CT | FUNC-P19-002 | step2 NPC 이주·대체기록단서정의있음; 같은요청2 회 | 대체 step 로이동·메인귀환단서획득가능; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P19-IT-002](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-it-002) | IT | FUNC-P19-002 | step2 NPC 이주·대체기록단서정의있음; 모듈 adapter 를실제 구현으로교체 | 대체 step 로이동·메인귀환단서획득가능; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P19-UT-003](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ut-003) | UT | FUNC-P19-003 | 같은저등급던전반복중·희귀단서예산남음 | 단서후보노출가능·전설장비확정지급없음 | NOT_RUN |
| [P19-BT-003](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-bt-003) | BT | FUNC-P19-003 | 개입예산0 | 추가개입0·기존세계시뮬레이션계속 | NOT_RUN |
| [P19-FT-003](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ft-003) | FT | FUNC-P19-003 | director 보조추천계산실패 | 추천숨김·핵심시간/전투진행정상·진단로그 | NOT_RUN |
| [P19-CT-003](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ct-003) | CT | FUNC-P19-003 | 같은저등급던전반복중·희귀단서예산남음; 같은요청2 회 | 단서후보노출가능·전설장비확정지급없음; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P19-IT-003](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-it-003) | IT | FUNC-P19-003 | 같은저등급던전반복중·희귀단서예산남음; 모듈 adapter 를실제 구현으로교체 | 단서후보노출가능·전설장비확정지급없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P19-UT-004](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ut-004) | UT | FUNC-P19-004 | 전술 A/B 각100seed 비교 | 실세계 stateHash/RNG 변경0·분산/성공률표시 | NOT_RUN |
| [P19-BT-004](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-bt-004) | BT | FUNC-P19-004 | 자동화금상한50·다음행동60 | 실행중단·추가소비0 | NOT_RUN |
| [P19-FT-004](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ft-004) | FT | FUNC-P19-004 | 실험도중사용자취소 | 실험 scope 종료·live world 유지 | NOT_RUN |
| [P19-CT-004](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-ct-004) | CT | FUNC-P19-004 | 같은 자동화 규칙 저장 CommandEnvelope 2 회; 같은 ID/다른 규칙 1 회 | automation_rule 1 행·receipt/Event 1 세트; 같은 payload는 효과1 회, 다른 payload는 IdempotencyKeyReuse | NOT_RUN |
| [P19-IT-004](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-it-004) | IT | FUNC-P19-004 | 전술 A/B 비교 후 전략/규칙 저장과 허용된 위임 행동 1 회; 모듈 adapter 를실제 구현으로교체 | 계산 중 live hash/RNG 불변, 저장행과 receipt/Event가 재기동 후 동일, 위임 행동은 예산·권한 내에서 정확히 1 회 반영 | NOT_RUN |
| [P19-RT-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-rt-001) | RT | PHASE-19 | 플레이어미관측도시의 NPC 이직사건; 선행 Phase 의승인 fixture 전체 | 세계에는이직반영·플레이어즉시전지적뉴스없음; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P19-CN-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-cn-001) | CN | PHASE-19 | 플레이어미관측도시의 NPC 이직사건; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P19-REC-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-rec-001) | REC | PHASE-19 | effect3 개중2 번째실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P19-PT-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-pt-001) | PT | PHASE-19 | 플레이어미관측도시의 NPC 이직사건; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P19-OP-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-op-001) | OP | PHASE-19 | 전술 A/B 각100seed 비교; 네트워크차단·앱재실행/도구재실행 | 실세계 stateHash/RNG 변경0·분산/성공률표시; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P19-ET-001](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-et-001) | ET | PHASE-19 | 실험도중사용자취소 | 실험 scope 종료·live world 유지; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P19-IT-005](20_Phase19_장기사건_AdventureDirector_상세설계서.md#p19-it-005) | IT | PHASE-19 | 플레이어미관측도시의 NPC 이직사건→전술 A/B 각100seed 비교 | 세계에는이직반영·플레이어즉시전지적뉴스없음 및 실세계 stateHash/RNG 변경0·분산/성공률표시; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P20-UT-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ut-001) | UT | FUNC-P20-001 | 7 핵모두봉인·원천봉쇄완료 | naturalSpawnRate0·이후생성시도새던전0 | NOT_RUN |
| [P20-BT-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-bt-001) | BT | FUNC-P20-001 | 이미봉인한 core 재봉인 | AlreadySealed·소모/진척추가0 | NOT_RUN |
| [P20-FT-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ft-001) | FT | FUNC-P20-001 | 봉인정산중저장실패 | 기존 core 상태·발생률·재료일치복원 | NOT_RUN |
| [P20-CT-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ct-001) | CT | FUNC-P20-001 | 7 핵모두봉인·원천봉쇄완료; 같은요청2 회 | naturalSpawnRate0·이후생성시도새던전0; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P20-IT-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-it-001) | IT | FUNC-P20-001 | 7 핵모두봉인·원천봉쇄완료; 모듈 adapter 를실제 구현으로교체 | naturalSpawnRate0·이후생성시도새던전0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P20-UT-002](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ut-002) | UT | FUNC-P20-002 | 잔존4 범주0·무사건89 일→90 일 | 종전 proof 자격1 회 | NOT_RUN |
| [P20-BT-002](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-bt-002) | BT | FUNC-P20-002 | 무사건89 일째새문생성 | 카운터0·종전확정안함 | NOT_RUN |
| [P20-FT-002](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ft-002) | FT | FUNC-P20-002 | 잔존집계와실제 base 수가다름 | 검증 실패·귀환차단·숨겨진거점자동삭제금지 | NOT_RUN |
| [P20-CT-002](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ct-002) | CT | FUNC-P20-002 | 잔존4 범주0·무사건89 일→90 일; 같은요청2 회 | 종전 proof 자격1 회; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P20-IT-002](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-it-002) | IT | FUNC-P20-002 | 잔존4 범주0·무사건89 일→90 일; 모듈 adapter 를실제 구현으로교체 | 종전 proof 자격1 회; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P20-UT-003](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ut-003) | UT | FUNC-P20-003 | 증표5·활성잔존던전1 | 귀환불가·해당잔존조건표시 | NOT_RUN |
| [P20-BT-003](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-bt-003) | BT | FUNC-P20-003 | 증표5·던전0·발생차단90 일·전쟁안전 | 귀환가능·자동종료아님 | NOT_RUN |
| [P20-FT-003](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ft-003) | FT | FUNC-P20-003 | 동일 proofsource 재수신 | 증표1 개·세대기여중복0 | NOT_RUN |
| [P20-CT-003](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ct-003) | CT | FUNC-P20-003 | 증표5·활성잔존던전1; 같은요청2 회 | 귀환불가·해당잔존조건표시; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P20-IT-003](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-it-003) | IT | FUNC-P20-003 | 증표5·활성잔존던전1; 모듈 adapter 를실제 구현으로교체 | 귀환불가·해당잔존조건표시; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P20-UT-004](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ut-004) | UT | FUNC-P20-004 | 귀환가능상태에서잔류 | 캠페인 ACTIVE_STAY·플레이계속 | NOT_RUN |
| [P20-BT-004](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-bt-004) | BT | FUNC-P20-004 | 귀환확인 후엔딩 UI 강제종료 | ENDING_COMMITTED 기록으로동일요약재표시 | NOT_RUN |
| [P20-FT-004](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ft-004) | FT | FUNC-P20-004 | 조건검사후새위기발생 version 변경 | 재검사·귀환확정차단·세이브유지 | NOT_RUN |
| [P20-CT-004](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-ct-004) | CT | FUNC-P20-004 | 귀환가능상태에서잔류; 같은요청2 회 | 캠페인 ACTIVE_STAY·플레이계속; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P20-IT-004](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-it-004) | IT | FUNC-P20-004 | 귀환가능상태에서잔류; 모듈 adapter 를실제 구현으로교체 | 캠페인 ACTIVE_STAY·플레이계속; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P20-RT-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-rt-001) | RT | PHASE-20 | 7 핵모두봉인·원천봉쇄완료; 선행 Phase 의승인 fixture 전체 | naturalSpawnRate0·이후생성시도새던전0; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P20-CN-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-cn-001) | CN | PHASE-20 | 7 핵모두봉인·원천봉쇄완료; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P20-REC-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-rec-001) | REC | PHASE-20 | 봉인정산중저장실패; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P20-PT-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-pt-001) | PT | PHASE-20 | 7 핵모두봉인·원천봉쇄완료; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P20-OP-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-op-001) | OP | PHASE-20 | 귀환가능상태에서잔류; 네트워크차단·앱재실행/도구재실행 | 캠페인 ACTIVE_STAY·플레이계속; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P20-ET-001](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-et-001) | ET | PHASE-20 | 조건검사후새위기발생 version 변경 | 재검사·귀환확정차단·세이브유지; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P20-IT-005](21_Phase20_균열_악마전쟁_귀환엔딩_상세설계서.md#p20-it-005) | IT | PHASE-20 | 7 핵모두봉인·원천봉쇄완료→귀환가능상태에서잔류 | naturalSpawnRate0·이후생성시도새던전0 및 캠페인 ACTIVE_STAY·플레이계속; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P21-UT-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ut-001) | UT | FUNC-P21-001 | 보스첫정복 event 가인물/파티/장비에연결 | 원본1 개·링크3 개·각타임라인조회가능 | NOT_RUN |
| [P21-BT-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-bt-001) | BT | FUNC-P21-001 | 삭제된일반장비의중요획득기록 | 요약 item identity 로링크유지 | NOT_RUN |
| [P21-FT-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ft-001) | FT | FUNC-P21-001 | consumer 중단후 event 재소비 | 같은 chronicle1 개·중복문장0 | NOT_RUN |
| [P21-CT-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ct-001) | CT | FUNC-P21-001 | 보스첫정복 event 가인물/파티/장비에연결; 같은요청2 회 | 원본1 개·링크3 개·각타임라인조회가능; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P21-IT-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-it-001) | IT | FUNC-P21-001 | 보스첫정복 event 가인물/파티/장비에연결; 모듈 adapter 를실제 구현으로교체 | 원본1 개·링크3 개·각타임라인조회가능; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P21-UT-002](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ut-002) | UT | FUNC-P21-002 | 피해10/20 event 와 event20 중복 | 합30·건수2 | NOT_RUN |
| [P21-BT-002](22_Phase21_연대기_통계_검색_상세설계서.md#p21-bt-002) | BT | FUNC-P21-002 | 12 월30 일23:59→다음년1 월1 일 | 정확한일월년 bucket 변경 | NOT_RUN |
| [P21-FT-002](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ft-002) | FT | FUNC-P21-002 | 집계재생성중실패 | 이전집계계속읽기·재빌드중상태표시 | NOT_RUN |
| [P21-CT-002](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ct-002) | CT | FUNC-P21-002 | 피해10/20 event 와 event20 중복; 같은요청2 회 | 합30·건수2; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P21-IT-002](22_Phase21_연대기_통계_검색_상세설계서.md#p21-it-002) | IT | FUNC-P21-002 | 피해10/20 event 와 event20 중복; 모듈 adapter 를실제 구현으로교체 | 합30·건수2; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P21-UT-003](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ut-003) | UT | FUNC-P21-003 | 같은이름 NPC3 명·페이지크기2 | 다음페이지중복0·소속/연령으로구별 | NOT_RUN |
| [P21-BT-003](22_Phase21_연대기_통계_검색_상세설계서.md#p21-bt-003) | BT | FUNC-P21-003 | 잠재력 exact 정렬요청 | UnsupportedSort·전체 NPC 숨은값반환0 | NOT_RUN |
| [P21-FT-003](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ft-003) | FT | FUNC-P21-003 | 검색색인손상 | 재구축중제한검색·원사건/세이브유지 | NOT_RUN |
| [P21-CT-003](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ct-003) | CT | FUNC-P21-003 | 같은 북마크 추가 CommandEnvelope 2 회; 같은 ID/다른 entity 1 회 | bookmark 1 행·receipt/Event 1 세트; 같은 payload는 효과1 회, 다른 payload는 IdempotencyKeyReuse | NOT_RUN |
| [P21-IT-003](22_Phase21_연대기_통계_검색_상세설계서.md#p21-it-003) | IT | FUNC-P21-003 | 같은이름 NPC3 명 검색 후 북마크 추가; 모듈 adapter 를실제 구현으로교체 | 검색 중 save hash 불변, 북마크와 receipt/Event는 새세션에도 동일, 색인 재소비는 search_document 중복0 | NOT_RUN |
| [P21-UT-004](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ut-004) | UT | FUNC-P21-004 | 중요도80·즐겨찾기 true·100 년경과 | 원본문장/출처영구보존 | NOT_RUN |
| [P21-BT-004](22_Phase21_연대기_통계_검색_상세설계서.md#p21-bt-004) | BT | FUNC-P21-004 | 중요도25 일일기록100 개 | 연간집계합계일치확인 후상세압축 | NOT_RUN |
| [P21-FT-004](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ft-004) | FT | FUNC-P21-004 | 압축중저장실패 | 원본100 개또는완전요약만·양쪽소실없음 | NOT_RUN |
| [P21-CT-004](22_Phase21_연대기_통계_검색_상세설계서.md#p21-ct-004) | CT | FUNC-P21-004 | 중요도80·즐겨찾기 true·100 년경과; 같은요청2 회 | 원본문장/출처영구보존; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P21-IT-004](22_Phase21_연대기_통계_검색_상세설계서.md#p21-it-004) | IT | FUNC-P21-004 | 중요도80·즐겨찾기 true·100 년경과; 모듈 adapter 를실제 구현으로교체 | 원본문장/출처영구보존; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P21-RT-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-rt-001) | RT | PHASE-21 | 보스첫정복 event 가인물/파티/장비에연결; 선행 Phase 의승인 fixture 전체 | 원본1 개·링크3 개·각타임라인조회가능; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P21-CN-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-cn-001) | CN | PHASE-21 | 보스첫정복 event 가인물/파티/장비에연결; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P21-REC-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-rec-001) | REC | PHASE-21 | consumer 중단후 event 재소비; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P21-PT-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-pt-001) | PT | PHASE-21 | 보스첫정복 event 가인물/파티/장비에연결; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P21-OP-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-op-001) | OP | PHASE-21 | 중요도80·즐겨찾기 true·100 년경과; 네트워크차단·앱재실행/도구재실행 | 원본문장/출처영구보존; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P21-ET-001](22_Phase21_연대기_통계_검색_상세설계서.md#p21-et-001) | ET | PHASE-21 | 압축중저장실패 | 원본100 개또는완전요약만·양쪽소실없음; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P21-IT-005](22_Phase21_연대기_통계_검색_상세설계서.md#p21-it-005) | IT | PHASE-21 | 보스첫정복 event 가인물/파티/장비에연결→중요도80·즐겨찾기 true·100 년경과 | 원본1 개·링크3 개·각타임라인조회가능 및 원본문장/출처영구보존; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P22-UT-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ut-001) | UT | FUNC-P22-001 | 연대기→NPC→던전→Back2 회 | NPC 목록/기록의필터·스크롤복원 | NOT_RUN |
| [P22-BT-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-bt-001) | BT | FUNC-P22-001 | 로그인/서버응답없는비행기모드 | 모든핵심 route 가로컬에서동작 | NOT_RUN |
| [P22-FT-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ft-001) | FT | FUNC-P22-001 | 없는 NPC ID 딥링크 | 기록요약/없음화면·앱 crash 없음 | NOT_RUN |
| [P22-CT-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ct-001) | CT | FUNC-P22-001 | 연대기→NPC→던전→Back2 회; 같은요청2 회 | NPC 목록/기록의필터·스크롤복원; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P22-IT-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-it-001) | IT | FUNC-P22-001 | 연대기→NPC→던전→Back2 회; 모듈 adapter 를실제 구현으로교체 | NPC 목록/기록의필터·스크롤복원; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P22-UT-002](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ut-002) | UT | FUNC-P22-002 | NPC-W-03147 목록/상세/대화/전투 | 같은 assetKey·서로다른 crop profile | NOT_RUN |
| [P22-BT-002](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-bt-002) | BT | FUNC-P22-002 | 이미지로드실패 | fallback 표시·이름/행동버튼사용가능 | NOT_RUN |
| [P22-FT-002](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ft-002) | FT | FUNC-P22-002 | 토큰외임의색/폰트가추가됨 | 디자인 lint/review 경고·승인없이기준선변경금지 | NOT_RUN |
| [P22-CT-002](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ct-002) | CT | FUNC-P22-002 | NPC-W-03147 목록/상세/대화/전투; 같은요청2 회 | 같은 assetKey·서로다른 crop profile; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P22-IT-002](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-it-002) | IT | FUNC-P22-002 | NPC-W-03147 목록/상세/대화/전투; 모듈 adapter 를실제 구현으로교체 | 같은 assetKey·서로다른 crop profile; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P22-UT-003](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ut-003) | UT | FUNC-P22-003 | 폭360dp·글자2 배·NPC 긴이름 | 주요확인/취소버튼잘림0·스크롤접근가능 | NOT_RUN |
| [P22-BT-003](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-bt-003) | BT | FUNC-P22-003 | 숨은스탯화면 TalkBack 읽기 | 미확인만읽음·내부수치없음 | NOT_RUN |
| [P22-FT-003](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ft-003) | FT | FUNC-P22-003 | 회전/리사이즈중모달열림 | 선택내용유지·명령중복0 | NOT_RUN |
| [P22-CT-003](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ct-003) | CT | FUNC-P22-003 | 폭360dp·글자2 배·NPC 긴이름; 같은요청2 회 | 주요확인/취소버튼잘림0·스크롤접근가능; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P22-IT-003](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-it-003) | IT | FUNC-P22-003 | 폭360dp·글자2 배·NPC 긴이름; 모듈 adapter 를실제 구현으로교체 | 주요확인/취소버튼잘림0·스크롤접근가능; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P22-UT-004](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ut-004) | UT | FUNC-P22-004 | 구매성공직후 Activity 재생성 | 보유품1 개·금1 회차감·성공안내중복과도표시없음 | NOT_RUN |
| [P22-BT-004](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-bt-004) | BT | FUNC-P22-004 | 전투중 Back | 허용메뉴또는확인·계산결과삭제없음 | NOT_RUN |
| [P22-FT-004](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ft-004) | FT | FUNC-P22-004 | process death 후 SavedStateHandle 만존재 | 권위세이브로드후 projection 재생성·UI 값으로월드복구금지 | NOT_RUN |
| [P22-CT-004](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-ct-004) | CT | FUNC-P22-004 | 슬롯 A→B 전환 뒤 PublicSnapshot(sessionEpoch,stateVersion) 순서: 지연 A:v12, B:v3, B:v4, 지연 B:v3와 일회성 효과 | 현재 epoch의 B:v4만 최종 표시; A:v12와 회귀 B:v3 폐기; 동일 version은 멱등; 일회성 효과 재실행 0 | NOT_RUN |
| [P22-IT-004](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-it-004) | IT | FUNC-P22-004 | 구매성공직후 Activity 재생성; 모듈 adapter 를실제 구현으로교체 | 보유품1 개·금1 회차감·성공안내중복과도표시없음; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P22-RT-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-rt-001) | RT | PHASE-22 | 연대기→NPC→던전→Back2 회; 선행 Phase 의승인 fixture 전체 | NPC 목록/기록의필터·스크롤복원; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P22-CN-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-cn-001) | CN | PHASE-22 | 연대기→NPC→던전→Back2 회; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P22-REC-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-rec-001) | REC | PHASE-22 | 없는 NPC ID 딥링크; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P22-PT-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-pt-001) | PT | PHASE-22 | 연대기→NPC→던전→Back2 회; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P22-OP-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-op-001) | OP | PHASE-22 | 구매성공직후 Activity 재생성; 네트워크차단·앱재실행/도구재실행 | 보유품1 개·금1 회차감·성공안내중복과도표시없음; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P22-ET-001](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-et-001) | ET | PHASE-22 | process death 후 SavedStateHandle 만존재 | 권위세이브로드후 projection 재생성·UI 값으로월드복구금지; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P22-IT-005](23_Phase22_전체UI_UX_접근성_디자인_상세설계서.md#p22-it-005) | IT | PHASE-22 | 연대기→NPC→던전→Back2 회→구매성공직후 Activity 재생성 | NPC 목록/기록의필터·스크롤복원 및 보유품1 개·금1 회차감·성공안내중복과도표시없음; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P23-UT-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ut-001) | UT | FUNC-P23-001 | seed42·고정 fixture·2 회검사 | 결과 stateHash 동일·runId 서로다름 | NOT_RUN |
| [P23-BT-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-bt-001) | BT | FUNC-P23-001 | 실게임 DB 경로입력 | UnsafeTestPath 로시작거절 | NOT_RUN |
| [P23-FT-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ft-001) | FT | FUNC-P23-001 | runner 중단 | run=ABORTED·완료된 case 증거보존·PASS 표시금지 | NOT_RUN |
| [P23-CT-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ct-001) | CT | FUNC-P23-001 | seed42·고정 fixture·2 회검사; 같은요청2 회 | 결과 stateHash 동일·runId 서로다름; 동일 commandId/payload 반복은 효과1 회, 같은 ID/다른 payload 는 IdempotencyKeyReuse | NOT_RUN |
| [P23-IT-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-it-001) | IT | FUNC-P23-001 | seed42·고정 fixture·2 회검사; 모듈 adapter 를실제 구현으로교체 | 결과 stateHash 동일·runId 서로다름; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P23-UT-002](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ut-002) | UT | FUNC-P23-002 | 아이템동일 ID2 장착 fixture | 소유권불변식정확한 ID 와함께 FAIL | NOT_RUN |
| [P23-BT-002](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-bt-002) | BT | FUNC-P23-002 | 실행순서/로그 level 만변경 | 최종 canonical hash 동일 | NOT_RUN |
| [P23-FT-002](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ft-002) | FT | FUNC-P23-002 | 300 년중실패 seed1 개 | 전체 PASS 금지·최초실패 경계/축소 fixture 저장 | NOT_RUN |
| [P23-CT-002](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ct-002) | CT | FUNC-P23-002 | 아이템동일 ID2 장착 fixture; 같은요청2 회 | 소유권불변식정확한 ID 와함께 FAIL; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P23-IT-002](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-it-002) | IT | FUNC-P23-002 | 아이템동일 ID2 장착 fixture; 모듈 adapter 를실제 구현으로교체 | 소유권불변식정확한 ID 와함께 FAIL; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P23-UT-003](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ut-003) | UT | FUNC-P23-003 | Bernoulli p=.01·N100000 | 성공률·Wilson95%CI 와기대분포보고·정해진허용구간검사 | NOT_RUN |
| [P23-BT-003](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-bt-003) | BT | FUNC-P23-003 | 성공0 건인 N10 | 확률0 으로단정하지않고표본부족표시 | NOT_RUN |
| [P23-FT-003](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ft-003) | FT | FUNC-P23-003 | 기준선에없는미정계수 | NOT_READY·임의수치로출시승인하지않음 | NOT_RUN |
| [P23-CT-003](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ct-003) | CT | FUNC-P23-003 | Bernoulli p=.01·N100000; 같은요청2 회 | 성공률·Wilson95%CI 와기대분포보고·정해진허용구간검사; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P23-IT-003](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-it-003) | IT | FUNC-P23-003 | Bernoulli p=.01·N100000; 모듈 adapter 를실제 구현으로교체 | 성공률·Wilson95%CI 와기대분포보고·정해진허용구간검사; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P23-UT-004](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ut-004) | UT | FUNC-P23-004 | 실패 fixture 를다른 JVM 에서 import | 같은최초불변식오류재현 | NOT_RUN |
| [P23-BT-004](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-bt-004) | BT | FUNC-P23-004 | 증거파일 checksum 불일치 | 재현패키지거절·원본검사결과보존 | NOT_RUN |
| [P23-FT-004](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ft-004) | FT | FUNC-P23-004 | reviewer 미지정 | 검토대기·DoD 완료로표시하지않음 | NOT_RUN |
| [P23-CT-004](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-ct-004) | CT | FUNC-P23-004 | 실패 fixture 를다른 JVM 에서 import; 같은요청2 회 | 같은최초불변식오류재현; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P23-IT-004](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-it-004) | IT | FUNC-P23-004 | 실패 fixture 를다른 JVM 에서 import; 모듈 adapter 를실제 구현으로교체 | 같은최초불변식오류재현; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P23-RT-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-rt-001) | RT | PHASE-23 | seed42·고정 fixture·2 회검사; 선행 Phase 의승인 fixture 전체 | 결과 stateHash 동일·runId 서로다름; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P23-CN-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-cn-001) | CN | PHASE-23 | seed42·고정 fixture·2 회검사; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P23-REC-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-rec-001) | REC | PHASE-23 | runner 중단; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P23-PT-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-pt-001) | PT | PHASE-23 | seed42·고정 fixture·2 회검사; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P23-OP-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-op-001) | OP | PHASE-23 | 실패 fixture 를다른 JVM 에서 import; 네트워크차단·앱재실행/도구재실행 | 같은최초불변식오류재현; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P23-ET-001](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-et-001) | ET | PHASE-23 | reviewer 미지정 | 검토대기·DoD 완료로표시하지않음; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P23-IT-005](24_Phase23_검증센터_밸런스_개발도구_상세설계서.md#p23-it-005) | IT | PHASE-23 | seed42·고정 fixture·2 회검사→실패 fixture 를다른 JVM 에서 import | 결과 stateHash 동일·runId 서로다름 및 같은최초불변식오류재현; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P24-UT-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ut-001) | UT | FUNC-P24-001 | NPC2200/아이템100000/연대기1000000fixture | 측정값/DB 크기/PSS/기기정보모두보고 | NOT_RUN |
| [P24-BT-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-bt-001) | BT | FUNC-P24-001 | 화면목록스크롤100 회 | 화면밖 bitmap domain 참조0 | NOT_RUN |
| [P24-FT-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ft-001) | FT | FUNC-P24-001 | benchmark 에 debug build 사용 | 비교불가표시·release 승인증거로인정하지않음 | NOT_RUN |
| [P24-CT-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ct-001) | CT | FUNC-P24-001 | NPC2200/아이템100000/연대기1000000fixture; 같은요청2 회 | 측정값/DB 크기/PSS/기기정보모두보고; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P24-IT-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-it-001) | IT | FUNC-P24-001 | NPC2200/아이템100000/연대기1000000fixture; 모듈 adapter 를실제 구현으로교체 | 측정값/DB 크기/PSS/기기정보모두보고; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P24-UT-002](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ut-002) | UT | FUNC-P24-002 | 슬롯 A/B 전환100 회 | 활성 WorldSession1·닫힌 sessionjob0 | NOT_RUN |
| [P24-BT-002](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-bt-002) | BT | FUNC-P24-002 | 앱종료현실7 일후재개 | 세계 GameClock/RNG 진행0 | NOT_RUN |
| [P24-FT-002](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ft-002) | FT | FUNC-P24-002 | 취소중 DBcommit 완료 | receipt 조회로확정상태복원·임의재실행없음 | NOT_RUN |
| [P24-CT-002](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ct-002) | CT | FUNC-P24-002 | 슬롯 A/B 전환100 회; 같은요청2 회 | 활성 WorldSession1·닫힌 sessionjob0; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P24-IT-002](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-it-002) | IT | FUNC-P24-002 | 슬롯 A/B 전환100 회; 모듈 adapter 를실제 구현으로교체 | 활성 WorldSession1·닫힌 sessionjob0; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P24-UT-003](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ut-003) | UT | FUNC-P24-003 | 이미지20%손상+전투1 회 | fallback·동일전투 hash·NPC 원 portraitKey 유지 | NOT_RUN |
| [P24-BT-003](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-bt-003) | BT | FUNC-P24-003 | 디스크잔여0 에서강화 | 새명령 commit 안됨·이전세이브유효 | NOT_RUN |
| [P24-FT-003](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ft-003) | FT | FUNC-P24-003 | 참조중 checkpoint chunk GC 후보 | 삭제거절·세대복원성유지 | NOT_RUN |
| [P24-CT-003](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ct-003) | CT | FUNC-P24-003 | 이미지20%손상+전투1 회; 같은요청2 회 | fallback·동일전투 hash·NPC 원 portraitKey 유지; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P24-IT-003](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-it-003) | IT | FUNC-P24-003 | 이미지20%손상+전투1 회; 모듈 adapter 를실제 구현으로교체 | fallback·동일전투 hash·NPC 원 portraitKey 유지; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P24-UT-004](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ut-004) | UT | FUNC-P24-004 | Array 최적화전후1000seed | 모든권위 hash 동일·성능수치만변화 | NOT_RUN |
| [P24-BT-004](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-bt-004) | BT | FUNC-P24-004 | 컬렉션순서를 HashMap iteration 에의존 | 동치실패·merge 차단 | NOT_RUN |
| [P24-FT-004](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ft-004) | FT | FUNC-P24-004 | R8 후 DTOserialization 필드누락 | release 회귀실패·keep/serializer 수정후재검증 | NOT_RUN |
| [P24-CT-004](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-ct-004) | CT | FUNC-P24-004 | Array 최적화전후1000seed; 같은요청2 회 | 모든권위 hash 동일·성능수치만변화; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P24-IT-004](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-it-004) | IT | FUNC-P24-004 | Array 최적화전후1000seed; 모듈 adapter 를실제 구현으로교체 | 모든권위 hash 동일·성능수치만변화; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P24-RT-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-rt-001) | RT | PHASE-24 | NPC2200/아이템100000/연대기1000000fixture; 선행 Phase 의승인 fixture 전체 | 측정값/DB 크기/PSS/기기정보모두보고; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P24-CN-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-cn-001) | CN | PHASE-24 | NPC2200/아이템100000/연대기1000000fixture; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P24-REC-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-rec-001) | REC | PHASE-24 | benchmark 에 debug build 사용; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P24-PT-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-pt-001) | PT | PHASE-24 | NPC2200/아이템100000/연대기1000000fixture; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P24-OP-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-op-001) | OP | PHASE-24 | Array 최적화전후1000seed; 네트워크차단·앱재실행/도구재실행 | 모든권위 hash 동일·성능수치만변화; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P24-ET-001](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-et-001) | ET | PHASE-24 | R8 후 DTOserialization 필드누락 | release 회귀실패·keep/serializer 수정후재검증; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P24-IT-005](25_Phase24_성능_장기안정화_장애격리_상세설계서.md#p24-it-005) | IT | PHASE-24 | NPC2200/아이템100000/연대기1000000fixture→Array 최적화전후1000seed | 측정값/DB 크기/PSS/기기정보모두보고 및 모든권위 hash 동일·성능수치만변화; 선행 port/DTO/version 인계완료 | NOT_RUN |
| [P25-UT-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ut-001) | UT | FUNC-P25-001 | 모든필수 case PASS·결함 P0/P1 0·검토승인완료 | RC 출시승인가능 | NOT_RUN |
| [P25-BT-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-bt-001) | BT | FUNC-P25-001 | 테스트목록만있고실행 증거없음 | NOT_RUN·승인불가 | NOT_RUN |
| [P25-FT-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ft-001) | FT | FUNC-P25-001 | 원문요구사항1 개매핑없음 | traceability FAIL·출시차단 | NOT_RUN |
| [P25-CT-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ct-001) | CT | FUNC-P25-001 | 모든필수 case PASS·결함 P0/P1 0·검토승인완료; 같은요청2 회 | RC 출시승인가능; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P25-IT-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-it-001) | IT | FUNC-P25-001 | 모든필수 case PASS·결함 P0/P1 0·검토승인완료; 모듈 adapter 를실제 구현으로교체 | RC 출시승인가능; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P25-UT-002](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ut-002) | UT | FUNC-P25-002 | GREENFIELD_V1 fresh save 또는 LEGACY_CHAIN oldest supported 실제 save | GREENFIELD fresh-open·migration 0개 또는 LEGACY 실제 chain·권위값 보존 계획 | NOT_RUN |
| [P25-BT-002](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-bt-002) | BT | FUNC-P25-002 | current+1 schema 세이브를 current 앱에서 열기 | 호환불가·파일쓰기0 | NOT_RUN |
| [P25-FT-002](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ft-002) | FT | FUNC-P25-002 | 실제 migration chain 중 한 단계 실패 주입 | 업그레이드전자산파일 hash 동일·오류기록 | NOT_RUN |
| [P25-CT-002](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ct-002) | CT | FUNC-P25-002 | 동일 fresh-open 또는 실제 upgrade scenario를 2회 검증 | 두 보고서 동치, GREENFIELD migration 0개, LEGACY 실제 단계 수 일치, 원본/백업 중복 변경 0건 | NOT_RUN |
| [P25-IT-002](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-it-002) | IT | FUNC-P25-002 | GREENFIELD_V1: current save의 CombatCompleted.v1 roundtrip; LEGACY_CHAIN: 실제 oldest save/event→current 지원체인 | GREENFIELD migration 0개·current event roundtrip; LEGACY 실제 단계·아이템/가문/증표 보존; 보존 codec decode/upcast와 projection 재구축 성공; 앱/headless 동일 | NOT_RUN |
| [P25-UT-003](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ut-003) | UT | FUNC-P25-003 | 서명 release 신규설치후인터넷차단 | 필수콘텐츠이미포함·첫플레이완료 | NOT_RUN |
| [P25-BT-003](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-bt-003) | BT | FUNC-P25-003 | 필수 asset 팩미설치 | 설치완료검수실패·조용한네트워크의존금지 | NOT_RUN |
| [P25-FT-003](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ft-003) | FT | FUNC-P25-003 | 서명키/manifest 다름 | 배포차단·기존설치덮어쓰기강행금지 | NOT_RUN |
| [P25-CT-003](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ct-003) | CT | FUNC-P25-003 | 서명 release 신규설치후인터넷차단; 같은요청2 회 | 필수콘텐츠이미포함·첫플레이완료; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P25-IT-003](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-it-003) | IT | FUNC-P25-003 | 서명 release 신규설치후인터넷차단; 모듈 adapter 를실제 구현으로교체 | 필수콘텐츠이미포함·첫플레이완료; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P25-UT-004](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ut-004) | UT | FUNC-P25-004 | 출시후강화중복보고·repro bundle 제공 | 영향버전/receipt/seed 기준재현·원본세이브보존 | NOT_RUN |
| [P25-BT-004](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-bt-004) | BT | FUNC-P25-004 | 필수리뷰서명없음 | HANDOFF_PENDING·완료율100%금지 | NOT_RUN |
| [P25-FT-004](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ft-004) | FT | FUNC-P25-004 | 핫픽스에 schema 변경있음 | P3/P25migration+회귀재개·단순파일교체출시금지 | NOT_RUN |
| [P25-CT-004](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-ct-004) | CT | FUNC-P25-004 | 출시후강화중복보고·repro bundle 제공; 같은요청2 회 | 영향버전/receipt/seed 기준재현·원본세이브보존; 같은입력2 회 결과동일·live state/RNG 쓰기0 | NOT_RUN |
| [P25-IT-004](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-it-004) | IT | FUNC-P25-004 | 출시후강화중복보고·repro bundle 제공; 모듈 adapter 를실제 구현으로교체 | 영향버전/receipt/seed 기준재현·원본세이브보존; 앱/헤드리스 entry 가 동일핵심 use case 를호출하고 새세션으로재조회시동일결과 | NOT_RUN |
| [P25-RT-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-rt-001) | RT | PHASE-25 | 모든필수 case PASS·결함 P0/P1 0·검토승인완료; 선행 Phase 의승인 fixture 전체 | RC 출시승인가능; 선행의권위 hash/금액/아이템/시간/기존오류동작동일 | NOT_RUN |
| [P25-CN-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-cn-001) | CN | PHASE-25 | 모든필수 case PASS·결함 P0/P1 0·검토승인완료; 요청2 개동시에제출/이전 epoch 응답지연 | mutation 은직렬화·동일 명령효과1 회·오래된 epoch 쓰기0; 순수/도구기능은출력동치및독립임시경로,live 쓰기0 | NOT_RUN |
| [P25-REC-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-rec-001) | REC | PHASE-25 | 원문요구사항1 개매핑없음; 정상요청직전/커밋직전/직후 kill | 원문 규칙상예상실패를유지하면서완전이전또는완전다음세대/산출물만보존·부분 혼합0 | NOT_RUN |
| [P25-PT-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-pt-001) | PT | PHASE-25 | 모든필수 case PASS·결함 P0/P1 0·검토승인완료; seed0..99 를반복하고대표최대 fixture 사용 | 결과와 bounded 종료확인; latency/PSS/DB bytes 실측기록. 성능목표는 P24 표/본 Phase 특화 fixture 에대조하며미측정 PASS 금지 | NOT_RUN |
| [P25-OP-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-op-001) | OP | PHASE-25 | 출시후강화중복보고·repro bundle 제공; 네트워크차단·앱재실행/도구재실행 | 영향버전/receipt/seed 기준재현·원본세이브보존; 필수 네트워크요청0·게임현실시간 catchup0 | NOT_RUN |
| [P25-ET-001](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-et-001) | ET | PHASE-25 | 핫픽스에 schema 변경있음 | P3/P25migration+회귀재개·단순파일교체출시금지; 권위 상태오류는안전정지,이미지/파생리포트오류는격리·로그에오류범위명시 | NOT_RUN |
| [P25-IT-005](26_Phase25_통합검증_릴리즈_롤백_상세설계서.md#p25-it-005) | IT | PHASE-25 | 모든필수 case PASS·결함 P0/P1 0·검토승인완료→출시후강화중복보고·repro bundle 제공 | RC 출시승인가능 및 영향버전/receipt/seed 기준재현·원본세이브보존; 선행 port/DTO/version 인계완료 | NOT_RUN |

## 5. 전체 End-to-End / 통합 / Regression / 배포 / Upgrade / Rollback 상세

<a id="all-e2e-001"></a>
### ALL-E2E-001 — 신규게임에서첫던전후퇴후재실행

| 항목 | 설계 |
|---|---|
| Test ID | ALL-E2E-001 |
| 테스트 종류 | E2E |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 비행기모드·신규 seed42·F 던전·준비된6 인이하파티 |
| 수행 절차 | 생성→장비→탐색→전투→상자→후퇴→거점저장→앱 kill→로드 |
| 예상 결과 | 동일인물/얼굴/금/아이템/시간·XP 한번·출전6 상한 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 동일인물/얼굴/금/아이템/시간·XP 한번·출전6 상한 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-e2e-002"></a>
### ALL-E2E-002 — 패배→구조실패→치료→재출전

| 항목 | 설계 |
|---|---|
| Test ID | ALL-E2E-002 |
| 테스트 종류 | E2E |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 휴대금1000·내구80·HP100 최대·구조강제실패 |
| 수행 절차 | 패배정산→12h+정의추가시간→치료예약→완료경계→재출전 |
| 예상 결과 | 금900·내구72·HP30 복귀·패배사실유지·치료중현실종료진척0 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 금900·내구72·HP30 복귀·패배사실유지·치료중현실종료진척0 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-e2e-003"></a>
### ALL-E2E-003 — 강화→정련→대여→운송→반환

| 항목 | 설계 |
|---|---|
| Test ID | ALL-E2E-003 |
| 테스트 종류 | E2E |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 장비 I1·전설보호석·재료충분·단계19 |
| 수행 절차 | 강화결과확정중 kill→로드→정련→타 NPC 대여→운송→반환 |
| 예상 결과 | 소유권1 개·시도/비용1 회·보호/각인상한·대여판매금지 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 소유권1 개·시도/비용1 회·보호/각인상한·대여판매금지 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-e2e-004"></a>
### ALL-E2E-004 — 길드30일1위와가문승계

| 항목 | 설계 |
|---|---|
| Test ID | ALL-E2E-004 |
| 테스트 종류 | E2E |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 승인랭킹 fixture·기여충족·28 일연속1 위·성인후계자 |
| 수행 절차 | 29 일마감→세대 교체→30 일마감→증표조회 |
| 예상 결과 | 개인성장복사0·가문증표1 회·연속마감/기여판정유지 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 개인성장복사0·가문증표1 회·연속마감/기여판정유지 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-e2e-005"></a>
### ALL-E2E-005 — 7핵→90일봉인→잔당→귀환

| 항목 | 설계 |
|---|---|
| Test ID | ALL-E2E-005 |
| 테스트 종류 | E2E |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 메인경로 fixture·귀환 proof 진척·잔존던전1 |
| 수행 절차 | 7 핵봉인→89/90 일경계→악마재출현→90 일재검증→잔존정복→귀환확인 |
| 예상 결과 | 90 일리셋정확·잔존1 이면차단·전부충족시명시확인 후엔딩1 개 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 90 일리셋정확·잔존1 이면차단·전부충족시명시확인 후엔딩1 개 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-rt-006"></a>
### ALL-RT-006 — 100년대비300년핵심원본보존

| 항목 | 설계 |
|---|---|
| Test ID | ALL-RT-006 |
| 테스트 종류 | RT |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 동일 seed·가문/역사장비/중요도80 북마크 |
| 수행 절차 | 각100/300 게임년진행→압축→save/load→가계도/연대기조회 |
| 예상 결과 | 핵심 ID/원본문장/증표보존·통계 sum 일치·일반일지압축가능 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 핵심 ID/원본문장/증표보존·통계 sum 일치·일반일지압축가능 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-cn-007"></a>
### ALL-CN-007 — 시간진행·구매·슬롯변경경합

| 항목 | 설계 |
|---|---|
| Test ID | ALL-CN-007 |
| 테스트 종류 | CN |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 슬롯 A 장기진행중구매명령·B 로드요청 |
| 수행 절차 | 늦은 A 응답/동일구매두번/슬롯변경순서를매 seed 변형 |
| 예상 결과 | epoch 혼합쓰기0·double debit0·활성 session1 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | epoch 혼합쓰기0·double debit0·활성 session1 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-ft-008"></a>
### ALL-FT-008 — 다중테이블commit전후강제종료

| 항목 | 설계 |
|---|---|
| Test ID | ALL-FT-008 |
| 테스트 종류 | FT |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 정산·강화·경매·승계각권위 command |
| 수행 절차 | 각 DAO 쓰기전후/commit 직전직후 kill→재실행 |
| 예상 결과 | 모든 cut 에서완전 old 또는 new 만·비용과보상짝일치 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 모든 cut 에서완전 old 또는 new 만·비용과보상짝일치 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-upg-009"></a>
### ALL-UPG-009 — 지원모든구schema→현schema

| 항목 | 설계 |
|---|---|
| Test ID | ALL-UPG-009 |
| 테스트 종류 | UPG |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 버전별정상/손상/legacy content fixture |
| 수행 절차 | 복제백업→N+1 체인→불변식→실제 release 로로드 |
| 예상 결과 | 원본 hash 동일·ID/금/가문/귀환기록보존·명시 alias 이외변경0 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 원본 hash 동일·ID/금/가문/귀환기록보존·명시 alias 이외변경0 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-rb-010"></a>
### ALL-RB-010 — 앱롤백과데이터롤백분리

| 항목 | 설계 |
|---|---|
| Test ID | ALL-RB-010 |
| 테스트 종류 | RB |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 업데이트전자동3+수동5·업데이트후신규진행 |
| 수행 절차 | 구앱으로신 schema 읽기→거절→구세이브복원 |
| 예상 결과 | 신세이브쓰기0·구 snapshot 만복원·이후진행손실안내 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 신세이브쓰기0·구 snapshot 만복원·이후진행손실안내 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-pt-011"></a>
### ALL-PT-011 — Full오프라인release성능

| 항목 | 설계 |
|---|---|
| Test ID | ALL-PT-011 |
| 테스트 종류 | PT |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 현역2200·아이템100000·연대기1000000·실제10kasset |
| 수행 절차 | 시작/조회/스크롤/전투/1 일진행/save/load 각30 회 warm·cold 분리 |
| 예상 결과 | P24 예산및기기별기준충족·RNG/결과동치·원시측정파일제공 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | P24 예산및기기별기준충족·RNG/결과동치·원시측정파일제공 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-dep-012"></a>
### ALL-DEP-012 — 설치·내보내기·기기이동·복구

| 항목 | 설계 |
|---|---|
| Test ID | ALL-DEP-012 |
| 테스트 종류 | DEP |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 서명 release 와동일 content·서버계정없음 |
| 수행 절차 | A 기기새게임→export→B 기기 import→kill/load→기능여정 |
| 예상 결과 | 새슬롯생성·기존세이브보존·필수 네트워크0·원문이미지 mapping 유지 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 새슬롯생성·기존세이브보존·필수 네트워크0·원문이미지 mapping 유지 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-sec-013"></a>
### ALL-SEC-013 — 정상화면의비공개정보경계

| 항목 | 설계 |
|---|---|
| Test ID | ALL-SEC-013 |
| 테스트 종류 | SEC |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | 공개동일·잠재력/스킬상성만다른 NPC 쌍 |
| 수행 절차 | 목록/검색/정렬/추천/대화/TalkBack/공유요약 비교 |
| 예상 결과 | 공개허용값이외차이0·portrait 번호로능력유추규칙없음 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 공개허용값이외차이0·portrait 번호로능력유추규칙없음 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |

<a id="all-rec-014"></a>
### ALL-REC-014 — chunk corruption와root GC

| 항목 | 설계 |
|---|---|
| Test ID | ALL-REC-014 |
| 테스트 종류 | REC |
| 대상 기능 | SYSTEM |
| 사전 조건 | 관련 모든 Phase Gate 의 구현 및 승인 완료. 격리 데이터·해당버전 release artifact·독립 QA oracle 준비. 현재는 실행 전. |
| 입력값 | g1 금100/g2 금60/g3 손상·수동 g1 핀 |
| 수행 절차 | g3 로드→g2 복구제안→g1 수동로드→GC→모든 root 재검증 |
| 예상 결과 | 세대혼합0·수동 g1 보존·미참조 chunk 만삭제 |
| DB/파일 확인 | 각여정의권위테이블·receipt·RNG·generation root 를직접조회하고계정/아이템/시간합계를대조. |
| 로그 확인 | sourceCommandId/epoch/generation/boundary/오류범위와버전을확인. 원시로그/기기정보를증거저장. |
| 상태 확인 | 세대혼합0·수동 g1 보존·미참조 chunk 만삭제 |
| 성공 기준 | 모든하위 assertion 충족·실행 증거 hash 연결·새회귀결함0 |
| 실행 상태/실제 결과/증거 | NOT_RUN / 미실행 / 없음 |


## 6. 결함 등급과 중단 기준
| 등급 | 예 | 출시정책 |
|---|---|---|
| BLOCKER | 세이브손상/아이템복제/다른 슬롯쓰기/진행영구잠김/후계자원본소실 | 0 건필수·출시불가 |
| HIGH | 필수조건불이행/랭킹증표중복/숨은정보유출/큰성능중단 | 0 건필수 또는명시적 scope 비활성승인;원문삭제금지 |
| MEDIUM | 비핵심문구/선택이미지 fallback/사용성 | 책임자·영향·회피방법·수정계획승인 |
| LOW | 미세정렬/장식 | 출시후처리가능;진행관리대장보존 |

## 7. 테스트 데이터 규모와 실행주기
PR 은계약/Unit/빠른 FK·codec·대표회귀,통합브랜치는실제 DB/전체 source validator/UI,릴리즈후보는 Full 자산/10·50·100·300 년/실기기성능/업그레이드/rollback 을수행한다. CI 주기는프로젝트운영계획일뿐이문서작성중실행된것이아니다. 확률·성능시험은표본과기기를고정하고불안정테스트를근거없이 skip 하지않는다.
