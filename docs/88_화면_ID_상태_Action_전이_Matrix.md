# 88. 화면 ID · 상태 · Action · 전이 Matrix

> 목적: P22의 방대한 UI/UX 규칙을 화면 단위 계약으로 고정한다. 화면 ID는 설계 보완안이며 route 실제 이름은 Navigation 구현 시 동일 의미를 유지하는 범위에서 조정할 수 있다.

## 1. 공통 화면 상태

```text
LOADING → READY / EMPTY / ERROR / BLOCKED
READY → MUTATING → READY
READY → NAVIGATING → DESTINATION
MUTATING → ERROR(재시도/복구 가능)
BLOCKED → READY / 이전 화면
STOPPED → RESTORED
```

| 상태 | 의미 | UI 규칙 |
|---|---|---|
| `LOADING` | 초기 데이터 로딩 | Skeleton/Progress, 중복 command 금지 |
| `READY` | 상호작용 가능 | 권위 snapshot version 표시/보관 |
| `EMPTY` | 정상이나 데이터 없음 | 원인+다음 행동 제공 |
| `MUTATING` | Command 처리 중 | 중복 탭 방지, 취소 가능성 표시 |
| `DIRTY_CONFIRM` | 마지막 durable save 이후 변경 존재 | 손실 범위·저장 후/저장하지 않고/취소 CTA를 함께 표시 |
| `DELETE_CONFIRM` | 사용자가 전체 세이브 삭제를 요청하고 대상·영향을 확인 중 | 가문·플레이 시간·진행도와 선택적 Export를 표시하고 취소를 기본 포커스로 둔다; 확인 전 변경 금지 |
| `DELETING` | 전체 세이브 삭제가 접수되어 결과를 확인 중 | 중복 삭제/시작 CTA를 막고, active writer close/drain 이유를 표시하며 확정 전 빈 상태를 보이지 않는다 |
| `DELETE_COMPLETED` | 전체 세이브 삭제가 durable하게 확인됨 | 삭제 결과를 한 번 알리고 SAVE 목록의 EMPTY 상태로 이동 |
| `DELETE_ERROR` | 삭제 실패 또는 미확정 결과 | 실패 확정 전 기존 세이브 보존 여부를 단정하지 않고, 미확정은 조회/receipt로 확인하며 재요청하지 않는다 |
| `VALIDATING` | 파일·후보·호환성 검사 중 | 검사 이유와 취소 가능 여부 표시, 성공으로 선행 전환 금지 |
| `MIGRATING` | 단계별 세이브 변환 중 | 원본 보존, 진행 단계·중단 불가 이유 표시 |
| `ERROR` | 조회/명령 실패 | 기존 정상 상태 보존, 재시도/복구 |
| `BLOCKED` | 기능 미구현·권한·선행 조건 미충족 | 가짜 성공을 표시하지 않고 이유·가능한 다음 행동·돌아가기 제공 |
| `INTERRUPTED` | 시간/작업이 중요 사건으로 중단 | 중단 이유와 남은 목표 표시 |
| `RESTORED` | Process death/Back 후 복원 | 필터/스크롤/편집중 draft 정책 준수 |

## 2. Screen Registry 및 전이 Matrix

P0는 아래 registry에 새 Screen ID나 실제 gameplay route를 추가하지 않는다. `AppRoot`는 Loading/Ready/Empty/Error/Blocked shell을 검증하기 위해 기존 ID를 placeholder destination으로만 사용하며, 각 화면의 실제 route·action·저장 조립은 표의 Owner Phase가 소유한다.

