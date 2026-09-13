package com.imsi.mud.content

/** Typed, immutable C25 payloads exposed after the canonical JSON has passed the V1 decoder. */
sealed interface ContentDefinitionFieldsV1

data class WeaponDefinitionV1(
    val name: String,
    val weaponType: String,
    val grade: String,
    val recommendedLevel: Long,
    val physicalPower: Long,
    val magicPower: Long,
    val tags: List<String>,
) : ContentDefinitionFieldsV1

data class ArmorDefinitionV1(
    val name: String,
    val slot: String,
    val grade: String,
    val recommendedLevel: Long,
    val defense: Long,
    val magicDefense: Long,
    val weightClass: String,
) : ContentDefinitionFieldsV1

data class AccessoryDefinitionV1(
    val name: String,
    val slot: String,
    val grade: String,
    val recommendedLevel: Long,
    val effects: List<EffectNodeV1>,
) : ContentDefinitionFieldsV1

data class ItemDefinitionV1(
    val name: String,
    val itemCategory: String,
    val grade: String,
    val primaryUseCategory: String,
    val effects: List<EffectNodeV1>,
) : ContentDefinitionFieldsV1

data class LevelBandV1(val min: Long, val max: Long)

data class MonsterDefinitionV1(
    val name: String,
    val family: String,
    val levelBand: LevelBandV1,
    val threatCoefficientBp: Long,
    val role: String,
    val attributes: List<String>,
) : ContentDefinitionFieldsV1

data class BossDefinitionV1(
    val name: String,
    val family: String,
    val recommendedLevel: Long,
    val dungeonRank: String,
    val archetypeTags: List<String>,
    val phaseCount: Long,
    val coreMechanic: String,
    val rewardTags: List<String>,
) : ContentDefinitionFieldsV1

/** No V1 value exists until a dedicated sealed codec is approved. */
sealed interface UnmodeledExecutableV1

data class EquipmentAffixDefinitionV1(
    val position: String,
    val name: String,
    val grade: String,
    val effects: List<EffectNodeV1>,
    val allowedEquipmentScope: UnmodeledExecutableV1?,
) : ContentDefinitionFieldsV1

data class MonsterAffixDefinitionV1(
    val position: String,
    val name: String,
    val grade: String,
    val effects: List<EffectNodeV1>,
    val threatMultiplierBp: Long,
) : ContentDefinitionFieldsV1

data class EquipmentSetTierV1(val pieces: Long, val effects: List<EffectNodeV1>)

data class EquipmentSetDefinitionV1(
    val name: String,
    val grade: String,
    val tiers: List<EquipmentSetTierV1>,
) : ContentDefinitionFieldsV1

sealed interface ResourceCostV1 {
    data object None : ResourceCostV1
    data class Single(val resource: ResourceTypeV1, val amount: Long) : ResourceCostV1
    data class Multi(val costs: List<Single>) : ResourceCostV1
    data class Unresolved(val reason: String) : ResourceCostV1
}

enum class ResourceTypeV1 { HP, MP, MANA, STAMINA }

data class SkillDefinitionV1(
    val name: String,
    val skillGroup: String,
    val skillType: String,
    val grade: String,
    val classScope: String,
    val resourceCost: ResourceCostV1,
    val cooldownCombatMillis: Long,
    val effects: List<EffectNodeV1>,
    val tags: List<String>,
) : ContentDefinitionFieldsV1

data class SkillAffixDefinitionV1(
    val position: String,
    val name: String,
    val grade: String,
    val modificationKind: UnmodeledExecutableV1?,
    val amount: ValueExprV1?,
    val applicability: UnmodeledExecutableV1?,
) : ContentDefinitionFieldsV1

data class ContractDefinitionV1(
    val name: String,
    val category: String,
    val contractRank: String,
    val duration: DurationSpecV1?,
    val objective: UnmodeledExecutableV1?,
    val rewards: List<UnmodeledExecutableV1>,
    val featureTags: List<String>,
) : ContentDefinitionFieldsV1

data class RelicDefinitionV1(
    val name: String,
    val category: String,
    val eligibility: ConditionExprV1?,
    val relationshipEffects: List<EffectNodeV1>,
    val followup: UnmodeledExecutableV1?,
) : ContentDefinitionFieldsV1

data class EventDefinitionV1(
    val category: String,
    val name: String,
    val eligibility: ConditionExprV1?,
    val choices: List<UnmodeledExecutableV1>,
    val effects: List<EffectNodeV1>,
) : ContentDefinitionFieldsV1

data class EventChainDefinitionV1(
    val category: String,
    val name: String,
    val steps: List<UnmodeledExecutableV1>,
    val finalEffects: List<EffectNodeV1>,
) : ContentDefinitionFieldsV1

data class LegendDefinitionV1(
    val name: String,
    val eligibility: ConditionExprV1?,
    val scenario: UnmodeledExecutableV1?,
    val chronicleTags: List<String>,
) : ContentDefinitionFieldsV1

