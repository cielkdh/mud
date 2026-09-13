# P1-TASK-006 작업 컨텍스트

> 자동 생성된 착수용 요약이다. 충돌 시 아래 원문 링크와 관리데이터가 우선한다.

## Task

- 기능: `FUNC-P1-002` 콘텐츠 검증·사전 DB 빌드
- 단계/모듈: 계약 / :core:content / :tools:content-builder
- 선행 Task: P0-TASK-021
- 상세: ContentBundle, content-bundle-manifest.json, profile, ContentId=sourceId/closed kind, 무조건 실행하는 fresh DDL, 갱신용 row_version 없는 immutable content row, CDB-Q01..Q06 query inventory, transaction 전 foreign_keys=1/journal_mode=delete, resource close/sidecar 0/read-only 재오픈, 참조 license registry entry를 포함한 assetManifestSha256, generatedByVersion을 포함한 bundleId, 동일 target idempotent publish와 build output 전용 current.json, sourceHash/logicalContentHash/artifactFileSha256 범위를 고정한다. JSON validation report를 권위로 두고 Markdown/asset-preview.html은 동일 JSON을 읽는다. FUNC-P1-002 REQUIRED/DATA Assertion과 fixture를 승인하거나 결정 근거로 재분류한다.
- 완료 조건: canonical ID/kind·fresh DDL/query inventory/journal·close·sidecar/idempotent publish/build pointer/report schema/hash/profile fixture 승인, FUNC-P1-002 미승인 REQUIRED/DATA 0건

## 기능 계약

- 메소드: `ContentBuilder.build(draft: CatalogDraft, rules: BuildRules) -> ContentBundle`
- 대상 schema: content_manifest, content_template, content_alias
- 규칙: schema/type/range/reference/tag/recipe cycle/AST/profile을 고정 순서로 검사하고 diagnostics를 안정 정렬한다 / content.db는 비어 있는 새 staging root에 무조건 DDL로 만들며 transaction 전에 foreign_keys=1과 journal_mode=delete를 확인하고 기존 파일/schema object는 STAGING_NOT_EMPTY로 거절하며 N→N+1 migration을 적용하지 않는다 / JSON validation report를 권위 산출물로 만들고 Markdown과 asset-preview.html은 같은 JSON만 읽어 생성한다 / 모든 값은 PreparedStatement parameter로 적재하고 FK·integrity·독립 semantic audit·resource close·SQLite sidecar 0·read-only 재오픈 통과 뒤 hash로 외부 bundle manifest를 확정·fsync해 bundleId directory로 발행하며 current.json은 build output 전용 pointer다
- 정상: 두 번 동일 소스를 빌드 / canonical 콘텐츠 hash 동일, wall-clock metadata는 hash 제외
- 경계: 태그 허용과 금지 동시 지정 / ERROR; 합리화하여 자동 수정하지 않음
- 실패: DB 빌드 중 I/O 실패 / 직전 승인 bundle 유지; 절반짜리 bundle 비활성

## 결정 의존

- C09: 승인·기준선 반영 — 보호키탈취금지·일반공유후 최후generic key 명시배정; 이름동명이인허용.
- C10: 승인·기준선 반영 — unit=RATIO/BASIS_POINT/FLAT, typed effect AST; description-only effect를임의숫자로출시하지않음.
- C13: 승인·기준선 반영 — NFC·대소문자·공백 정규화 후 exact/prefix index를 기본으로 하고 한글 부분검색은 결정적 2-gram shadow token table을 사용한다. FTS5 추가는 P0 가용성과 품질 우위가 실측될 때만 허용한다.
- C18: 승인·기준선 반영 / 실물 NOT_RUN — 고정 실물 초상 M/W 각5000장, 512x640 opaque sRGB WebP, install-time portraits_v1 asset pack, pack 512MiB/전체 install-time 768MiB 이하. Full 활성 catalog는 미정 효과·깨진 참조·배포권 미확인 0건. 실제 파일/검수는 NOT_RUN.
- C23: 승인·기준선 반영 — 117개 자동 후보를 개별 검토했다. P0 강제 항목은 AR-S0123-001, AR-S3023-001·002·003·008·015, AR-S3024-001~004·007·008·011, AR-S3025-003~004, AR-S3027-005, AR-S3030-003, AR-S3031-001·004, AR-S3032-002, AR-S3126-002, AR-S3135-001~004·012·013·019·021~023이다. 나머지 AR-S0124-001~032, AR-S0127-003, 위 범위 밖 AR-S3023/3024/3031/3135, AR-S3046-004, AR-S3095-001은 P0 구현으로 주장하지 않고 콘텐츠·RNG·저장·UI·성능·통합의 실제 Owner Phase에서 IMPLEMENTED/VERIFIED로 전환한다. APPROVED_REQUIREMENT는 원문 요구와 Owner를 승인했다는 뜻이며 P0 구현 완료를 뜻하지 않는다.
- C24: 기술책임자 승인·기준선 반영 — ContentSnapshot.v1은 immutable content identity와 template map만 보유하고 WorldSession에 필수 주입한다. ContentCompatibilitySnapshot.v1은 save가 실제 참조하는 (kind,id,definitionVersion,definitionHash)를 보존하며 승인된 legacy provenance 없이는 같은 ID의 의미 변경도 자동 수용하지 않는다. portraitImageKey와 exact key는 확장자 없는 canonical AssetId이며 path는 검증된 root-relative PNG/WebP다. P1은 pure/in-memory 계약, builder JVM SQLite oracle, debug fixture gallery만 소유하고 Android SQLite adapter는 P3, production Loading/copy/semantics는 P22가 소유한다. Android memory 기준은 steady 384MiB, transient 512MiB이며 768MiB는 install-time compressed size다.

