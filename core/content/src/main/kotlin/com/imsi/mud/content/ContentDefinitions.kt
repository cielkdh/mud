package com.imsi.mud.content

import java.util.Collections

data class UnresolvedField(
    val code: String,
    val field: String,
    val sourceText: String,
    val reason: String,
) {
    init {
        require(code.isNotBlank()) { "unresolved code must not be blank" }
        require(field.isNotBlank()) { "unresolved field must not be blank" }
        require(reason.isNotBlank()) { "unresolved reason must not be blank" }
    }
}

/**
 * Validated, sealed view of the C25 ContentKind.v1 definition envelope.
 *
 * Runtime persistence remains canonical JSON, but callers cannot treat an unvalidated JSON object
 * as executable content. Every subtype can only be produced by [ContentDefinitionV1Decoder].
 */
sealed class ContentDefinitionV1 protected constructor(
    val kind: ContentKind,
    val canonicalJson: String,
    val fields: ContentDefinitionFieldsV1,
    unresolved: List<UnresolvedField>,
    references: Set<ContentId>,
) {
    val unresolved: List<UnresolvedField> = Collections.unmodifiableList(unresolved.toList())
    val references: Set<ContentId> = Collections.unmodifiableSet(references.toSortedSet(compareBy(ContentId::value)))
    val typedReferences: Set<TypedContentReferenceV1> = Collections.unmodifiableSet(
        fields.typedContentReferences()
            .sortedWith(compareBy({ it.expectedKind.wireValue }, { it.id.value }))
            .toCollection(linkedSetOf())
    )

    class Accessory internal constructor(json: String, val definition: AccessoryDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.ACCESSORY, json, definition, unresolved, references)
    class Armor internal constructor(json: String, val definition: ArmorDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.ARMOR, json, definition, unresolved, references)
    class Boss internal constructor(json: String, val definition: BossDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.BOSS, json, definition, unresolved, references)
    class Chain internal constructor(json: String, val definition: EventChainDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.CHAIN, json, definition, unresolved, references)
    class Contract internal constructor(json: String, val definition: ContractDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.CONTRACT, json, definition, unresolved, references)
    class DungeonEvent internal constructor(json: String, val definition: EventDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.DUNGEON_EVENT, json, definition, unresolved, references)
    class EquipmentPrefix internal constructor(json: String, val definition: EquipmentAffixDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.EQUIPMENT_PREFIX, json, definition, unresolved, references)
    class EquipmentSuffix internal constructor(json: String, val definition: EquipmentAffixDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.EQUIPMENT_SUFFIX, json, definition, unresolved, references)
    class Event internal constructor(json: String, val definition: EventDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.EVENT, json, definition, unresolved, references)
    class Item internal constructor(json: String, val definition: ItemDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.ITEM, json, definition, unresolved, references)
    class Legend internal constructor(json: String, val definition: LegendDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.LEGEND, json, definition, unresolved, references)
    class Monster internal constructor(json: String, val definition: MonsterDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.MONSTER, json, definition, unresolved, references)
    class MonsterPrefix internal constructor(json: String, val definition: MonsterAffixDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.MONSTER_PREFIX, json, definition, unresolved, references)
    class MonsterSuffix internal constructor(json: String, val definition: MonsterAffixDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.MONSTER_SUFFIX, json, definition, unresolved, references)
    class Relic internal constructor(json: String, val definition: RelicDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.RELIC, json, definition, unresolved, references)
    class SetDefinition internal constructor(json: String, val definition: EquipmentSetDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.SET, json, definition, unresolved, references)
    class Skill internal constructor(json: String, val definition: SkillDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.SKILL, json, definition, unresolved, references)
    class SkillPrefix internal constructor(json: String, val definition: SkillAffixDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.SKILL_PREFIX, json, definition, unresolved, references)
    class SkillSuffix internal constructor(json: String, val definition: SkillAffixDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.SKILL_SUFFIX, json, definition, unresolved, references)
    class Weapon internal constructor(json: String, val definition: WeaponDefinitionV1, unresolved: List<UnresolvedField>, references: Set<ContentId>) : ContentDefinitionV1(ContentKind.WEAPON, json, definition, unresolved, references)
}

class ContentDefinitionContractException(val field: String, detail: String) : IllegalArgumentException("$field: $detail")

object ContentDefinitionV1Decoder {
    private val requiredKeys = mapOf(
        ContentKind.WEAPON to setOf("name", "weaponType", "grade", "recommendedLevel", "physicalPower", "magicPower", "tags", "unresolved"),
        ContentKind.ARMOR to setOf("name", "slot", "grade", "recommendedLevel", "defense", "magicDefense", "weightClass", "unresolved"),
        ContentKind.ACCESSORY to setOf("name", "slot", "grade", "recommendedLevel", "effects", "unresolved"),
        ContentKind.ITEM to setOf("name", "itemCategory", "grade", "primaryUseCategory", "effects", "unresolved"),
        ContentKind.MONSTER to setOf("name", "family", "levelBand", "threatCoefficientBp", "role", "attributes", "unresolved"),
        ContentKind.BOSS to setOf("name", "family", "recommendedLevel", "dungeonRank", "archetypeTags", "phaseCount", "coreMechanic", "rewardTags", "unresolved"),
        ContentKind.EQUIPMENT_PREFIX to setOf("position", "name", "grade", "effects", "allowedEquipmentScope", "unresolved"),
        ContentKind.EQUIPMENT_SUFFIX to setOf("position", "name", "grade", "effects", "allowedEquipmentScope", "unresolved"),
        ContentKind.MONSTER_PREFIX to setOf("position", "name", "grade", "effects", "threatMultiplierBp", "unresolved"),
        ContentKind.MONSTER_SUFFIX to setOf("position", "name", "grade", "effects", "threatMultiplierBp", "unresolved"),
        ContentKind.SET to setOf("name", "grade", "tiers", "unresolved"),
        ContentKind.SKILL to setOf("name", "skillGroup", "skillType", "grade", "classScope", "resourceCost", "cooldownCombatMillis", "effects", "tags", "unresolved"),
        ContentKind.SKILL_PREFIX to setOf("position", "name", "grade", "modificationKind", "amount", "applicability", "unresolved"),
        ContentKind.SKILL_SUFFIX to setOf("position", "name", "grade", "modificationKind", "amount", "applicability", "unresolved"),
        ContentKind.CONTRACT to setOf("name", "category", "contractRank", "duration", "objective", "rewards", "featureTags", "unresolved"),
        ContentKind.RELIC to setOf("name", "category", "eligibilityAST", "relationshipEffects", "followup", "unresolved"),
        ContentKind.EVENT to setOf("category", "name", "eligibilityAST", "choices", "effects", "unresolved"),
        ContentKind.DUNGEON_EVENT to setOf("category", "name", "eligibilityAST", "choices", "effects", "unresolved"),
        ContentKind.CHAIN to setOf("category", "name", "steps", "finalEffects", "unresolved"),
        ContentKind.LEGEND to setOf("name", "eligibilityAST", "scenario", "chronicleTags", "unresolved"),
    )

