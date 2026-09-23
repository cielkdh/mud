# 84. 전체 Command · Event 계약서

> 목적: UI/UseCase/Simulation/DB/연대기 사이의 변경 계약을 전역적으로 통일한다. 원문에 명시된 `WorldCommand → WorldEngine → DomainEvent → WorldState update` 구조를 기준으로 하고, 세부 Command/Event 이름은 「설계 보완안」 namespace로 관리한다.

## 1. CommandEnvelope

```kotlin
data class CommandEnvelope<P : WorldCommandPayload>(
    val commandId: CommandId,
    val sessionEpoch: SessionEpoch,
    val expectedVersion: StateVersion?,
    val actorId: EntityId?,
    val payload: P,
    val payloadHash: Hash
)
```

필수 규칙: 동일 epoch/commandId/동일 payload는 이전 receipt 반환, 같은 ID/다른 payload는 `IdempotencyKeyReuse`, stale epoch는 `StaleSession`, expectedVersion 불일치는 재계산 없이 거절한다.

`payloadHash`는 호출자가 임의로 채우는 문자열이 아니다. `CommandPayloadCodec.v1`이 payload를 UTF-8 canonical JSON으로 직렬화한 바이트의 SHA-256 소문자 hex다. 객체 key는 Unicode code point 오름차순, 배열은 의미 순서 유지, 문자열은 NFC, 정수는 부호 있는 10진수(선행 0 없음), enum/ID는 계약의 canonical name, `null`은 명시 필드에만 허용한다. float/NaN/Infinity, map의 비문자 key, 로케일 의존 형식은 payload에 금지한다. hash 입력은 `"CMDPAYLOAD\u0000" + codecId + "\u0000" + canonicalPayloadBytes`이며 `codecId`도 receipt에 포함된 `result_json`의 metadata로 보존한다. 서버나 UI가 보낸 hash는 신뢰하지 않고 UseCase 경계에서 재계산한다.

## 2. DomainEvent

```kotlin
data class DomainEvent<E : DomainEventPayload>(
    val eventId: EventId,
    val sourceId: EntityId?,
    val sourceEventId: EventId?,
    val sourceEpoch: SessionEpoch,
    val sourceCommandId: CommandId,
    val sourceVersion: StateVersion,
    val gameMinute: GameMinute,
    val subMinuteMs: SubMinuteMillis,
    val eventSequence: EventSequence,
    val visibility: EventVisibility,
    val importance: EventImportance,
    val payload: E
)
```

`subMinuteMs`는 0..59,999이고, `eventSequence`는 같은 `(sourceEpoch, sourceCommandId)` 안에서 0부터 단조 증가한다. 영속화와 재생은 `(gameMinute, subMinuteMs, sourceVersion, sourceEpoch, sourceCommandId, eventSequence, eventId)`로 정렬해 명령·epoch 간 동률까지 제거한다. `sourceEpoch/sourceCommandId/sourceVersion`은 **해당 event를 생성한 transaction**의 `command_receipt.epoch/command_id/state_version`과 일치해야 하고, DTO의 모든 필드는 `world_event`의 대응 열과 손실 없이 왕복해야 한다. 일반 command에서는 receipt의 최종 version과 같다. resumable command는 이후 segment가 같은 receipt를 갱신하므로 과거 event의 `sourceVersion`이 receipt의 최종 `stateVersion`과 달라도 정상이며, event의 version을 후대 segment에 맞춰 rewrite하지 않는다.

`EventVisibility.v1`의 영속 값은 `PUBLIC`, `PARTICIPANTS`, `OBSERVER_SCOPED`, `SYSTEM_HIDDEN` 네 가지다. projection은 observer 권한을 적용해 public DTO를 만든 뒤 UI로 전달하며 `SYSTEM_HIDDEN`과 권한 밖 값을 preview/summary/log에 포함하지 않는다. 호환 불가능한 visibility 의미 변경은 기존 값을 재해석하지 않고 새 계약과 migration으로만 도입한다.

`world_event.event_type`은 `payload`의 안정적인 versioned `EventCodecId`이며 `CombatCompleted.v1`처럼 `<event-name>.v<schema-version>` 형식을 쓴다. payload의 호환 불가능한 변경은 기존 ID의 의미를 바꾸지 않고 새 `.vN` codec으로 등록한다. EventCodec registry는 보존 중인 세이브와 event log가 요구하는 decoder/upcaster를 유지하고, migration·projection 재구축은 구버전 event를 현재 도메인 표현으로 해석한 뒤 새 projection에 staging한다. 별도 schema-version 열은 추가하지 않는다.

연대기/통계/dirty registration은 Event consumer가 처리할 수 있으나, Event가 authoritative mutation을 재실행하여 이중 효과를 만들면 안 된다.

### 2.1. `StateHash.v1` 범위와 직렬화

`stateHash`는 매 command의 DB row_version 대체물이 아니라 결정론·복원 동등성 검증값이다. `schema_registry.json`에서 `save` store의 **권위 게임 상태**로 분류된 current row와 `rng_state`를 포함하고, `command_receipt`, `world_event`, `save_generation`, `checkpoint_chunk`, `generation_chunk`, `save_slot`, `migration_history`, `recovery_journal`, 검색/통계 projection, 캐시, UI preference, `row_version`, 현실시각, 기존 `world_state.state_hash`는 제외한다. 포함/제외 분류가 없는 새 save 객체는 schema 검증을 실패시킨다.

canonical stream은 `STATEHASH.v1\n` 뒤에 table name UTF-8 오름차순, 각 table의 PK tuple 오름차순, 물리 column name 오름차순으로 `(type-tag, byte-length u32 big-endian, value-bytes)`를 이어 붙인다. 정수는 signed 64-bit big-endian, Boolean은 0/1 한 byte, 문자열은 NFC UTF-8, BLOB은 원바이트, null은 별도 tag다. 각 domain codec의 JSON/AST는 저장 문자열이 아니라 해당 codec의 canonical bytes를 사용한다. 구현 전 golden fixture가 이 byte stream과 SHA-256을 함께 고정한다.

v1은 복잡한 증분 Merkle 구조를 도입하지 않는다. 일반 command는 `stateVersion`만 증가시키고, `stateHash`는 명시적 checkpoint, load/restore, 결정론 Test, crash-cut 검증에서 전체 스캔으로 계산한다. MIN 단말의 100년 fixture에서 checkpoint hash가 500ms를 넘는다는 실측이 있을 때만 같은 canonical leaf 규칙을 유지한 증분 cache를 별도 ADR로 추가한다.

## 3. Command 처리 파이프라인

```mermaid
sequenceDiagram
    participant UI
    participant VM as ViewModel/UseCase
    participant WS as WorldSession
    participant WE as WorldEngine
    participant SP as SavePort
    participant DB as SaveCoordinator/Room
    participant PR as Projection
    UI->>VM: UiAction
    VM->>WS: CommandEnvelope
    WS->>WS: epoch/version/idempotency/guard
    WS->>WE: plan(envelope, immutable snapshot)
    WE-->>WS: ExecutionPlan(Atomic/Segment)
    WS->>SP: commit/commitSegment(envelope, plan)
    SP->>DB: atomic commit(delta, events, receipt)
    DB-->>SP: committed stateVersion
    SP-->>WS: CommitReceipt
    WS->>WS: verify receipt, apply committed delta
    WS-->>PR: PublicDomainEvent + PublicSnapshot
    PR-->>UI: UiState
```

