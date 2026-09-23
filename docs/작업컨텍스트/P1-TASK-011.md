# P1-TASK-011 작업 컨텍스트

> 자동 생성된 착수용 요약이다. 충돌 시 아래 원문 링크와 관리데이터가 우선한다.

## Task

- 기능: `FUNC-P1-003` 정적 콘텐츠 조회·로컬 AssetResolver·크롭
- 단계/모듈: 계약 / :core:content / :core:image / :tools:content-builder
- 선행 Task: P0-TASK-021
- 상세: licenseId를 가진 AssetManifest entry, closed EntityKind.v1과 nullable templateId/ordered exactAssetKeys: List<AssetId>를 가진 category 없는 AssetResolveRequest/ResolvedAsset, findTemplate/listTemplates/findAlias/findAssetBindings(templateId,usage)/findAsset/listAssetFallbacks(usage) 6개 read method와 CDB-Q01..Q06 prepared query allowlist, canonical path/usage에서 유일하게 파생한 category/crop, 정수 floor/clamp crop geometry, target decode/input 상한/focal 좌표와 화면별 fallback matcher 순서를 고정한다. portraitImageKey와 C18 NPC-M/W key는 확장자 없는 canonical AssetId이며 별도 key namespace/파일명 조합은 없다. listTemplates는 id 순이며 alias는 terminal 한 hop/TOMBSTONE만 반환한다. TEXT usage allowlist, 후보 assetId 1회·유한 종료, memoryCacheKey와 ContentRepository owner cancel/join/close, Exact/Fallback/SkippedByQualityMode와 debug gallery Loading 표시 계약을 포함해 FUNC-P1-003 REQUIRED/DATA Assertion과 PNG/WebP fixture를 승인하거나 결정 근거로 재분류한다.
- 완료 조건: canonical type·license registry·manifest/path/input 상한/physical metadata·파생 category/crop/focal/화면별 fallback/TEXT/cache/UI state와 6 read API/query plan/lifecycle fixture 승인, FUNC-P1-003 미승인 REQUIRED/DATA 0건

## 기능 계약

- 메소드: `AssetResolver.resolve(request: AssetResolveRequest) -> ResolvedAsset`
- 대상 schema: content_template, content_alias, asset_image, asset_binding, asset_fallback
- 규칙: AssetCatalogCompiler는 input 상한과 magic bytes/hash/크기/단일 frame/EXIF orientation/alpha/sRGB/license registry/focal 좌표를 검증하고 참조 registry entry를 assetManifestSha256에 포함한다 / ContentRepository는 findTemplate/listTemplates/findAlias/findAssetBindings(templateId,usage)/findAsset/listAssetFallbacks(usage) 6개 read method만 제공하며 InstalledBundle 하나에 고정되고 CDB-Q01..Q06 prepared query와 owner cancel/join/close lifecycle을 사용한다. P1은 in-memory contract와 builder JVM SQLite oracle만 구현하고 Android SQLite adapter는 P3가 소유한다 / AssetResolver는 nullable templateId의 binding을 ordered exactAssetKeys: List<AssetId> 뒤에 붙이고 entityKind/usage별 공개 fallbackContext matcher를 유한 순회하며 후보 assetId를 한 번만 열고 source key를 바꾸지 않는다. portraitImageKey와 exact key는 확장자 없는 canonical AssetId다 / ResolvedAsset은 Exact/Fallback/SkippedByQualityMode를 구분하고 TEXT usage allowlist, usage에서 유일하게 파생한 category/crop과 정수 crop geometry·target decode 상한을 반환한다 / core:image는 local-only Coil을 사용하고 memoryCacheKey=bundleId|assetId|sha256|usage|targetBucket|qualityMode로 bounded cache를 재사용한다
- 정상: MONSTER, templateId=MON-0001, BATTLE_TOKEN / 동일 파일의 1:1 crop과 원래 key 유지
- 경계: 해당 파일 없음 / 여성 generic fallback 표시; 용병 identity 불변
- 실패: ../../save.db를 asset key로 입력 / INVALID_ASSET_PATH; 파일 읽지 않음

## 결정 의존