    fun decode(template: ContentTemplate): ContentDefinitionV1 {
        val definition = (CanonicalJson.parse(template.definitionJson) as? CanonicalJson.JsonObject)
            ?: invalid("definition", "must be an object")
        val required = requiredKeys.getValue(template.kind)
        if (definition.values.keys != required) {
            invalid(
                "definition",
                "unknown=${(definition.values.keys - required).sorted()} missing=${(required - definition.values.keys).sorted()}",
            )
        }
        val unresolved = decodeUnresolved(definition.array("unresolved"))
        val references = linkedSetOf<ContentId>()
        validateCommon(template.kind, definition, references)
        val json = definition.render()
        val fields = decodeFields(template.kind, definition)
        return when (template.kind) {
            ContentKind.ACCESSORY -> ContentDefinitionV1.Accessory(json, fields as AccessoryDefinitionV1, unresolved, references)
            ContentKind.ARMOR -> ContentDefinitionV1.Armor(json, fields as ArmorDefinitionV1, unresolved, references)
            ContentKind.BOSS -> ContentDefinitionV1.Boss(json, fields as BossDefinitionV1, unresolved, references)
            ContentKind.CHAIN -> ContentDefinitionV1.Chain(json, fields as EventChainDefinitionV1, unresolved, references)
            ContentKind.CONTRACT -> ContentDefinitionV1.Contract(json, fields as ContractDefinitionV1, unresolved, references)
            ContentKind.DUNGEON_EVENT -> ContentDefinitionV1.DungeonEvent(json, fields as EventDefinitionV1, unresolved, references)
            ContentKind.EQUIPMENT_PREFIX -> ContentDefinitionV1.EquipmentPrefix(json, fields as EquipmentAffixDefinitionV1, unresolved, references)
            ContentKind.EQUIPMENT_SUFFIX -> ContentDefinitionV1.EquipmentSuffix(json, fields as EquipmentAffixDefinitionV1, unresolved, references)
            ContentKind.EVENT -> ContentDefinitionV1.Event(json, fields as EventDefinitionV1, unresolved, references)
            ContentKind.ITEM -> ContentDefinitionV1.Item(json, fields as ItemDefinitionV1, unresolved, references)
            ContentKind.LEGEND -> ContentDefinitionV1.Legend(json, fields as LegendDefinitionV1, unresolved, references)
            ContentKind.MONSTER -> ContentDefinitionV1.Monster(json, fields as MonsterDefinitionV1, unresolved, references)
            ContentKind.MONSTER_PREFIX -> ContentDefinitionV1.MonsterPrefix(json, fields as MonsterAffixDefinitionV1, unresolved, references)
            ContentKind.MONSTER_SUFFIX -> ContentDefinitionV1.MonsterSuffix(json, fields as MonsterAffixDefinitionV1, unresolved, references)
            ContentKind.RELIC -> ContentDefinitionV1.Relic(json, fields as RelicDefinitionV1, unresolved, references)
            ContentKind.SET -> ContentDefinitionV1.SetDefinition(json, fields as EquipmentSetDefinitionV1, unresolved, references)
            ContentKind.SKILL -> ContentDefinitionV1.Skill(json, fields as SkillDefinitionV1, unresolved, references)
            ContentKind.SKILL_PREFIX -> ContentDefinitionV1.SkillPrefix(json, fields as SkillAffixDefinitionV1, unresolved, references)
            ContentKind.SKILL_SUFFIX -> ContentDefinitionV1.SkillSuffix(json, fields as SkillAffixDefinitionV1, unresolved, references)
            ContentKind.WEAPON -> ContentDefinitionV1.Weapon(json, fields as WeaponDefinitionV1, unresolved, references)
        }
    }

