package com.imsi.mud.content

import java.util.Collections

data class ContentDefinitionRef(
    val kind: ContentKind,
    val id: ContentId,
    val definitionVersion: Int,
    val definitionHash: String
) {
    init {
        require(definitionVersion == ContentTemplate.SUPPORTED_DEFINITION_VERSION) { "unsupported definition version" }
        require(definitionHash.matches(Regex("[0-9a-f]{64}"))) { "definition hash must be lowercase SHA-256" }
    }
}

/**
 * The save-side proof set. It is purposefully content only: asset checksum changes do not change
 * simulation compatibility.
 */
class ContentCompatibilitySnapshot private constructor(
    val snapshotVersion: Int,
    val logicalContentHash: String,
    requiredDefinitions: Iterable<ContentDefinitionRef>
) {
    val requiredDefinitions: List<ContentDefinitionRef> = Collections.unmodifiableList(
        requiredDefinitions.sortedWith(compareBy<ContentDefinitionRef>({ it.kind.wireValue }, { it.id.value }, { it.definitionVersion }, { it.definitionHash }))
    )

    init {
        require(snapshotVersion == SNAPSHOT_VERSION) { "unsupported compatibility snapshot version" }
        require(logicalContentHash.matches(Regex("[0-9a-f]{64}"))) { "logical content hash must be lowercase SHA-256" }
        require(this.requiredDefinitions.map { it.kind to it.id }.distinct().size == this.requiredDefinitions.size) {
            "compatibility snapshot definitions must be unique by kind and id"
        }
    }

    companion object {
        const val SNAPSHOT_VERSION = 1
        fun create(logicalContentHash: String, requiredDefinitions: Iterable<ContentDefinitionRef>): ContentCompatibilitySnapshot =
            ContentCompatibilitySnapshot(SNAPSHOT_VERSION, logicalContentHash, requiredDefinitions)
    }
}

sealed interface LegacyTerminal {
    data class Remap(val target: ContentDefinitionRef) : LegacyTerminal
    data object Tombstone : LegacyTerminal
}

data class LegacySnapshotEntry(
    val from: ContentDefinitionRef,
    val terminal: LegacyTerminal,
    val provenance: String,
    val approvalRevision: String
) {
    init {
        require(provenance.isNotBlank()) { "legacy provenance must not be blank" }
        require(approvalRevision.isNotBlank()) { "legacy approval revision must not be blank" }
        if (terminal is LegacyTerminal.Remap) require(terminal.target.kind == from.kind) {
            "legacy remap kind must not change"
        }
    }

    companion object {
        fun remap(
            from: ContentDefinitionRef,
            to: ContentDefinitionRef,
            provenance: String,
            approvalRevision: String
        ): LegacySnapshotEntry = LegacySnapshotEntry(from, LegacyTerminal.Remap(to), provenance, approvalRevision)

        fun tombstone(
            from: ContentDefinitionRef,
            provenance: String,
            approvalRevision: String
        ): LegacySnapshotEntry = LegacySnapshotEntry(from, LegacyTerminal.Tombstone, provenance, approvalRevision)
    }
}

class LegacySnapshotIndex private constructor(entries: List<LegacySnapshotEntry>) {
    private val entriesByFrom: Map<ContentDefinitionRef, LegacySnapshotEntry> = entries.associateBy(LegacySnapshotEntry::from)

    init { require(entriesByFrom.size == entries.size) { "duplicate legacy snapshot entry" } }

    fun find(from: ContentDefinitionRef): LegacySnapshotEntry? = entriesByFrom[from]

    companion object {
        fun of(entries: Iterable<LegacySnapshotEntry>): LegacySnapshotIndex = LegacySnapshotIndex(entries.toList())
        fun empty(): LegacySnapshotIndex = LegacySnapshotIndex(emptyList())
    }
}

sealed interface BindingPlan {
    data object Compatible : BindingPlan