enum class TimeDomainV1 { COMBAT_MS, GAME_MINUTE }
data class DurationSpecV1(val domain: TimeDomainV1, val value: Long)

enum class TriggerV1 {
    ON_COMBAT_START, ON_ACTION_START, ON_BASIC_ATTACK, ON_SKILL_CAST, ON_HIT,
    ON_DAMAGE_DEALT, ON_DAMAGE_TAKEN, ON_CRITICAL, ON_BLOCK, ON_EVADE,
    ON_STATUS_APPLIED, ON_STATUS_TICK, ON_KILL, ON_ALLY_DOWN, ON_HP_THRESHOLD,
    ON_COMBAT_END, ON_ROOM_ENTER, ON_EXPLORE, ON_CAMP_START, ON_TIME_BOUNDARY,
    ON_ENHANCEMENT_SUCCESS,
}

enum class TargetSelectorV1 {
    SELF, CURRENT_TARGET, ALLY_LOWEST_HP, ENEMY_LOWEST_HP, ENEMY_HIGHEST_THREAT,
    ALL_ALLIES, ALL_ENEMIES, ADJACENT_ENEMIES, RANDOM_ENEMY,
}

enum class CompareOpV1 { LT, LTE, EQ, NE, GTE, GT }
enum class DamageTypeV1 { PHYSICAL, MAGIC, FIRE, ICE, LIGHTNING, HOLY, DARK, TRUE }
enum class ModifyOpV1 { ADD, ADD_BP, MULTIPLY_BP, SET }
enum class MoveModeV1 { PUSH, PULL, SWAP, ADVANCE, RETREAT }
enum class BaseDamageSourceV1 { SELF, CURRENT_TARGET }

sealed interface EffectNodeV1 {
    data class TriggeredEffect(
        val trigger: TriggerV1,
        val condition: ConditionExprV1,
        val target: TargetSelectorV1,
        val chancePpm: Long,
        val cooldown: DurationSpecV1?,
        val effects: List<EffectNodeV1>,
    ) : EffectNodeV1

    data class ApplyDamage(val amount: ValueExprV1, val damageType: DamageTypeV1) : EffectNodeV1
    data class Heal(val amount: ValueExprV1) : EffectNodeV1
    data class ModifyStat(
        val stat: String,
        val op: ModifyOpV1,
        val value: ValueExprV1,
        val duration: DurationSpecV1?,
    ) : EffectNodeV1
    data class ApplyStatus(val statusId: String, val stacks: ValueExprV1, val duration: DurationSpecV1?) : EffectNodeV1
    data class RemoveStatus(val selector: StatusSelectorV1, val count: Long?) : EffectNodeV1
    data class AddShield(val amount: ValueExprV1, val duration: DurationSpecV1?) : EffectNodeV1
    data class RestoreResource(val resource: ResourceTypeV1, val amount: ValueExprV1) : EffectNodeV1
    data class MoveTarget(val mode: MoveModeV1, val distance: ValueExprV1) : EffectNodeV1
}

sealed interface ConditionExprV1 {
    data object Always : ConditionExprV1
    data class Not(val expr: ConditionExprV1) : ConditionExprV1
    data class AllOf(val expressions: List<ConditionExprV1>) : ConditionExprV1
    data class AnyOf(val expressions: List<ConditionExprV1>) : ConditionExprV1
    data class Compare(val lhs: ValueExprV1, val op: CompareOpV1, val rhs: ComparisonOperandV1) : ConditionExprV1
    data class HasStatus(val target: TargetSelectorV1, val statusId: String, val minStacks: Long) : ConditionExprV1
    data class HasTag(val target: TargetSelectorV1, val tag: String) : ConditionExprV1
    data class HpRatio(val target: TargetSelectorV1, val op: CompareOpV1, val valueBp: Long) : ConditionExprV1
    data class ResourceRatio(
        val target: TargetSelectorV1,
        val resource: ResourceTypeV1,
        val op: CompareOpV1,
        val valueBp: Long,
    ) : ConditionExprV1
    data class Count(val selector: TargetSelectorV1, val op: CompareOpV1, val value: Long) : ConditionExprV1
    data class IsBoss(val target: TargetSelectorV1) : ConditionExprV1
    data class IsFrontRow(val target: TargetSelectorV1) : ConditionExprV1
    data class TerrainHas(val tag: String) : ConditionExprV1
}

sealed interface ComparisonOperandV1 {
    data class Literal(val value: Long) : ComparisonOperandV1
    data class Expression(val value: ValueExprV1) : ComparisonOperandV1
}