| Screen ID | 화면 | Route | Owner | 상태 | 대표 Action | Destination | Guard | 실행 계약 |
|---|---|---|---|---|---|---|---|---|
| SCR-START-001 | 시작/이어하기 | start | P3/P22 | LOADING/READY/EMPTY/ERROR/BLOCKED | 최근 슬롯 이어하기/새 게임/가져오기 | 성공한 세션 open 후 HOME 또는 SCR-START-002 | 슬롯 checksum/version | `QUERY` / `NAVIGATION` |
| SCR-START-002 | 새 게임/슬롯 생성 | start/new | P3/P4/P22 | DRAFT/CREATING/ERROR/BLOCKED | 슬롯·seed·기본 profile 확인/생성 | receipt와 첫 complete generation 확인 후 HOME | content/balance 호환·중복 실행 방지 | `CMD-P3-F001` (`CreateNewWorld`) |
| SCR-START-003 | 저장되지 않은 진행 확인 | start/dirty | P3/P22 | DIRTY_CONFIRM/SAVING/DISCARDING/CANCELLED/ERROR/BLOCKED | 저장 후 계속/저장하지 않고 계속/취소 | 취소 시 이전 화면, 완료 시 목적지 | dirty generation·활성 command | `CMD-P3-F001` / `LOCAL_UI` |
| SCR-HOME-001 | 홈 | home | P22 | READY/LOADING/ERROR | 던전 카드 선택 | SCR-DUN-001 | - | `NAVIGATION` |
| SCR-DUN-001 | 던전 목록 | dungeon/list | P8/P22 | LOADING/READY/EMPTY/ERROR | 필터/던전 선택 | SCR-DUN-002 | 공개정보 정책 | `QUERY` |
| SCR-DUN-002 | 던전 상세 | dungeon/detail/{id} | P8/P22 | LOADING/READY/ERROR | 입장/등록/추적 | SCR-DUN-003 | 파티·거리·상태 Guard | `CMD-P9-F001` |
| SCR-DUN-003 | 던전 탐색 | dungeon/explore/{id} | P9/P22 | READY/MUTATING/INTERRUPTED/ERROR | 이동/조사/휴식/전투 | SCR-DUN-004 또는 SCR-CMB-001 | 현재 방/통로/행동 가능 | `CMD-P9-F001` / `CMD-P9-F003` |
| SCR-DUN-004 | 던전 지도 | dungeon/map/{id} | P9/P22 | READY/MUTATING/INTERRUPTED/EMPTY/ERROR | 방 선택/길찾기/주석 저장/안전 복귀 | SCR-DUN-003 | 발견 정보·현재 routeVersion·행동 가능 | `QUERY FUNC-P9-002` / `CMD-P9-F002` |
| SCR-EVT-001 | 던전/월드 이벤트 | event/{id} | P19/P22 | READY/CHOICE_PENDING/RESOLVED | 선택지 선택 | 이전 화면 | 선택 가능 상태 | `CMD-P19-F002` |
| SCR-CMB-001 | 전투 | combat/{id} | P6/P22 | RUNNING/PAUSED/FINISHED/ERROR | 속도/로그/후퇴 | SCR-CMB-002 | 후퇴 조건/전투 상태 | `LOCAL_UI` / `CMD-P6-F005` |
| SCR-CMB-002 | 전투 결과 | combat/{id}/result | P6/P9 | READY | 보상 확인/계속 | SCR-DUN-003 또는 HOME | 정산 완료 | `LOCAL_UI` |
| SCR-PTY-001 | 파티 홈 | party | P13/P22 | READY/EMPTY | 멤버/편성/전술/정치 | SCR-PTY-002 | - | `QUERY` |
| SCR-PTY-002 | 출전 편성 | party/formation | P13/P22 | READY/DIRTY/ERROR | 6명 출전/교대 | SCR-PTY-001 | 조직/출전 상한 | `CMD-P13-F001` |
| SCR-PTY-003 | 전술 편집 | party/tactics | P5/P13 | READY/DIRTY/INVALID | 조건식 편집/저장 | SCR-PTY-001 | 전술 validator | `LOCAL_DRAFT` / `CMD-P5-F003` |
| SCR-PTY-004 | 파티 정치/투표 | party/politics | P13 | READY/VOTING/RESOLVED | 제안/투표 | SCR-PTY-001 | 헌장 권한 | `CMD-P13-F002` |
| SCR-MER-001 | 용병 목록 | mercenaries | P4/P22 | LOADING/READY/EMPTY | 검색/필터/선택 | SCR-MER-002 | 공개 정보만 | `QUERY FUNC-P4-005` |
| SCR-MER-002 | 용병 상세 | mercenary/{id} | P4/P15 | LOADING/READY/ERROR | 영입/대화/장비/관계 | SCR-DLG-001 또는 SCR-REC-001 | 정보 비대칭 정책 | `QUERY FUNC-P4-005` / `CMD-P11-F002` |
| SCR-REC-001 | 모집 공고 | recruitment | P11 | READY/EMPTY | 공고등록/지원자 | SCR-REC-002 | 모집 조건 | `CMD-P11-F002` |
| SCR-REC-002 | 협상 | recruitment/negotiate/{id} | P11 | PROPOSED/COUNTERED/ACCEPTED/REJECTED | 역제안/수락/거절 | SCR-PTY-001 | 계약 Guard | `CMD-P11-F002` |
| SCR-QST-001 | 의뢰 목록 | contracts | P11/P22 | LOADING/READY/EMPTY/ERROR | 필터/의뢰 선택 | SCR-QST-002 | 공개 정보·기한 | `QUERY ContractListView` |
| SCR-QST-002 | 의뢰 상세/수락 | contract/{id} | P11/P22 | LOADING/READY/MUTATING/ERROR | 수락/거절/추적 | 이전 또는 SCR-TIME-002 | 적격·기한·동시 의뢰 상한 | `CMD-P11-F001` |
| SCR-DLG-001 | 대화 | dialogue/{npcId} | P11/P15 | OPEN/CHOICE_PENDING/RESOLVED | topic/선택 | 이전 | 관계/상태 | `CMD-P11-F004` |
| SCR-ITM-001 | 장비/인벤토리 | inventory | P5/P22 | LOADING/READY/EMPTY | 필터/장착/상세 | SCR-ITM-002 | 소유권/보호 | `QUERY` / `CMD-P5-F001` |
| SCR-ITM-002 | 장비 상세/비교 | item/{id} | P5 | READY | 장착/강화/정련 | SCR-ENH-001 | 아이템 상태 | `QUERY` / `CMD-P5-F001` |
| SCR-SKL-001 | 스킬 | skills | P5 | READY/EMPTY | 장착/해제/상세 | SCR-SKL-002 | 슬롯 제한 | `QUERY` / `CMD-P5-F003` |
| SCR-SKL-002 | 스킬 상세 | skill/{id} | P5 | READY | 장착/학습 | SCR-SKL-001 | 클래스/보유 조건 | `CMD-P5-F002` / `CMD-P5-F003` |
| SCR-ENH-001 | 장비 강화 | enhancement/{itemId} | P12 | QUOTED/MUTATING/RESULT/ERROR | 촉매/보호석/강화 | SCR-ITM-002 | 비용·attempt·확률 | `CMD-P12-F001` |
| SCR-RFN-001 | 정련/재각성/유물 복원 | refinement/{itemId} | P12 | QUOTED/MUTATING/RESULT/ERROR | 방식·재료 선택/미리보기/확정 | SCR-ITM-002 | 아이템 상태·비용·확률·보호 | `CMD-P12-F003` |
| SCR-CRF-001 | 제작/연금/연구 | craft | P12 | DRAFT/RESERVED/RUNNING/COMPLETE | 레시피/시작/취소 | SCR-CITY-002 | 재료 선점 | `LOCAL_DRAFT` / `CMD-P12-F004` |
| SCR-CITY-001 | 도시 | city | P10/P22 | READY | 시설/이동/빠른정비 | SCR-CITY-002 | 영업/위치 | `QUERY` |
| SCR-CITY-002 | 시설 | city/facility/{id} | P10 | READY/QUEUED/CLOSED | 이용/대기 | SCR-CITY-001 | 영업시간/점유 | `CMD-P10-F001` / `CMD-P10-F002` |
| SCR-MNT-001 | 귀환 후 정비 | maintenance | P10 | QUOTED/EXECUTING/PARTIAL/COMPLETE | 자동정비 미리보기/실행 | HOME | 비용/시간 | `QUERY` / `CMD-P10-F004` |
| SCR-MKT-001 | 시장 | market | P14 | OPEN/READY/ERROR | 구매/판매/흥정 | SCR-ITM-002 | 재고/금액 | `CMD-P14-F002` |
| SCR-AUC-001 | 경매 | auction | P14 | LISTED/BIDDING/SETTLED/EXPIRED | 입찰/등록 | SCR-MKT-001 | 자금 선점 | `CMD-P14-F002` |
| SCR-LOAN-001 | 장비 대여 | rental | P14 | LOADING/READY/MUTATING/EMPTY/ERROR | 조건 확인/대여/반환 | SCR-ITM-002 | 소유권·보증금·기한 | `CMD-P14-F003` |
| SCR-LOG-001 | 물류/운송 | logistics | P14 | LOADING/READY/MUTATING/EMPTY/ERROR | 출발지·목적지·물품/배송 확인 | SCR-ITM-001 | 보관 위치·수량·운송 가능 | `CMD-P14-F004` |
| SCR-HOU-001 | 주거 | residence | P10 | ACTIVE/ARREARS/RELOCATING | 확장/이사/창고 | SCR-ITM-001 | 소유/임대 | `CMD-P10-F003` |
| SCR-GIL-001 | 길드 홈 | guild | P16/P22 | READY/OUTSIDER | 가입/직위/시설 | SCR-GIL-002 | 권한 | `QUERY` / `CMD-P16-F001` / `CMD-P16-F002` |
| SCR-GIL-002 | 길드 랭킹 | guild/ranking | P16 | READY | 상세/이력 | SCR-GIL-001 | 공식 일마감 | `QUERY` |
| SCR-GIL-003 | 길드 결정 | guild/decision | P16 | AGENDA/DEBATE/VOTE/RESOLVED | 토론/투표 | SCR-GIL-001 | 직위/권한 | `CMD-P16-F003` |
| SCR-GIL-004 | 길드 예산 | guild/budget | P16 | DRAFT/BUDGETED/ASSIGNED | 배정/승인 | SCR-GIL-001 | 재정 권한 | `CMD-P16-F002` |
| SCR-STR-001 | 전술 실험실/자동화 | strategy | P19/P22 | IDLE/SIMULATING/DIRTY/MUTATING/ERROR | 실험/전략 저장/자동화 규칙/위임 실행 | 이전 화면 | 정보범위·예산·중단정책·행동권한 | `QUERY FUNC-P19-004` / `CMD-P19-F004` |
| SCR-TIME-001 | 시간 진행 | time/advance | P2/P22 | IDLE/ADVANCING/PAUSE_REQUESTED/CANCEL_REQUESTED/INTERRUPTED/DECISION_REQUIRED/COMPLETED/CANCELLED/UNREACHABLE/LIMIT_REACHED/FAILED/ADVANCE_IN_PROGRESS/SUMMARY | 프리셋/대상/일시중단/취소/결정/새 continuation/요약 확인 | 이전 또는 결정 대상 | control 즉시 피드백+다음 safe boundary 정지; terminal receipt 재활성화 금지; public summary에 경과/정지/주요 사건/완료/경고/묶음/미확인 수 표시 | `CMD-P2-F004` / `CONTROL pause,cancel` |
| SCR-TIME-002 | 일정/예약 | schedule | P2/P22 | READY/EMPTY/CONFLICT/RISK_CONFIRM/MUTATING/STALE_PREVIEW/ERROR | 예약 상세/취소/충돌 해결 선택/위험 변경 확인 | SCR-TIME-001 | PREEMPT/CANCEL_AND_INSERT/진행 중·FINAL_BOUNDARY 취소/손실은 PublicConsequencePreview의 현재·새·취소 일정, 금액, 자원, 진행률, 공개 관계·평판, 재예약 가능 여부와 NONE/UNDETERMINED/UNKNOWN을 표시; hidden 정보 금지; preview hash+row version 재검증 | `CMD-P2-F003` reserve/cancel/resolveConflict |
| SCR-FAM-001 | 가문 | family | P18/P22 | READY | 가족/교육/후계 | SCR-FAM-002 | 공개 가족 상태 | `QUERY` |
| SCR-FAM-002 | 후계자 | family/succession | P18 | CANDIDATES/NOMINATED/READY | 지정/승계 | SCR-FAM-001 | 적격/의사/안전장치 | `CMD-P18-F002` / `CMD-P18-F003` |
| SCR-RECOR-001 | 연대기 | records/chronicle | P21/P22 | LOADING/READY/MUTATING/EMPTY/ERROR | 필터/상세/북마크 전환 | SCR-SEARCH-001 | 공개 정책·observer 권한 | `QUERY FUNC-P21-003` / `CMD-P21-F003` |
| SCR-RECOR-002 | 통계 | records/stats | P21 | LOADING/READY | 기간/지표 | SCR-RECOR-001 | aggregate 검증 | `QUERY` |
| SCR-SEARCH-001 | 전역 검색 | search | P21/P22 | IDLE/LOADING/READY/MUTATING/EMPTY/ERROR | 검색/필터/정렬/북마크 전환 | 대상 상세 | 공개정보 authorization·observer 권한 | `QUERY FUNC-P21-003` / `CMD-P21-F003` |
| SCR-NOTI-001 | 알림 센터 | notifications | P22 | READY/EMPTY | 읽음/이동 | 딥링크 대상 | event visibility | `LOCAL_PROJECTION` / `NAVIGATION` |
| SCR-SAVE-001 | 세이브/로드 | save | P3/P22 | LOADING/READY/EMPTY/SAVING/DELETE_CONFIRM/DELETING/DELETE_COMPLETED/DELETE_ERROR/ERROR/BLOCKED | 슬롯 선택/미리보기/수동저장/로드/가져오기/내보내기/전체 세이브 삭제 | 성공한 load의 session open 후 월드; 삭제 성공 후 EMPTY; 그 외 저장 목록 | 슬롯 선택·세션/무결성/dirty guard/전체 삭제 명시 확인 | `CMD-P3-F001` (`CheckpointWorld`) / `CMD-P3-F003` / `CMD-P3-F005` / `QUERY` / save maintenance |
| SCR-SAVE-002 | 세이브 복구 | save/recovery | P3 | LOADING/RECOVERABLE/EMPTY/VALIDATING/RECOVERY_LOADING/CORRUPTED/RECOVERY_COMPLETED/ERROR/BLOCKED | 후보 비교/복원/다른 후보/뒤로 | RECOVERY_COMPLETED와 새 session open 확인 후 HOME; Back은 SCR-SAVE-001 | checksum/version/complete generation | `CMD-P3-F003` |
| SCR-SAVE-003 | 세이브 호환·마이그레이션 | save/migration | P3 | LOADING/COMPATIBLE/MIGRATION_AVAILABLE/MIGRATING/INCOMPATIBLE/MIGRATION_ERROR/BLOCKED | COMPATIBLE은 열기, MIGRATION_AVAILABLE은 변환 후 열기, INCOMPATIBLE은 내보내기/뒤로 | 성공 시 선택 월드, 실패/취소 시 슬롯 목록 | schema/content/balance compatibility·원본 보존 | `CMD-P3-F004` |
| SCR-SAVE-004 | 세이브 파일 가져오기/내보내기 | save/archive | P3 | FILE_PICKER/VALIDATING/IMPORTING/IMPORTED/EXPORTING/EXPORTED/CANCELLED/PERMISSION_DENIED/ERROR/BLOCKED | 파일 선택/가져오기/내보내기/취소 | 성공은 SCR-SAVE-001, 취소/실패는 호출 화면 | SAF permission/format/checksum/content binding·기존 슬롯 보존 | `CMD-P3-F005` / `QUERY` |
| SCR-RET-001 | 귀환 조건 | return | P20 | PROGRESSING/ELIGIBLE | 증표/조건/귀환 | SCR-RET-002 | 모든 필수조건 | `QUERY` / `CMD-P20-F004` |
| SCR-RET-002 | 귀환/후일담 | return/ending | P20 | ELIGIBLE/ENDING_COMMITTED/PRESENTED | 귀환/잔류/연대기 | 종료 또는 HOME | ending commit | `CMD-P20-F004` |
| SCR-SET-001 | 설정/접근성 | settings | P22 | READY | theme/font/motion/TalkBack 지원값 | 이전 | - | `LOCAL_PREFERENCE` |
| SCR-VAL-001 | Validation Center | validation | P23 | QUEUED/RUNNING/PASSED/FAILED | suite/seed/replay | SCR-VAL-002 | debug build | `TOOL FUNC-P23-001` |
| SCR-VAL-002 | 검증 결과/재현 | validation/result/{id} | P23 | READY/FAILED/PACKAGED | trace/diff/replay | SCR-VAL-001 | artifact isolation | `TOOL_QUERY` |