    class MigrationRequired(steps: List<BindingMigrationStep>) : BindingPlan {
        init {
            require(steps.isNotEmpty()) { "binding migration requires at least one step" }
            val terminalKeys = steps.map { it.from.kind to it.from.id }
            require(terminalKeys.distinct().size == terminalKeys.size) {
                "binding migration must have exactly one terminal step per source definition"
            }
        }

        val steps: List<BindingMigrationStep> = Collections.unmodifiableList(steps.sortedWith(bindingStepComparator))

        override fun equals(other: Any?): Boolean = other is MigrationRequired && steps == other.steps
        override fun hashCode(): Int = steps.hashCode()
        override fun toString(): String = "MigrationRequired(steps=$steps)"
    }

    class Unsupported(missingIds: List<ContentId>, changedDefinitions: List<ContentId>) : BindingPlan {
        val missingIds: List<ContentId> = Collections.unmodifiableList(missingIds.distinct().sortedBy(ContentId::value))
        val changedDefinitions: List<ContentId> = Collections.unmodifiableList(changedDefinitions.distinct().sortedBy(ContentId::value))

        override fun equals(other: Any?): Boolean = other is Unsupported &&
            missingIds == other.missingIds && changedDefinitions == other.changedDefinitions

        override fun hashCode(): Int = 31 * missingIds.hashCode() + changedDefinitions.hashCode()
        override fun toString(): String = "Unsupported(missingIds=$missingIds, changedDefinitions=$changedDefinitions)"
    }
}

sealed interface BindingMigrationStep {
    val from: ContentDefinitionRef
    val provenance: String
    val approvalRevision: String

    data class Remap(
        override val from: ContentDefinitionRef,
        val to: ContentDefinitionRef,
        override val provenance: String,
        override val approvalRevision: String
    ) : BindingMigrationStep {
        init {
            require(from.kind == to.kind) { "binding remap kind must not change" }
            require(provenance.isNotBlank()) { "binding remap provenance must not be blank" }
            require(approvalRevision.isNotBlank()) { "binding remap approval revision must not be blank" }
        }
    }

    data class Tombstone(
        override val from: ContentDefinitionRef,
        override val provenance: String,
        override val approvalRevision: String
    ) : BindingMigrationStep {
        init {
            require(provenance.isNotBlank()) { "binding tombstone provenance must not be blank" }
            require(approvalRevision.isNotBlank()) { "binding tombstone approval revision must not be blank" }
        }
    }
}

private val bindingStepComparator = compareBy<BindingMigrationStep>(
    { it.from.kind.wireValue },
    { it.from.id.value },
    { it.from.definitionVersion },
    { it.from.definitionHash },
    { if (it is BindingMigrationStep.Remap) 0 else 1 }
)

/**
 * Immutable P3/P22/P25 handoff representation. Decoding rejects unknown codec IDs, versions,
 * plan kinds, fields, or malformed JSON rather than guessing a migration.
 */
object BindingPlanCodec {
    const val CODEC_ID = "binding-plan.v1"
    private const val VERSION = 1

    fun encode(plan: BindingPlan): String = when (plan) {
        BindingPlan.Compatible -> CanonicalJson.objectOf(
            "planType" to CanonicalJson.string("COMPATIBLE"),
            "version" to VERSION.toString()
        )

        is BindingPlan.MigrationRequired -> CanonicalJson.objectOf(
            "planType" to CanonicalJson.string("MIGRATION_REQUIRED"),
            "steps" to plan.steps.joinToString(prefix = "[", postfix = "]", separator = ",") { stepJson(it) },
            "version" to VERSION.toString()
        )

        is BindingPlan.Unsupported -> CanonicalJson.objectOf(
            "changedDefinitions" to idsJson(plan.changedDefinitions),
            "missingIds" to idsJson(plan.missingIds),
            "planType" to CanonicalJson.string("UNSUPPORTED"),
            "version" to VERSION.toString()
        )
    }