`CommandEnvelope`는 UI/스케줄러가 시작하는 **gameplay** application UseCase 경계에서 한 번만 생성하고 `WorldSession.execute`로 제출한다. `WorldSession`·`WorldEngine`·순수 Kotlin `SavePort` 계약은 `:core:simulation`이 소유한다. P0 조립 루트는 `:app`만이며, P3에서 `:core:save`의 `SaveCoordinator`가 `SavePort`를 구현한다. `:tools:headless`는 P23/P25에서 독립 실행 요구가 확인될 때만 동일 계약을 조립한다. SaveCoordinator는 envelope를 새로 만들거나 `commandId`를 바꾸지 않고 `DomainDelta`를 persistence plan으로 변환한다. 일반 command는 envelope당 receipt 1개와 write transaction 1개다. 시간 진행처럼 중간 내구 경계가 제품 요구인 **resumable command**만 같은 receipt row를 `RUNNING → COMMITTED|INTERRUPTED`로 갱신하는 여러 bounded segment commit을 허용하며, segment별 receipt/CommandEnvelope는 만들지 않는다. process kill 뒤에는 RUNNING receipt만 durable cursor에서 복구할 수 있다. INTERRUPTED receipt는 terminal이고, 사용자의 continuation은 `continuationOfCommandId`를 가진 새 gameplay envelope/receipt로 제출한다. 재시도는 `expectedVersion` 검사와 delta 계산 전에 `SavePort.findReceipt(epoch, commandId)`로 durable receipt를 조회한다. 같은 payload hash면 원 result를 반환하고, 다른 hash면 `IdempotencyKeyReuse`로 거절한다. 메모리 cache는 조회 최적화일 뿐 멱등성의 권위가 아니다.

`WorldSession`은 유일한 authoritative lane이자 commit/apply/publish 조정자다. 구체 `WorldEngine`은 `WorldSnapshot(stateVersion, AuthoritativeWorldState(clock,calendar,timeAdvance,boundaryBinding), rngState, aggregates)`에서 `ExecutionPlan.Atomic` 또는 `ExecutionPlan.Segment`만 계산하며 SavePort를 호출하지 않는다. `DomainDelta.worldChange`는 typed authoritative before/after를 가지며 P2 핵심 state를 JSON aggregate map에 숨기지 않는다. 실제 `BoundarySource` ordered list는 immutable engine dependency이고 snapshot에는 `BoundaryRegistryBinding` 값만 저장한다. 외부 payload/caller가 current snapshot, calendar 또는 source 객체를 공급하는 경로는 금지한다.

P3의 정상 DB 재기동은 마지막 성공 transaction의 current rows·stateVersion·receipt·RNG·event/cursor를 재조회한다. 일반 commit마다 generation을 만들지 않으며 마지막 checkpoint 이후의 성공 commit도 보존한다. `CheckpointWorld`의 frozen generation은 freeze 시점까지 이미 확정된 receipt/event만 포함하고 자기 바깥 receipt는 manifest와 동일 transaction에 별도로 확정한다. 정상 reopen의 중복 조회는 이 durable receipt를 사용한다. 과거 generation의 명시적 restore는 새 branch/epoch로 열며 이전 branch receipt를 새 명령으로 재실행하지 않는다. 손상 DB fallback은 검증된 완전 generation까지만 보장한다.

세계 clock이 증가하는 모든 gameplay command는 원인과 무관하게 Phase 2 `WorldTimeTraversal`을 호출해 target 사이의 모든 boundary batch를 처리한다. 전투·던전·여행·일반 행동은 clock row를 직접 증가시키지 않는다. 진행 mode는 표시·알림 defer·fast-forward stop policy만 바꾸며 authoritative boundary source/order를 바꾸지 않는다.

`SavePort.commitSegment(envelope, expectedSegmentNo, delta, timeAdvanceState, terminalResult)`는 admission segment 0 또는 다음 `BoundarySlice`만 current rows·RNG·events·state·같은 receipt와 원자 commit한다. `expectedSegmentNo` 불일치는 double apply 없이 기존 결과/Conflict다. COMPLETED/UNREACHABLE/LIMIT_REACHED/CANCELLED는 COMMITTED, INTERRUPTED/DECISION_REQUIRED와 durable progress 뒤 FAILED는 INTERRUPTED, admission 전 deterministic domain reject는 REJECTED다. SYSTEM_HALT는 신뢰할 수 없는 상태에서 새 receipt/Event를 쓰지 않는다. DECISION_REQUIRED는 미처리 ordered candidate suffix의 codec/payload/hash를 terminal time_advance_state에 보존한다. `ATOMIC_SEALED.v1`은 gate≤1, choice 1..8, 추가 gate 없음과 choice canonical 순 총 평가≤`8×maxBoundaryCount`인 모든 branch의 cap/limit/codec 적합성을 mutation 전에 증명할 수 있을 때만 허용하며, 증명 불가 action은 reject하거나 처음부터 ScheduledAction으로 분류한다. 이미 계산된 뒤 gate를 만나면 같은 state에 `SealedElapsedOutcome.v1` payload/hash/effectiveMinute/domainResultId와 소비된 action-local RNG를 추가해 재추첨·사후 취소를 막는다. decision continuation의 첫 transaction은 predecessor `(epoch,commandId)`·terminal 상태·gate 선택·suffix/outcome hash를 검증하고 UNIQUE child claim·선택 적용·suffix 소비·새 receipt/state/event를 원자 commit하며 target까지를 protected completion으로 처리한다. 이 구간에는 PAUSE/CANCEL/일반 limit terminal이 개입하지 않으며 kill은 이전 DECISION_REQUIRED 또는 완전한 target commit만 남긴다. sealed outcome은 command와 무관한 domainResultId를 stableEntityId로 하는 `elapsed.action.apply.v1` BoundaryCandidate로 effectiveMinute frozen batch에 포함되어 같은 `BoundaryOrder.v1`을 따른다. DomainEvent provenance는 event를 실제 생성한 continuation receipt를 가리키며 original receipt로 위장하지 않는다. 원 predecessor는 재활성화/삭제하지 않는다. 장시간 이동·안전 복귀·치료·훈련·운송·제작은 ScheduledAction으로 모델링한다. Phase 2는 test-only port conformance, Phase 3은 실제 Room/WAL/reopen/process-kill 재실행을 소유한다.

권위 상태 쓰기는 capacity 64 `Channel<QueuedCommand>` 하나와 consumer 하나로만 실행한다. enqueue 완료 시 부여한 `submissionSequence`가 수락 순서다. active AdvanceTime 중 UI의 새 gameplay 제출은 enqueue하지 않고 `AdvanceInProgress(activeCommandId, allowedControls)`를 반환한다. 이미 수락된 scheduler/internal command는 terminal 뒤 FIFO를 지킨다. `PAUSE`, `CANCEL_ADVANCE`, `APP_BACKGROUND`, `CLOSE`는 별도 lifecycle/control plane에서 다음 safe boundary를 요청하지만 독립 envelope·receipt·Event를 만들지 않는다. control request는 `sessionEpoch`, `expectedActiveCommandId`, `kind`, `allowCommitDrain`을 가지며 WorldSession의 pending latch에만 기록된다. consumer가 다음 segment plan 전에 latch를 읽고, 선택된 terminal은 기존 active gameplay receipt로만 commit한다. stale epoch/active ID는 write/publish 0, protected completion은 `allowedControls=[]`, CLOSE는 admission 차단→safe-boundary drain→consumer join→handle close 순서다. `CANCEL_ADVANCE`는 취소 가능한 traversal만 `CANCELLED`/receipt `COMMITTED`로, 나머지는 remaining goal을 보존한 `INTERRUPTED`로 끝낸다. 이 경로 밖의 병렬 authoritative mutation은 금지한다.

`DomainDelta`는 optional typed `WorldStateChange`, typed aggregate change, 새 RNG state/counter, typed DomainEvent payload, command result만 포함한다. `WorldStateChange`와 `AggregateChange`는 실제 typed `before`/`after` state를 가지며 hash만으로 row 변경을 표현하지 않는다. RNG는 `(streamKey, algorithmVersion, state, counter)`의 여러 stream state로 표현한다. table name, DAO, SQL, `dirtyRows[]`를 포함하지 않는다. SaveCoordinator의 mapper가 DomainDelta를 current row 변경과 dirty shard key로 변환하므로 `:core:simulation`은 저장 구조를 모른다.