## 3. Bottom Navigation 계약

| 기본 탭 | Root Screen | Back 규칙 |
|---|---|---|
| 홈 | `SCR-HOME-001` | 탭 root에서 Back은 앱 종료/상위 정책, 하위 route는 직전 상태 복원 |
| 던전 | `SCR-DUN-001` | 탭 root에서 Back은 앱 종료/상위 정책, 하위 route는 직전 상태 복원 |
| 파티 | `SCR-PTY-001` | 탭 root에서 Back은 앱 종료/상위 정책, 하위 route는 직전 상태 복원 |
| 도시 | `SCR-CITY-001` | 탭 root에서 Back은 앱 종료/상위 정책, 하위 route는 직전 상태 복원 |
| 기록 | `SCR-RECOR-001` | 탭 root에서 Back은 앱 종료/상위 정책, 하위 route는 직전 상태 복원 |

기본은 위 5개다. 커스터마이즈 시에도 슬롯 수는 5개를 유지하며, 사용자는 도시/기록 슬롯을 의뢰·캐릭터·길드 등 등록된 상위 화면으로 교체할 수 있다. 나머지 화면은 홈 또는 등록된 상위 화면에서 진입한다.

앱 시작 중 `LOADING`은 목록 확인·이전 writer 종료·선택 저장 열기 중 무엇을 기다리는지 공개 문구로 구분하며, 모르는 예상 시간을 퍼센트로 만들지 않는다. 정상 슬롯이 하나 이상이면 `SCR-START-001`에서 최근 슬롯 이어하기를 기본 CTA로 제공하고, 슬롯이 없으면 `EMPTY` 상태에서 `새 게임`과 `가져오기`만 제공한다. dirty 상태에서 이어하기·새 게임·가져오기를 선택하면 `SCR-START-003`에서 손실 경계를 확인한다. 새 게임 생성 중에는 HOME을 먼저 보여주지 않으며 `CreateNewWorld` receipt와 첫 complete generation이 모두 확인된 뒤 이동한다. checksum/version 실패는 손상 슬롯을 덮어쓰지 않고 `SCR-SAVE-002` 또는 `SCR-SAVE-003`으로 보낸다. 복구 성공 후에는 새 branch/epoch 생성과 새 세션 open 확인 뒤 HOME으로 이동하며, 복구 후보가 없으면 `SCR-SAVE-002/EMPTY`에서 저장 목록으로 돌아간다.