    private fun decodeFields(kind: ContentKind, value: CanonicalJson.JsonObject): ContentDefinitionFieldsV1 = when (kind) {
        ContentKind.WEAPON -> WeaponDefinitionV1(
            name = value.nonBlankString("name"),
            weaponType = value.nonBlankString("weaponType"),
            grade = value.nonBlankString("grade"),
            recommendedLevel = value.integer("recommendedLevel"),
            physicalPower = value.integer("physicalPower"),
            magicPower = value.integer("magicPower"),
            tags = value.stringArray("tags"),
        )
        ContentKind.ARMOR -> ArmorDefinitionV1(
            name = value.nonBlankString("name"),
            slot = value.nonBlankString("slot"),
            grade = value.nonBlankString("grade"),
            recommendedLevel = value.integer("recommendedLevel"),
            defense = value.integer("defense"),
            magicDefense = value.integer("magicDefense"),
            weightClass = value.nonBlankString("weightClass"),
        )
        ContentKind.ACCESSORY -> AccessoryDefinitionV1(
            name = value.nonBlankString("name"),
            slot = value.nonBlankString("slot"),
            grade = value.nonBlankString("grade"),
            recommendedLevel = value.integer("recommendedLevel"),
            effects = value.effectNodes("effects"),
        )
        ContentKind.ITEM -> ItemDefinitionV1(
            name = value.nonBlankString("name"),
            itemCategory = value.nonBlankString("itemCategory"),
            grade = value.nonBlankString("grade"),
            primaryUseCategory = value.nonBlankString("primaryUseCategory"),
            effects = value.effectNodes("effects"),
        )
        ContentKind.MONSTER -> value.objectValue("levelBand").let { band ->
            MonsterDefinitionV1(
                name = value.nonBlankString("name"),
                family = value.nonBlankString("family"),
                levelBand = LevelBandV1(band.integer("min"), band.integer("max")),
                threatCoefficientBp = value.integer("threatCoefficientBp"),
                role = value.nonBlankString("role"),
                attributes = value.stringArray("attributes"),
            )
        }
        ContentKind.BOSS -> BossDefinitionV1(
            name = value.nonBlankString("name"),
            family = value.nonBlankString("family"),
            recommendedLevel = value.integer("recommendedLevel"),
            dungeonRank = value.nonBlankString("dungeonRank"),
            archetypeTags = value.stringArray("archetypeTags"),
            phaseCount = value.integer("phaseCount"),
            coreMechanic = value.nonBlankString("coreMechanic"),
            rewardTags = value.stringArray("rewardTags"),
        )
        ContentKind.EQUIPMENT_PREFIX, ContentKind.EQUIPMENT_SUFFIX -> EquipmentAffixDefinitionV1(
            position = value.nonBlankString("position"),
            name = value.nonBlankString("name"),
            grade = value.nonBlankString("grade"),
            effects = value.effectNodes("effects"),
            allowedEquipmentScope = null,
        )
        ContentKind.MONSTER_PREFIX, ContentKind.MONSTER_SUFFIX -> MonsterAffixDefinitionV1(
            position = value.nonBlankString("position"),
            name = value.nonBlankString("name"),
            grade = value.nonBlankString("grade"),
            effects = value.effectNodes("effects"),
            threatMultiplierBp = value.integer("threatMultiplierBp"),
        )
        ContentKind.SET -> EquipmentSetDefinitionV1(
            name = value.nonBlankString("name"),
            grade = value.nonBlankString("grade"),
            tiers = value.array("tiers").values.mapIndexed { index, node ->
                val tier = node as? CanonicalJson.JsonObject ?: invalid("tiers[$index]", "must be an object")
                EquipmentSetTierV1(tier.integer("pieces"), tier.effectNodes("effects"))
            },
        )
        ContentKind.SKILL -> SkillDefinitionV1(
            name = value.nonBlankString("name"),
            skillGroup = value.nonBlankString("skillGroup"),
            skillType = value.nonBlankString("skillType"),
            grade = value.nonBlankString("grade"),
            classScope = value.nonBlankString("classScope"),
            resourceCost = value.resourceCost(),
            cooldownCombatMillis = value.integer("cooldownCombatMillis"),
            effects = value.effectNodes("effects"),
            tags = value.stringArray("tags"),
        )
        ContentKind.SKILL_PREFIX, ContentKind.SKILL_SUFFIX -> SkillAffixDefinitionV1(
            position = value.nonBlankString("position"),
            name = value.nonBlankString("name"),
            grade = value.nonBlankString("grade"),
            modificationKind = null,
            amount = value.values.getValue("amount").let { node ->
                if (node.isNullLiteral()) null else node.asAstObject("amount").valueNode()
            },
            applicability = null,
        )
        ContentKind.CONTRACT -> ContractDefinitionV1(
            name = value.nonBlankString("name"),
            category = value.nonBlankString("category"),
            contractRank = value.nonBlankString("contractRank"),
            duration = value.values.getValue("duration").durationSpecOrNull("duration"),
            objective = null,
            rewards = emptyList(),
            featureTags = value.stringArray("featureTags"),
        )
        ContentKind.RELIC -> RelicDefinitionV1(
            name = value.nonBlankString("name"),
            category = value.nonBlankString("category"),
            eligibility = value.values.getValue("eligibilityAST").conditionNodeOrNull("eligibilityAST"),
            relationshipEffects = value.effectNodes("relationshipEffects"),
            followup = null,
        )
        ContentKind.EVENT, ContentKind.DUNGEON_EVENT -> EventDefinitionV1(
            category = value.nonBlankString("category"),
            name = value.nonBlankString("name"),
            eligibility = value.values.getValue("eligibilityAST").conditionNodeOrNull("eligibilityAST"),
            choices = emptyList(),
            effects = value.effectNodes("effects"),
        )
        ContentKind.CHAIN -> EventChainDefinitionV1(
            category = value.nonBlankString("category"),
            name = value.nonBlankString("name"),
            steps = emptyList(),
            finalEffects = value.effectNodes("finalEffects"),
        )
        ContentKind.LEGEND -> LegendDefinitionV1(
            name = value.nonBlankString("name"),
            eligibility = value.values.getValue("eligibilityAST").conditionNodeOrNull("eligibilityAST"),
            scenario = null,
            chronicleTags = value.stringArray("chronicleTags"),
        )
    }

    private fun CanonicalJson.JsonObject.effectNodes(name: String): List<EffectNodeV1> =
        array(name).values.mapIndexed { index, node ->
            node.asAstObject("$name[$index]").effectNode("$name[$index]")
        }

    private fun CanonicalJson.JsonObject.effectNode(path: String): EffectNodeV1 = when (astType(path)) {
        "TriggeredEffect" -> EffectNodeV1.TriggeredEffect(
            trigger = TriggerV1.valueOf(nonBlankStringAt("trigger", path)),
            condition = values["condition"]?.asAstObject("$path.condition")?.conditionNode("$path.condition")
                ?: ConditionExprV1.Always,
            target = TargetSelectorV1.valueOf(nonBlankStringAt("target", path)),
            chancePpm = values["chancePpm"]?.integerAt("$path.chancePpm") ?: 1_000_000L,
            cooldown = values["cooldown"]?.durationSpecOrNull("$path.cooldown"),
            effects = arrayAt("effects", path).values.mapIndexed { index, node ->
                node.asAstObject("$path.effects[$index]").effectNode("$path.effects[$index]")
            },
        )
        "ApplyDamage" -> EffectNodeV1.ApplyDamage(
            amount = objectAt("amount", path).valueNode("$path.amount"),
            damageType = DamageTypeV1.valueOf(nonBlankStringAt("damageType", path)),
        )
        "Heal" -> EffectNodeV1.Heal(objectAt("amount", path).valueNode("$path.amount"))
        "ModifyStat" -> EffectNodeV1.ModifyStat(
            stat = nonBlankStringAt("stat", path),
            op = ModifyOpV1.valueOf(nonBlankStringAt("op", path)),
            value = objectAt("value", path).valueNode("$path.value"),
            duration = values["duration"]?.durationSpecOrNull("$path.duration"),
        )
        "ApplyStatus" -> EffectNodeV1.ApplyStatus(
            statusId = nonBlankStringAt("statusId", path),
            stacks = objectAt("stacks", path).valueNode("$path.stacks"),
            duration = values["duration"]?.durationSpecOrNull("$path.duration"),
        )
        "RemoveStatus" -> EffectNodeV1.RemoveStatus(
            selector = objectAt("selector", path).statusSelector("$path.selector"),
            count = values["count"]?.integerAt("$path.count"),
        )
        "AddShield" -> EffectNodeV1.AddShield(
            amount = objectAt("amount", path).valueNode("$path.amount"),
            duration = values["duration"]?.durationSpecOrNull("$path.duration"),
        )
        "RestoreResource" -> EffectNodeV1.RestoreResource(
            resource = ResourceTypeV1.valueOf(nonBlankStringAt("resource", path)),
            amount = objectAt("amount", path).valueNode("$path.amount"),
        )
        "MoveTarget" -> EffectNodeV1.MoveTarget(
            mode = MoveModeV1.valueOf(nonBlankStringAt("mode", path)),
            distance = objectAt("distance", path).valueNode("$path.distance"),
        )
        else -> invalid("$path.type", "unsupported EffectNode type")
    }