    fun decode(codecId: String, canonicalJson: String): BindingPlan {
        require(codecId == CODEC_ID) { "unsupported binding plan codec: $codecId" }
        val root = CanonicalJson.parse(canonicalJson).asObject("binding plan")
        val type = root.requiredString("planType")
        require(root.requiredInt("version") == VERSION) { "unsupported binding plan version" }
        return when (type) {
            "COMPATIBLE" -> {
                root.requireOnly("planType", "version")
                BindingPlan.Compatible
            }

            "MIGRATION_REQUIRED" -> {
                root.requireOnly("planType", "steps", "version")
                BindingPlan.MigrationRequired(root.requiredArray("steps").values.map(::decodeStep))
            }

            "UNSUPPORTED" -> {
                root.requireOnly("changedDefinitions", "missingIds", "planType", "version")
                BindingPlan.Unsupported(
                    missingIds = decodeIds(root.requiredArray("missingIds")),
                    changedDefinitions = decodeIds(root.requiredArray("changedDefinitions"))
                )
            }

            else -> throw IllegalArgumentException("unsupported binding plan type: $type")
        }
    }

    private fun stepJson(step: BindingMigrationStep): String = when (step) {
        is BindingMigrationStep.Remap -> CanonicalJson.objectOf(
            "approvalRevision" to CanonicalJson.string(step.approvalRevision),
            "from" to definitionJson(step.from),
            "provenance" to CanonicalJson.string(step.provenance),
            "stepType" to CanonicalJson.string("REMAP"),
            "to" to definitionJson(step.to)
        )

        is BindingMigrationStep.Tombstone -> CanonicalJson.objectOf(
            "approvalRevision" to CanonicalJson.string(step.approvalRevision),
            "from" to definitionJson(step.from),
            "provenance" to CanonicalJson.string(step.provenance),
            "stepType" to CanonicalJson.string("TOMBSTONE")
        )
    }

    private fun decodeStep(value: CanonicalJson.JsonValue): BindingMigrationStep {
        val fields = value.asObject("binding migration step")
        val stepType = fields.requiredString("stepType")
        val from = decodeDefinition(fields.requiredObject("from"))
        val provenance = fields.requiredString("provenance")
        val approvalRevision = fields.requiredString("approvalRevision")
        return when (stepType) {
            "REMAP" -> {
                fields.requireOnly("approvalRevision", "from", "provenance", "stepType", "to")
                BindingMigrationStep.Remap(from, decodeDefinition(fields.requiredObject("to")), provenance, approvalRevision)
            }

            "TOMBSTONE" -> {
                fields.requireOnly("approvalRevision", "from", "provenance", "stepType")
                BindingMigrationStep.Tombstone(from, provenance, approvalRevision)
            }

            else -> throw IllegalArgumentException("unsupported binding migration step: $stepType")
        }
    }

    private fun definitionJson(reference: ContentDefinitionRef): String = CanonicalJson.objectOf(
        "definitionHash" to CanonicalJson.string(reference.definitionHash),
        "definitionVersion" to reference.definitionVersion.toString(),
        "id" to CanonicalJson.string(reference.id.value),
        "kind" to CanonicalJson.string(reference.kind.wireValue)
    )

    private fun decodeDefinition(fields: Map<String, CanonicalJson.JsonValue>): ContentDefinitionRef {
        fields.requireOnly("definitionHash", "definitionVersion", "id", "kind")
        return ContentDefinitionRef(
            kind = ContentKind.fromWire(fields.requiredString("kind"))
                ?: throw IllegalArgumentException("unsupported content kind"),
            id = ContentId(fields.requiredString("id")),
            definitionVersion = fields.requiredInt("definitionVersion"),
            definitionHash = fields.requiredString("definitionHash")
        )
    }