- C09: 승인·기준선 반영 — 보호키탈취금지·일반공유후 최후generic key 명시배정; 이름동명이인허용.
- C10: 승인·기준선 반영 — unit=RATIO/BASIS_POINT/FLAT, typed effect AST; description-only effect를임의숫자로출시하지않음.
- C13: 승인·기준선 반영 — NFC·대소문자·공백 정규화 후 exact/prefix index를 기본으로 하고 한글 부분검색은 결정적 2-gram shadow token table을 사용한다. FTS5 추가는 P0 가용성과 품질 우위가 실측될 때만 허용한다.
- C18: 승인·기준선 반영 / 실물 NOT_RUN — 고정 실물 초상 M/W 각5000장, 200x200 opaque sRGB PNG 또는 WebP(전체 동일 형식), install-time portraits_v1 asset pack, pack 512MiB/전체 install-time 768MiB 이하. Full 활성 catalog는 미정 효과·깨진 참조·배포권 미확인 0건. 실제 파일/검수는 NOT_RUN.
- C23: 승인·기준선 반영 — 117개 자동 후보를 개별 검토했다. P0 강제 항목은 AR-S0123-001, AR-S3023-001·002·003·008·015, AR-S3024-001~004·007·008·011, AR-S3025-003~004, AR-S3027-005, AR-S3030-003, AR-S3031-001·004, AR-S3032-002, AR-S3126-002, AR-S3135-001~004·012·013·019·021~023이다. 나머지 AR-S0124-001~032, AR-S0127-003, 위 범위 밖 AR-S3023/3024/3031/3135, AR-S3046-004, AR-S3095-001은 P0 구현으로 주장하지 않고 콘텐츠·RNG·저장·UI·성능·통합의 실제 Owner Phase에서 IMPLEMENTED/VERIFIED로 전환한다. APPROVED_REQUIREMENT는 원문 요구와 Owner를 승인했다는 뜻이며 P0 구현 완료를 뜻하지 않는다.
- C24: 기술책임자 승인·기준선 반영 — ContentSnapshot.v1은 immutable content identity와 template map만 보유하고 WorldSession에 필수 주입한다. ContentCompatibilitySnapshot.v1은 save가 실제 참조하는 (kind,id,definitionVersion,definitionHash)를 보존하며 승인된 legacy provenance 없이는 같은 ID의 의미 변경도 자동 수용하지 않는다. portraitImageKey와 exact key는 확장자 없는 canonical AssetId이며 path는 검증된 root-relative PNG/WebP다. P1은 pure/in-memory 계약, builder JVM SQLite oracle, debug fixture gallery만 소유하고 Android SQLite adapter는 P3, production Loading/copy/semantics는 P22가 소유한다. Android memory 기준은 steady 384MiB, transient 512MiB이며 768MiB는 install-time compressed size다.

## 관련 Test

- P1-UT-003: InstalledBundle의 findTemplate/listTemplates/findAlias와 MON-0001/BATTLE_TOKEN findAssetBindings(templateId,usage)·findAsset·listAssetFallbacks(usage), templateId=MON-0001, exactAssetKeys=[], entityKind=MONSTER, fallbackContext, targetPx=320, qualityMode=FULL AssetResolveRequest → 6개 read method가 template id 순·terminal alias 한 hop·binding/fallback priority 순과 미존재 empty/null을 반환하고 Exact ResolvedAsset, usage 파생 PORTRAIT/SQUARE_CENTER geometry, decode 256px 상한, 정규화 focal·원래 key 목록·cache identity가 동일 [NOT_RUN]
- P1-BT-003: 용병 LIST/DETAIL/DIALOG/BATTLE/CHRONICLE, monster family, dungeon/room region/theme, item type, facility category별 missing/corrupt exact와 첫 fallback 표 → 용병은 LIST/DETAIL/DIALOG=SEX, BATTLE=CLASS, CHRONICLE=CATEGORY_DEFAULT 우선이고 다른 kind도 usage별 첫 승인 matcher를 사용하며 후보 assetId 1회·재귀 0·source key/identity 불변·유한 Fallback 또는 AssetUnavailable [NOT_RUN]
- P1-FT-003: ../save.db, C:/save.db, https://host/a.png, backslash, root 밖 symlink, 32MiB/8,192px/16,777,216px 초과, 미등록·BLOCKED licenseId와 MIME/animated WebP/EXIF/ICC/alpha 불일치 → 경로 공격은 INVALID_ASSET_PATH와 root 밖 read 0, input 상한·license registry·physical metadata 불일치는 VALIDATION_FAILED build ERROR이며 과대 decode·artifact 발행 0 [NOT_RUN]
- P1-CT-003: FACE/BUST/BATTLE/ROOM_BG/KEYART legacy code, 10개 usage→category/crop 표, 홀짝 source 크기·경계 focal crop golden, TEXT 허용/생략과 Exact/Fallback/Skipped/Loading fixture → builder만 canonical usage로 변환하고 runtime legacy 거절, category/crop은 usage에서 유일 파생, 정수 floor/clamp crop golden 일치, TEXT 허용 usage 정상 decode·금지 usage repository/file/decode 0, Loading/Fallback/terminal text layout 구분 [NOT_RUN]
- P1-IT-003: 승인 licenseId와 물리 metadata가 검증된 PNG/WebP, CDB-Q01..Q06, usage 파생 crop, Exact/Fallback/TEXT/debug Loading/terminal layout, HTML 특수문자 공개 이름과 분할 asset-preview → asset-preview.html index/category별 정적 page·manifest·builder JVM SQLite query oracle·in-memory repository·debug gallery의 binding/category/crop/focal/physical metadata가 일치하고 공개 이름 contentDescription, Android DB adapter·production route·외부 요청·DOM 주입·stale cache·Main-thread I/O 0 [NOT_RUN]