    private fun CanonicalJson.JsonObject.valueNode(path: String = "value"): ValueExprV1 = when (astType(path)) {
        "ConstInt" -> ValueExprV1.ConstInt(values.getValue("value").integerAt("$path.value"))
        "Stat" -> ValueExprV1.Stat(
            TargetSelectorV1.valueOf(nonBlankStringAt("target", path)),
            nonBlankStringAt("statType", path),
        )
        "ResourceMax" -> ValueExprV1.ResourceMax(
            TargetSelectorV1.valueOf(nonBlankStringAt("target", path)),
            ResourceTypeV1.valueOf(nonBlankStringAt("resource", path)),
        )
        "BaseDamage" -> ValueExprV1.BaseDamage(BaseDamageSourceV1.valueOf(nonBlankStringAt("source", path)))
        "SkillPower" -> ValueExprV1.SkillPower(ContentId(nonBlankStringAt("sourceSkill", path)))
        "Multiply" -> ValueExprV1.Multiply(
            objectAt("value", path).valueNode("$path.value"),
            values.getValue("ratioBp").integerAt("$path.ratioBp"),
        )
        "Add" -> ValueExprV1.Add(arrayAt("values", path).values.mapIndexed { index, node ->
            node.asAstObject("$path.values[$index]").valueNode("$path.values[$index]")
        })
        "Min" -> ValueExprV1.Min(
            objectAt("a", path).valueNode("$path.a"),
            objectAt("b", path).valueNode("$path.b"),
        )
        "Max" -> ValueExprV1.Max(
            objectAt("a", path).valueNode("$path.a"),
            objectAt("b", path).valueNode("$path.b"),
        )
        "Clamp" -> ValueExprV1.Clamp(
            objectAt("value", path).valueNode("$path.value"),
            objectAt("min", path).valueNode("$path.min"),
            objectAt("max", path).valueNode("$path.max"),
        )
        "Stacks" -> ValueExprV1.Stacks(
            TargetSelectorV1.valueOf(nonBlankStringAt("target", path)),
            nonBlankStringAt("statusId", path),
        )
        "Level" -> ValueExprV1.Level(TargetSelectorV1.valueOf(nonBlankStringAt("target", path)))
        else -> invalid("$path.type", "unsupported ValueExpr type")
    }

    private fun CanonicalJson.JsonValue.conditionNodeOrNull(path: String): ConditionExprV1? {
        if (isNullLiteral()) return null
        return asAstObject(path).conditionNode(path)
    }

    private fun CanonicalJson.JsonObject.conditionNode(path: String): ConditionExprV1 = when (astType(path)) {
        "Always" -> ConditionExprV1.Always
        "Not" -> ConditionExprV1.Not(objectAt("expr", path).conditionNode("$path.expr"))
        "AllOf" -> ConditionExprV1.AllOf(arrayAt("exprs", path).values.mapIndexed { index, node ->
            node.asAstObject("$path.exprs[$index]").conditionNode("$path.exprs[$index]")
        })
        "AnyOf" -> ConditionExprV1.AnyOf(arrayAt("exprs", path).values.mapIndexed { index, node ->
            node.asAstObject("$path.exprs[$index]").conditionNode("$path.exprs[$index]")
        })
        "Compare" -> ConditionExprV1.Compare(
            lhs = objectAt("lhs", path).valueNode("$path.lhs"),
            op = CompareOpV1.valueOf(nonBlankStringAt("op", path)),
            rhs = values.getValue("rhs").let { rhs ->
                if (rhs is CanonicalJson.JsonObject) ComparisonOperandV1.Expression(rhs.valueNode("$path.rhs"))
                else ComparisonOperandV1.Literal(rhs.integerAt("$path.rhs"))
            },
        )
        "HasStatus" -> ConditionExprV1.HasStatus(
            TargetSelectorV1.valueOf(nonBlankStringAt("target", path)),
            nonBlankStringAt("statusId", path),
            values.getValue("minStacks").integerAt("$path.minStacks"),
        )
        "HasTag" -> ConditionExprV1.HasTag(
            TargetSelectorV1.valueOf(nonBlankStringAt("target", path)),
            nonBlankStringAt("tag", path),
        )
        "HpRatio" -> ConditionExprV1.HpRatio(
            TargetSelectorV1.valueOf(nonBlankStringAt("target", path)),
            CompareOpV1.valueOf(nonBlankStringAt("op", path)),
            values.getValue("value").integerAt("$path.value"),
        )
        "ResourceRatio" -> ConditionExprV1.ResourceRatio(
            TargetSelectorV1.valueOf(nonBlankStringAt("target", path)),
            ResourceTypeV1.valueOf(nonBlankStringAt("resource", path)),
            CompareOpV1.valueOf(nonBlankStringAt("op", path)),
            values.getValue("value").integerAt("$path.value"),
        )
        "Count" -> ConditionExprV1.Count(
            TargetSelectorV1.valueOf(nonBlankStringAt("selector", path)),
            CompareOpV1.valueOf(nonBlankStringAt("op", path)),
            values.getValue("value").integerAt("$path.value"),
        )
        "IsBoss" -> ConditionExprV1.IsBoss(TargetSelectorV1.valueOf(nonBlankStringAt("target", path)))
        "IsFrontRow" -> ConditionExprV1.IsFrontRow(TargetSelectorV1.valueOf(nonBlankStringAt("target", path)))
        "TerrainHas" -> ConditionExprV1.TerrainHas(nonBlankStringAt("tag", path))
        else -> invalid("$path.type", "unsupported ConditionExpr type")
    }