## 4. UiAction → Command 규칙

1. 화면 버튼은 먼저 `UiAction`으로 들어가며, 실행 계약이 `CMD-*`인 mutation만 ViewModel/UseCase가 CommandEnvelope로 변환한다.
2. `QUERY`, `NAVIGATION`, `LOCAL_*`, `TOOL*`은 authoritative WorldCommand나 gameplay command receipt를 만들지 않는다.
3. 금화·아이템·시간·관계·랭킹·던전 상태뿐 아니라 지도 주석, 안전 복귀, 전략/자동화 설정·위임 실행, 북마크처럼 live save.db를 바꾸는 Action은 Command receipt를 요구한다.
4. 동일 CTA 연타는 같은 commandId 재사용 또는 UI disable로 중복 효과를 막는다.
5. 화면이 STOPPED된 뒤 늦게 도착한 이전 sessionEpoch 결과는 적용하지 않는다.
6. Android 화면은 ViewModel의 `StateFlow<UiState>`를 `collectAsStateWithLifecycle()`로 수집한다. 화면 lifecycle마다 임의 `launch` collector를 중복 생성하지 않고, 슬롯/route key 변경 시 이전 수집과 검색 job을 취소한다.
7. NPC 2,000개·인벤토리 1,000개·100년 연대기는 전체 행을 한 번에 메모리에 올리지 않는다. Room query는 `(sortKey,id)` keyset과 초기 page size 100을 사용하고 Compose `LazyColumn`은 안정 key를 지정한다. Paging 라이브러리는 이 계약으로 P95를 못 맞춘 실측이 있을 때만 추가한다.
8. Phase 2 mutation 화면은 WorldSession의 commit 후 `CommittedPublication(PublicSnapshot, publicEvents, sourceCommandId, PublicTimeAdvanceTerminal?)`만 소비한다. TimeAdvance의 terminal 화면 상태는 `execute`한 command ID와 일치하는 committed `PublicTimeAdvanceTerminal`로만 정하고, 해당 publication이 없거나 다른 command이면 성공 Summary를 만들지 않는다. 현재 `sessionEpoch`가 아니거나 마지막 표시 `stateVersion`보다 과거인 publication은 버리고 raw DomainEvent·`AuthoritativeWorldState`·hidden payload를 직접 표시하지 않는다.
9. active AdvanceTime 중 새 gameplay CTA가 `AdvanceInProgress(activeCommandId, allowedControls)`를 반환하면 이를 일반 오류/무응답으로 표시하지 않는다. 진행 중 목표와 마지막 committed cursor, 허용된 중단 동작을 보여주며 `allowedControls=[]`인 protected completion에서는 취소 CTA를 숨기거나 비활성화한다. control 요청은 즉시 REQUESTED 상태를 표시하되 authoritative 완료는 다음 committed safe-boundary publication으로만 확정한다.
10. Phase3 save/recovery/import 화면은 `PublicProjection` 필드만 표시한다. generation/schema/checksum/path/raw exception/debug validation 값은 일반 화면과 접근성 label에서 제거한다.
11. Save/restore/import/migration 중 mutation CTA는 단일 flight로 잠그고, safe cancellation이 불가능한 경우 Back을 차단한 이유와 완료 조건을 표시한다.
12. 모든 화면은 상태별 CTA enablement, 비활성 이유, Back/Cancel 결과 목적지를 명시한다. `EMPTY`에는 실제 가능한 다음 행동을 제공하며, migration 불가 상태에 `변환 후 열기`를 제공하지 않는다.
13. Import/Export는 `IMPORTING/IMPORTED`와 `EXPORTING/EXPORTED`를 구분하고, 파일 선택기 취소·권한 거부 후 호출 화면 및 포커스를 복원한다.
14. recreate/process death 중 mutation 결과가 미확정이면 동일 command를 재실행하지 않는다. durable receipt/read contract로 결과를 확인하는 동안 공개 대기 상태를 알리고 확정된 결과만 한 번 게시한다.