## 관련 Test

- P1-UT-002: 같은 CatalogDraft를 두 번, wall-clock/절대경로만 다르게 제공 → logicalContentHash와 정렬 row가 같고 진단 metadata만 다를 수 있음 [NOT_RUN]
- P1-BT-002: 허용/금지 tag 동시 지정과 recipe A→B→A → 안정 정렬된 TAG_CONFLICT/RECIPE_CYCLE ERROR [NOT_RUN]
- P1-FT-002: 기존 파일/schema가 있는 staging root, WAL 요청·열린 DB handle·가짜 content.db-wal/shm/journal, staging row write, bundle/current.json ATOMIC_MOVE 직전 fault와 atomic move 미지원 → 기존 staging은 STAGING_NOT_EMPTY, journal은 DELETE로 정규화되고 열린 handle/sidecar 잔존은 안전 실패, current.json은 완전 이전/다음 bundle만 가리키며 미지원은 PUBLISH_ATOMIC_UNSUPPORTED, 혼합 active 0 [NOT_RUN]
- P1-CT-002: ERROR 1개와 WARN-only CatalogDraft → ERROR면 writer 0회, WARN-only면 staging writer 1회 [NOT_RUN]
- P1-IT-002: 동일 source/rules/tool/license registry 2회와 같은 logical content·서로 다른 asset/registry, O'Brien'); DROP TABLE content_template;--·줄바꿈·Unicode 표시명, dangling template/alias·미지원 ContentKind·usage/category mismatch와 display/JSON/asset metadata corruption fault → PreparedStatement 경계 문자열 roundtrip과 schema 유지, dangling FK·미지원 kind·usage 파생 category 불일치·display/JSON/definition/byte-size/SHA sealing 전 거절. 동일 build는 같은 artifact/bundleId이며 기존 target 검증 뒤 idempotent success, 참조 asset 또는 license registry entry 차이는 서로 다른 assetManifestSha256/bundleId. 각 DB는 IF NOT EXISTS 없는 fresh DDL, foreign_keys=1, journal_mode=delete, content_manifest 1행, foreign_key_check 0행, integrity_check=ok, semantic audit PASS, sidecar 0, read-only 재오픈/외부 hash 일치 [NOT_RUN]

## REQUIRED/DATA Atomic Assertions

- AR-S3049-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3049` §3049 content.db L63029: 앱 실행 중 일반적으로 수정하지 않는다.
- AR-S3076-001 / DATA / APPROVED_REQUIREMENT / `REQ-S3076` §3076 정적 콘텐츠 제작 Pipeline L63513: 단계=원본; 기술=CSV/JSON; 설명=대량 아이템/몬스터/이벤트 편집
- AR-S3076-002 / DATA / APPROVED_REQUIREMENT / `REQ-S3076` §3076 정적 콘텐츠 제작 Pipeline L63514: 단계=Schema Validation; 기술=Kotlin validator; 설명=필수 필드/ID/FK/범위
- AR-S3076-003 / DATA / APPROVED_REQUIREMENT / `REQ-S3076` §3076 정적 콘텐츠 제작 Pipeline L63515: 단계=Cross Validation; 기술=ContentValidator; 설명=드롭/레시피/스킬/이미지 참조
- AR-S3076-004 / DATA / APPROVED_REQUIREMENT / `REQ-S3076` §3076 정적 콘텐츠 제작 Pipeline L63516: 단계=Build; 기술=Gradle task; 설명=content.db + asset-manifest 생성
- AR-S3076-005 / DATA / APPROVED_REQUIREMENT / `REQ-S3076` §3076 정적 콘텐츠 제작 Pipeline L63517: 단계=Package; 기술=assets/; 설명=content.db + 이미지
- AR-S3076-006 / DATA / APPROVED_REQUIREMENT / `REQ-S3076` §3076 정적 콘텐츠 제작 Pipeline L63518: 단계=Runtime; 기술=Read-only ContentRepository; 설명=정적 콘텐츠 조회
- AR-S3077-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3077` §3077 Content Source Format L63537: 런타임에 CSV를 직접 읽지 않는다.

## Command/Event 계약

| `FUNC-P1-002` 콘텐츠 검증·사전 DB 빌드 | `tool` | content build 산출물만 | live save.db와 command receipt를 사용하지 않음 |

## 권위 문서

- [Phase 상세](../02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md)
- [Atomic Assertions](../82_원자_요구사항_및_Assertion_추적표.md)
- [Command/Event](../84_전체_Command_Event_계약서.md)
- [Data Dictionary](../81_전체_데이터사전.md)