    private fun CanonicalJson.JsonObject.statusSelector(path: String): StatusSelectorV1 = when (astType(path)) {
        "StatusId" -> StatusSelectorV1.StatusId(nonBlankStringAt("statusId", path))
        "AllStatuses" -> StatusSelectorV1.AllStatuses
        else -> invalid("$path.type", "unsupported StatusSelector type")
    }

    private fun CanonicalJson.JsonValue.durationSpecOrNull(path: String): DurationSpecV1? {
        if (isNullLiteral()) return null
        val objectValue = asAstObject(path)
        return DurationSpecV1(
            domain = TimeDomainV1.valueOf(objectValue.nonBlankString("type")),
            value = objectValue.integer("value"),
        )
    }

    private fun CanonicalJson.JsonObject.resourceCost(): ResourceCostV1 {
        val resource = objectValue("resourceCost")
        return when (resource.nonBlankString("type")) {
            "NONE" -> ResourceCostV1.None
            "SINGLE" -> ResourceCostV1.Single(
                ResourceTypeV1.valueOf(resource.nonBlankString("resource")),
                resource.integer("amount"),
            )
            "MULTI" -> ResourceCostV1.Multi(
                resource.array("costs").values.mapIndexed { index, node ->
                    val cost = node as? CanonicalJson.JsonObject ?: invalid("resourceCost.costs[$index]", "must be an object")
                    ResourceCostV1.Single(ResourceTypeV1.valueOf(cost.nonBlankString("resource")), cost.integer("amount"))
                }
            )
            "UNRESOLVED" -> ResourceCostV1.Unresolved(resource.nonBlankString("reason"))
            else -> invalid("resourceCost.type", "unsupported type")
        }
    }

    private fun validateCommon(kind: ContentKind, value: CanonicalJson.JsonObject, references: MutableSet<ContentId>) {
        val budget = AstBudget()
        listOf("name", "grade", "position", "category", "slot", "weaponType", "weightClass", "itemCategory", "primaryUseCategory", "family", "role", "dungeonRank", "coreMechanic", "skillGroup", "skillType", "classScope", "contractRank")
            .filter(value.values::containsKey)
            .forEach { value.nonBlankString(it) }
        listOf("tags", "attributes", "archetypeTags", "rewardTags", "featureTags", "chronicleTags")
            .filter(value.values::containsKey)
            .forEach { value.stringArray(it) }
        listOf("recommendedLevel", "phaseCount").filter(value.values::containsKey).forEach { field ->
            if (value.integer(field) < 1L) invalid(field, "must be at least 1")
        }
        listOf("physicalPower", "magicPower", "defense", "magicDefense", "threatCoefficientBp", "threatMultiplierBp", "cooldownCombatMillis")
            .filter(value.values::containsKey)
            .forEach { field -> if (value.integer(field) < 0L) invalid(field, "must not be negative") }

        when (kind) {
            ContentKind.MONSTER -> {
                val band = value.objectValue("levelBand")
                requireOnly(band, setOf("min", "max"), "levelBand")
                val min = band.integer("min")
                val max = band.integer("max")
                if (min < 1 || max < min) invalid("levelBand", "must satisfy 1 <= min <= max")
            }
            ContentKind.SET -> {
                val tiers = value.array("tiers").values.mapIndexed { index, node ->
                    val tier = node as? CanonicalJson.JsonObject ?: invalid("tiers[$index]", "must be an object")
                    requireOnly(tier, setOf("pieces", "effects"), "tiers[$index]")
                    tier.integer("pieces") to tier.array("effects")
                }
                if (tiers.map { it.first }.zipWithNext().any { (a, b) -> b <= a }) {
                    invalid("tiers", "piece counts must increase strictly")
                }
                tiers.forEachIndexed { index, tier -> validateEffectArray(tier.second, "tiers[$index].effects", references, budget) }
            }
            ContentKind.SKILL -> validateResourceCost(value.objectValue("resourceCost"))
            else -> Unit
        }

        listOf("effects", "relationshipEffects", "finalEffects")
            .filter(value.values::containsKey)
            .forEach { validateEffectArray(value.array(it), it, references, budget) }
        listOf("choices", "steps", "rewards")
            .filter(value.values::containsKey)
            .forEach { field ->
                if (value.array(field).values.isNotEmpty()) {
                    invalid(field, "no executable v1 codec is registered; preserve source text as unresolved")
                }
            }
        value.values["eligibilityAST"]?.let { node ->
            if (!node.isNullLiteral()) {
                validateConditionNode(node.asAstObject("eligibilityAST"), "eligibilityAST", references, budget, 1)
            }
        }
        value.values["duration"]?.let { node ->
            if (!node.isNullLiteral()) validateDuration(node.asAstObject("duration"), "duration", budget, 1)
        }
        listOf("objective", "followup", "scenario", "allowedEquipmentScope", "modificationKind", "applicability")
            .forEach { field ->
                value.values[field]?.let { node ->
                    if (!node.isNullLiteral()) {
                        invalid(field, "no executable v1 codec is registered; preserve source text as unresolved")
                    }
                }
            }
        if (value.values.containsKey("amount")) {
            val amount = value.values.getValue("amount")
            if (!amount.isNullLiteral()) {
                validateValueNode(amount.asAstObject("amount"), "amount", references, budget, 1)
            }
        }
    }