## 5. 위험 Action Matrix

| Action | UX 보호 | 되돌리기 |
|---|---|---|
| 강화 고단계 시도 | 확률/실패 하락/보호석/비용 명시 + 확인 | 결과 RNG commit 후 일반 Undo 금지 |
| 파티원 퇴출/계약 해지 | 사유·위약금·관계영향 미리보기 | 실행 전 취소, 실행 후 규약에 따른 후속 처리 |
| 일정 PREEMPT/CANCEL_AND_INSERT·진행 중/FINAL_BOUNDARY 취소·손실 동반 변경 | `PublicConsequencePreview.v1`로 현재/새/취소 일정, 환불·손실, 소비·반환 자원, 잃는 진행률, 공개 관계·평판, 재예약 가능 여부를 표시하고 hidden 값은 제거. preview hash/row version stale이면 재확인 | confirm 전 취소 가능. commit 후 일반 Undo 금지, action kind 취소/재예약 정책만 사용 |
| 세대 교체 | 상속/개인귀속/후계자 상태 최종 확인 | commit 전 취소, commit 후 SaveGeneration 복원 정책만 사용 |
| 귀환 엔딩 | 조건·종료 영향·후일담 안내, Hold/Confirm | ENDING_COMMITTED 후 일반 Undo 금지 |
| 세이브 로드/새 게임 | dirty 손실 범위와 저장 후/저장하지 않고/취소 선택 | confirm 전 취소, 기존 정상 generation 보존 |
| 세이브 복구 | 후보별 정상 시각·진행·손실 경계·원본 보존 표시 | 검증 성공 전 기존 슬롯 삭제·덮어쓰기 금지 |
| 세이브 전체 삭제 | 영향 대상·가문·플레이 시간·진행도 표시, 선택 슬롯 범위를 밝힌 Export, 명시적 확인과 취소 | 별도 DELETE_CONFIRM → DELETING; 확인 전 변경 0, 완료 확정 전 EMPTY 전환 금지. 한 슬롯 Export를 전체 백업으로 표시하지 않음 |
| 세이브 가져오기 | 새 슬롯·이름 충돌·권한 정책 명시 | 기존 슬롯 자동 덮어쓰기 금지 |