`PersistedReceipt`는 최소 `lifecycleStatus`, `stateVersion`, `lastCommittedSegmentNo`, `TimeAdvanceState?`를 포함한다. WorldSession은 admission segment 0부터 같은 receipt의 watermark를 검증하고, commit 불확정 시 `findReceipt`로 성공 여부를 reconcile한 뒤 정확히 한 번만 apply/publish한다. 확인 불가 또는 plan/receipt 불일치는 session을 안전 정지하며 추정 apply/publish하지 않는다. commit 성공 후 publication은 `CommittedPublication(PublicSnapshot, List<PublicDomainEvent>, sourceCommandId, PublicTimeAdvanceTerminal?)` 하나로 갱신한다. terminal field는 같은 command의 `TimeAdvanceResult`만 전달해 UI가 성공을 추정하지 않게 하며 raw/hidden event·pending suffix·RNG 상태는 외부에 내보내지 않는다. StateFlow conflation은 durable event replay를 보장하지 않으며 그 책임은 P3 outbox/read model에 있다.

도메인 guard 거절은 권위 상태/RNG를 바꾸지 않는 receipt-only transaction으로 `REJECTED` receipt를 먼저 확정한 뒤, 필요할 때만 같은 transaction의 `SYSTEM_HIDDEN` visibility failure DomainEvent를 기록한다. Registry의 Failure Event는 이 **결정론적 도메인 거절**에만 해당한다. malformed envelope, `StaleSession`, `IdempotencyKeyReuse`, 인증/정보노출 위험, DB/파일/프로세스 실패는 신뢰 가능한 receipt/Event를 만들 수 없으므로 typed 응답·로컬 진단만 남긴다. 모든 `world_event`는 해당 segment가 생성한 durable receipt를 FK로 가지며 커밋 전에 publish되지 않는다. resumable receipt가 이후 RUNNING/INTERRUPTED/COMMITTED로 전이해도 이미 생성된 event와 sourceVersion은 유효하다.

```kotlin
data class PublicSnapshot<V : PublicView>(
    val sessionEpoch: SessionEpoch,
    val stateVersion: StateVersion,
    val checkpointGenerationId: GenerationId?,
    val payload: V
)
```

ViewModel은 현재 `sessionEpoch`와 일치하는 snapshot만 받고 `stateVersion`을 단조 증가시킨다. 동일 version 재수신은 멱등 허용하고 더 낮은 version은 버린다. 슬롯/세션 전환 시 이전 collector를 취소하며 `checkpointGenerationId`는 가장 최근 완전 checkpoint와 복구 출처만 식별한다. 일반 command commit이 SaveGeneration을 만들었다는 뜻으로 사용하지 않는다.

decision gate open event는 `decision.required.v1`, 선택 적용 event는 `decision.selection.applied.v1`로 분리한다. 두 codec은 payload 의미를 공유하거나 재사용하지 않으며, projection/replay가 선택 완료를 새 미해결 gate로 오인하지 않도록 한다.

## 4. 원문에서 직접 제시된 WorldCommand 예

| Command | 연결 기능 | 권위 변경 | 기본 멱등성 |
|---|---|---|---|
| `AdvanceTime` | `FUNC-P2-004` 시간 진행 | 예 | command receipt 필수 |
| `EnterDungeon` | `FUNC-P9-001` 던전 진입/탐색 | 예 | command receipt 필수 |
| `RecruitMercenary` | `FUNC-P11-002` 용병 모집/영입 | 예 | command receipt 필수 |
| `ChangeFormation` | `FUNC-P13-001` 출전/진형 변경 | 예 | command receipt 필수 |
| `BuyItem` | `FUNC-P14-002` 구매 | 예 | command receipt 필수 |
| `AcceptQuest` | `FUNC-P11-001` 의뢰 수락 | 예 | command receipt 필수 |
| `SelectDialogueChoice` | `FUNC-P11-004` 대화 선택 | 예 | command receipt 필수 |
| `StartTraining` | `FUNC-P4-004` 훈련/재훈련 | 예 | command receipt 필수 |

## 5. 원문에서 직접 제시된 DomainEvent 예

| Event | 의미 | Projection 후보 |
|---|---|---|
| `TimeAdvanced` | 시간 진행 결과 | 연대기/통계/알림/dirty index 중 해당 consumer |
| `MercenaryJoined` | 용병 합류 | 연대기/통계/알림/dirty index 중 해당 consumer |
| `ItemPurchased` | 구매 완료 | 연대기/통계/알림/dirty index 중 해당 consumer |
| `DungeonEntered` | 던전 진입 | 연대기/통계/알림/dirty index 중 해당 consumer |
| `CombatCompleted` | 전투 종료 | 연대기/통계/알림/dirty index 중 해당 consumer |
| `RelationshipChanged` | 관계 변화 | 연대기/통계/알림/dirty index 중 해당 consumer |
| `PotentialChanged` | 잠재력 변화 | 연대기/통계/알림/dirty index 중 해당 consumer |

## 6. Capability-level Mutation Registry — 설계 보완안

> 아래 표는 capability namespace와 대표 계산 메소드를 함께 추적한다. **표의 모든 메소드가 외부 CommandEnvelope 경계라는 뜻은 아니다.** UI/스케줄러가 시작하는 `EXTERNAL` wrapper만 receipt를 만들고, `INTERNAL` 계산은 그 wrapper의 DomainDelta에 합쳐진다. read/compute/tool/lifecycle/event consumer와 `SaveCoordinator` 구현은 7절 계약을 따르며 별도 CommandEnvelope·command receipt·COMMITTED/REJECTED Event를 만들지 않는다. 실제 UI 명령이 더 세분화될 경우 동일 Function namespace 아래 sealed payload subtype을 추가한다.

### 6.1. 경계 분류 override

| Capability | 분류 | receipt 소유 외부 경계 |
|---|---|---|
| CMD-P2-F001 | `PROTOCOL` | 다른 모든 외부 command를 접수하는 facade 자체이며 독립 gameplay command가 아니다. |
| CMD-P2-F002 | `INTERNAL` | 전투/시간 외부 command가 GameClockMath/RNG 결과를 포함하되 증가 target은 `WorldTimeTraversal`로 처리한다. |
| CMD-P3-F002, CMD-P3-F004 | `INTERNAL` | 수동/자동 checkpoint, load/import 외부 command가 generation/migration 계산을 포함한다. |
| CMD-P6-F001, CMD-P6-F003, CMD-P6-F004 | `INTERNAL` | `StartCombat`/`ChooseRetreat`/자동 전투 외부 command의 combat step이다. |
| CMD-P7-F001~CMD-P7-F004 | `INTERNAL` | 전투·던전 외부 command 안의 AI/phase/encounter 계산이다. |
| CMD-P8-F002, CMD-P8-F003 | `INTERNAL` | `EnterDungeon`/던전 생성 외부 command 안의 배치 계산이다. |
| 그 밖의 `CMD-*` | `EXTERNAL_NAMESPACE` | 대표 메소드를 직접 노출하지 않고 UI/스케줄러 의도를 sealed payload subtype으로 정의한다. |

`INTERNAL` 항목의 Registry Completion/Failure Event 이름은 독립 receipt 결과가 아니라 외부 command에 포함될 수 있는 typed domain outcome 식별자다. 구현 lock 시 독립 command로 승격하려면 사용자/스케줄러 trigger, 멱등키, transaction, UX 실패 표면을 함께 추가해야 한다.