## REQUIRED/DATA Atomic Assertions

- AR-S2710-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2710` §2710 이미지 시스템 목표 L56247: 이미지가 없어도 UI가 깨지지 않는다.
- AR-S2710-005 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2710` §2710 이미지 시스템 목표 L56250: Android 모바일 환경에서 메모리/스크롤 성능을 해치지 않는다.
- AR-S2710-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2710` §2710 이미지 시스템 목표 L56252: 동일 엔티티가 여러 화면에서 일관된 대표 이미지를 사용한다.
- AR-S2711-001 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56260: 카테고리=mercenary_face; 설명=용병/NPC 얼굴 정면 일러스트; 주 사용 화면=용병 상세/대화/전투/관계/길드원 목록
- AR-S2711-002 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56261: 카테고리=mercenary_bust; 설명=용병 상반신 반신 일러스트; 주 사용 화면=대화 이벤트/스토리/중요 연대기
- AR-S2711-003 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56262: 카테고리=mercenary_battle_chibi; 설명=전투용 간략 토큰/미니 일러스트; 주 사용 화면=전투 UI, 파티 진형
- AR-S2711-004 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56263: 카테고리=monster_portrait; 설명=몬스터 초상/대표 이미지; 주 사용 화면=도감/조우 화면/전투 상단
- AR-S2711-005 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56264: 카테고리=monster_token; 설명=전투용 토큰/실루엣; 주 사용 화면=전투 대상 표시
- AR-S2711-006 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56265: 카테고리=dungeon_keyart; 설명=던전 대표 배경 이미지; 주 사용 화면=던전 목록/상세/입장 전
- AR-S2711-007 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56266: 카테고리=room_bg; 설명=방/구역 배경 이미지; 주 사용 화면=탐색 메인 화면
- AR-S2711-008 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56267: 카테고리=terrain_tile; 설명=지형/환경 아이콘 또는 타일; 주 사용 화면=지도/방 상태/지형 페널티
- AR-S2711-009 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56268: 카테고리=weapon_icon; 설명=무기 아이콘; 주 사용 화면=장비 목록/상세/전리품
- AR-S2711-010 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56269: 카테고리=armor_icon; 설명=방어구 아이콘; 주 사용 화면=장비 목록/상세/전리품
- AR-S2711-011 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56270: 카테고리=accessory_icon; 설명=반지/목걸이 등 아이콘; 주 사용 화면=장비 목록/상세/전리품
- AR-S2711-012 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56271: 카테고리=item_icon; 설명=소비 아이템/재료 아이콘; 주 사용 화면=인벤토리/상점/제작
- AR-S2711-013 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56272: 카테고리=set_emblem; 설명=세트 장비 엠블럼; 주 사용 화면=장비 상세/세트 UI
- AR-S2711-014 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56273: 카테고리=guild_emblem; 설명=길드 문장; 주 사용 화면=길드 화면/랭킹
- AR-S2711-015 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56274: 카테고리=facility_thumb; 설명=도시 시설 썸네일; 주 사용 화면=도시 시설 목록
- AR-S2711-016 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56275: 카테고리=event_illustration; 설명=중요 이벤트 삽화; 주 사용 화면=희귀/전설 이벤트
- AR-S2711-017 / DATA / APPROVED_REQUIREMENT / `REQ-S2711` §2711 이미지 자산 카테고리 L56276: 카테고리=fallback_generic; 설명=대표 기본 이미지; 주 사용 화면=에셋 미존재 시
- AR-S2712-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2712` §2712 정적 자산 vs 런타임 데이터 L56293: 실제 이미지 바이너리는 세이브에 저장하지 않는다.
- AR-S2715-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2715` §2715 용병 NPC 이미지 파일명 및 경로 규칙 L56347: 용병/NPC 개별 이미지는 이미 고정된 파일 풀을 사용한다.
- AR-S2715-009 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2715` §2715 용병 NPC 이미지 파일명 및 경로 규칙 L56378: 총 10,000장의 고정 NPC portrait pool을 가진다.
- AR-S2716-009 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2716` §2716 용병 NPC 단일 Portrait 재사용 구조 L56444: 따라서 기본 NPC 이미지 파일을 다음처럼 세 종류로 따로 만들지 않는다.
- AR-S2716-013 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2716` §2716 용병 NPC 단일 Portrait 재사용 구조 L56459: 한 장만 사용한다.
- AR-S2717-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2717` §2717 용병 Portrait 사용 위치 L56469: 동일 `portraitImageKey`는 다음 화면에서 공통 사용한다.
- AR-S2718-005 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2718` §2718 용병 상세 정보 이미지 규칙 L56516: 자산이 정상적으로 존재하면 그대로 사용한다.
- AR-S2719-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2719` §2719 대화 화면 이미지 규칙 L56534: 대화에서도 같은 portrait를 사용한다.
- AR-S2719-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2719` §2719 대화 화면 이미지 규칙 L56554: 표정 variation은 일반 NPC에게 요구하지 않는다.
- AR-S2719-011 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2719` §2719 대화 화면 이미지 규칙 L56565: 없으면 기본 `NPC-W-03147.webp`를 그대로 사용한다.
- AR-S2720-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2720` §2720 전투 화면 이미지 규칙 L56571: 전투에서도 동일 portrait를 사용한다.
- AR-S2720-006 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2720` §2720 전투 화면 이미지 규칙 L56589: 별도의 battle 이미지가 없더라도 정상 동작해야 한다.
- AR-S2722-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2722` §2722 길드원 목록 이미지 규칙 L56618: 얼굴은 48dp 수준 썸네일만 사용한다.
- AR-S2724-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2724` §2724 몬스터 변종 표현 L56650: 모든 조합별 고유 이미지를 둘 필요는 없다.
- AR-S2725-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2725` §2725 던전 이미지 구조 L56692: 탐색 메인 화면은 `room_bg`를 사용한다.
- AR-S2727-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2727` §2727 방/구역 배경 이미지 규칙 L56713: 탐색 UI에서 매 방마다 완전히 다른 대형 그림을 강제하지 않는다.
- AR-S2729-006 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2729` §2729 지형 이미지 L56761: 지도/방 정보/전투 지형 패널에서 재사용한다.
- AR-S2732-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2732` §2732 접두사/접미어 장비 시각 표현 L56811: 모든 접사별 아이콘을 새로 만들지 않는다.
- AR-S2738-003 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56926: 화면=대화 화면; 표현 방식=bust 이미지 크게 표시; 우선 규칙=감정 상태에 따라 표정 variant 가능
- AR-S2738-004 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56927: 화면=전투 화면 - 아군; 표현 방식=battle token 또는 face crop; 우선 규칙=battle token 우선
- AR-S2738-005 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56928: 화면=전투 화면 - 적; 표현 방식=monster token/portrait; 우선 규칙=보스는 portrait + token 동시 가능
- AR-S2738-006 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56929: 화면=파티 편성; 표현 방식=얼굴 썸네일; 우선 규칙=상성/상태 배지와 함께
- AR-S2738-007 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56930: 화면=길드원 목록; 표현 방식=소형 얼굴 + 길드 문장; 우선 규칙=스크롤 성능 우선
- AR-S2738-008 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56931: 화면=던전 목록; 표현 방식=던전 keyart; 우선 규칙=없으면 지역군 대표 이미지
- AR-S2738-009 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56932: 화면=던전 탐색 메인; 표현 방식=room background; 우선 규칙=없으면 dungeon keyart blur 또는 room fallback
- AR-S2738-010 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56933: 화면=지도; 표현 방식=지형 아이콘/방 아이콘; 우선 규칙=실시간 배경 전체 렌더 금지
- AR-S2738-011 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56934: 화면=장비 목록; 표현 방식=아이콘 중심; 우선 규칙=세트 엠블럼/등급 테두리
- AR-S2738-012 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56935: 화면=장비 상세; 표현 방식=대형 아이콘 + 대표 렌더; 우선 규칙=없으면 타입별 fallback
- AR-S2738-013 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56936: 화면=상점/제작; 표현 방식=아이템 아이콘; 우선 규칙=희귀 재료는 강조 프레임
- AR-S2738-014 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56937: 화면=몬스터 도감; 표현 방식=monster portrait; 우선 규칙=가족군/변종 정보 표시
- AR-S2738-015 / DATA / APPROVED_REQUIREMENT / `REQ-S2738` §2738 화면별 이미지 사용 매핑 L56938: 화면=이벤트 콘텐츠; 표현 방식=event illustration 선택적; 우선 규칙=일반 이벤트는 텍스트 중심
- AR-S2740-003 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56965: 누락 상황=개별 용병 battle token 없음; 대체=face crop + 프레임; 비고=최후에는 generic token
- AR-S2740-004 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56966: 누락 상황=monster portrait 없음; 대체=monster family image; 비고=최후에는 generic monster
- AR-S2740-005 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56967: 누락 상황=monster token 없음; 대체=portrait crop/실루엣; 비고=가독성 우선
- AR-S2740-006 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56968: 누락 상황=room_bg 없음; 대체=roomTheme 대표 배경; 비고=없으면 dungeon keyart blur
- AR-S2740-007 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56969: 누락 상황=dungeon keyart 없음; 대체=region/family dungeon image; 비고=없으면 generic dungeon
- AR-S2740-008 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56970: 누락 상황=weapon/armor/item icon 없음; 대체=item type generic icon; 비고=예: generic sword
- AR-S2740-009 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56971: 누락 상황=facility thumbnail 없음; 대체=facility category image; 비고=예: hospital_generic
- AR-S2740-010 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56972: 누락 상황=guild emblem 없음; 대체=generic guild crest; 비고=문장 placeholder
- AR-S2740-011 / DATA / APPROVED_REQUIREMENT / `REQ-S2740` §2740 fallback 규칙 예시 L56973: 누락 상황=event illustration 없음; 대체=이벤트 카테고리 대표 이미지; 비고=텍스트는 반드시 유지
- AR-S2741-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2741` §2741 NPC Portrait Pool 구조 L56980: 고정 portrait pool을 우선한다.
- AR-S2741-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2741` §2741 NPC Portrait Pool 구조 L57003: 은 이미지 선택에 직접 관여하지 않는다.
- AR-S2753-001 / DATA / APPROVED_REQUIREMENT / `REQ-S2753` §2753 화면 로딩 정책 L57285: 화면=용병 목록; 권장 로딩=48~64dp 썸네일, lazy load; 비고=최대 2,000명
- AR-S2753-002 / DATA / APPROVED_REQUIREMENT / `REQ-S2753` §2753 화면 로딩 정책 L57286: 화면=용병 상세; 권장 로딩=중형 1장 + 하위 썸네일; 비고=세부 이미지는 지연 로드
- AR-S2753-003 / DATA / APPROVED_REQUIREMENT / `REQ-S2753` §2753 화면 로딩 정책 L57287: 화면=대화; 권장 로딩=bust 1장 우선; 비고=표정 변형은 필요 시
- AR-S2753-004 / DATA / APPROVED_REQUIREMENT / `REQ-S2753` §2753 화면 로딩 정책 L57288: 화면=전투; 권장 로딩=battle token 12개 내외; 비고=대형 portrait 남발 금지
- AR-S2753-005 / DATA / APPROVED_REQUIREMENT / `REQ-S2753` §2753 화면 로딩 정책 L57289: 화면=던전 탐색; 권장 로딩=room_bg 1장; 비고=스크롤 전체 이미지 금지
- AR-S2753-006 / DATA / APPROVED_REQUIREMENT / `REQ-S2753` §2753 화면 로딩 정책 L57290: 화면=장비 목록; 권장 로딩=아이콘 48dp; 비고=리스트 가상화 필수
- AR-S2753-007 / DATA / APPROVED_REQUIREMENT / `REQ-S2753` §2753 화면 로딩 정책 L57291: 화면=도감; 권장 로딩=중형 portrait + 썸네일; 비고=필터 전환 시 캐시 활용
- AR-S2757-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2757` §2757 과도한 선로드 금지 L57341: 2,000명 전체 얼굴,
- AR-S2757-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2757` §2757 과도한 선로드 금지 L57342: 모든 몬스터 portrait,
- AR-S2757-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2757` §2757 과도한 선로드 금지 L57343: 모든 장비 아이콘을 앱 시작 시 한 번에 로드하지 않는다.
- AR-S2757-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2757` §2757 과도한 선로드 금지 L57345: 필요 시점 기반 로드를 원칙으로 한다.
- AR-S2758-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2758` §2758 오프라인 전용 구조 L57352: 이미지는 네트워크 URL에 의존하지 않는다.
- AR-S2770-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2770` §2770 부상/상태이상 시각 반영 L57654: 배지/오버레이를 우선 사용한다.
- AR-S2771-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2771` §2771 전투 적/아군 수가 많은 경우 L57671: 여러 적을 모두 portrait 로 크게 보여주지 않는다.
- AR-S2771-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2771` §2771 전투 적/아군 수가 많은 경우 L57673: 원칙:
- AR-S2773-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2773` §2773 연대기/기록과 이미지 L57697: 모든 기록에 이미지를 붙이지 않는다.
- AR-S2774-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2774` §2774 메모리 예산 원칙 L57713: 이미지는 게임 몰입 요소지만
- AR-S2774-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2774` §2774 메모리 예산 원칙 L57714: 텍스트/시스템 게임 성격을 해치지 않아야 한다.
- AR-S2774-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2774` §2774 메모리 예산 원칙 L57716: 따라서 한 화면에서 동시에 로드하는 대형 이미지 수를 제한한다.
- AR-S2774-005 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2774` §2774 메모리 예산 원칙 L57721: 홈 / 0~2장의 중형 썸네일 / 용병 상세 / 1장의 주 portrait + 소형 보조 / 대화 / 1장의 bust / 전투 / 다수 소형 token + 보스 portrait 0~1장 / 탐색 / 1장의 room background
- AR-S2775-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2775` §2775 Android 성능 최적화 원칙 L57741: 썸네일은 다운샘플링 디코딩
- AR-S2775-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2775` §2775 Android 성능 최적화 원칙 L57742: 원본 대형 이미지는 필요 시만
- AR-S2775-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2775` §2775 Android 성능 최적화 원칙 L57743: Recycler 스크롤 중 대형 디코드 금지
- AR-S2775-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2775` §2775 Android 성능 최적화 원칙 L57744: 동일 아이콘 반복 로딩 금지
- AR-S2775-005 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2775` §2775 Android 성능 최적화 원칙 L57745: room_bg 전환 시 crossfade 짧게
- AR-S2775-006 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2775` §2775 Android 성능 최적화 원칙 L57746: 저사양 모드에서는 배경 blur/애니메이션 축소
- AR-S2777-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2777` §2777 접근성과 이미지 L57778: 텍스트 대체가 반드시 있어야 한다.
- AR-S2777-008 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2777` §2777 접근성과 이미지 L57787: 스크린리더용 alt 라벨도 생성 가능해야 한다.
- AR-S2780-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2780` §2780 미공개 정보와 이미지 L57825: 이미지로 과도하게 암시하지 않는다.
- AR-S2784-006 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2784` §2784 UI용 NPC 이미지 우선순위 L57932: NPC의 기본 얼굴은 모든 화면에서 동일한 `portraitImageKey`를 사용한다.
- AR-S2784-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2784` §2784 UI용 NPC 이미지 우선순위 L57934: 몬스터/던전/장비의 기존 fallback 규칙은 V26 설계를 그대로 유지한다.
- AR-S2786-011 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2786` §2786 이미지 관리용 툴/화면 필요성 L57994: 일반 플레이어에게는 필요 없다.
- AR-S2788-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2788` §2788 최소 시작 커버리지 권장 L58019: 용병 개별 face: 주요/고정 NPC 우선
- AR-S2792-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2792` §2792 애니메이션 이미지 사용 범위 L58086: 짧은 hover-like pulse 없음
- AR-S2792-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2792` §2792 애니메이션 이미지 사용 범위 L58091: GIF/무거운 APNG 남용 금지.
- AR-S2796-001 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58138: 모든 핵심 엔티티는 이미지 카테고리 체계로 관리한다.
- AR-S2796-002 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58139: 세이브에는 이미지 바이너리가 아니라 assetId/바인딩 정보만 저장한다.
- AR-S2796-003 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58140: 용병/NPC는 face, bust, battle token 3계층 구조를 권장한다.
- AR-S2796-004 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58141: 용병 얼굴은 상세/대화/전투/파티/길드/관계/연대기에서 재사용한다.
- AR-S2796-005 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58142: 몬스터는 portrait 와 token 을 분리한다.
- AR-S2796-006 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58143: 던전은 keyart, 방은 room_bg, 지형은 icon 중심으로 분리 관리한다.
- AR-S2796-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58144: 무기/장비/아이템은 대량 관리를 위해 icon family 체계를 우선한다.
- AR-S2796-008 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58145: 접두어/접미어/강화/세트는 기본 아이콘 위에 오버레이로 표현한다.
- AR-S2796-010 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58147: 대표 이미지가 나와도 실제 이름/설명 텍스트는 항상 함께 표시한다.
- AR-S2796-011 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58148: placeholder(로딩 중)와 fallback(자산 없음)을 구분한다.
- AR-S2796-012 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58149: AssetImage / EntityImageBinding / FallbackRule 구조로 DB 매핑을 관리한다.
- AR-S2796-015 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58152: 모든 몬스터·장비에 고유 그림을 강제하지 않고 family/대표 이미지를 적극 활용한다.
- AR-S2796-016 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58153: 모바일 성능을 위해 목록은 썸네일, 상세는 중형, 전투는 token 중심으로 제한한다.
- AR-S2796-017 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58154: preload 는 현재 파티/현재 전투/현재 던전 등 작은 범위에만 적용한다.
- AR-S2796-018 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58155: 오프라인 게임이므로 이미지 로딩은 네트워크가 아니라 로컬 자산 기반이어야 한다.
- AR-S2796-019 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58156: 이미지 정보가 없더라도 UI가 깨지지 않고 대표 이미지로 자연스럽게 동작해야 한다.
- AR-S2796-020 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S2796` §2796 모바일 UI/UX용 이미지 최종 원칙 L58157: 궁극적으로 이미지 시스템은 텍스트 중심 게임의 몰입감과 가독성을 높이는 보조 레이어다.
- AR-S3079-007 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3079` §3079 Image 기술 L63578: NPC Portrait 10,000장을 앱 시작 시 decode하지 않는다.
- AR-S3081-005 / REQUIRED / APPROVED_REQUIREMENT / `REQ-S3081` §3081 이미지 URI L63611: UI는 물리 경로를 알 필요가 없다.

## Command/Event 계약

| `FUNC-P1-003` 정적 콘텐츠 조회·로컬 AssetResolver·크롭 | `read/tool` | content/asset build 산출물만 | 런타임 조회·`resolve`는 읽기 전용이며 command receipt를 만들지 않음 |

## 권위 문서

- [Phase 상세](../02_Phase1_콘텐츠_자산_빌드파이프라인_상세설계서.md)
- [Atomic Assertions](../82_원자_요구사항_및_Assertion_추적표.md)
- [Command/Event](../84_전체_Command_Event_계약서.md)
- [Data Dictionary](../81_전체_데이터사전.md)