    private fun validateResourceCost(resource: CanonicalJson.JsonObject) {
        when (resource.nonBlankString("type")) {
            "NONE" -> requireOnly(resource, setOf("type"), "resourceCost")
            "SINGLE" -> {
                requireOnly(resource, setOf("type", "resource", "amount"), "resourceCost")
                if (resource.nonBlankString("resource") !in setOf("MANA", "STAMINA")) {
                    invalid("resourceCost.resource", "unsupported resource")
                }
                if (resource.integer("amount") < 0) invalid("resourceCost.amount", "must not be negative")
            }
            "MULTI" -> {
                requireOnly(resource, setOf("type", "costs"), "resourceCost")
                val costs = resource.array("costs")
                if (costs.values.size < 2) invalid("resourceCost.costs", "MULTI requires at least two costs")
                val resources = costs.values.mapIndexed { index, node ->
                    val cost = node as? CanonicalJson.JsonObject ?: invalid("resourceCost.costs[$index]", "must be an object")
                    requireOnly(cost, setOf("resource", "amount"), "resourceCost.costs[$index]")
                    if (cost.integer("amount") < 0) invalid("resourceCost.costs[$index].amount", "must not be negative")
                    cost.nonBlankString("resource").also { resourceName ->
                        if (resourceName !in setOf("MANA", "STAMINA")) {
                            invalid("resourceCost.costs[$index].resource", "unsupported resource")
                        }
                    }
                }
                if (resources.distinct().size != resources.size) invalid("resourceCost.costs", "resources must be unique")
            }
            "UNRESOLVED" -> {
                requireOnly(resource, setOf("type", "reason"), "resourceCost")
                resource.nonBlankString("reason")
            }
            else -> invalid("resourceCost.type", "unsupported type")
        }
    }

    private fun validateEffectArray(
        array: CanonicalJson.JsonArray,
        path: String,
        references: MutableSet<ContentId>,
        budget: AstBudget,
        depth: Int = 1,
    ) {
        array.values.forEachIndexed { index, node ->
            validateEffectNode(node.asAstObject("$path[$index]"), "$path[$index]", references, budget, depth)
        }
    }

    private fun validateEffectNode(
        node: CanonicalJson.JsonObject,
        path: String,
        references: MutableSet<ContentId>,
        budget: AstBudget,
        depth: Int,
    ) {
        budget.consume(path, depth)
        when (val type = node.astType(path)) {
            "TriggeredEffect" -> {
                requireFields(node, setOf("type", "trigger", "target", "effects"), setOf("condition", "chancePpm", "cooldown"), path)
                node.enumString("trigger", triggers, path)
                node.enumString("target", targets, path)
                node.values["condition"]?.let { validateConditionNode(it.asAstObject("$path.condition"), "$path.condition", references, budget, depth + 1) }
                node.values["chancePpm"]?.let {
                    val chance = it.integerAt("$path.chancePpm")
                    if (chance !in 0L..1_000_000L) invalid("$path.chancePpm", "must be in 0..1000000")
                }
                node.values["cooldown"]?.let {
                    if (!it.isNullLiteral()) validateDuration(it.asAstObject("$path.cooldown"), "$path.cooldown", budget, depth + 1)
                }
                val effects = node.arrayAt("effects", path)
                if (effects.values.isEmpty()) invalid("$path.effects", "must not be empty")
                validateEffectArray(effects, "$path.effects", references, budget, depth + 1)
            }
            "ApplyDamage" -> {
                requireFields(node, setOf("type", "amount", "damageType"), emptySet(), path)
                node.enumString("damageType", damageTypes, path)
                validateValueNode(node.objectAt("amount", path), "$path.amount", references, budget, depth + 1)
            }
            "Heal", "AddShield" -> {
                val optional = if (type == "AddShield") setOf("duration") else emptySet()
                requireFields(node, setOf("type", "amount"), optional, path)
                validateValueNode(node.objectAt("amount", path), "$path.amount", references, budget, depth + 1)
                node.values["duration"]?.let {
                    if (!it.isNullLiteral()) validateDuration(it.asAstObject("$path.duration"), "$path.duration", budget, depth + 1)
                }
            }
            "ModifyStat" -> {
                requireFields(node, setOf("type", "stat", "op", "value"), setOf("duration"), path)
                node.symbol("stat", path)
                node.enumString("op", modifyOps, path)
                validateValueNode(node.objectAt("value", path), "$path.value", references, budget, depth + 1)
                node.values["duration"]?.let {
                    if (!it.isNullLiteral()) validateDuration(it.asAstObject("$path.duration"), "$path.duration", budget, depth + 1)
                }
            }
            "ApplyStatus" -> {
                requireFields(node, setOf("type", "statusId", "stacks"), setOf("duration"), path)
                node.symbol("statusId", path)
                validateValueNode(node.objectAt("stacks", path), "$path.stacks", references, budget, depth + 1)
                node.values["duration"]?.let {
                    if (!it.isNullLiteral()) validateDuration(it.asAstObject("$path.duration"), "$path.duration", budget, depth + 1)
                }
            }
            "RemoveStatus" -> {
                requireFields(node, setOf("type", "selector"), setOf("count"), path)
                validateStatusSelector(node.objectAt("selector", path), "$path.selector", budget, depth + 1)
                node.values["count"]?.let { if (it.integerAt("$path.count") < 1) invalid("$path.count", "must be at least 1") }
            }
            "RestoreResource" -> {
                requireFields(node, setOf("type", "resource", "amount"), emptySet(), path)
                node.enumString("resource", resources, path)
                validateValueNode(node.objectAt("amount", path), "$path.amount", references, budget, depth + 1)
            }
            "MoveTarget" -> {
                requireFields(node, setOf("type", "mode", "distance"), emptySet(), path)
                node.enumString("mode", moveModes, path)
                validateValueNode(node.objectAt("distance", path), "$path.distance", references, budget, depth + 1)
            }
            else -> invalid("$path.type", "unsupported EffectNode type: $type")
        }
    }