## 6. 정보 비대칭 표시 규칙

- 미확인 던전/몬스터/잠재력/관계 수치를 authoritative 값 그대로 노출하지 않는다.
- `?`, 미확인, 추정 범위, 신뢰도 등 Projection 정책을 사용한다.
- debug Validation 화면만 별도 권한/빌드 조건에서 숨은 상태를 볼 수 있다.

## 7. Adaptive/접근성 Matrix

| 조건 | 모든 핵심 화면 요구 |
|---|---|
| 큰 글자 | CTA/수치 잘림 없음, 세로 확장 허용 |
| TalkBack | EntityRow/버튼/Badge에 의미 있는 label과 traversal order |
| 색각 | 등급/위험/관계를 색만으로 구분하지 않음 |
| 태블릿 | 목록+상세 2-pane 가능, 동일 Command 계약 유지 |
| 모션 감소 | 전설 이벤트/전투 연출도 정보 손실 없이 축소 |
| Process death / Activity recreate | 저장 가능한 편집/필터/스크롤/선택 슬롯/draft 상태 복구, transient effect 중복 재생 금지 |
| Save/recovery | 상태 title·body·CTA를 live region으로 한 번만 게시하고, 성공·오류·취소·Blocked 각각에서 안전한 결과 제목 또는 CTA로 포커스 복귀 |
| 슬롯 선택·위험 확인 | TalkBack에서 슬롯의 selected 상태와 공개 요약을 읽고, 전체 삭제 확인은 취소에 안전 포커스를 둠 |
| 비활성 CTA | TalkBack에 role/disabled 상태를 전달하고, 이유는 상태 설명에서 한 번 알리며 비활성 동작은 실행되지 않음 |
| 360dp / 큰 글자 | 200% 글꼴에서 가로 CTA를 세로로 확장하고 한국어 문구를 잘라내지 않음 |

