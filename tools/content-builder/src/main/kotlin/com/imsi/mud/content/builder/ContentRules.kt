package com.imsi.mud.content.builder

import com.imsi.mud.content.ContentDiagnostic
import com.imsi.mud.content.ContentSourceTemplate
import com.imsi.mud.content.DiagnosticSeverity

internal fun validateBuilderRules(records: List<ContentSourceTemplate>, rules: BuildRuleSet): List<ContentDiagnostic> {
    val diagnostics = mutableListOf<ContentDiagnostic>()
    val byId = records.associateBy(ContentSourceTemplate::id)
    records.sortedBy(ContentSourceTemplate::location).forEach { record ->
        val definition = JsonParser.parse(record.definitionJson).asObject()
        val unresolved = definition.optional("unresolved")?.let { it.asArray().values }.orEmpty()
        if (!record.enabled && unresolved.isNotEmpty()) {
            diagnostics += ContentDiagnostic(
                code = "UNRESOLVED_DISABLED",
                message = "unresolved content is disabled and excluded from active roots",
                location = record.location,
                severity = DiagnosticSeverity.WARNING,
                subject = record.id,
                field = "unresolved",
                expected = "disabled when unresolved content remains",
                actual = unresolved.size.toString()
            )
        }
    }

    rules.tagRules.sortedWith(compareBy(TagRule::subjectId, { it.allowed.sorted().joinToString(",") }, { it.forbidden.sorted().joinToString(",") })).forEach { rule ->
        val record = byId[rule.subjectId]
        val location = record?.location ?: com.imsi.mud.content.SourceLocation("builder-rules", 0)
        if (record == null) {
            diagnostics += ContentDiagnostic(
                code = "DANGLING_REFERENCE",
                message = "tag rule subject does not exist: ${rule.subjectId}",
                location = location,
                subject = rule.subjectId,
                field = "tagRules",
                expected = "existing content id",
                actual = rule.subjectId
            )
        }
        (rule.allowed intersect rule.forbidden).sorted().forEach { tag ->
            diagnostics += ContentDiagnostic(
                code = "TAG_CONFLICT",
                message = "tag is both allowed and forbidden: $tag",
                location = location,
                subject = rule.subjectId,
                field = "tagRules",
                expected = "not both allowed and forbidden",
                actual = tag
            )
        }
    }

    rules.recipeEdges.sortedWith(compareBy(RecipeEdge::fromId, RecipeEdge::toId)).forEach { edge ->
        if (edge.fromId !in byId) {
            diagnostics += ContentDiagnostic(
                code = "DANGLING_REFERENCE",
                message = "recipe edge source does not exist: ${edge.fromId}",
                location = com.imsi.mud.content.SourceLocation("builder-rules", 0),
                subject = edge.fromId,
                field = "recipeEdges.fromId",
                expected = "existing content id",
                actual = edge.fromId
            )
        }
        if (edge.toId !in byId) {
            diagnostics += ContentDiagnostic(
                code = "DANGLING_REFERENCE",
                message = "recipe edge target does not exist: ${edge.toId}",
                location = com.imsi.mud.content.SourceLocation("builder-rules", 0),
                subject = edge.toId,
                field = "recipeEdges.toId",
                expected = "existing content id",
                actual = edge.toId
            )
        }
    }

    val recipeRefs = rules.recipeEdges.groupBy(RecipeEdge::fromId).mapValues { (_, edges) -> edges.map(RecipeEdge::toId).sorted() }
    val reported = mutableSetOf<String>()
    fun visit(id: String, path: List<String>) {
        val cycleStart = path.indexOf(id)
        if (cycleStart >= 0) {
            val cycle = (path.subList(cycleStart, path.size) + id).dropLast(1)
            val key = cycle.minOrNull() ?: id
            if (reported.add(key)) {
                val record = byId.getValue(key)
                diagnostics += ContentDiagnostic(
                    code = "RECIPE_CYCLE",
                    message = "recipe reference cycle: ${(cycle + key).joinToString(" -> ")}",
                    location = record.location,
                    subject = key,
                    field = "recipeRefs"
                )
            }
            return
        }
        recipeRefs[id].orEmpty().filter { it in recipeRefs }.forEach { next -> visit(next, path + id) }
    }
    recipeRefs.keys.sorted().forEach { visit(it, emptyList()) }
    return diagnostics.sorted()
}