| Command ID | Phase | Function | Payload/메소드 계약 | Tables | Transaction | Completion Event | Failure Event |
|---|---|---|---|---|---|---|---|
| `CMD-P2-F001` | P2 | `FUNC-P2-001` 단일 작성자 명령 처리 | `WorldSession.execute(envelope)`; 내부 `WorldEngine.plan(envelope,snapshot)->ExecutionPlan` | world_state, command_receipt, world_event, rng_state | WorldSession만 authoritative commit/apply/publish 조정 | `EVT-P2-F001-COMMITTED` | `EVT-P2-F001-REJECTED` |
| `CMD-P2-F003` | P2 | `FUNC-P2-003` 예약·점유·자원 선점 | `reserve`/`cancel`/`resolveConflict`; canonical ResourceIdentity, row versions·SchedulePriority·public consequence/claim 정책 | scheduled_action, occupancy, resource_reservation | Risk confirm의 preview hash+expected row version 재검증 뒤 일정 변경·claim 정산·receipt 원자 처리; P2 자동 선택/hidden 정보 노출 금지 | `EVT-P2-F003-COMMITTED` | `EVT-P2-F003-REJECTED` |
| `CMD-P2-F004` | P2 | `FUNC-P2-004` 공통 세계시간 traversal·자동중단 | 외부 `AdvanceTime`; 모든 elapsed command는 내부 `WorldTimeTraversal` 사용 | world_state, scheduled_action, time_advance_state, world_event, command_receipt | gate 없는 짧은 action은 outer commit; 중간 gate는 sealed outcome/RNG+prefix/suffix를 durable 보존해 continuation; 장시간 action은 ScheduledAction | `EVT-P2-F004-COMMITTED` | `EVT-P2-F004-REJECTED` |
| `CMD-P3-F001` | P3 | `FUNC-P3-001` 명시적 checkpoint·새 슬롯 bootstrap | `SaveCommand = CheckpointWorld | CreateNewWorld`; `SavePort.checkpoint` 1회 | world_state, command_receipt, rng_state, save_generation, save_slot | 기존 world의 frozen snapshot과 자기 바깥 receipt를 동일 transaction에 확정하고 자기 receipt는 frozen snapshot에서 제외한다. `EMPTY`의 CreateNewWorld는 미게시 version 0 genesis로 session을 열어 초기 current rows/RNG/manifest/slot/receipt를 원자 확정한 뒤에만 게시한다. 이전 mutation 재적용 없음 | `EVT-P3-F001-COMMITTED` | `EVT-P3-F001-REJECTED` |
| `CMD-P3-F002` | P3 | `FUNC-P3-002` 복원 가능한 세대·슬롯·불변 청크 | 내부 `GenerationStore.create(snapshot, parent) -> GenerationManifest` | save_generation, save_slot, checkpoint_chunk, generation_chunk | P3-F001/P3-F003/P3-F005 내부 persistence plan; 독립 receipt 없음 | `EVT-P3-F002-COMMITTED` | `EVT-P3-F002-REJECTED` |
| `CMD-P3-F003` | P3 | `FUNC-P3-003` 장기진행·예약 복구 | `RecoveryService.restore(checkpoint: RecoveryCheckpoint) -> RecoverableSession` | recovery_checkpoint, time_advance_state, scheduled_action, occupancy, resource_reservation | P2 active baseline만 복구한다. combat/dialogue codec·domain은 P6/P11 등록 전 `IncompatibleSave`. 일반 `RESUME` mutation은 WorldSession만 authoritative commit/apply/publish한다. `START_CHECKPOINT` 파일 교체는 배타 lease에서 old session/DB close 후 RecoveryService가 수행하고 검증된 새 session만 게시한다. 복구 중 gameplay receipt/event는 생성하지 않는다. | `EVT-P3-F003-COMMITTED` | `EVT-P3-F003-REJECTED` |
| `CMD-P3-F004` | P3 | `FUNC-P3-004` 마이그레이션·콘텐츠 호환 | `MigrationPlanner.migrate(source: SaveArchive, target: SchemaVersion) -> MigrationResult` | migration_history, save_generation, content_binding | authoritative 변경+receipt 원자 처리 | `EVT-P3-F004-COMMITTED` | `EVT-P3-F004-REJECTED` |
| `CMD-P3-F005` | P3 | `FUNC-P3-005` 오프라인 Export·Import·아카이브 보호 | `SaveArchiveService.importArchive(input: LocalDocument) -> NewSlotResult` | save_slot, save_generation, recovery_journal | authoritative 변경+receipt 원자 처리 | `EVT-P3-F005-COMMITTED` | `EVT-P3-F005-REJECTED` |
| `CMD-P3-F006` | P3 | `FUNC-P3-006` 무결성 검사·복구·보존 GC | `SaveIntegrityService.repair(command: RepairCommand, report: IntegrityReport) -> RepairResult` | checkpoint_chunk, generation_chunk, save_generation, recovery_journal | 승인된 repair/GC만 authoritative 변경+receipt 원자 처리; `audit`은 읽기 전용 | `EVT-P3-F006-COMMITTED` | `EVT-P3-F006-REJECTED` |
| `CMD-P4-F001` | P4 | `FUNC-P4-001` NPC 생성·성별 이름·초상 일괄 확정 | `NpcFactory.create(request: NpcCreationRequest, ctx: CreationContext) -> NpcCreatedDelta` | mercenary, name_registry, portrait_reservation, character_stat | authoritative 변경+receipt 원자 처리 | `EVT-P4-F001-COMMITTED` | `EVT-P4-F001-REJECTED` |
| `CMD-P4-F002` | P4 | `FUNC-P4-002` 기본스탯·경험치·성장원장 | `GrowthService.applyExperience(id: NpcId, amount: Experience) -> GrowthDelta` | mercenary, character_stat, growth_ledger | authoritative 변경+receipt 원자 처리 | `EVT-P4-F002-COMMITTED` | `EVT-P4-F002-REJECTED` |
| `CMD-P4-F003` | P4 | `FUNC-P4-003` 잠재력·후천변화·숙련 | `PotentialService.apply(change: PotentialChange, current: CharacterPotential) -> PotentialDelta` | character_stat, potential_event, mastery | authoritative 변경+receipt 원자 처리 | `EVT-P4-F003-COMMITTED` | `EVT-P4-F003-REJECTED` |
| `CMD-P4-F004` | P4 | `FUNC-P4-004` 클래스 재훈련·스탯 재계산 | `RetrainingService.start(request: RetrainRequest) -> RetrainPlan` | mercenary, growth_ledger, scheduled_action, character_stat | authoritative 변경+receipt 원자 처리 | `EVT-P4-F004-COMMITTED` | `EVT-P4-F004-REJECTED` |
| `CMD-P5-F001` | P5 | `FUNC-P5-001` 인벤토리·장착·소유권 | `InventoryService.move(command: MoveItem, view: InventoryState) -> InventoryDelta` | item_instance, storage_location, inventory_stack, equipment_slot, money_account | authoritative 변경+receipt 원자 처리 | `EVT-P5-F001-COMMITTED` | `EVT-P5-F001-REJECTED` |
| `CMD-P5-F002` | P5 | `FUNC-P5-002` 스킬·접사·개인 상성 데이터 | `SkillService.learn(command: LearnSkill, character: CharacterState) -> SkillDelta` | skill_instance, skill_affinity, mastery | authoritative 변경+receipt 원자 처리 | `EVT-P5-F002-COMMITTED` | `EVT-P5-F002-REJECTED` |
| `CMD-P5-F003` | P5 | `FUNC-P5-003` Loadout·프리셋·전술 조건식 | `LoadoutService.update(command: UpdateLoadout, actor: CharacterState) -> LoadoutDelta` | loadout, tactic_rule, equipment_slot, skill_instance | authoritative 변경+receipt 원자 처리; 내부 `validate`는 순수 계산 | `EVT-P5-F003-COMMITTED` | `EVT-P5-F003-REJECTED` |
| `CMD-P5-F004` | P5 | `FUNC-P5-004` 드롭·보상 예산·타겟파밍 | `LootService.roll(context: LootContext, stream: RngStream) -> LootPlan` | loot_receipt, item_instance, inventory_stack | authoritative 변경+receipt 원자 처리 | `EVT-P5-F004-COMMITTED` | `EVT-P5-F004-REJECTED` |
| `CMD-P6-F001` | P6 | `FUNC-P6-001` 이벤트 큐·동시 해결 배치 | `CombatTimeline.resolveNext(state: CombatState) -> CombatStep` | combat_checkpoint | authoritative 변경+receipt 원자 처리 | `EVT-P6-F001-COMMITTED` | `EVT-P6-F001-REJECTED` |
| `CMD-P6-F003` | P6 | `FUNC-P6-003` 액션 상태·자원·발사체 스냅샷 | `ActionExecutor.start(action: ActionSpec, actor: ActorState) -> ActionPlan` | combat_checkpoint | authoritative 변경+receipt 원자 처리 | `EVT-P6-F003-COMMITTED` | `EVT-P6-F003-REJECTED` |
| `CMD-P6-F004` | P6 | `FUNC-P6-004` 상태이상·축적·Tick·점감 | `StatusEngine.apply(effect: StatusApplication, now: CombatMillis) -> StatusDelta` | combat_checkpoint, status_effect | authoritative 변경+receipt 원자 처리 | `EVT-P6-F004-COMMITTED` | `EVT-P6-F004-REJECTED` |
| `CMD-P6-F005` | P6 | `FUNC-P6-005` 후퇴·반응·전투 종료 정산 | `CombatFinalizer.evaluate(state: CombatState) -> CombatResolution` | combat_result, time_advance_state, command_receipt | elapsed 중 gate가 없으면 outer commit; gate가 있으면 combat outcome/RNG를 sealed 보존하고 P2 continuation이 target에서 정확히 1회 적용 | `EVT-P6-F005-COMMITTED` | `EVT-P6-F005-REJECTED` |
| `CMD-P7-F001` | P7 | `FUNC-P7-001` 몬스터 감지·Utility·역할 AI | `MonsterDecisionEngine.decide(ctx: CombatDecisionContext) -> ActionIntent` | monster_state | authoritative 변경+receipt 원자 처리 | `EVT-P7-F001-COMMITTED` | `EVT-P7-F001-REJECTED` |
| `CMD-P7-F002` | P7 | `FUNC-P7-002` 그룹 Blackboard·순찰·학습 | `MonsterGroupAI.update(input: GroupPerception) -> GroupPlan` | monster_state, patrol_state | authoritative 변경+receipt 원자 처리 | `EVT-P7-F002-COMMITTED` | `EVT-P7-F002-REJECTED` |
| `CMD-P7-F003` | P7 | `FUNC-P7-003` 보스 전조·페이즈·강인도 | `BossController.transition(input: BossFrame) -> BossPhaseDelta` | boss_state, combat_checkpoint | authoritative 변경+receipt 원자 처리 | `EVT-P7-F003-COMMITTED` | `EVT-P7-F003-REJECTED` |
| `CMD-P7-F004` | P7 | `FUNC-P7-004` 보스 카탈로그·공략대·웨이브 | `EncounterFactory.create(spec: EncounterSpec) -> EncounterPlan` | boss_state, monster_state | authoritative 변경+receipt 원자 처리 | `EVT-P7-F004-COMMITTED` | `EVT-P7-F004-REJECTED` |
| `CMD-P8-F001` | P8 | `FUNC-P8-001` 던전 그래프·열쇠/문 위상 생성 | `DungeonGenerator.generate(spec: DungeonSpec, seed: Long) -> GenerationResult` | dungeon_instance, dungeon_room, dungeon_connection | authoritative 변경+receipt 원자 처리 | `EVT-P8-F001-COMMITTED` | `EVT-P8-F001-REJECTED` |
| `CMD-P8-F002` | P8 | `FUNC-P8-002` 지형·구역·위험/보상 예산 | `DungeonBudgetAllocator.allocate(graph: DungeonGraph, budget: Budget) -> DungeonPlan` | dungeon_room, dungeon_population, dungeon_treasure | authoritative 변경+receipt 원자 처리 | `EVT-P8-F002-COMMITTED` | `EVT-P8-F002-REJECTED` |
| `CMD-P8-F003` | P8 | `FUNC-P8-003` 몬스터·상자·함정·정복목표 배치 | `DungeonPopulationBuilder.place(plan: DungeonPlan) -> PopulationPlan` | dungeon_population, dungeon_treasure, dungeon_trap, monster_state | authoritative 변경+receipt 원자 처리 | `EVT-P8-F003-COMMITTED` | `EVT-P8-F003-REJECTED` |
| `CMD-P8-F004` | P8 | `FUNC-P8-004` 던전 생명주기·Seed·재방문 | `DungeonLifecycle.advance(dungeon: DungeonState, to: GameMinute) -> DungeonDelta` | dungeon_instance, dungeon_population, world_event | authoritative 변경+receipt 원자 처리 | `EVT-P8-F004-COMMITTED` | `EVT-P8-F004-REJECTED` |
| `CMD-P9-F001` | P9 | `FUNC-P9-001` MUD 이동·조사·점진 공개 | `ExplorationService.act(command: ExploreAction, state: RunState) -> ExplorationDelta` | exploration_state, dungeon_room, dungeon_connection, knowledge_fact | authoritative 변경+receipt 원자 처리 | `EVT-P9-F001-COMMITTED` | `EVT-P9-F001-REJECTED` |
| `CMD-P9-F002` | P9 | `FUNC-P9-002` 지도·주석·안전 복귀 경로 | `DungeonMapCommandService.execute(command: DungeonMapCommand) -> DungeonMapDelta`; `route`는 별도 read | map_annotation, exploration_state, dungeon_connection, scheduled_action, world_event | `StartSafeReturn`은 ScheduledAction 시작만 commit하고 crossed boundary/decision은 P2 traversal로 처리; annotation 외 `route`는 write 없음 | `EVT-P9-F002-COMMITTED` | `EVT-P9-F002-REJECTED` |
| `CMD-P9-F003` | P9 | `FUNC-P9-003` 야영·보급·경계·응급처치 | `CampService.start(command: CampCommand) -> ScheduledCamp` | camp_state, scheduled_action, inventory_stack, status_effect | authoritative 변경+receipt 원자 처리 | `EVT-P9-F003-COMMITTED` | `EVT-P9-F003-REJECTED` |
| `CMD-P9-F004` | P9 | `FUNC-P9-004` 패배·구조·SAFE_RECOVERY | `DefeatRecoveryService.resolve(result: DefeatResult) -> RecoveryPlan` | recovery_receipt, character_state, item_instance, inventory_stack, scheduled_action, world_event | 패배 손실+취소 불가 SAFE_RECOVERY 시작을 원자 commit; gate에서 pause/continuation, 완료 boundary에서 HUB 도착·회복을 정확히 1회 적용 | `EVT-P9-F004-COMMITTED` | `EVT-P9-F004-REJECTED` |
| `CMD-P9-F005` | P9 | `FUNC-P9-005` 정복·보상·첫 완결 플레이 루프 | `RunSettlementService.settle(command: SettleRun) -> RunReceipt` | run_state, loot_receipt, combat_result, dungeon_instance, command_receipt | authoritative 변경+receipt 원자 처리 | `EVT-P9-F005-COMMITTED` | `EVT-P9-F005-REJECTED` |
| `CMD-P10-F001` | P10 | `FUNC-P10-001` 도시 이동·시설·영업·대기 | `CityService.visit(command: VisitFacility) -> VisitOutcome` | city_state, facility_state, scheduled_action | authoritative 변경+receipt 원자 처리 | `EVT-P10-F001-COMMITTED` | `EVT-P10-F001-REJECTED` |
| `CMD-P10-F002` | P10 | `FUNC-P10-002` 부상·질병·치료·재활 | `HealthService.treat(command: TreatmentCommand) -> TreatmentPlan` | injury, disease, treatment_order, scheduled_action, money_account | authoritative 변경+receipt 원자 처리 | `EVT-P10-F002-COMMITTED` | `EVT-P10-F002-REJECTED` |
| `CMD-P10-F003` | P10 | `FUNC-P10-003` 주거·숙식·유지비·시설 확장 | `ResidenceService.change(command: HousingCommand) -> HousingDelta` | residence, facility_state, storage_location, money_account, scheduled_action | authoritative 변경+receipt 원자 처리 | `EVT-P10-F003-COMMITTED` | `EVT-P10-F003-REJECTED` |
| `CMD-P10-F004` | P10 | `FUNC-P10-004` 귀환 정비·생활 프리셋·도시 행사 | `MaintenancePlanner.execute(command: MaintenanceBatch) -> BatchReport` | maintenance_order, inventory_stack, money_account, facility_state | authoritative 변경+receipt 원자 처리 | `EVT-P10-F004-COMMITTED` | `EVT-P10-F004-REJECTED` |
| `CMD-P11-F001` | P11 | `FUNC-P11-001` 보조 의뢰·생성·수락·정산 | `ContractService.accept(command: AcceptContract) -> ContractDelta` | contract, contract_objective, contract_receipt, scheduled_action | authoritative 변경+receipt 원자 처리 | `EVT-P11-F001-COMMITTED` | `EVT-P11-F001-REJECTED` |
| `CMD-P11-F002` | P11 | `FUNC-P11-002` 모집·협상·단기/상시 고용 | `RecruitmentService.negotiate(command: RecruitmentProposal) -> ProposalOutcome` | recruitment_post, employment_contract, party_member, money_account | authoritative 변경+receipt 원자 처리 | `EVT-P11-F002-COMMITTED` | `EVT-P11-F002-REJECTED` |
| `CMD-P11-F003` | P11 | `FUNC-P11-003` 계약 종료·퇴출·위약금·신뢰 | `EmploymentService.terminate(command: TerminateEmployment) -> TerminationPlan` | employment_contract, party_member, loan_contract, relationship_memory, money_account | authoritative 변경+receipt 원자 처리 | `EVT-P11-F003-COMMITTED` | `EVT-P11-F003-REJECTED` |
| `CMD-P11-F004` | P11 | `FUNC-P11-004` 선택형 대화·Topic·기억·말투 | `DialogueDirector.choose(command: DialogueChoiceCommand) -> DialogueOutcome` | dialogue_session, dialogue_memory, dialogue_template_binding | authoritative 변경+receipt 원자 처리 | `EVT-P11-F004-COMMITTED` | `EVT-P11-F004-REJECTED` |
| `CMD-P12-F001` | P12 | `FUNC-P12-001` 강화 확률·시도 원장·비파괴 | `EnhancementService.attempt(command: EnhanceItem) -> EnhancementReceipt` | enhancement_state, enhancement_attempt, money_account, inventory_stack | authoritative 변경+receipt 원자 처리 | `EVT-P12-F001-COMMITTED` | `EVT-P12-F001-REJECTED` |
| `CMD-P12-F002` | P12 | `FUNC-P12-002` 강화 성장·안정도·각인 슬롯 | `EnhancementGrowthService.apply(input: SuccessGrowth) -> GrowthResult` | enhancement_growth, enhancement_state, item_inscription | authoritative 변경+receipt 원자 처리 | `EVT-P12-F002-COMMITTED` | `EVT-P12-F002-REJECTED` |
| `CMD-P12-F003` | P12 | `FUNC-P12-003` 계승·정련·재각성·유물복원 | `EquipmentRefinery.execute(command: RefineryCommand) -> RefineryDelta` | item_instance, enhancement_state, enhancement_growth, item_inscription, craft_order | authoritative 변경+receipt 원자 처리 | `EVT-P12-F003-COMMITTED` | `EVT-P12-F003-REJECTED` |
| `CMD-P12-F004` | P12 | `FUNC-P12-004` 제작·연금·연구·분해 | `CraftingService.start(command: CraftCommand) -> CraftOrder` | craft_order, craft_material_reservation, scheduled_action, inventory_stack, item_instance | authoritative 변경+receipt 원자 처리 | `EVT-P12-F004-COMMITTED` | `EVT-P12-F004-REJECTED` |
| `CMD-P13-F001` | P13 | `FUNC-P13-001` 파티 구성·출전·교대·헌장 | `PartyService.changeRoster(command: PartyRosterCommand) -> PartyDelta` | party, party_member, deployment, party_charter, scheduled_action | authoritative 변경+receipt 원자 처리 | `EVT-P13-F001-COMMITTED` | `EVT-P13-F001-REJECTED` |
| `CMD-P13-F002` | P13 | `FUNC-P13-002` 분배·공동자금·투표·공정성 | `PartyGovernanceService.resolve(command: PartyDecision) -> DecisionReceipt` | party_charter, party_proposal, party_vote, money_account, party_distribution | authoritative 변경+receipt 원자 처리 | `EVT-P13-F002-COMMITTED` | `EVT-P13-F002-REJECTED` |
| `CMD-P13-F003` | P13 | `FUNC-P13-003` 만족·갈등·리더교체·분열·승계 | `PartyPoliticsService.apply(command: PartyPoliticalAction) -> PoliticalDelta` | party, party_member, party_proposal, party_history, relationship_memory | authoritative 변경+receipt 원자 처리 | `EVT-P13-F003-COMMITTED` | `EVT-P13-F003-REJECTED` |
| `CMD-P13-F004` | P13 | `FUNC-P13-004` 파티 공식 랭킹·연속1위 | `PartyRankingService.closeDay(input: RankingDay) -> RankingSnapshot` | ranking_snapshot, ranking_streak, party_history | authoritative 변경+receipt 원자 처리 | `EVT-P13-F004-COMMITTED` | `EVT-P13-F004-REJECTED` |
| `CMD-P14-F001` | P14 | `FUNC-P14-001` 도시 시장지수·재고·수요공급 | `MarketEngine.close(input: MarketBoundary) -> MarketDelta` | market_index, market_stock, money_account, economic_ledger | authoritative 변경+receipt 원자 처리 | `EVT-P14-F001-COMMITTED` | `EVT-P14-F001-REJECTED` |
| `CMD-P14-F002` | P14 | `FUNC-P14-002` 구매·판매·흥정·경매 | `TradingService.execute(command: TradeCommand) -> TradeReceipt` | trade_receipt, auction, auction_bid, market_stock, money_account, item_instance | authoritative 변경+receipt 원자 처리 | `EVT-P14-F002-COMMITTED` | `EVT-P14-F002-REJECTED` |
| `CMD-P14-F003` | P14 | `FUNC-P14-003` 장비 대여·회수·손상·보험 | `LoanService.change(command: LoanCommand) -> LoanDelta` | loan_contract, item_instance, equipment_slot, money_account | authoritative 변경+receipt 원자 처리 | `EVT-P14-F003-COMMITTED` | `EVT-P14-F003-REJECTED` |
| `CMD-P14-F004` | P14 | `FUNC-P14-004` 창고·운송·원정보급·분실복구 | `LogisticsService.dispatch(command: ShipmentCommand) -> ShipmentPlan` | storage_location, shipment, shipment_item, item_instance, inventory_stack, scheduled_action | authoritative 변경+receipt 원자 처리 | `EVT-P14-F004-COMMITTED` | `EVT-P14-F004-REJECTED` |
| `CMD-P15-F001` | P15 | `FUNC-P15-001` 지식·관측·소문·정보 공개 | `KnowledgeService.observe(input: Observation) -> KnowledgeDelta` | knowledge_fact, observation, rumor, knowledge_projection | authoritative 변경+receipt 원자 처리 | `EVT-P15-F001-COMMITTED` | `EVT-P15-F001-REJECTED` |
| `CMD-P15-F002` | P15 | `FUNC-P15-002` 평판·법률·계약 신뢰·지역기여 | `ReputationService.apply(event: ReputationEvent) -> ReputationDelta` | reputation_score, reputation_event, legal_case | authoritative 변경+receipt 원자 처리 | `EVT-P15-F002-COMMITTED` | `EVT-P15-F002-REJECTED` |
| `CMD-P15-F003` | P15 | `FUNC-P15-003` 다축 관계·기억·호흡·연애 | `RelationshipService.apply(event: SocialEvent) -> RelationshipDelta` | relationship, relationship_memory, relationship_stage | authoritative 변경+receipt 원자 처리 | `EVT-P15-F003-COMMITTED` | `EVT-P15-F003-REJECTED` |
| `CMD-P15-F004` | P15 | `FUNC-P15-004` 성격·특성·매력·개인 목표 | `PersonalityService.evaluate(input: SocialDecisionContext) -> SocialIntent` | personality_state, trait_binding, personal_goal | authoritative 변경+receipt 원자 처리 | `EVT-P15-F004-COMMITTED` | `EVT-P15-F004-REJECTED` |
| `CMD-P16-F001` | P16 | `FUNC-P16-001` 길드 가입·직위·승계·권한 | `GuildMembershipService.change(command: GuildMembershipCommand) -> GuildDelta` | guild, guild_member, guild_office, guild_history | authoritative 변경+receipt 원자 처리 | `EVT-P16-F001-COMMITTED` | `EVT-P16-F001-REJECTED` |
| `CMD-P16-F002` | P16 | `FUNC-P16-002` 길드 재정·시설·인재·간부 운영 | `GuildOperationsService.execute(command: GuildOperation) -> OperationReceipt` | guild_budget, guild_facility, guild_assignment, money_account, scheduled_action | authoritative 변경+receipt 원자 처리 | `EVT-P16-F002-COMMITTED` | `EVT-P16-F002-REJECTED` |
| `CMD-P16-F003` | P16 | `FUNC-P16-003` 파벌·정책·정당성·길드 공략대 | `GuildPoliticsService.resolve(command: GuildPolicyDecision) -> GuildPoliticalDelta` | guild_faction, guild_proposal, guild_vote, raid_assignment, guild_history | authoritative 변경+receipt 원자 처리 | `EVT-P16-F003-COMMITTED` | `EVT-P16-F003-REJECTED` |
| `CMD-P16-F004` | P16 | `FUNC-P16-004` 길드 공식10000점·일마감·기여 | `GuildRankingService.closeDay(input: GuildRankingDay) -> RankingSnapshot` | ranking_snapshot, ranking_component, ranking_streak, contribution_ledger, return_proof | authoritative 변경+receipt 원자 처리 | `EVT-P16-F004-COMMITTED` | `EVT-P16-F004-REJECTED` |
| `CMD-P17-F001` | P17 | `FUNC-P17-001` NPC 목표·행동 Utility·경제 의사결정 | `NpcPlanner.plan(input: NpcDayContext) -> NpcActionPlan` | npc_activity, personal_goal, personality_state, scheduled_action | authoritative 변경+receipt 원자 처리 | `EVT-P17-F001-COMMITTED` | `EVT-P17-F001-REJECTED` |
| `CMD-P17-F002` | P17 | `FUNC-P17-002` 상세/축약 시뮬레이션·승격·강등 | `NpcSimulationRouter.advance(command: NpcBoundary) -> NpcBatchDelta` | npc_activity, npc_summary, simulation_cursor, world_event | authoritative 변경+receipt 원자 처리 | `EVT-P17-F002-COMMITTED` | `EVT-P17-F002-REJECTED` |
| `CMD-P17-F003` | P17 | `FUNC-P17-003` 용병 유입·은퇴·복귀·직업전환 | `PopulationEngine.closePeriod(input: PopulationBoundary) -> PopulationDelta` | population_cohort, character_state, npc_summary, mercenary_registry | authoritative 변경+receipt 원자 처리 | `EVT-P17-F003-COMMITTED` | `EVT-P17-F003-REJECTED` |
| `CMD-P17-F004` | P17 | `FUNC-P17-004` 장기 정체성·압축·사회 순환 검증 | `NpcHistoryCompactor.compact(input: NpcRetentionPlan) -> CompactionResult` | npc_summary, relationship_memory, party_history, guild_history, portrait_reservation | authoritative 변경+receipt 원자 처리 | `EVT-P17-F004-COMMITTED` | `EVT-P17-F004-REJECTED` |
| `CMD-P18-F001` | P18 | `FUNC-P18-001` 가족·출생·입양·성장·교육 | `FamilyService.apply(command: FamilyAction) -> FamilyDelta` | lineage, family_member, education_plan, population_cohort, money_account | authoritative 변경+receipt 원자 처리 | `EVT-P18-F001-COMMITTED` | `EVT-P18-F001-REJECTED` |
| `CMD-P18-F002` | P18 | `FUNC-P18-002` 후계자 후보·의사·지정·부재 안전장치 | `SuccessionPlanner.nominate(command: NominateSuccessor) -> SuccessionPlan` | lineage, family_member, succession_plan, character_state | authoritative 변경+receipt 원자 처리 | `EVT-P18-F002-COMMITTED` | `EVT-P18-F002-REJECTED` |
| `CMD-P18-F003` | P18 | `FUNC-P18-003` 원자적 세대 교체·자산·증표 유지 | `SuccessionService.execute(command: ExecuteSuccession) -> SuccessionReceipt` | lineage, succession_receipt, character_state, money_account, storage_location, return_proof, party_history, guild_history | authoritative 변경+receipt 원자 처리 | `EVT-P18-F003-COMMITTED` | `EVT-P18-F003-REJECTED` |
| `CMD-P19-F001` | P19 | `FUNC-P19-001` NPC 사건·등장인물·자동 해결 | `NpcEventDirector.select(input: EventSelectionContext) -> EventPlan` | event_instance, event_participant, event_receipt, relationship_memory | authoritative 변경+receipt 원자 처리 | `EVT-P19-F001-COMMITTED` | `EVT-P19-F001-REJECTED` |
| `CMD-P19-F002` | P19 | `FUNC-P19-002` 연쇄 사건·조건·쿨다운·대안 경로 | `EventChainEngine.advance(command: ChainChoice) -> ChainDelta` | event_chain, event_chain_step, event_instance, event_receipt | authoritative 변경+receipt 원자 처리 | `EVT-P19-F002-COMMITTED` | `EVT-P19-F002-REJECTED` |
| `CMD-P19-F003` | P19 | `FUNC-P19-003` AdventureDirector·장기 목표·전설화 | `AdventureDirector.plan(input: DirectorContext) -> DirectorPlan` | director_state, director_budget, personal_goal, legendary_record | authoritative 변경+receipt 원자 처리 | `EVT-P19-F003-COMMITTED` | `EVT-P19-F003-REJECTED` |
| `CMD-P19-F004` | P19 | `FUNC-P19-004` 전술 실험실·전략 회의·행동 자동화 | `StrategyAutomationService.execute(command: StrategyAutomationCommand) -> StrategyAutomationDelta`; `StrategyAssistant.evaluate`는 별도 compute | strategy_plan, automation_rule, subtype이 위임받은 대상 테이블 | `SaveStrategyPlan`/`UpsertAutomationRule`/`ExecuteDelegatedAction`만 authoritative 변경+receipt 원자 처리; 실험/조언은 write 없음 | `EVT-P19-F004-COMMITTED` | `EVT-P19-F004-REJECTED` |
| `CMD-P20-F001` | P20 | `FUNC-P20-001` 균열 탐사·7핵 봉인·발생원 차단 | `RiftCampaignService.seal(command: SealRiftCore) -> CampaignDelta` | return_campaign, rift_core, dungeon_instance, world_event | authoritative 변경+receipt 원자 처리 | `EVT-P20-F001-COMMITTED` | `EVT-P20-F001-REJECTED` |
| `CMD-P20-F002` | P20 | `FUNC-P20-002` 악마 전쟁·잔존세력·90일 종전 | `DemonWarService.closeDay(input: CampaignDay) -> WarDelta` | demon_faction, demon_base, demon_gate, demon_presence, return_campaign | authoritative 변경+receipt 원자 처리 | `EVT-P20-F002-COMMITTED` | `EVT-P20-F002-REJECTED` |
| `CMD-P20-F003` | P20 | `FUNC-P20-003` 영구증표·랭킹·잔존던전·귀환 판정 | `ReturnEligibilityService.evaluate(input: ReturnEvaluation) -> ReturnEligibility` | return_proof, return_campaign, ranking_streak, lineage_contribution, dungeon_instance | authoritative 변경+receipt 원자 처리 | `EVT-P20-F003-COMMITTED` | `EVT-P20-F003-REJECTED` |
| `CMD-P20-F004` | P20 | `FUNC-P20-004` 귀환·잔류·후일담·캠페인 종료 | `EndingService.choose(command: EndingChoice) -> EndingRecord` | ending_record, return_campaign, lineage_contribution, chronicle_event | authoritative 변경+receipt 원자 처리 | `EVT-P20-F004-COMMITTED` | `EVT-P20-F004-REJECTED` |
| `CMD-P21-F003` | P21 | `FUNC-P21-003` 공개정보 검색·필터·페이지·북마크 | `BookmarkService.change(command: BookmarkCommand) -> BookmarkDelta`; `SearchService.search`는 별도 read | bookmark | 북마크 추가/해제만 authoritative 변경+receipt 원자 처리; 검색과 색인 조회는 write 없음 | `EVT-P21-F003-COMMITTED` | `EVT-P21-F003-REJECTED` |

