# 87. 콘텐츠 Effect DSL · AST 상세 명세

> 목적: “화상 대상 추가 피해”, “피격 시 보호막”, “행동속도 +7%” 같은 설명 문자열을 실제 결정론 Simulation이 실행 가능한 버전된 데이터로 변환한다. 기존 `content_template.definition_json`을 우선 활용하여 불필요한 DB 구조 증설을 피한다.

## 1. 설계 원칙

1. 스크립트 문자열 `eval`을 사용하지 않는다. JSON → typed AST → validator → evaluator 순서를 사용한다.
2. 모든 확률은 ppm 정수, 시간은 CombatMillis/GameMinute 명시 타입, 금액은 Money 정수 타입을 사용한다.
3. Trigger/Condition/Target/Value/Effect를 분리해 재사용한다.
4. AST evaluation은 순수 함수에 가깝게 유지하고 실제 mutation은 Combat/World Delta에 기록한다.
5. 정의 버전이 미지원이면 임의 해석하지 않고 콘텐츠 bundle 활성화를 차단한다.
6. 설명 텍스트는 표시용이며 권위 계산값은 AST가 가진다.

## 2. 최상위 JSON

```json
{
  "schemaVersion": 1,
  "effectSetId": "EFF-EXAMPLE-001",
  "tags": ["FIRE", "ON_HIT"],
  "nodes": [],
  "descriptionKey": "effect.fire_bonus_on_burning"
}
```

## 3. AST 타입

```kotlin
sealed interface EffectNode

data class TriggeredEffect(
    val trigger: Trigger,
    val condition: ConditionExpr = Always,
    val target: TargetSelector,
    val chancePpm: Int = 1_000_000,
    val cooldown: DurationSpec? = null,
    val effects: List<EffectNode>
) : EffectNode

data class ApplyDamage(val amount: ValueExpr, val damageType: DamageType) : EffectNode
data class Heal(val amount: ValueExpr) : EffectNode
data class ModifyStat(val stat: StatType, val op: ModifyOp, val value: ValueExpr, val duration: DurationSpec?) : EffectNode
data class ApplyStatus(val statusId: String, val stacks: ValueExpr, val duration: DurationSpec?) : EffectNode
data class RemoveStatus(val selector: StatusSelector, val count: Int?) : EffectNode
data class AddShield(val amount: ValueExpr, val duration: DurationSpec?) : EffectNode
data class RestoreResource(val resource: ResourceType, val amount: ValueExpr) : EffectNode
data class MoveTarget(val mode: MoveMode, val distance: ValueExpr) : EffectNode
```

v1에는 임의 `eventType: String`/`Map<String, ValueExpr>`를 받는 escape hatch를 두지 않는다. 위 typed node가 성공하면 evaluator가 engine-owned sealed `EffectOutcome`을 만들고, 외부 command handler가 필요에 따라 versioned `DomainEventPayload`로 변환한다. 콘텐츠가 새 종류의 결과를 요구하면 기존 node로 표현 가능한지 먼저 확인하고, 불가능할 때만 sealed node·codec·validator·golden test를 같은 변경에서 추가한다. 콘텐츠 데이터만으로 임의 authoritative event나 command를 호출할 수 없다.

## 4. Trigger Registry