sealed interface ValueExprV1 {
    data class ConstInt(val value: Long) : ValueExprV1
    data class Stat(val target: TargetSelectorV1, val statType: String) : ValueExprV1
    data class ResourceMax(val target: TargetSelectorV1, val resource: ResourceTypeV1) : ValueExprV1
    data class BaseDamage(val source: BaseDamageSourceV1) : ValueExprV1
    data class SkillPower(val sourceSkill: ContentId) : ValueExprV1
    data class Multiply(val value: ValueExprV1, val ratioBp: Long) : ValueExprV1
    data class Add(val values: List<ValueExprV1>) : ValueExprV1
    data class Min(val a: ValueExprV1, val b: ValueExprV1) : ValueExprV1
    data class Max(val a: ValueExprV1, val b: ValueExprV1) : ValueExprV1
    data class Clamp(val value: ValueExprV1, val min: ValueExprV1, val max: ValueExprV1) : ValueExprV1
    data class Stacks(val target: TargetSelectorV1, val statusId: String) : ValueExprV1
    data class Level(val target: TargetSelectorV1) : ValueExprV1
}

sealed interface StatusSelectorV1 {
    data class StatusId(val statusId: String) : StatusSelectorV1
    data object AllStatuses : StatusSelectorV1
}

data class TypedContentReferenceV1(val id: ContentId, val expectedKind: ContentKind)

internal fun ContentDefinitionFieldsV1.typedContentReferences(): Set<TypedContentReferenceV1> {
    val references = linkedSetOf<TypedContentReferenceV1>()

    fun value(node: ValueExprV1) {
        when (node) {
            is ValueExprV1.SkillPower -> references += TypedContentReferenceV1(node.sourceSkill, ContentKind.SKILL)
            is ValueExprV1.Multiply -> value(node.value)
            is ValueExprV1.Add -> node.values.forEach(::value)
            is ValueExprV1.Min -> { value(node.a); value(node.b) }
            is ValueExprV1.Max -> { value(node.a); value(node.b) }
            is ValueExprV1.Clamp -> { value(node.value); value(node.min); value(node.max) }
            is ValueExprV1.ConstInt, is ValueExprV1.Stat, is ValueExprV1.ResourceMax,
            is ValueExprV1.BaseDamage, is ValueExprV1.Stacks, is ValueExprV1.Level -> Unit
        }
    }

    fun condition(node: ConditionExprV1) {
        when (node) {
            is ConditionExprV1.Not -> condition(node.expr)
            is ConditionExprV1.AllOf -> node.expressions.forEach(::condition)
            is ConditionExprV1.AnyOf -> node.expressions.forEach(::condition)
            is ConditionExprV1.Compare -> {
                value(node.lhs)
                (node.rhs as? ComparisonOperandV1.Expression)?.let { value(it.value) }
            }
            is ConditionExprV1.Always, is ConditionExprV1.HasStatus, is ConditionExprV1.HasTag,
            is ConditionExprV1.HpRatio, is ConditionExprV1.ResourceRatio, is ConditionExprV1.Count,
            is ConditionExprV1.IsBoss, is ConditionExprV1.IsFrontRow, is ConditionExprV1.TerrainHas -> Unit
        }
    }

    fun effect(node: EffectNodeV1) {
        when (node) {
            is EffectNodeV1.TriggeredEffect -> { condition(node.condition); node.effects.forEach(::effect) }
            is EffectNodeV1.ApplyDamage -> value(node.amount)
            is EffectNodeV1.Heal -> value(node.amount)
            is EffectNodeV1.ModifyStat -> value(node.value)
            is EffectNodeV1.ApplyStatus -> value(node.stacks)
            is EffectNodeV1.AddShield -> value(node.amount)
            is EffectNodeV1.RestoreResource -> value(node.amount)
            is EffectNodeV1.MoveTarget -> value(node.distance)
            is EffectNodeV1.RemoveStatus -> Unit
        }
    }

    when (this) {
        is AccessoryDefinitionV1 -> effects.forEach(::effect)
        is ItemDefinitionV1 -> effects.forEach(::effect)
        is EquipmentAffixDefinitionV1 -> effects.forEach(::effect)
        is MonsterAffixDefinitionV1 -> effects.forEach(::effect)
        is EquipmentSetDefinitionV1 -> tiers.flatMap(EquipmentSetTierV1::effects).forEach(::effect)
        is SkillDefinitionV1 -> effects.forEach(::effect)
        is SkillAffixDefinitionV1 -> amount?.let(::value)
        is RelicDefinitionV1 -> { eligibility?.let(::condition); relationshipEffects.forEach(::effect) }
        is EventDefinitionV1 -> { eligibility?.let(::condition); effects.forEach(::effect) }
        is EventChainDefinitionV1 -> finalEffects.forEach(::effect)
        is LegendDefinitionV1 -> eligibility?.let(::condition)
        is WeaponDefinitionV1, is ArmorDefinitionV1, is MonsterDefinitionV1, is BossDefinitionV1,
        is ContractDefinitionV1 -> Unit
    }
    return references
}