## 7. Query / Tool 계약

| Function | 종류 | 변경 허용 | 규칙 |
|---|---|---|---|
| `FUNC-P0-001` 원문 기준선과 충돌 판정 | `tool` | 아니오 | 문서/관리 JSON 읽기 전용; 검증 artifact만 기록 |
| `FUNC-P0-002` 빌드·모듈·기술버전 고정 | `tool` | 아니오 | Gradle build output/lock/report만 기록; 게임 DB 사용 안 함 |
| `FUNC-P0-003` 공통 타입·명령·오류·이벤트 계약 | `lifecycle/compute` | 아니오 | 실제 명령의 계약 소유; P0는 in-memory SavePort만 사용 |
| `FUNC-P0-004` 최소 검증 하네스·공통 UI 껍데기 | `ui/local` | 아니오 | AppRoot 로컬 state/semantics만 검증; 권위 저장 없음 |
| `FUNC-P1-001` 정적 카탈로그 스키마와 ID 보존 | `tool` | content build 산출물만 | live save.db와 command receipt를 사용하지 않음 |
| `FUNC-P1-002` 콘텐츠 검증·사전 DB 빌드 | `tool` | content build 산출물만 | live save.db와 command receipt를 사용하지 않음 |
| `FUNC-P1-003` 정적 콘텐츠 조회·로컬 AssetResolver·크롭 | `read/tool` | content/asset build 산출물만 | 런타임 조회·`resolve`는 읽기 전용이며 command receipt를 만들지 않음 |
| `FUNC-P1-004` 콘텐츠·이미지 버전 교체와 호환 | `compute` | 아니오 | `resolve`는 BindingPlan만 반환하고 적용은 별도 승인된 migration command가 수행 |
| `FUNC-P2-002` 게임 달력·잔여 밀리초·RNG 스트림 | `compute` | 아니오 | immutable clock/RNG 계산만 수행하며 외부 envelope·receipt·event·SavePort를 직접 소유하지 않음 |
| `FUNC-P2-005` 세션·생명주기·작업 종료 | `lifecycle` | 런타임 자원만 | 새 명령 차단·drain·close를 수행하되 gameplay command receipt를 만들지 않음 |
| `FUNC-P4-005` 초기 정보 비대칭·인물 조회 계약 | `read` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P6-002` 피해·치유·명중·보호막 공식 | `compute` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P6-006` 재생·로그·전투 버전 일치 | `read` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P18-004` 가문 목표·유물·세대 기여·기록 UI | `read` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P21-001` 용병·장비·파티·세계 연대기 | `consumer` | Projection만 | sourceEventId별 consumer receipt로 중복을 막고 WorldCommand receipt는 만들지 않음 |
| `FUNC-P21-002` 일월연 통계·기여·추세·랭킹 이력 | `consumer` | Projection만 | sourceEventId별 consumer receipt로 중복을 막고 WorldCommand receipt는 만들지 않음 |
| `FUNC-P21-004` 보존·압축·뉴스·정보 알림 | `consumer/tool` | Projection·보존 대상만 | retention plan과 consumer receipt를 사용하고 WorldCommand receipt는 만들지 않음 |
| `FUNC-P22-001` 정보구조·화면 계약·Navigation | `read` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P22-002` 디자인 토큰·컴포넌트·이미지 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P22-003` Adaptive·큰글자·TalkBack·터치 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P22-004` 화면 생명주기·일회성 효과·유저 여정 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P23-001` Validation Center·공통 검사 실행 | `tool` | artifact 저장소만 | live save와 격리하며 gameplay command receipt를 만들지 않음 |
| `FUNC-P23-002` 불변식·속성·퍼즈·장기 월드 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P23-003` 확률·수치·드롭·경제 밸런스 비교 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P23-004` 재현 패키지·리뷰·결함·승인 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P24-001` 실측 성능·이미지·목록·DB 예산 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P24-002` 누수·취소·배터리·앱 중단 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P24-003` 저장공간·장기 압축·실패 격리 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P24-004` 최적화 동치·인덱스·R8·프로필 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P25-001` 전체 End-to-End·회귀·출시 범위 검수 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P25-002` 업그레이드·마이그레이션·다운그레이드 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P25-003` 서명 Release·오프라인 설치·배포 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |
| `FUNC-P25-004` 릴리즈 운영·결함 대응·문서 인계 | `tool` | 아니오 | live save.db hash 불변, 필요한 경우 artifact 저장소에만 결과 기록 |

## 8. Event 소비자 우선순위

| Consumer | 목적 | 실패 시 정책 |
|---|---|---|
| DirtyEntityIndex | 다음 incremental save 대상 등록 | 권위 상태와 함께 commit되거나 재구축 가능해야 함 |
| ChronicleProjector | 중요 사건/인물/장비 이력 | 중복 sourceEventId 차단, 실패가 게임 진행을 rollback하지 않도록 격리 가능 |
| StatisticsProjector | 일/월/연 aggregate | 재생성 가능 Projection로 취급 |
| NotificationProjector | 사용자 알림 | 표시 실패는 core 진행에 전파하지 않음 |
| RankingProjector | 일 마감 랭킹/이력 | 마감 transaction과 일관성 계약 필요 |
| UI PublicProjection | 화면 상태 | `PublicSnapshot(sessionEpoch,stateVersion,generationId,payload)`만 publish; 현재 epoch·단조 version 외 응답 폐기 |

## 9. 오류 코드 공통 분류

| Error | 의미 | 기본 처리 |
|---|---|---|
| `ValidationError` | 입력/범위/상태 불일치 | 상태변경 없음 |
| `StaleSession` | 이전 sessionEpoch 요청 | 재로드/현재 세션으로 재요청 |
| `VersionConflict` | expectedVersion 불일치 | 최신 snapshot 재조회 |
| `IdempotencyKeyReuse` | 같은 commandId 다른 payload | 호출자 버그로 기록 |
| `InsufficientResource` | 금화/재료/점유 부족 | 상태변경 없음 |
| `UnsupportedFeature` | Phase 미구현/비활성 | 기능 준비 안 됨 UI |
| `PersistenceFailure` | DB/I/O 실패 | transaction rollback·안전 중단 |
| `InvariantViolation` | 핵심 정합성 위반 | 기능 중단·재현 package |
| `ContentCompatibilityError` | 콘텐츠/세이브 버전 불일치 | migration/지원불가 안내 |

## 10. Command/Event Definition of Done

- [ ] 모든 mutation entry point가 CommandEnvelope/receipt 규칙을 따른다.
- [ ] RNG가 개입한 결과는 RNG state/counter와 함께 저장된다.
- [ ] commit 이전에 UI/연대기/통계가 결과를 확정 표시하지 않는다.
- [ ] 재시작 후 동일 command 재전송이 이중 비용/보상을 만들지 않는다.
- [ ] 이벤트 consumer 실패가 신규 기능에서 기존 핵심 진행으로 장애 전파되지 않도록 격리된다.