## 8. 화면 Test 생성 규칙

- 각 Screen ID마다 READY/EMPTY/ERROR 최소 3-state snapshot 또는 semantics test.
- Mutation CTA는 정상/중복탭/DB실패/process death를 검증한다.
- Back/딥링크는 Guard 실패 시 안전한 root로 redirect한다.
- 2,000 NPC/1,000 inventory/100년 연대기 fixture로 목록 복귀와 성능을 검증한다.
- STOPPED/STARTED 100회에서 collector·query job 수가 증가하지 않고 이전 epoch 결과가 0건 적용되는지 검증한다.
- keyset 경계에서 insert/delete가 발생해도 같은 항목 중복/누락이 없고 복귀 위치가 stable ID로 복원되는지 검증한다.
- 중요한 선택은 선택 결과가 화면 복원 후 중복 적용되지 않는지 receipt로 확인한다.
- Phase3 각 Screen ID는 DIRTY_CONFIRM/VALIDATING/MIGRATING/permission denied/storage full/compatibility failure의 semantics를 별도 검증한다.
- Phase3는 `SCR-SAVE-001/EMPTY`, `SCR-SAVE-001/DELETE_CONFIRM·DELETING·DELETE_COMPLETED·DELETE_ERROR`, `SCR-SAVE-002/EMPTY·CORRUPTED·ERROR·BLOCKED·RECOVERY_COMPLETED`, migration 상태별 CTA, 가져오기/내보내기 방향별 진행·취소, SAF 복귀 포커스, 실패 후 재시도·복귀 포커스를 검증한다.
- 전체 세이브 삭제는 영향 요약·선택적 Export·취소 기본 포커스·확인 전 불변·중복 탭·Activity recreate/미확정 결과 확인·완료 후 EMPTY 동선을 검증한다.
- Activity recreate 중 결과 미확정 mutation은 재실행 없이 receipt 결과 확인 UI로 복원되는지 검증한다.
- public UI snapshot에는 raw generation ID·schema·checksum·DB path가 0건이어야 한다.