    private fun validateConditionNode(
        node: CanonicalJson.JsonObject,
        path: String,
        references: MutableSet<ContentId>,
        budget: AstBudget,
        depth: Int,
    ) {
        budget.consume(path, depth)
        when (val type = node.astType(path)) {
            "Always" -> requireFields(node, setOf("type"), emptySet(), path)
            "Not" -> {
                requireFields(node, setOf("type", "expr"), emptySet(), path)
                validateConditionNode(node.objectAt("expr", path), "$path.expr", references, budget, depth + 1)
            }
            "AllOf", "AnyOf" -> {
                requireFields(node, setOf("type", "exprs"), emptySet(), path)
                val exprs = node.arrayAt("exprs", path)
                if (exprs.values.isEmpty()) invalid("$path.exprs", "must not be empty")
                exprs.values.forEachIndexed { index, child ->
                    validateConditionNode(child.asAstObject("$path.exprs[$index]"), "$path.exprs[$index]", references, budget, depth + 1)
                }
            }
            "Compare" -> {
                requireFields(node, setOf("type", "lhs", "op", "rhs"), emptySet(), path)
                validateValueNode(node.objectAt("lhs", path), "$path.lhs", references, budget, depth + 1)
                node.enumString("op", compareOps, path)
                val rhs = node.values.getValue("rhs")
                if (rhs is CanonicalJson.JsonObject) validateValueNode(rhs, "$path.rhs", references, budget, depth + 1)
                else rhs.integerAt("$path.rhs")
            }
            "HasStatus" -> {
                requireFields(node, setOf("type", "target", "statusId", "minStacks"), emptySet(), path)
                node.enumString("target", targets, path)
                node.symbol("statusId", path)
                if (node.values.getValue("minStacks").integerAt("$path.minStacks") < 1) invalid("$path.minStacks", "must be at least 1")
            }
            "HasTag" -> {
                requireFields(node, setOf("type", "target", "tag"), emptySet(), path)
                node.enumString("target", targets, path)
                node.symbol("tag", path)
            }
            "HpRatio" -> ratioCondition(node, path, null)
            "ResourceRatio" -> ratioCondition(node, path, "resource")
            "Count" -> {
                requireFields(node, setOf("type", "selector", "op", "value"), emptySet(), path)
                node.enumString("selector", targets, path)
                node.enumString("op", compareOps, path)
                if (node.values.getValue("value").integerAt("$path.value") < 0) invalid("$path.value", "must not be negative")
            }
            "IsBoss", "IsFrontRow" -> {
                requireFields(node, setOf("type", "target"), emptySet(), path)
                node.enumString("target", targets, path)
            }
            "TerrainHas" -> {
                requireFields(node, setOf("type", "tag"), emptySet(), path)
                node.symbol("tag", path)
            }
            else -> invalid("$path.type", "unsupported ConditionExpr type: $type")
        }
    }

    private fun ratioCondition(node: CanonicalJson.JsonObject, path: String, resourceField: String?) {
        val required = linkedSetOf("type", "target", "op", "value").apply { resourceField?.let(::add) }
        requireFields(node, required, emptySet(), path)
        node.enumString("target", targets, path)
        resourceField?.let { node.enumString(it, resources, path) }
        node.enumString("op", compareOps, path)
        val value = node.values.getValue("value").integerAt("$path.value")
        if (value !in 0L..10_000L) invalid("$path.value", "ratio must be in 0..10000 basis points")
    }

    private fun validateValueNode(
        node: CanonicalJson.JsonObject,
        path: String,
        references: MutableSet<ContentId>,
        budget: AstBudget,
        depth: Int,
    ) {
        budget.consume(path, depth)
        when (val type = node.astType(path)) {
            "ConstInt" -> {
                requireFields(node, setOf("type", "value"), emptySet(), path)
                node.values.getValue("value").integerAt("$path.value")
            }
            "Stat" -> {
                requireFields(node, setOf("type", "target", "statType"), emptySet(), path)
                node.enumString("target", targets, path)
                node.symbol("statType", path)
            }
            "ResourceMax" -> {
                requireFields(node, setOf("type", "target", "resource"), emptySet(), path)
                node.enumString("target", targets, path)
                node.enumString("resource", resources, path)
            }
            "BaseDamage" -> {
                requireFields(node, setOf("type", "source"), emptySet(), path)
                node.enumString("source", setOf("SELF", "CURRENT_TARGET"), path)
            }
            "SkillPower" -> {
                requireFields(node, setOf("type", "sourceSkill"), emptySet(), path)
                references += ContentId(node.nonBlankStringAt("sourceSkill", path))
            }
            "Multiply" -> {
                requireFields(node, setOf("type", "value", "ratioBp"), emptySet(), path)
                validateValueNode(node.objectAt("value", path), "$path.value", references, budget, depth + 1)
                node.values.getValue("ratioBp").integerAt("$path.ratioBp")
            }
            "Add" -> {
                requireFields(node, setOf("type", "values"), emptySet(), path)
                val values = node.arrayAt("values", path)
                if (values.values.size < 2) invalid("$path.values", "requires at least two values")
                values.values.forEachIndexed { index, child ->
                    validateValueNode(child.asAstObject("$path.values[$index]"), "$path.values[$index]", references, budget, depth + 1)
                }
            }
            "Min", "Max" -> {
                requireFields(node, setOf("type", "a", "b"), emptySet(), path)
                validateValueNode(node.objectAt("a", path), "$path.a", references, budget, depth + 1)
                validateValueNode(node.objectAt("b", path), "$path.b", references, budget, depth + 1)
            }
            "Clamp" -> {
                requireFields(node, setOf("type", "value", "min", "max"), emptySet(), path)
                validateValueNode(node.objectAt("value", path), "$path.value", references, budget, depth + 1)
                validateValueNode(node.objectAt("min", path), "$path.min", references, budget, depth + 1)
                validateValueNode(node.objectAt("max", path), "$path.max", references, budget, depth + 1)
            }
            "Stacks" -> {
                requireFields(node, setOf("type", "target", "statusId"), emptySet(), path)
                node.enumString("target", targets, path)
                node.symbol("statusId", path)
            }
            "Level" -> {
                requireFields(node, setOf("type", "target"), emptySet(), path)
                node.enumString("target", targets, path)
            }
            else -> invalid("$path.type", "unsupported ValueExpr type: $type")
        }
    }

    private fun validateDuration(node: CanonicalJson.JsonObject, path: String, budget: AstBudget, depth: Int) {
        budget.consume(path, depth)
        requireFields(node, setOf("type", "value"), emptySet(), path)
        node.enumString("type", setOf("COMBAT_MS", "GAME_MINUTE"), path)
        if (node.values.getValue("value").integerAt("$path.value") < 0) invalid("$path.value", "must not be negative")
    }

    private fun validateStatusSelector(node: CanonicalJson.JsonObject, path: String, budget: AstBudget, depth: Int) {
        budget.consume(path, depth)
        when (val type = node.astType(path)) {
            "StatusId" -> {
                requireFields(node, setOf("type", "statusId"), emptySet(), path)
                node.symbol("statusId", path)
            }
            "AllStatuses" -> requireFields(node, setOf("type"), emptySet(), path)
            else -> invalid("$path.type", "unsupported StatusSelector type: $type")
        }
    }

    private class AstBudget {
        private var nodeCount = 0

