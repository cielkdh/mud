# P0-TASK-006 작업 컨텍스트

> 자동 생성된 착수용 요약이다. 충돌 시 아래 원문 링크와 관리데이터가 우선한다.

## Task

- 기능: `FUNC-P0-002` 빌드·모듈·기술버전 고정
- 단계/모듈: 계약 / Gradle root / :app / :core:simulation
- 선행 Task: 없음
- 상세: P0 Build Manifest를 실제 Gradle 파일로 옮긴다. root IMSI, Android identity, JDK 17, :app/:core:simulation 두 project, plugin/dependency allowlist, 실행 명령과 evidence 경로를 고정하고 FUNC-P0-002의 REQUIRED/DATA를 승인하며 채택한 RECOMMENDED Assertion을 결정 ID에 연결한다. 별도 BuildBaseline class는 만들지 않는다.
- 완료 조건: P0 Build Manifest·두 project graph·fixture 승인, FUNC-P0-002 미승인 REQUIRED/DATA 0건, 채택 RECOMMENDED 결정 ID 연결

## 기능 계약

- 메소드: `Gradle :core:simulation:test + :app:testDebugUnitTest/:app:lintDebug/:app:assembleDebug`
- 대상 schema: 없음
- 규칙: 원문 §3031 모듈명은 논리 namespace로 유지하고 P0 물리 Gradle project는 :app과 :core:simulation 두 개로 제한한다 / 실제로 분리한 두 모듈만 allowlist를 적용하고 근거 없는 빈 모듈 및 simulation의 Android·Room·Compose·네트워크 import를 정적 검사한다 / WorldSession과 SavePort 계약은 :core:simulation에 두되 SaveCoordinator·Room·:core:save는 P3에서 실제 schema와 함께 생성한다 / P0 조립 루트는 :app만 두고 :tools:headless는 P23/P25에서 독립 실행 요구가 확인될 때만 생성한다 / AGP/Kotlin 및 P0 직접 의존성을 exact pin하고 Room/KSP/schema export는 P3 Gate에서 검증한다 / 기존 구현이 있으면 adapter부터 연결하며 UI/도메인 전면 재작성은 별도 승인한다
- 정상: simulation 소스에 Android import 없음 / 순수 JVM test 태스크 단독 성공
- 경계: 허용 edge :app→:core:simulation; 금지 edge :core:simulation→:app; simulation의 Android/Room/Compose/네트워크 import와 미선언 P0 module/plugin/dependency / 금지 fixture는 각각 실패하고 허용 graph와 :core:simulation JVM test는 통과
- 실패: 잠금 버전 의존성 resolve 실패 / 빌드 차단; 자동 최신 버전으로 변경하지 않음

## 결정 의존

- C14: 승인·기준선 반영 / 빌드 NOT_RUN — Android-only greenfield: compileSdk37/targetSdk36/minSdk26, AGP9.4.0/Gradle9.6.0/JDK17/Kotlin2.4.20/KSP2.3.11/Compose BOM2026.08.00/Room3.0.2/Coil3.5.0 exact pin, SchemaBaselineMode=GREENFIELD_V1. 실제 출시 이력이 발견될 때만 재승인 후 LEGACY_CHAIN으로 전환한다. P0는 resolve/JVM·app compile·launch, P3는 Room compile/최초 v1 schema export를 검증한다.

## 관련 Test

- P0-UT-002: P0 Build Manifest와 gradlew.bat :core:simulation:test → 물리 project가 :app, :core:simulation뿐이고 JVM test가 성공한다. [NOT_RUN]
- P0-BT-002: 정상 edge :app→:core:simulation; 금지 edge :core:simulation→:app; simulation의 Android/Room/Compose/네트워크 import; 미선언 P0 module/plugin/dependency → 모든 금지 fixture는 실패하고 정상 :app→:core:simulation graph와 JVM test는 통과 [NOT_RUN]
- P0-FT-002: 잠금 버전 의존성 resolve 실패 → 빌드 차단; 자동 최신 버전으로 변경하지 않음 [NOT_RUN]
- P0-CT-002: simulation 소스에 Android import 없음; 같은요청2회 → 순수 JVM test 태스크 단독 성공; 같은입력2회 결과동일·live state/RNG쓰기0 [NOT_RUN]
- P0-IT-002: gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug와 AppRoot launch smoke → 세 Gradle task와 AppRoot smoke가 성공하고 built-in Kotlin 중복 plugin, 미선언 module, 실제 DB 생성이 없다. [NOT_RUN]

## REQUIRED/DATA Atomic Assertions