| Trigger | 의미 | RNG 사용 위치 |
|---|---|---|
| `ON_COMBAT_START` | Combat Start 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_ACTION_START` | Actistart 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_BASIC_ATTACK` | Basic Attack 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_SKILL_CAST` | Skill Cast 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_HIT` | Hit 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_DAMAGE_DEALT` | Damage Dealt 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_DAMAGE_TAKEN` | Damage Taken 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_CRITICAL` | Critical 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_BLOCK` | Block 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_EVADE` | Evade 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_STATUS_APPLIED` | Status Applied 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_STATUS_TICK` | Status Tick 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_KILL` | Kill 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_ALLY_DOWN` | Ally Down 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_HP_THRESHOLD` | Hp Threshold 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_COMBAT_END` | Combat End 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_ROOM_ENTER` | Room Enter 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_EXPLORE` | Explore 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_CAMP_START` | Camp Start 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_TIME_BOUNDARY` | Time Boundary 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |
| `ON_ENHANCEMENT_SUCCESS` | Enhancement Success 발생 시 | chance가 100% 미만일 때 해당 stream/counter 소비 |

## 5. Condition Expression

```text
Always
Not(expr)
AllOf(expr[])
AnyOf(expr[])
Compare(lhs, op, rhs)
HasStatus(target, statusId, minStacks)
HasTag(target, tag)
HpRatio(target) <= value
ResourceRatio(target, type) <= value
Count(selector) >= value
IsBoss(target)
IsFrontRow(target)
TerrainHas(tag)
```

Condition은 상태를 수정하지 않으며 동일 snapshot에서 평가한다. 부동소수 비교 대신 정수 ratio/basis-point를 사용한다.

## 6. TargetSelector

| Selector | 의미 | 결정론 tie-break |
|---|---|---|
| `SELF` | 사용자 자신 | actorId |
| `CURRENT_TARGET` | 현재 선택 대상 | 기존 intent |
| `ALLY_LOWEST_HP` | 가장 낮은 HP 아군 | hpRatio → entityId |
| `ENEMY_LOWEST_HP` | 가장 낮은 HP 적 | hpRatio → entityId |
| `ENEMY_HIGHEST_THREAT` | 위협도 최고 적 | threat → entityId |
| `ALL_ALLIES` | 전체 아군 | formation index → entityId |
| `ALL_ENEMIES` | 전체 적 | formation index → entityId |
| `ADJACENT_ENEMIES` | 인접 적 | distance/slot → entityId |
| `RANDOM_ENEMY` | 후보 중 RNG 선택 | 전용 target-selection stream |

## 7. ValueExpr

```text
ConstInt(value)
Stat(target, statType)
ResourceMax(target, resourceType)
BaseDamage(source)
SkillPower(sourceSkill)
Multiply(value, ratioBp)
Add(values[])
Min(a,b) / Max(a,b)
Clamp(value,min,max)
Stacks(target,statusId)
Level(target)
```

Overflow는 오류로 처리하며 clamp로 overflow를 은폐하지 않는다.

## 8. Stacking Policy

| Policy | 규칙 |
|---|---|
| `NONE` | 중첩 없음, 재적용 정책 별도 |
| `REFRESH_DURATION` | 수치는 유지하고 지속시간 갱신 |
| `ADD_STACK` | maxStacks까지 중첩 |
| `REPLACE_IF_STRONGER` | 동일 effect key 중 큰 값만 유지 |
| `INDEPENDENT` | 각 source instance별 독립 timer |

## 9. 실행 예제

### 9.1 화상 대상 추가 피해

```json
{
  "schemaVersion": 1,
  "nodes": [{
    "type": "TriggeredEffect",
    "trigger": "ON_DAMAGE_DEALT",
    "condition": {"type":"HasStatus","target":"CURRENT_TARGET","statusId":"BURN","minStacks":1},
    "target": "CURRENT_TARGET",
    "chancePpm": 1000000,
    "effects": [{"type":"ApplyDamage","damageType":"FIRE","amount":{"type":"Multiply","value":{"type":"BaseDamage","source":"SELF"},"ratioBp":2500}}]
  }]
}
```

### 9.2 피격 시 5% 보호막

```json
{
  "schemaVersion": 1,
  "nodes": [{
    "type":"TriggeredEffect",
    "trigger":"ON_DAMAGE_TAKEN",
    "target":"SELF",
    "chancePpm":50000,
    "cooldown":{"type":"COMBAT_MS","value":5000},
    "effects":[{"type":"AddShield","amount":{"type":"Multiply","value":{"type":"ResourceMax","target":"SELF","resource":"HP"},"ratioBp":1000},"duration":{"type":"COMBAT_MS","value":3000}}]
  }]
}
```

### 9.3 장신구 마력 순환

```json
{
  "schemaVersion":1,
  "nodes":[{
    "type":"TriggeredEffect",
    "trigger":"ON_ACTION_START",
    "condition":{"type":"Compare","lhs":{"type":"ResourceRatio","target":"SELF","resource":"MP"},"op":"LT","rhs":10000},
    "target":"SELF",
    "cooldown":{"type":"COMBAT_MS","value":5000},
    "effects":[{"type":"RestoreResource","resource":"MP","amount":{"type":"Multiply","value":{"type":"ResourceMax","target":"SELF","resource":"MP"},"ratioBp":200}}]
  }]
}
```

> 위 수치는 DSL 구조 설명용 예시이며, 원문에 수치가 없는 효과의 실제 balance value로 간주하지 않는다.

## 10. Validator

| 검사 | ERROR 조건 |
|---|---|
| Schema | 미지원 schemaVersion/필수필드 누락 |
| Type | GameMinute/CombatMillis/Money/ratio 타입 혼용 |
| Range | chancePpm <0 또는 >1,000,000, 음수 duration 등 |
| Reference | 존재하지 않는 stat/status/tag/content ID |
| Target | trigger에서 불가능한 target selector |
| Recursion | AST depth/node count 상한 초과 |
| Cycle | nested TriggeredEffect가 같은 `(effectSetId,nodePath,trigger)`를 재진입하거나 reaction budget을 초과 |
| Stack | maxStacks/refresh policy 불일치 |
| Class/Tag | 장비/몬스터 허용 태그 위반 |
| Determinism | RANDOM selector가 지정 RNG stream 없이 사용 |

권장 초기 안전 상한(설계 보완안): AST depth ≤ 16, effect nodes ≤ 128/template, nested selector 후보 ≤ 128. 실제 콘텐츠 분석 후 P1에서 조정한다.

## 11. Evaluation 순서

```text
1. Trigger batch 확정
2. 같은 snapshot에서 Condition 평가
3. 대상 후보 결정 및 tie-break/RNG 확정
4. ValueExpr 계산
5. Effect Delta 생성
6. 동일시각 batch 규칙에 따라 Apply
7. engine-owned typed EffectOutcome에서 Reaction/secondary trigger 수집
8. recursion/event budget 검사
9. 결과 trace 기록
```

## 12. 콘텐츠 저장/버전

- `content_template.definition_json`에 타입별 AST를 저장한다.
- `definition_version`과 최상위 `schemaVersion`을 함께 검증한다.
- 저장 게임은 content/balance version binding을 기록하며, 미지원 AST는 migration 없이 임의 실행하지 않는다.
- 표시명/설명 문구도 canonical content record에 포함되므로 변경 시 `logicalContentHash(=content_manifest.bundle_hash)`는 바뀐다. 계산 AST가 같으면 `balanceVersion`은 유지할 수 있지만, 완성 DB bytes의 `artifactFileSha256`는 외부 bundle manifest에서 별도로 검증한다.

## 13. 테스트 전략

- Parser golden: JSON → AST canonical form hash.
- Validator property test: 잘못된 타입/range/reference가 반드시 거절됨.
- Evaluator unit: 각 node의 순수 계산.
- Determinism: 동일 state/seed/effect AST → 동일 Delta/trace.
- Fuzz: AST depth/빈 배열/큰 수/overflow/순환 event.
- Escape-hatch 방지: 알 수 없는 node, 임의 eventType/payload map, command 호출 필드는 parser 단계에서 거절.
- Content-wide compile: 모든 WPN/ARM/ACC/SKL/MON/affix/set effect를 startup이 아니라 build-time에 검증.

## 14. Definition of Done

- [ ] description-only 효과가 출시 활성 콘텐츠에 남아 있지 않다.
- [ ] 모든 효과가 typed AST compile/validation을 통과한다.
- [ ] 원문에 수치가 없는 효과는 승인된 balance profile 전에는 활성화하지 않는다.
- [ ] evaluator가 Android/Room/Compose에 의존하지 않는다.
- [ ] 콘텐츠 AST가 임의 DomainEvent/Command 문자열을 만들 수 없다.
- [ ] Golden Seed에서 effect trace가 재현된다.
