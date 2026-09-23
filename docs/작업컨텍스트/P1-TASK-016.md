# P1-TASK-016 작업 컨텍스트

> 자동 생성된 착수용 요약이다. 충돌 시 아래 원문 링크와 관리데이터가 우선한다.

## Task

- 기능: `FUNC-P1-004` 콘텐츠·이미지 버전 교체와 호환
- 단계/모듈: 계약 / :core:content
- 선행 Task: P0-TASK-021
- 상세: save의 logicalContentHash를 콘텐츠 호환 identity로 사용하는 Compatible/MigrationRequired/Unsupported BindingPlan, REMAP/TOMBSTONE alias와 ContentCompatibilitySnapshot.v1/legacySnapshotIndex.v1 입력을 고정한다. compatibility snapshot은 save가 실제 참조하는 (kind,id,definitionVersion,definitionHash)를 정렬 보존하며, 같은 ID의 definition 변경도 승인 provenance가 있는 terminal legacy entry 없이는 Unsupported다. assetManifestSha256/artifactFileSha256/bundleId는 설치 무결성 값이며 save 호환 판정에서 제외한다. 적용 owner가 P3/P25임을 명시하고 FUNC-P1-004 REQUIRED/DATA Assertion을 승인하거나 결정 근거로 재분류한다.
- 완료 조건: BindingPlan/alias/오류 fixture 승인, FUNC-P1-004 미승인 REQUIRED/DATA 0건

## 기능 계약

- 메소드: `ContentBinding.resolve(saved: ContentBinding, installed: ContentBundle) -> BindingPlan`
- 대상 schema: content_manifest, content_alias, asset_image
- 규칙: save content/balance/generator/RNG 버전, ContentCompatibilitySnapshot.v1 required definitions와 설치 bundle/alias/legacySnapshotIndex.v1을 입력으로 immutable BindingPlan만 계산한다 / alias는 same-kind terminal 한 hop 또는 TOMBSTONE만 허용하고 self/cycle/chain/missing target을 build time에 거절한다 / logical hash가 다를 때 각 (kind,id,definitionVersion,definitionHash)는 동일 tuple/hash 또는 승인 provenance가 있는 terminal REMAP/same-ID legacy entry로 증명해야 한다. 미증명 필수 ID나 같은 ID의 의미 변경은 Unsupported이며 자동 치환하지 않는다 / Android는 세션 시작 시 검증된 InstalledBundle을 ContentSnapshot.v1로 고정해 WorldSession 필수 생성자에 주입하며 P1은 BindingPlan을 적용하거나 save를 쓰지 않고 실제 적용은 P3/P25가 소유한다
- 정상: 옛 ID alias OLD-WPN→WPN-0001 / 변환 이력과 이전 ID 보존
- 경계: 선택 이미지 팩 비활성 / 기본 portrait/fallback으로 정상 표시
- 실패: 필수 스킬 ID에 alias/legacy 없음 / 로드 차단·원본 세이브 보존

## 결정 의존

- C09: 승인·기준선 반영 — 보호키탈취금지·일반공유후 최후generic key 명시배정; 이름동명이인허용.
- C10: 승인·기준선 반영 — unit=RATIO/BASIS_POINT/FLAT, typed effect AST; description-only effect를임의숫자로출시하지않음.
- C13: 승인·기준선 반영 — NFC·대소문자·공백 정규화 후 exact/prefix index를 기본으로 하고 한글 부분검색은 결정적 2-gram shadow token table을 사용한다. FTS5 추가는 P0 가용성과 품질 우위가 실측될 때만 허용한다.
- C18: 승인·기준선 반영 / 실물 NOT_RUN — 고정 실물 초상 M/W 각5000장, 200x200 opaque sRGB PNG 또는 WebP(전체 동일 형식), install-time portraits_v1 asset pack, pack 512MiB/전체 install-time 768MiB 이하. Full 활성 catalog는 미정 효과·깨진 참조·배포권 미확인 0건. 실제 파일/검수는 NOT_RUN.
- C23: 승인·기준선 반영 — 117개 자동 후보를 개별 검토했다. P0 강제 항목은 AR-S0123-001, AR-S3023-001·002·003·008·015, AR-S3024-001~004·007·008·011, AR-S3025-003~004, AR-S3027-005, AR-S3030-003, AR-S3031-001·004, AR-S3032-002, AR-S3126-002, AR-S3135-001~004·012·013·019·021~023이다. 나머지 AR-S0124-001~032, AR-S0127-003, 위 범위 밖 AR-S3023/3024/3031/3135, AR-S3046-004, AR-S3095-001은 P0 구현으로 주장하지 않고 콘텐츠·RNG·저장·UI·성능·통합의 실제 Owner Phase에서 IMPLEMENTED/VERIFIED로 전환한다. APPROVED_REQUIREMENT는 원문 요구와 Owner를 승인했다는 뜻이며 P0 구현 완료를 뜻하지 않는다.
- C24: 기술책임자 승인·기준선 반영 — ContentSnapshot.v1은 immutable content identity와 template map만 보유하고 WorldSession에 필수 주입한다. ContentCompatibilitySnapshot.v1은 save가 실제 참조하는 (kind,id,definitionVersion,definitionHash)를 보존하며 승인된 legacy provenance 없이는 같은 ID의 의미 변경도 자동 수용하지 않는다. portraitImageKey와 exact key는 확장자 없는 canonical AssetId이며 path는 검증된 root-relative PNG/WebP다. P1은 pure/in-memory 계약, builder JVM SQLite oracle, debug fixture gallery만 소유하고 Android SQLite adapter는 P3, production Loading/copy/semantics는 P22가 소유한다. Android memory 기준은 steady 384MiB, transient 512MiB이며 768MiB는 install-time compressed size다.

## 관련 Test

- P1-UT-004: OLD-WPN→WPN-0001 terminal REMAP → MigrationRequired step이 old/new ID와 provenance를 보존 [NOT_RUN]
- P1-BT-004: logicalContentHash 동일, assetManifestSha256/artifactFileSha256/bundleId만 다른 두 InstalledBundle → Compatible plan과 runtime fallback이며 save logical_content_hash/content identity 불변 [NOT_RUN]
- P1-FT-004: 필수 SKL ID에 alias와 legacy snapshot 없음 → Unsupported(missingIds), 원본 save bytes/hash 불변 [NOT_RUN]
- P1-CT-004: 동일 saved/installed/alias 입력을 두 instance에 제공 → 동일 BindingPlan이고 파일/DB open·write 0 [NOT_RUN]
- P1-IT-004: logicalContentHash 동일·변경, asset-only 변경, migration/unsupported version matrix → 세 BindingPlan DTO가 P3/P25 handoff codec roundtrip하며 적용·save write는 0 [NOT_RUN]

## REQUIRED/DATA Atomic Assertions

- AR-S2791-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2791` §2791 이미지 변경 시 세이브 호환성 L58069: 같은 `assetId` 를 유지하면 세이브 마이그레이션이 필요 없다.

## Command/Event 계약

| `FUNC-P1-004` 콘텐츠·이미지 버전 교체와 호환 | `compute` | 아니오 | `resolve`는 BindingPlan만 반환하고 적용은 별도 승인된 migration command가 수행 |

## 권위 문서

- [Phase 상세](../02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md)
- [Atomic Assertions](../82_원자_요구사항_및_Assertion_추적표.md)
- [Command/Event](../84_전체_Command_Event_계약서.md)
- [Data Dictionary](../81_전체_데이터사전.md)