- AR-S3023-001 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62440: 영역=언어; 기술=Kotlin 2.4.x; 사용처=Android 전 영역 + 순수 JVM 시뮬레이션; 판단=Java 혼용 최소화
- AR-S3023-002 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62441: 영역=빌드; 기술=AGP 9.4 + Gradle 9.6 + JDK 17; 사용처=최신 Android 빌드 체인; 판단=버전 고정
- AR-S3023-003 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62442: 영역=UI; 기술=Jetpack Compose + Material 3; 사용처=V25~V28 모바일 UI 구현; 판단=XML View 신규 사용 지양
- AR-S3023-004 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62443: 영역=Adaptive UI; 기술=Material 3 Adaptive; 사용처=폰/태블릿 분기; 판단=600dp+ 다중 패널
- AR-S3023-005 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62444: 영역=Navigation; 기술=Navigation Compose; 사용처=7개 상위 영역 + 상세 Back Stack; 판단=타입 안전 route 권장
- AR-S3023-006 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62445: 영역=상태관리; 기술=ViewModel + StateFlow + UDF; 사용처=화면 상태 일관성; 판단=전용 MVI 라이브러리 불필요
- AR-S3023-007 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62446: 영역=DI; 기술=Hilt; 사용처=Repository/Engine/DAO 교체; 판단=테스트 Fake 주입
- AR-S3023-008 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62447: 영역=비동기; 기술=Kotlin Coroutines + Flow; 사용처=DB/이미지/화면 상태; 판단=RxJava 신규 도입 안 함
- AR-S3023-009 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62448: 영역=DB; 기술=Room 3 + KSP; 사용처=save.db/content.db; 판단=SQLite 직접 API 최소화
- AR-S3023-010 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62449: 영역=SQLite Driver; 기술=BundledSQLiteDriver; 사용처=FTS5/테스트 환경 일관성; 판단=버전 차이 최소화
- AR-S3023-011 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62450: 영역=설정; 기술=Preferences DataStore; 사용처=UI 설정/사운드/로그 상세도; 판단=게임 세이브 저장 금지
- AR-S3023-012 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62451: 영역=직렬화; 기술=kotlinx.serialization; 사용처=Content import/export, save archive metadata; 판단=JSON 중심
- AR-S3023-013 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62452: 영역=이미지; 기술=Coil 3; 사용처=NPC 10,000 portrait, 몬스터/아이템; 판단=local asset + fallback
- AR-S3023-014 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62453: 영역=파일 접근; 기술=Storage Access Framework; 사용처=세이브 Export/Import; 판단=외부 저장소 직접 경로 의존 금지
- AR-S3023-015 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62454: 영역=테스트; 기술=JUnit + Kotlin/JVM tests + Compose UI Test; 사용처=도메인/DB/UI; 판단=시뮬레이션은 JVM 우선
- AR-S3023-016 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62455: 영역=속성기반 테스트; 기술=Kotest Property 또는 자체 generator; 사용처=Invariant/랜덤 데이터; 판단=test scope만
- AR-S3023-017 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62456: 영역=성능; 기술=Macrobenchmark + Baseline Profiles; 사용처=시작/스크롤/전투 UI; 판단=Release 필수
- AR-S3023-018 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62457: 영역=정적분석; 기술=Android Lint + Detekt/Ktlint; 사용처=코드 품질; 판단=CI 적용
- AR-S3023-019 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3023` §3023 최종 기술 스택 L62458: 영역=배포; 기술=Android App Bundle + R8; 사용처=Play 배포; 판단=debug/release 분리
- AR-S3024-001 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62466: 항목=compileSdk; 권장=37; 이유=Compose 최신 계열 및 API 호환
- AR-S3024-002 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62467: 항목=targetSdk; 권장=36 이상; 이유=2026-08-31 이후 Google Play 신규/업데이트 기준
- AR-S3024-003 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62468: 항목=minSdk; 권장=26 권장; 이유=Android 8.0+, 파일/Coroutine/Compose 운영 단순화
- AR-S3024-004 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62469: 항목=JDK; 권장=17; 이유=AGP 9.4 기본/최소 JDK
- AR-S3024-005 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62470: 항목=ABI; 권장=arm64-v8a 필수, armeabi-v7a 선택; 이유=대부분 최신 단말
- AR-S3024-006 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62471: 항목=화면; 권장=Portrait 우선, Tablet adaptive; 이유=V25 UI 설계와 일치
- AR-S3024-007 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62472: 항목=네트워크; 권장=필수 없음; 이유=완전 오프라인 싱글
- AR-S3024-008 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62474: 정확한 라이브러리 버전은 프로젝트 생성 시 `libs.versions.toml`에서 고정한다.
- AR-S3024-011 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3024` §3024 2026년 기준 플랫폼 Baseline L62483: 사용 금지.
- AR-S3025-003 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3025` §3025 시작 버전 권장 L62520: WorkManager는 선택적 유지보수 작업에만 사용한다.
- AR-S3025-004 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3025` §3025 시작 버전 권장 L62522: 게임 월드시간에는 사용하지 않는다.
- AR-S3027-005 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3027` §3027 Java 사용 정책 L62549: 게임 Core를 Java/Kotlin 반반으로 혼합하지 않는다.
- AR-S3030-003 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3030` §3030 프로젝트 전체 Architecture L62620: Clean Architecture를 지나치게 교과서적으로 나누지는 않는다.
- AR-S3031-001 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62630: Module=:app; 책임=Application, MainActivity, Navigation root; 특성=Android 의존
- AR-S3031-002 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62631: Module=:core:common; 책임=Result, clock abstraction, IDs, utilities; 특성=순수 Kotlin
- AR-S3031-003 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62632: Module=:core:model; 책임=공통 immutable model/value object; 특성=순수 Kotlin
- AR-S3031-004 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62633: Module=:core:simulation; 책임=World/Combat/Dungeon/NPC/Event engine; 특성=순수 Kotlin 핵심
- AR-S3031-005 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62634: Module=:core:content; 책임=정적 콘텐츠 model/repository/validator; 특성=Android 의존 최소
- AR-S3031-006 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62635: Module=:core:database; 책임=Room DB/DAO/Migration; 특성=Android/JVM Room
- AR-S3031-007 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62636: Module=:core:data; 책임=Repository 구현, mapper, transaction boundary; 특성=Android
- AR-S3031-008 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62637: Module=:core:designsystem; 책임=Theme, tokens, 공용 Compose component; 특성=Compose
- AR-S3031-009 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62638: Module=:core:image; 책임=AssetResolver, Coil configuration, fallback; 특성=Android
- AR-S3031-010 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62639: Module=:core:save; 책임=SaveGeneration, export/import, recovery; 특성=Android + domain interface
- AR-S3031-011 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62640: Module=:core:testing; 책임=Fixture, seed pack, fake repository; 특성=Test
- AR-S3031-012 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62641: Module=:feature:home; 책임=홈/추천/알림; 특성=Compose
- AR-S3031-013 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62642: Module=:feature:dungeon; 책임=던전 목록/상세/탐색/지도; 특성=Compose
- AR-S3031-014 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62643: Module=:feature:combat; 책임=전투 HUD/결과/Replay; 특성=Compose
- AR-S3031-015 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62644: Module=:feature:party; 책임=파티/전술/정치; 특성=Compose
- AR-S3031-016 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62645: Module=:feature:mercenary; 책임=용병/상세/스카우트/관계; 특성=Compose
- AR-S3031-017 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62646: Module=:feature:dialogue; 책임=대화/영입/거래/의뢰; 특성=Compose
- AR-S3031-018 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62647: Module=:feature:city; 책임=도시/시설/정비; 특성=Compose
- AR-S3031-019 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62648: Module=:feature:guild; 책임=길드/랭킹/정책; 특성=Compose
- AR-S3031-020 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62649: Module=:feature:records; 책임=연대기/통계/가문; 특성=Compose
- AR-S3031-021 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62650: Module=:feature:settings; 책임=설정/접근성; 특성=Compose
- AR-S3031-022 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62651: Module=:feature:validation; 책임=Simulation Validation Center; 특성=debug 전용
- AR-S3031-023 / DATA / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3031` §3031 Gradle Module 구조 L62652: Module=:benchmark; 책임=Macrobenchmark/Baseline Profile; 특성=benchmark 전용
- AR-S3032-002 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3032` §3032 Module Dependency 방향 L62675: 금지:
- AR-S3095-001 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3095` §3095 날짜 타입 L63880: 게임 날짜는 java.time.LocalDate를 직접 authoritative 값으로 사용하지 않는다.
- AR-S3126-002 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S3126` §3126 서버 기술 L64418: Backend Server 없음 / REST API 없음 / 로그인 없음 / 실시간 서버 없음

## Command/Event 계약

| `FUNC-P0-002` 빌드·모듈·기술버전 고정 | `tool` | 아니오 | Gradle build output/lock/report만 기록; 게임 DB 사용 안 함 |

## 권위 문서

- [Phase 상세](../01_Phase0_기준선_아키텍처_개발기반_상세설계서.md)
- [Atomic Assertions](../82_원자_요구사항_및_Assertion_추적표.md)
- [Command/Event](../84_전체_Command_Event_계약서.md)
- [Data Dictionary](../81_전체_데이터사전.md)