    private fun idsJson(ids: List<ContentId>): String = ids.sortedBy(ContentId::value)
        .joinToString(prefix = "[", postfix = "]", separator = ",") { CanonicalJson.string(it.value) }

    private fun decodeIds(value: CanonicalJson.JsonArray): List<ContentId> {
        val ids = value.values.map { ContentId((it as? CanonicalJson.JsonString)?.value ?: throw IllegalArgumentException("content id must be a string")) }
        require(ids == ids.distinct().sortedBy(ContentId::value)) { "content ids must be sorted and unique" }
        return ids
    }

    private fun CanonicalJson.JsonValue.asObject(subject: String): Map<String, CanonicalJson.JsonValue> =
        (this as? CanonicalJson.JsonObject)?.values ?: throw IllegalArgumentException("$subject must be an object")

    private fun Map<String, CanonicalJson.JsonValue>.requiredObject(name: String): Map<String, CanonicalJson.JsonValue> =
        (get(name) as? CanonicalJson.JsonObject)?.values ?: throw IllegalArgumentException("$name must be an object")

    private fun Map<String, CanonicalJson.JsonValue>.requiredArray(name: String): CanonicalJson.JsonArray =
        get(name) as? CanonicalJson.JsonArray ?: throw IllegalArgumentException("$name must be an array")

    private fun Map<String, CanonicalJson.JsonValue>.requiredString(name: String): String =
        (get(name) as? CanonicalJson.JsonString)?.value ?: throw IllegalArgumentException("$name must be a string")

    private fun Map<String, CanonicalJson.JsonValue>.requiredInt(name: String): Int = try {
        (get(name) as? CanonicalJson.JsonNumber)?.value?.intValueExact()
            ?: throw IllegalArgumentException("$name must be an integer")
    } catch (_: ArithmeticException) {
        throw IllegalArgumentException("$name must be an integer")
    }

    private fun Map<String, CanonicalJson.JsonValue>.requireOnly(vararg expected: String) {
        require(keys == expected.toSet()) { "unknown or missing binding plan fields" }
    }
}

/** Read-only binding planner; callers own save migration transaction boundaries. */
object ContentCompatibilityPlanner {
    fun plan(
        saved: ContentCompatibilitySnapshot,
        installed: ContentSnapshot,
        legacy: LegacySnapshotIndex,
        aliases: Map<ContentId, ContentAlias> = emptyMap()
    ): BindingPlan {
        if (saved.logicalContentHash == installed.identity.logicalContentHash) return BindingPlan.Compatible

        val missing = mutableListOf<ContentId>()
        val changed = mutableListOf<ContentId>()
        val steps = mutableListOf<BindingMigrationStep>()
        saved.requiredDefinitions.forEach { required ->
            val currentAtSavedId = installed.definitionRefFor(required.id)
            if (currentAtSavedId == required) return@forEach
            val entry = legacy.find(required)
            when (val terminal = entry?.terminal) {
                is LegacyTerminal.Remap -> {
                    val remap = terminal.target
                    val currentAtRemapId = installed.definitionRefFor(remap.id)
                    val aliasAgrees = aliases[required.id]?.let { it.policy == AliasPolicy.REMAP && it.newId == remap.id } ?: true
                    if (currentAtRemapId == remap && aliasAgrees) {
                        steps += BindingMigrationStep.Remap(required, remap, entry.provenance, entry.approvalRevision)
                    } else if (currentAtSavedId == null) {
                        missing += required.id
                    } else {
                        changed += required.id
                    }
                }

                LegacyTerminal.Tombstone -> {
                    if (currentAtSavedId == null) {
                        steps += BindingMigrationStep.Tombstone(required, entry.provenance, entry.approvalRevision)
                    } else {
                        changed += required.id
                    }
                }

                null -> if (currentAtSavedId == null) missing += required.id else changed += required.id
            }
        }
        if (missing.isNotEmpty() || changed.isNotEmpty()) {
            return BindingPlan.Unsupported(missing.distinct().sortedBy(ContentId::value), changed.distinct().sortedBy(ContentId::value))
        }
        return if (steps.isEmpty()) BindingPlan.Compatible else BindingPlan.MigrationRequired(steps)
    }
}