        fun consume(path: String, depth: Int) {
            if (depth > 16) invalid(path, "AST depth exceeds 16")
            nodeCount += 1
            if (nodeCount > 128) invalid(path, "AST node count exceeds 128")
        }
    }

    private fun requireFields(value: CanonicalJson.JsonObject, required: Set<String>, optional: Set<String>, path: String) {
        val allowed = required + optional
        val missing = required - value.values.keys
        val unknown = value.values.keys - allowed
        if (missing.isNotEmpty() || unknown.isNotEmpty()) {
            invalid(path, "unknown=${unknown.sorted()} missing=${missing.sorted()}")
        }
    }

    private fun CanonicalJson.JsonValue.asAstObject(path: String): CanonicalJson.JsonObject =
        this as? CanonicalJson.JsonObject ?: invalid(path, "must be a typed AST object")

    private fun CanonicalJson.JsonValue.integerAt(path: String): Long = try {
        (this as? CanonicalJson.JsonNumber)?.value?.longValueExact() ?: invalid(path, "must be an integer")
    } catch (_: ArithmeticException) {
        invalid(path, "must be an integer")
    }

    private fun CanonicalJson.JsonObject.astType(path: String): String = nonBlankStringAt("type", path)

    private fun CanonicalJson.JsonObject.nonBlankStringAt(name: String, path: String): String =
        ((values[name] as? CanonicalJson.JsonString)?.value ?: invalid("$path.$name", "must be a string")).also {
            if (it.isBlank()) invalid("$path.$name", "must not be blank")
        }

    private fun CanonicalJson.JsonObject.enumString(name: String, allowed: Set<String>, path: String): String =
        nonBlankStringAt(name, path).also { if (it !in allowed) invalid("$path.$name", "unsupported value: $it") }

    private fun CanonicalJson.JsonObject.symbol(name: String, path: String): String =
        nonBlankStringAt(name, path).also {
            if (!it.matches(Regex("[A-Z][A-Z0-9_]{0,63}"))) invalid("$path.$name", "must be a registered uppercase symbol")
        }

    private fun CanonicalJson.JsonObject.objectAt(name: String, path: String): CanonicalJson.JsonObject =
        values[name]?.asAstObject("$path.$name") ?: invalid("$path.$name", "is required")

    private fun CanonicalJson.JsonObject.arrayAt(name: String, path: String): CanonicalJson.JsonArray =
        values[name] as? CanonicalJson.JsonArray ?: invalid("$path.$name", "must be an array")

    private val triggers = setOf(
        "ON_COMBAT_START", "ON_ACTION_START", "ON_BASIC_ATTACK", "ON_SKILL_CAST", "ON_HIT",
        "ON_DAMAGE_DEALT", "ON_DAMAGE_TAKEN", "ON_CRITICAL", "ON_BLOCK", "ON_EVADE",
        "ON_STATUS_APPLIED", "ON_STATUS_TICK", "ON_KILL", "ON_ALLY_DOWN", "ON_HP_THRESHOLD",
        "ON_COMBAT_END", "ON_ROOM_ENTER", "ON_EXPLORE", "ON_CAMP_START", "ON_TIME_BOUNDARY",
        "ON_ENHANCEMENT_SUCCESS",
    )
    private val targets = setOf(
        "SELF", "CURRENT_TARGET", "ALLY_LOWEST_HP", "ENEMY_LOWEST_HP", "ENEMY_HIGHEST_THREAT",
        "ALL_ALLIES", "ALL_ENEMIES", "ADJACENT_ENEMIES", "RANDOM_ENEMY",
    )
    private val compareOps = setOf("LT", "LTE", "EQ", "NE", "GTE", "GT")
    private val damageTypes = setOf("PHYSICAL", "MAGIC", "FIRE", "ICE", "LIGHTNING", "HOLY", "DARK", "TRUE")
    private val modifyOps = setOf("ADD", "ADD_BP", "MULTIPLY_BP", "SET")
    private val resources = setOf("HP", "MP", "MANA", "STAMINA")
    private val moveModes = setOf("PUSH", "PULL", "SWAP", "ADVANCE", "RETREAT")

    private fun decodeUnresolved(array: CanonicalJson.JsonArray): List<UnresolvedField> = array.values.mapIndexed { index, node ->
        val value = node as? CanonicalJson.JsonObject ?: invalid("unresolved[$index]", "must be an object")
        requireOnly(value, setOf("code", "field", "reason", "sourceText"), "unresolved[$index]")
        UnresolvedField(
            code = value.nonBlankString("code"),
            field = value.nonBlankString("field"),
            sourceText = value.string("sourceText"),
            reason = value.nonBlankString("reason"),
        )
    }

    private fun requireOnly(value: CanonicalJson.JsonObject, keys: Set<String>, path: String) {
        if (value.values.keys != keys) invalid(path, "unknown=${(value.values.keys - keys).sorted()} missing=${(keys - value.values.keys).sorted()}")
    }

    private fun CanonicalJson.JsonObject.string(name: String): String =
        (values[name] as? CanonicalJson.JsonString)?.value ?: invalid(name, "must be a string")

    private fun CanonicalJson.JsonObject.nonBlankString(name: String): String = string(name).also {
        if (it.isBlank()) invalid(name, "must not be blank")
    }

    private fun CanonicalJson.JsonObject.integer(name: String): Long = try {
        (values[name] as? CanonicalJson.JsonNumber)?.value?.longValueExact() ?: invalid(name, "must be an integer")
    } catch (_: ArithmeticException) {
        invalid(name, "must be an integer")
    }

    private fun CanonicalJson.JsonObject.array(name: String): CanonicalJson.JsonArray =
        values[name] as? CanonicalJson.JsonArray ?: invalid(name, "must be an array")

    private fun CanonicalJson.JsonObject.objectValue(name: String): CanonicalJson.JsonObject =
        values[name] as? CanonicalJson.JsonObject ?: invalid(name, "must be an object")

    private fun CanonicalJson.JsonObject.stringArray(name: String): List<String> = array(name).values.mapIndexed { index, value ->
        (value as? CanonicalJson.JsonString)?.value ?: invalid("$name[$index]", "must be a string")
    }

    private fun CanonicalJson.JsonValue.isNullLiteral(): Boolean = this is CanonicalJson.JsonLiteral && value == "null"

    private fun invalid(field: String, detail: String): Nothing = throw ContentDefinitionContractException(field, detail)
}