## 9. Definition of Done

- [ ] 현재 정의된 59개 Screen ID가 Navigation graph에 등록 또는 명시적으로 제외된다. Phase3 추가 ID는 `SCR-START-003`, `SCR-SAVE-003`, `SCR-SAVE-004`다. Registry·`document_manifest`·Test mapping의 개수가 모두 일치해야 한다.
- [ ] 각 Screen의 authoritative-changing Action이 실행 계약 열의 `CMD-*`를 통해 84 Mutation Registry에 연결된다.
- [ ] 모든 Screen이 공통 Loading/Empty/Error/Restore 정책을 따른다.
- [ ] Android Flow 수집은 lifecycle-aware이며 대형 목록은 bounded keyset loading과 stable key를 사용한다.
- [ ] P22 UI 자동화와 P25 E2E가 Screen ID를 공통 식별자로 사용한다.
- [ ] Phase3 Screen ID가 dirty/recovery/migration/import/export 상태와 public CTA 계약을 모두 포함한다.
- [ ] 전체 세이브 삭제는 `SCR-SAVE-001` 내 confirm state로 제공되고 원문 요구의 가문·플레이 시간·진행도·선택적 Export를 반영한다.
- [ ] Phase3 UI는 Phase22 공통 ColorScheme/Typography/Shapes/Spacing/Game Components 토큰을 사용하며 화면별 시각 값은 추가하지 않는다.
- [ ] 360dp·200% 글꼴·TalkBack·Activity recreate·process death에서 선택·스크롤·draft·focus가 복원된다.