data class AliasEdge(
    val oldId: ContentId,
    val kind: ContentKind,
    val targetId: ContentId?,
    val policy: AliasPolicy = AliasPolicy.REMAP,
    val reason: String = "legacy alias"
) {
    init {
        require((policy == AliasPolicy.REMAP) == (targetId != null)) { "alias terminal policy and target disagree" }
        requireNfc(reason)
    }
}

class ContentAliasGraphException(val code: String, detail: String) : IllegalArgumentException(detail)

/** Flattens source alias chains into direct, terminal aliases and rejects malformed graph evidence. */
object ContentAliasGraph {
    fun flatten(
        edges: Iterable<AliasEdge>,
        terminalTemplates: Map<ContentId, ContentTemplate>
    ): Map<ContentId, ContentAlias> {
        val byOldId = linkedMapOf<ContentId, AliasEdge>()
        edges.forEach { edge ->
            if (byOldId.put(edge.oldId, edge) != null) throw ContentAliasGraphException("ALIAS_DUPLICATE", "duplicate alias: ${edge.oldId.value}")
        }
        val flattened = linkedMapOf<ContentId, ContentAlias>()
        byOldId.keys.sortedBy(ContentId::value).forEach { oldId ->
            flattened[oldId] = resolve(oldId, byOldId, terminalTemplates, linkedSetOf())
        }
        return Collections.unmodifiableMap(flattened)
    }

    private fun resolve(
        root: ContentId,
        byOldId: Map<ContentId, AliasEdge>,
        terminalTemplates: Map<ContentId, ContentTemplate>,
        visiting: LinkedHashSet<ContentId>
    ): ContentAlias {
        if (!visiting.add(root)) throw ContentAliasGraphException("ALIAS_CYCLE", "alias cycle: ${visiting.joinToString(" -> ") { it.value }} -> ${root.value}")
        val edge = byOldId[root] ?: throw ContentAliasGraphException("ALIAS_MISSING", "missing alias source: ${root.value}")
        if (edge.policy == AliasPolicy.TOMBSTONE) return ContentAlias(edge.oldId, null, AliasPolicy.TOMBSTONE, edge.reason)
        val targetId = requireNotNull(edge.targetId)
        val child = byOldId[targetId]
        val terminal = if (child != null) {
            if (child.kind != edge.kind) throw ContentAliasGraphException("ALIAS_KIND_MISMATCH", "alias kind mismatch: ${edge.oldId.value}")
            resolve(targetId, byOldId, terminalTemplates, visiting)
        } else {
            val target = terminalTemplates[targetId]
                ?: throw ContentAliasGraphException("ALIAS_MISSING", "missing terminal alias target: ${targetId.value}")
            if (target.kind != edge.kind) throw ContentAliasGraphException("ALIAS_KIND_MISMATCH", "alias kind mismatch: ${edge.oldId.value}")
            ContentAlias(edge.oldId, target.id, AliasPolicy.REMAP, edge.reason)
        }
        visiting.remove(root)
        if (terminal.policy == AliasPolicy.TOMBSTONE) return ContentAlias(edge.oldId, null, AliasPolicy.TOMBSTONE, edge.reason)
        val target = terminalTemplates[terminal.newId]
            ?: throw ContentAliasGraphException("ALIAS_MISSING", "missing flattened alias target: ${terminal.newId!!.value}")
        if (target.kind != edge.kind) throw ContentAliasGraphException("ALIAS_KIND_MISMATCH", "alias kind mismatch: ${edge.oldId.value}")
        return ContentAlias(edge.oldId, target.id, AliasPolicy.REMAP, edge.reason)
    }
}
