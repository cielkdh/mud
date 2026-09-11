# P1-TASK-016 작업 컨텍스트

> 자동 생성된 착수용 요약이다. 충돌 시 아래 원문 링크와 관리데이터가 우선한다.

## Task

- 기능: `FUNC-P1-004` 콘텐츠·이미지 버전 교체와 호환
- 단계/모듈: 계약 / :core:content
- 선행 Task: P0-TASK-021
- 상세: save의 logicalContentHash를 유일한 콘텐츠 호환 identity로 사용하는 Compatible/MigrationRequired/Unsupported BindingPlan, REMAP/TOMBSTONE alias와 legacy snapshot 입력을 고정한다. assetManifestSha256/artifactFileSha256/bundleId는 설치 무결성 값이며 save 호환 판정에서 제외한다. 적용 owner가 P3/P25임을 명시하고 FUNC-P1-004 REQUIRED/DATA Assertion을 승인하거나 결정 근거로 재분류한다.
- 완료 조건: BindingPlan/alias/오류 fixture 승인, FUNC-P1-004 미승인 REQUIRED/DATA 0건

## 기능 계약

- 메소드: `ContentBinding.resolve(saved: ContentBinding, installed: ContentBundle) -> BindingPlan`
- 대상 schema: content_manifest, content_alias, asset_image
- 규칙: save content/balance/generator/RNG 버전과 설치 bundle/alias/legacy를 입력으로 immutable BindingPlan만 계산한다 / alias는 same-kind terminal 한 hop 또는 TOMBSTONE만 허용하고 self/cycle/chain/missing target을 build time에 거절한다 / 필수 ID가 alias/legacy에 없으면 Unsupported이며 의미가 다른 template으로 자동 치환하지 않는다 / Android는 세션 시작 시 검증된 InstalledBundle을 immutable snapshot으로 고정하며 P1은 BindingPlan을 적용하거나 save를 쓰지 않고 실제 적용은 P3/P25가 소유한다
- 정상: 옛 ID alias OLD-WPN→WPN-0001 / 변환 이력과 이전 ID 보존
- 경계: 선택 이미지 팩 비활성 / 기본 portrait/fallback으로 정상 표시
- 실패: 필수 스킬 ID에 alias/legacy 없음 / 로드 차단·원본 세이브 보존

## 결정 의존

- C09: 승인·기준선 반영 — 보호키탈취금지·일반공유후 최후generic key 명시배정; 이름동명이인허용.
- C10: 승인·기준선 반영 — unit=RATIO/BASIS_POINT/FLAT, typed effect AST; description-only effect를임의숫자로출시하지않음.
- C13: 승인·기준선 반영 — NFC·대소문자·공백 정규화 후 exact/prefix index를 기본으로 하고 한글 부분검색은 결정적 2-gram shadow token table을 사용한다. FTS5 추가는 P0 가용성과 품질 우위가 실측될 때만 허용한다.
- C18: 승인·기준선 반영 / 실물 NOT_RUN — 고정 실물 초상 M/W 각5000장, 512x640 opaque sRGB WebP, install-time portraits_v1 asset pack, pack 512MiB/전체 install-time 768MiB 이하. Full 활성 catalog는 미정 효과·깨진 참조·배포권 미확인 0건. 실제 파일/검수는 NOT_RUN.

## 관련 Test

- P1-UT-004: OLD-WPN→WPN-0001 terminal REMAP → MigrationRequired step이 old/new ID와 provenance를 보존 [NOT_RUN]
- P1-BT-004: logicalContentHash 동일, assetManifestSha256/artifactFileSha256/bundleId만 다른 두 InstalledBundle → Compatible plan과 runtime fallback이며 save logical_content_hash/content identity 불변 [NOT_RUN]
- P1-FT-004: 필수 SKL ID에 alias와 legacy snapshot 없음 → Unsupported(missingIds), 원본 save bytes/hash 불변 [NOT_RUN]
- P1-CT-004: 동일 saved/installed/alias 입력을 두 instance에 제공 → 동일 BindingPlan이고 파일/DB open·write 0 [NOT_RUN]
- P1-IT-004: logicalContentHash 동일·변경, asset-only 변경, migration/unsupported version matrix → 세 BindingPlan DTO가 P3/P25 handoff codec roundtrip하며 적용·save write는 0 [NOT_RUN]

## REQUIRED/DATA Atomic Assertions

- AR-S2791-002 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S2791` §2791 이미지 변경 시 세이브 호환성 L58069: 같은 `assetId` 를 유지하면 세이브 마이그레이션이 필요 없다.
- AR-S2791-003 / REQUIRED / AUTO_EXTRACTED_REVIEW_REQUIRED / `REQ-S2791` §2791 이미지 변경 시 세이브 호환성 L58071: 정말 삭제해야 할 경우:

## Command/Event 계약

| `FUNC-P1-004` 콘텐츠·이미지 버전 교체와 호환 | `compute` | 아니오 | `resolve`는 BindingPlan만 반환하고 적용은 별도 승인된 migration command가 수행 |

## 권위 문서

- [Phase 상세](../02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md)
- [Atomic Assertions](../82_원자_요구사항_및_Assertion_추적표.md)
- [Command/Event](../84_전체_Command_Event_계약서.md)
- [Data Dictionary](../81_전체_데이터사전.md)
