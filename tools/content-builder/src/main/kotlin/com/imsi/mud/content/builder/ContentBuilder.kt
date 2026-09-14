package com.imsi.mud.content.builder

import com.imsi.mud.content.CatalogImporter
import com.imsi.mud.content.AssetResolver
import com.imsi.mud.content.ContentHasher
import com.imsi.mud.content.ContentSourceTemplate
import com.imsi.mud.content.ContentDefinitionContractException
import com.imsi.mud.content.ContentDefinitionV1Decoder
import com.imsi.mud.content.ImageUsage
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.channels.SeekableByteChannel
import java.nio.file.DirectoryStream
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.BasicFileAttributeView
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.text.Normalizer

data class ContentBuildRequest(
    val sourceRoot: Path,
    val outputRoot: Path,
    val ddlPath: Path,
    val contentVersion: String,
    val balanceVersion: String,
    val profile: String = "PROTOTYPE",
    val generatedByVersion: String = "p1-content-builder.v2",
    val assetEntries: List<AssetPreviewEntry> = emptyList(),
    val assetRoot: Path? = null,
    val approvedLicenseIds: Set<String> = emptySet(),
    val licenseRegistry: List<LicenseRegistryEntry> = emptyList(),
    val aliases: List<ContentAliasEntry> = emptyList(),
    val assetBindings: List<AssetBindingEntry> = emptyList(),
    val assetFallbacks: List<AssetFallbackEntry> = emptyList(),
    val rules: BuildRuleSet = BuildRuleSet(),
    val fault: BuildFault? = null
)

data class ContentAliasEntry(
    val id: String,
    val oldId: String,
    val newId: String?,
    val policy: String,
    val reason: String,
    val oldKind: String,
    val provenanceSection: Int,
    val provenanceRow: Int,
    val approvalRevision: String
)
data class AssetBindingEntry(val id: String, val templateId: String, val usageType: String, val assetId: String, val priority: Int)
data class AssetFallbackEntry(val id: String, val usageType: String, val matcherType: String, val matcherValue: String?, val assetId: String, val priority: Int)
data class TagRule(val subjectId: String, val allowed: Set<String>, val forbidden: Set<String>)
data class RecipeEdge(val fromId: String, val toId: String)
data class BuildRuleSet(val tagRules: List<TagRule> = emptyList(), val recipeEdges: List<RecipeEdge> = emptyList())

enum class BuildFault {
    WAL_REQUEST,
    OPEN_HANDLE,
    FAKE_SIDECAR,
    CRASH_AFTER_STAGING,
    CRASH_BEFORE_BUNDLE_MOVE,
    CRASH_AFTER_BUNDLE_MOVE_BEFORE_POINTER,
    HOLD_AFTER_STAGING_FOR_EXTERNAL_KILL,
    HOLD_BEFORE_BUNDLE_MOVE_FOR_EXTERNAL_KILL,
    HOLD_AFTER_BUNDLE_MOVE_FOR_EXTERNAL_KILL,
    HOLD_AFTER_POINTER_FOR_EXTERNAL_KILL,
    ATOMIC_MOVE_UNSUPPORTED,
    POINTER_MOVE_FAILURE,
    DIRECTORY_FSYNC_UNSUPPORTED,
    MUTATE_ASSET_AFTER_COPY,
    MUTATE_ASSET_SOURCE_AFTER_COPY,
    SWAP_ASSET_ROOT_BEFORE_HANDLE,
    SWAP_ASSET_PARENT_BEFORE_HANDLE,
    HOLD_LOCK
}

data class ContentBuildResult(
    val bundleDirectory: Path,
    val bundleHash: String,
    val sourceHash: String,
    val logicalContentHash: String,
    val rowCount: Int,
    val report: Path
)

class ContentBuildException(
    val code: String,
    val detailMessage: String,
) : IllegalStateException("$code: $detailMessage") {
    val externalResultCode: String = when {
        code in EXTERNAL_RESULT_CODES -> code
        code == "JSON_DUPLICATE_KEY" || code.startsWith("SOURCE_") -> "SOURCE_INVALID"
        code.startsWith("CONTENT_") || code in setOf("DANGLING_REFERENCE", "PROFILE_INVALID", "UNRESOLVED_CONTENT") -> "VALIDATION_FAILED"
        code.startsWith("ASSET_LICENSE_") || code == "ASSET_VALIDATION_FAILED" -> "VALIDATION_FAILED"
        code in setOf("PRAGMA_INVALID", "RESOURCE_OPEN", "SQLITE_SIDECAR") -> "INTEGRITY_FAILED"
        code.startsWith("PUBLISH_") -> "PUBLISH_ATOMIC_UNSUPPORTED"
        else -> "BUILD_IO"
    }

    private companion object {
        val EXTERNAL_RESULT_CODES = setOf(
            "SOURCE_INVALID", "VALIDATION_FAILED", "STAGING_NOT_EMPTY", "BUILD_IO", "INTEGRITY_FAILED",
            "PUBLISH_CONFLICT", "PUBLISH_ATOMIC_UNSUPPORTED", "INVALID_ASSET_PATH", "MISSING_REQUIRED_ASSET",
            "ASSET_DECODE_FAILED", "ASSET_UNAVAILABLE", "INCOMPATIBLE_CONTENT",
        )
    }
}

internal fun assetValidationFailure(diagnostics: List<AssetDiagnostic>): ContentBuildException {
    val codes = diagnostics.map(AssetDiagnostic::code).toSet()
    val resultCode = when {
        codes.any { it in setOf("ASSET_INVALID_PATH", "ASSET_PATH_TRAVERSAL", "ASSET_SYMLINK_ESCAPE") } -> "INVALID_ASSET_PATH"
        "ASSET_MISSING_FILE" in codes -> "MISSING_REQUIRED_ASSET"
        "ASSET_DECODE_FAILED" in codes -> "ASSET_DECODE_FAILED"
        else -> "VALIDATION_FAILED"
    }
    return ContentBuildException(resultCode, diagnostics.joinToString("; ") { "${it.code}:${it.assetId}" })
}

private data class LoadedSource(
    val records: List<ContentSourceTemplate>,
    val sourceHash: String,
    val fileCount: Int
)

private data class AliasResolution(
    val alias: ContentAliasEntry,
    val sourceNewId: String?
)

private data class ResolvedAliasInputs(
    val aliases: List<ContentAliasEntry>,
    val resolutions: List<AliasResolution>
)

private data class AssetSourceIdentity(
    val fileKey: Any?,
    val realPath: Path,
    val size: Long,
    val lastModifiedMillis: Long,
)

private data class ExpectedContentReference(
    val sourceId: String,
    val targetId: String,
    val expectedKind: String,
)

private interface WindowsFinalPathApi : StdCallLibrary {
    fun GetFinalPathNameByHandleW(handle: WinNT.HANDLE, path: CharArray, capacity: Int, flags: Int): Int
}

private class BuildDiagnosticsException(
    val code: String,
    val diagnostics: List<ReportDiagnostic>,
    message: String,
) : IllegalStateException(message)

object ContentBuilder {
    private val windowsFinalPathApi by lazy {
        Native.load("kernel32", WindowsFinalPathApi::class.java)
    }

    private val queryInventory = linkedMapOf(
        "CDB-Q01" to "SELECT id,kind,source_display_name,display_name,enabled,definition_json,definition_version FROM content_template WHERE id = ?",
        "CDB-Q02" to "SELECT id,kind,source_display_name,display_name,enabled,definition_json,definition_version FROM content_template WHERE kind = ? ORDER BY id",
        "CDB-Q03" to "SELECT old_id,new_id,policy,reason FROM content_alias WHERE old_id = ?",
        "CDB-Q04" to "SELECT asset_id,priority FROM asset_binding WHERE template_id = ? AND usage_type = ? ORDER BY priority",
        "CDB-Q05" to "SELECT id,relative_path,category,width,height,byte_size,sha256 FROM asset_image WHERE id = ?",
        "CDB-Q06" to "SELECT asset_id,priority FROM asset_fallback WHERE usage_type = ?"
    )

    fun build(request: ContentBuildRequest): ContentBuildResult = try {
        buildInternal(request)
    } catch (error: SourceParseException) {
        writeFailureReport(request.outputRoot, listOf(error.diagnostic.toReportDiagnostic()), error.diagnostic.code)
        throw ContentBuildException(error.diagnostic.code, error.diagnostic.render())
    } catch (error: BuildDiagnosticsException) {
        writeFailureReport(request.outputRoot, error.diagnostics, error.code)
        throw ContentBuildException(error.code, error.message.orEmpty())
    } catch (error: ContentBuildException) {
        writeFailureReport(request.outputRoot, listOf(reportDiagnostic(error, request.sourceRoot.toString())), error.code)
        throw error
    } catch (error: Throwable) {
        val message = error.message.orEmpty()
        val code = when {
            message.contains("duplicate JSON key", ignoreCase = true) -> "JSON_DUPLICATE_KEY"
            message.contains("unknown fields", ignoreCase = true) -> "SOURCE_UNKNOWN_FIELD"
            message.contains("UTF-8", ignoreCase = true) || message.contains("NFC", ignoreCase = true) -> "SOURCE_ENCODING_INVALID"
            error is IllegalArgumentException -> "SOURCE_INVALID"
            else -> "BUILD_IO"
        }
        writeFailureReport(
            request.outputRoot,
            listOf(reportDiagnostic(code, message.ifBlank { error::class.java.simpleName }, request.sourceRoot.toString())),
            code
        )
        throw ContentBuildException(code, message.ifBlank { error::class.java.simpleName })
    }

    private fun buildInternal(request: ContentBuildRequest): ContentBuildResult {
        require(request.profile in setOf("PROTOTYPE", "ALPHA", "FULL")) { "unsupported bundle profile: ${request.profile}" }
        require(request.contentVersion == Normalizer.normalize(request.contentVersion, Normalizer.Form.NFC)) { "content version must be NFC" }
        require(request.balanceVersion == Normalizer.normalize(request.balanceVersion, Normalizer.Form.NFC)) { "balance version must be NFC" }
        require(Files.isRegularFile(request.ddlPath)) { "DDL is missing: ${request.ddlPath}" }
        require(!Files.readString(request.ddlPath).contains("IF NOT EXISTS", ignoreCase = true)) { "DDL must be checked-in and fresh" }
        Class.forName("org.sqlite.JDBC")
        val source = loadSource(request.sourceRoot)
        val imported = CatalogImporter.import(source.records)
        val importErrors = imported.diagnostics.filter { it.severity.name == "ERROR" }
        if (importErrors.isNotEmpty()) {
            throw BuildDiagnosticsException(
                "CONTENT_VALIDATION_FAILED",
                importErrors.map(::toReportDiagnostic),
                importErrors.joinToString("; ") { "${it.code}:${it.subject}" }
            )
        }
        val builderDiagnostics = validateBuilderRules(source.records, request.rules)
        val builderErrors = builderDiagnostics.filter { it.severity == com.imsi.mud.content.DiagnosticSeverity.ERROR }
        if (builderErrors.isNotEmpty()) {
            val dangling = builderErrors.filter { it.code == "DANGLING_REFERENCE" }
            if (dangling.isNotEmpty()) {
                throw BuildDiagnosticsException(
                    "DANGLING_REFERENCE",
                    builderErrors.map(::toReportDiagnostic),
                    dangling.joinToString(";") { "${it.subject}:${it.field}:${it.actual}" }
                )
            }
            throw BuildDiagnosticsException(
                "CONTENT_VALIDATION_FAILED",
                builderErrors.map(::toReportDiagnostic),
                builderErrors.joinToString("; ") { "${it.code}:${it.subject}" }
            )
        }
        val decodedReferences = linkedMapOf<String, Set<String>>()
        val expectedReferences = mutableListOf<ExpectedContentReference>()
        try {
            imported.templates.forEach { template ->
                val decoded = ContentDefinitionV1Decoder.decode(template)
                decodedReferences[template.id.value] = decoded.references.map { it.value }.toSet()
                expectedReferences += decoded.typedReferences.map { reference ->
                    ExpectedContentReference(template.id.value, reference.id.value, reference.expectedKind.wireValue)
                }
            }
        } catch (error: ContentDefinitionContractException) {
            throw ContentBuildException("CONTENT_VALIDATION_FAILED", "${error.field}: ${error.message.orEmpty()}")
        }
        val templatesById = imported.templates.associateBy { it.id.value }
        expectedReferences.sortedWith(compareBy(ExpectedContentReference::sourceId, ExpectedContentReference::targetId)).forEach { reference ->
            val target = templatesById[reference.targetId] ?: return@forEach
            if (target.kind.wireValue != reference.expectedKind) {
                throw ContentBuildException(
                    "CONTENT_VALIDATION_FAILED",
                    "${reference.sourceId}: reference ${reference.targetId} requires ${reference.expectedKind}, got ${target.kind.wireValue}"
                )
            }
        }
        val unresolvedTemplates = imported.templates.filter { template ->
            val definition = JsonParser.parse(template.definitionJson).asObject()
            (definition.optional("unresolved") as? JsonArray)?.values?.isNotEmpty() == true
        }
        val invalidEnabledTemplates = unresolvedTemplates.filter { it.enabled }
        if (invalidEnabledTemplates.isNotEmpty()) {
            throw ContentBuildException("PROFILE_INVALID", "unresolved rows cannot be enabled: ${invalidEnabledTemplates.size}")
        }
        validateProfileClosure(request.profile, imported.templates, unresolvedTemplates, decodedReferences, request.rules.recipeEdges)
        val logicalHash = ContentHasher.logicalContentHash(imported.templates)
        val assetManifestSha256 = if (request.assetEntries.isEmpty()) {
            CanonicalSourceConverter.sha256(JsonObject(mapOf("assets" to JsonArray(emptyList()), "licenses" to JsonArray(emptyList()))).render().toByteArray(StandardCharsets.UTF_8))
        } else AssetValidator.assetManifestSha256(request.assetEntries, request.licenseRegistry)
        val assetDiagnostics = if (request.assetEntries.isEmpty()) emptyList() else {
            require(request.assetRoot != null) { "asset root is required when assets are supplied" }
            AssetValidator.validate(
                request.assetEntries,
                request.assetRoot,
                request.approvedLicenseIds,
                request.licenseRegistry.associate { it.id to it.distributionScopes }
            )
        }
        if (assetDiagnostics.isNotEmpty()) {
            throw assetValidationFailure(assetDiagnostics)
        }
        val resolvedAliasInputs = validateBundleInputs(request, imported.templates, request.assetEntries)
        val effectiveRequest = request.copy(aliases = resolvedAliasInputs.aliases)

        Files.createDirectories(request.outputRoot)
        return withPublishLock(effectiveRequest.outputRoot, if (effectiveRequest.fault == BuildFault.HOLD_LOCK) 750L else 0L) {
            val staging = prepareStaging(effectiveRequest)
            try {
            val db = staging.resolve("content.db")
            writeDatabase(effectiveRequest, db, imported.templates, effectiveRequest.assetEntries, source.sourceHash, logicalHash)
            awaitExternalKill(effectiveRequest, BuildFault.HOLD_AFTER_STAGING_FOR_EXTERNAL_KILL)
            if (effectiveRequest.fault == BuildFault.CRASH_AFTER_STAGING) {
                throw ContentBuildException("BUILD_CRASHED", "fault injected after staging database creation")
            }
            auditReadOnlyDatabase(db, imported.templates.size, effectiveRequest.aliases, effectiveRequest.assetBindings, effectiveRequest.assetFallbacks)
            val dbSha256 = CanonicalSourceConverter.sha256(Files.readAllBytes(db))
            val bundleId = CanonicalSourceConverter.sha256(
                "v1:1:${request.generatedByVersion}:$dbSha256:$assetManifestSha256".toByteArray(StandardCharsets.UTF_8)
            )
            val plans = readOnlyAudit(db)
            Files.writeString(staging.resolve("query-plan.json"), JsonObject(
                mapOf(
                    "queries" to JsonArray(plans.map { (id, plan) -> JsonObject(mapOf("id" to JsonString(id), "plan" to JsonString(plan))) }),
                    "readOnly" to JsonBoolean(true)
                )
            ).render() + "\n")
            copyAssets(effectiveRequest, staging.resolve("assets"))
            val reportDiagnostics = stableReportDiagnostics((imported.diagnostics + builderDiagnostics).map(::toReportDiagnostic))
            val report = JsonObject(
                mapOf(
                    "bundleId" to JsonString(bundleId),
                    "contentVersion" to JsonString(effectiveRequest.contentVersion),
                    "diagnostics" to JsonArray(reportDiagnostics.map(::reportDiagnosticJson)),
                    "assets" to JsonArray(effectiveRequest.assetEntries.sortedBy(AssetPreviewEntry::id).map(::assetPreviewJson)),
                    "previewRows" to JsonArray(previewRows(effectiveRequest).map(::assetPreviewJson)),
                    "aliasResolutions" to JsonArray(resolvedAliasInputs.resolutions.sortedBy { it.alias.oldId }.map { resolution ->
                        val alias = resolution.alias
                        JsonObject(mapOf(
                            "aliasId" to JsonString(alias.id),
                            "oldId" to JsonString(alias.oldId),
                            "oldKind" to JsonString(alias.oldKind),
                            "sourceNewId" to (resolution.sourceNewId?.let(::JsonString) ?: JsonNull),
                            "newId" to (alias.newId?.let(::JsonString) ?: JsonNull),
                            "policy" to JsonString(alias.policy),
                            "provenanceSection" to JsonNumber(java.math.BigDecimal(alias.provenanceSection)),
                            "provenanceRow" to JsonNumber(java.math.BigDecimal(alias.provenanceRow)),
                            "approvalRevision" to JsonString(alias.approvalRevision)
                        ))
                    }),
                    "logicalContentHash" to JsonString(logicalHash),
                    "rowCount" to JsonNumber(java.math.BigDecimal(imported.templates.size)),
                    "sourceHash" to JsonString(source.sourceHash),
                    "status" to JsonString("PASS")
                )
            )
            val reportPath = staging.resolve("validation-report.json")
            Files.writeString(reportPath, report.render() + "\n", StandardCharsets.UTF_8)
            Files.writeString(staging.resolve("validation-report.md"), renderMarkdownReport(reportPath), StandardCharsets.UTF_8)
            renderAssetPreview(reportPath, staging.resolve("asset-preview"))
            val manifest = bundleManifest(effectiveRequest, source, imported.templates.size, bundleId, logicalHash, dbSha256, assetManifestSha256, staging)
            Files.writeString(staging.resolve("content-bundle-manifest.json"), manifest.render() + "\n", StandardCharsets.UTF_8)
            if (effectiveRequest.fault == BuildFault.CRASH_BEFORE_BUNDLE_MOVE) {
                throw ContentBuildException("BUILD_CRASHED_BEFORE_BUNDLE_MOVE", "fault injected with owned staging marker retained")
            }
                return@withPublishLock publish(effectiveRequest.outputRoot, staging, bundleId, dbSha256, assetManifestSha256, source.sourceHash, logicalHash, effectiveRequest)
            } catch (error: Throwable) {
                if (error is ContentBuildException) throw error
                throw ContentBuildException("BUILD_FAILED", error.message ?: error::class.java.simpleName)
            }
        }
    }

    private fun toReportDiagnostic(diagnostic: com.imsi.mud.content.ContentDiagnostic): ReportDiagnostic = ReportDiagnostic(
        severity = diagnostic.severity.name,
        code = diagnostic.code,
        messageKey = diagnostic.messageKey,
        message = diagnostic.message,
        sourceId = diagnostic.sourceId,
        sourceFile = diagnostic.sourceFile,
        row = diagnostic.row,
        column = diagnostic.column,
        field = diagnostic.field,
        expected = diagnostic.expected,
        actual = diagnostic.actual
    )

    private fun reportDiagnostic(error: ContentBuildException, sourceFile: String): ReportDiagnostic = reportDiagnostic(
        code = error.code,
        message = error.message.orEmpty(),
        sourceFile = sourceFile
    )

    private fun reportDiagnostic(code: String, message: String, sourceFile: String): ReportDiagnostic = ReportDiagnostic(
        severity = "ERROR",
        code = code,
        messageKey = code,
        message = message,
        sourceId = null,
        sourceFile = sourceFile,
        row = null,
        column = null,
        field = null,
        expected = null,
        actual = message
    )

    private fun reportDiagnosticJson(diagnostic: ReportDiagnostic): JsonObject = JsonObject(mapOf(
        "actual" to (diagnostic.actual?.let(::JsonString) ?: JsonNull),
        "code" to JsonString(diagnostic.code),
        "column" to (diagnostic.column?.let { JsonNumber(java.math.BigDecimal(it)) } ?: JsonNull),
        "expected" to (diagnostic.expected?.let(::JsonString) ?: JsonNull),
        "field" to (diagnostic.field?.let(::JsonString) ?: JsonNull),
        "message" to JsonString(diagnostic.message),
        "messageKey" to JsonString(diagnostic.messageKey),
        "row" to (diagnostic.row?.let { JsonNumber(java.math.BigDecimal(it)) } ?: JsonNull),
        "severity" to JsonString(diagnostic.severity),
        "sourceFile" to (diagnostic.sourceFile?.let(::JsonString) ?: JsonNull),
        "sourceId" to (diagnostic.sourceId?.let(::JsonString) ?: JsonNull)
    ))

    private fun assetPreviewJson(entry: AssetPreviewEntry): JsonObject = JsonObject(mapOf(
        "alphaMode" to JsonString(entry.alphaMode),
        "byteSize" to JsonNumber(java.math.BigDecimal(entry.byteSize)),
        "category" to JsonString(entry.category),
        "colorSpace" to JsonString(entry.colorSpace),
        "cropProfile" to (entry.cropProfile?.let(::JsonString) ?: JsonNull),
        "duplicate" to JsonBoolean(entry.duplicate),
        "effectiveDisplayName" to (entry.effectiveDisplayName?.let(::JsonString) ?: JsonNull),
        "focalXppm" to (entry.focalXppm?.let { JsonNumber(java.math.BigDecimal(it)) } ?: JsonNull),
        "focalYppm" to (entry.focalYppm?.let { JsonNumber(java.math.BigDecimal(it)) } ?: JsonNull),
        "height" to JsonNumber(java.math.BigDecimal(entry.height)),
        "id" to JsonString(entry.id),
        "licenseId" to JsonString(entry.licenseId),
        "mimeType" to JsonString(entry.mimeType),
        "missing" to JsonBoolean(entry.missing),
        "poolVersion" to JsonString(entry.poolVersion),
        "relativePath" to JsonString(entry.relativePath),
        "resolutionReason" to (entry.resolutionReason?.let(::JsonString) ?: JsonNull),
        "sha256" to JsonString(entry.sha256),
        "sourceDisplayName" to (entry.sourceDisplayName?.let(::JsonString) ?: JsonNull),
        "unused" to JsonBoolean(entry.unused),
        "usageType" to (entry.usageType?.let(::JsonString) ?: JsonNull),
        "validationStatus" to JsonString(entry.validationStatus),
        "width" to JsonNumber(java.math.BigDecimal(entry.width))
    ))

    private fun previewRows(request: ContentBuildRequest): List<AssetPreviewEntry> {
        val assetsById = request.assetEntries.associateBy(AssetPreviewEntry::id)
        val referenced = linkedSetOf<String>()
        val rows = buildList {
            request.assetBindings.sortedWith(compareBy(AssetBindingEntry::usageType, AssetBindingEntry::priority, AssetBindingEntry::id)).forEach { binding ->
                referenced += binding.assetId
                val usage = ImageUsage.valueOf(binding.usageType)
                add(assetsById.getValue(binding.assetId).copy(
                    usageType = usage.name,
                    cropProfile = AssetResolver.profileFor(usage).name,
                    resolutionReason = "EXACT",
                    unused = false
                ))
            }
            request.assetFallbacks.sortedWith(compareBy(AssetFallbackEntry::usageType, AssetFallbackEntry::priority, AssetFallbackEntry::id)).forEach { fallback ->
                referenced += fallback.assetId
                val usage = ImageUsage.valueOf(fallback.usageType)
                add(assetsById.getValue(fallback.assetId).copy(
                    usageType = usage.name,
                    cropProfile = AssetResolver.profileFor(usage).name,
                    resolutionReason = "FALLBACK:${fallback.matcherType}",
                    unused = false
                ))
            }
            request.assetEntries.filter { it.id !in referenced }.sortedBy(AssetPreviewEntry::id).forEach { asset ->
                add(asset.copy(usageType = null, cropProfile = null, resolutionReason = "UNUSED", unused = true))
            }
        }
        return rows.sortedWith(compareBy<AssetPreviewEntry>(
            { it.category }, { it.usageType.orEmpty() }, { it.resolutionReason.orEmpty() }, { it.id }
        ))
    }

    private fun renderAssetPreview(reportPath: Path, output: Path) {
        val report = JsonParser.parse(readUtf8(reportPath)).asObject()
        val entries = (report.optional("previewRows") ?: report.value("assets")).asArray().values.map(JsonValue::asObject).map { value ->
            fun nullableString(name: String): String? = value.value(name).let { if (it == JsonNull) null else it.asString() }
            fun nullableInt(name: String): Int? = value.value(name).let { if (it == JsonNull) null else it.asInt() }
            AssetPreviewEntry(
                id = value.value("id").asString(),
                category = value.value("category").asString(),
                relativePath = value.value("relativePath").asString(),
                width = value.value("width").asInt(),
                height = value.value("height").asInt(),
                byteSize = value.value("byteSize").asLong(),
                sha256 = value.value("sha256").asString(),
                licenseId = value.value("licenseId").asString(),
                mimeType = value.value("mimeType").asString(),
                alphaMode = value.value("alphaMode").asString(),
                colorSpace = value.value("colorSpace").asString(),
                poolVersion = value.value("poolVersion").asString(),
                validationStatus = value.value("validationStatus").asString(),
                focalXppm = nullableInt("focalXppm"),
                focalYppm = nullableInt("focalYppm"),
                usageType = nullableString("usageType"),
                cropProfile = nullableString("cropProfile"),
                resolutionReason = nullableString("resolutionReason"),
                sourceDisplayName = nullableString("sourceDisplayName"),
                effectiveDisplayName = nullableString("effectiveDisplayName"),
                missing = (value.value("missing") as JsonBoolean).value,
                duplicate = (value.value("duplicate") as JsonBoolean).value,
                unused = (value.value("unused") as JsonBoolean).value
            )
        }
        AssetPreviewRenderer.render(entries, output)
    }

    private fun stableReportDiagnostics(diagnostics: List<ReportDiagnostic>): List<ReportDiagnostic> = diagnostics.sortedWith(
        compareBy<ReportDiagnostic>({ if (it.severity == "ERROR") 0 else 1 }, { it.code }, { it.sourceId.orEmpty() }, { it.sourceFile.orEmpty() }, { it.row ?: -1 }, { it.column ?: -1 }, { it.field.orEmpty() }, { it.messageKey }, { it.actual.orEmpty() })
    )

    private fun writeFailureReport(outputRoot: Path, diagnostics: List<ReportDiagnostic>, errorCode: String) {
        val stable = stableReportDiagnostics(diagnostics)
        val report = JsonObject(mapOf(
            "diagnostics" to JsonArray(stable.map(::reportDiagnosticJson)),
            "errorCode" to JsonString(errorCode),
            "status" to JsonString("FAIL")
        ))
        val rendered = report.render() + "\n"
        val directory = outputRoot.resolve("failed-validation").resolve(CanonicalSourceConverter.sha256(rendered.toByteArray(StandardCharsets.UTF_8)).take(16))
        try {
            Files.createDirectories(directory)
            Files.writeString(directory.resolve("validation-report.json"), rendered, StandardCharsets.UTF_8)
            Files.writeString(directory.resolve("validation-report.md"), renderMarkdownReport(directory.resolve("validation-report.json")), StandardCharsets.UTF_8)
        } catch (error: Exception) {
            throw ContentBuildException(
                "BUILD_IO",
                "failed to persist validation report: ${error.message ?: error::class.java.simpleName}"
            )
        }
    }

    private fun loadSource(sourceRoot: Path): LoadedSource {
        val manifestPath = sourceRoot.resolve("catalog-manifest.json")
        require(Files.isRegularFile(manifestPath)) { "canonical source manifest is missing: $manifestPath" }
        val manifestSourceFile = "catalog-manifest.json"
        val manifest = parseSourceJson(readSourceUtf8(manifestPath, "catalog-manifest", manifestSourceFile), manifestSourceFile, "catalog-manifest").asObject()
        requireSourceKeys(manifest, setOf("files", "manifestVersion", "sourceVersion"), manifestSourceFile, 1, "manifest")
        require(manifest.value("manifestVersion").asInt() == 1) { "unsupported canonical source format" }
        val sourceVersion = manifest.value("sourceVersion").asString()
        require(sourceVersion.isNotBlank() && sourceVersion == Normalizer.normalize(sourceVersion, Normalizer.Form.NFC)) { "sourceVersion must be non-blank NFC" }
        val records = mutableListOf<ContentSourceTemplate>()
        val files = manifest.value("files").asArray().values.map(JsonValue::asObject)
        var totalRows = 0
        files.sortedBy { it.value("path").asString() }.forEachIndexed { fileIndex, fileEntry ->
            requireSourceKeys(fileEntry, setOf("exactFileSha256", "kind", "path", "rowCount", "schemaVersion"), manifestSourceFile, fileIndex + 1, "manifest.files")
            val relative = fileEntry.value("path").asString()
            require(fileEntry.value("schemaVersion").asInt() == 1) { "unsupported source schema: $relative" }
            val declaredRows = fileEntry.value("rowCount").asInt()
            if (declaredRows > 100_000) throw ContentBuildException("SOURCE_INVALID", "row limit exceeded: $relative")
            val path = Path.of(relative).normalize()
            if (path.isAbsolute || path.startsWith("..")) throw ContentBuildException("SOURCE_PATH_TRAVERSAL", relative)
            val file = sourceRoot.resolve(path).normalize()
            if (!file.startsWith(sourceRoot.normalize()) || !Files.isRegularFile(file)) throw ContentBuildException("SOURCE_FILE_MISSING", relative)
            if (Files.size(file) > 64L * 1024 * 1024) throw ContentBuildException("SOURCE_INVALID", "file size limit exceeded: $relative")
            val bytes = Files.readAllBytes(file)
            val hash = CanonicalSourceConverter.sha256(bytes)
            require(hash == fileEntry.value("exactFileSha256").asString()) { "source file hash mismatch: $relative" }
            if (relative.endsWith(".csv", ignoreCase = true)) {
                val csvRecords = parseCanonicalSourceCsv(readSourceUtf8(file, "canonical-csv", relative), relative, fileEntry.value("kind").asString())
                require(csvRecords.size == declaredRows) { "source row count mismatch: $relative" }
                totalRows += csvRecords.size
                if (totalRows > 1_000_000) throw ContentBuildException("SOURCE_INVALID", "total row limit exceeded")
                records += csvRecords
                return@forEachIndexed
            }
            val document = parseSourceJson(readSourceUtf8(file, "canonical-document", relative), relative, "canonical-document").asObject()
            requireSourceKeys(document, setOf("definitionVersion", "kind", "records", "schemaVersion", "sourceVersion"), relative, 1, "document")
            require(document.value("sourceVersion").asString() == sourceVersion) { "sourceVersion mismatch: $relative" }
            require(document.value("schemaVersion").asInt() == fileEntry.value("schemaVersion").asInt()) { "schemaVersion mismatch: $relative" }
            val kind = document.value("kind").asString()
            require(kind == fileEntry.value("kind").asString()) { "source file kind mismatch: $relative" }
            val sourceRecords = document.value("records").asArray().values.map(JsonValue::asObject)
            val definitionVersion = document.value("definitionVersion").asInt()
            require(definitionVersion == 1) { "unsupported definitionVersion: $relative" }
            if (sourceRecords.size > 100_000) throw ContentBuildException("SOURCE_INVALID", "row limit exceeded: $relative")
            require(sourceRecords.size == declaredRows) { "source row count mismatch: $relative" }
            totalRows += sourceRecords.size
            if (totalRows > 1_000_000) throw ContentBuildException("SOURCE_INVALID", "total row limit exceeded")
            sourceRecords.forEachIndexed { recordIndex, record ->
                val recordId = (record.optional("id") as? JsonString)?.value
                requireSourceKeys(record, setOf("definition", "definitionVersion", "displayNameOverride", "enabled", "grade", "id", "kind", "minLevel", "provenance", "sourceDisplayName", "tags"), relative, recordIndex + 1, "record", recordId)
                require(record.value("definitionVersion").asInt() == definitionVersion) { "definitionVersion mismatch: $relative" }
                val provenance = record.value("provenance").asObject()
                requireSourceKeys(provenance, setOf("rawRow", "row", "section"), relative, recordIndex + 1, "provenance", recordId)
                records += ContentSourceTemplate(
                    location = com.imsi.mud.content.SourceLocation(relative, provenance.value("row").asInt()),
                    id = record.value("id").asString(),
                    kind = kind,
                    sourceDisplayName = record.value("sourceDisplayName").asString(),
                    definitionVersion = record.value("definitionVersion").asInt(),
                    definitionJson = record.value("definition").render(),
                    displayNameOverride = record.value("displayNameOverride").asNullableString(),
                    grade = record.value("grade").asNullableString(),
                    minLevel = record.value("minLevel").let { if (it == JsonNull) null else it.asInt() },
                    tags = record.value("tags").asArray().values.map(JsonValue::asString),
                    enabled = record.value("enabled").asBoolean()
                )
            }
        }
        val sourceCanonical = JsonObject(mapOf(
            "files" to JsonArray(files.sortedBy { it.value("path").asString() }.map { entry ->
                JsonObject(mapOf(
                    "exactFileSha256" to entry.value("exactFileSha256"),
                    "kind" to entry.value("kind"),
                    "path" to entry.value("path"),
                    "rowCount" to entry.value("rowCount"),
                    "schemaVersion" to entry.value("schemaVersion")
                ))
            }),
            "manifestVersion" to JsonNumber(java.math.BigDecimal.ONE),
            "sourceVersion" to JsonString(sourceVersion)
        )).render()
        return LoadedSource(records, CanonicalSourceConverter.sha256(sourceCanonical.toByteArray(StandardCharsets.UTF_8)), files.size)
    }

    private fun readSourceUtf8(path: Path, field: String, sourceFile: String): String = try {
        readUtf8(path)
    } catch (error: Exception) {
        val actual = error.message ?: error::class.java.simpleName
        throw SourceParseException(SourceParseDiagnostic(
            code = "SOURCE_ENCODING_INVALID",
            messageKey = "SOURCE_ENCODING_INVALID",
            message = actual,
            sourceId = null,
            sourceFile = sourceFile,
            row = 1,
            column = 1,
            field = field,
            expected = "valid UTF-8",
            actual = actual
        ))
    }

    private fun requireSourceKeys(
        value: JsonObject,
        allowed: Set<String>,
        sourceFile: String,
        row: Int,
        field: String,
        sourceId: String? = null
    ) {
        val unknown = value.values.keys - allowed
        if (unknown.isNotEmpty()) {
            val actual = unknown.sorted().joinToString(",")
            throw SourceParseException(SourceParseDiagnostic(
                code = "SOURCE_UNKNOWN_FIELD",
                messageKey = "SOURCE_UNKNOWN_FIELD",
                message = "$field contains unknown fields: $actual",
                sourceId = sourceId,
                sourceFile = sourceFile,
                row = row,
                column = 1,
                field = field,
                expected = allowed.sorted().joinToString(","),
                actual = actual
            ))
        }
        val missing = allowed - value.values.keys
        if (missing.isNotEmpty()) {
            val actual = missing.sorted().joinToString(",")
            throw SourceParseException(SourceParseDiagnostic(
                code = "SOURCE_INVALID",
                messageKey = "SOURCE_INVALID",
                message = "$field is missing required fields: $actual",
                sourceId = sourceId,
                sourceFile = sourceFile,
                row = row,
                column = 1,
                field = field,
                expected = allowed.sorted().joinToString(","),
                actual = actual
            ))
        }
    }

    private fun validateDefinitionShapes(templates: List<com.imsi.mud.content.ContentTemplate>) {
        val requiredKeys = mapOf(
            "WPN" to setOf("name", "weaponType", "grade", "recommendedLevel", "physicalPower", "magicPower", "tags", "unresolved"),
            "ARM" to setOf("name", "slot", "grade", "recommendedLevel", "defense", "magicDefense", "weightClass", "unresolved"),
            "ACC" to setOf("name", "slot", "grade", "recommendedLevel", "effects", "unresolved"),
            "ITM" to setOf("name", "itemCategory", "grade", "primaryUseCategory", "effects", "unresolved"),
            "MON" to setOf("name", "family", "levelBand", "threatCoefficientBp", "role", "attributes", "unresolved"),
            "BOS" to setOf("name", "family", "recommendedLevel", "dungeonRank", "archetypeTags", "phaseCount", "coreMechanic", "rewardTags", "unresolved"),
            "EPRE" to setOf("position", "name", "grade", "effects", "allowedEquipmentScope", "unresolved"),
            "ESUF" to setOf("position", "name", "grade", "effects", "allowedEquipmentScope", "unresolved"),
            "MPRE" to setOf("position", "name", "grade", "effects", "threatMultiplierBp", "unresolved"),
            "MSUF" to setOf("position", "name", "grade", "effects", "threatMultiplierBp", "unresolved"),
            "SET" to setOf("name", "grade", "tiers", "unresolved"),
            "SKL" to setOf("name", "skillGroup", "skillType", "grade", "classScope", "resourceCost", "cooldownCombatMillis", "effects", "tags", "unresolved"),
            "SPRE" to setOf("position", "name", "grade", "modificationKind", "amount", "applicability", "unresolved"),
            "SSUF" to setOf("position", "name", "grade", "modificationKind", "amount", "applicability", "unresolved"),
            "CTR" to setOf("name", "category", "contractRank", "duration", "objective", "rewards", "featureTags", "unresolved"),
            "REL" to setOf("name", "category", "eligibilityAST", "relationshipEffects", "followup", "unresolved"),
            "EVT" to setOf("category", "name", "eligibilityAST", "choices", "effects", "unresolved"),
            "DNG-EVT" to setOf("category", "name", "eligibilityAST", "choices", "effects", "unresolved"),
            "CHAIN" to setOf("category", "name", "steps", "finalEffects", "unresolved"),
            "LEG" to setOf("name", "eligibilityAST", "scenario", "chronicleTags", "unresolved")
        )
        templates.forEach { template ->
            val definition = JsonParser.parse(template.definitionJson).asObject()
            val required = requiredKeys.getValue(template.kind.wireValue)
            val unknown = definition.values.keys - required
            val missing = required - definition.values.keys
            if (unknown.isNotEmpty() || missing.isNotEmpty()) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "${template.id.value}:definition keys unknown=${unknown.sorted()} missing=${missing.sorted()}")
            }
            val unresolved = definition.value("unresolved").asArray()
            unresolved.values.forEach { issueValue ->
                val issue = issueValue.asObject()
                issue.requireKeys(setOf("code", "field", "reason", "sourceText"), "${template.id.value}:unresolved")
                listOf("code", "field", "reason", "sourceText").forEach { issue.value(it).asString() }
            }
            fun requireJsonField(name: String, accepted: Set<Class<out JsonValue>>) {
                val value = definition.optional(name) ?: return
                if (accepted.none { it.isInstance(value) }) {
                    throw ContentBuildException("CONTENT_VALIDATION_FAILED", "${template.id.value}:$name must be typed, got ${value::class.simpleName}")
                }
            }
            when (template.kind.wireValue) {
                "SKL" -> {
                    val resource = definition.value("resourceCost").asObject()
                    if (resource.value("type").asString() !in setOf("NONE", "SINGLE", "MULTI", "UNRESOLVED")) {
                        throw ContentBuildException("CONTENT_VALIDATION_FAILED", "${template.id.value}:resourceCost type")
                    }
                    when (resource.value("type").asString()) {
                        "SINGLE" -> {
                            resource.requireKeys(setOf("type", "resource", "amount"), "${template.id.value}:resourceCost")
                            resource.value("resource").asString()
                            resource.value("amount").asLong()
                        }
                        "MULTI" -> {
                            resource.requireKeys(setOf("type", "costs"), "${template.id.value}:resourceCost")
                            resource.value("costs").asArray().values.forEach { costValue ->
                                val cost = costValue.asObject()
                                cost.requireKeys(setOf("resource", "amount"), "${template.id.value}:resourceCost.cost")
                                cost.value("resource").asString()
                                cost.value("amount").asLong()
                            }
                        }
                        "NONE" -> resource.requireKeys(setOf("type"), "${template.id.value}:resourceCost")
                        "UNRESOLVED" -> {
                            resource.requireKeys(setOf("type", "reason"), "${template.id.value}:resourceCost")
                            resource.value("reason").asString()
                        }
                    }
                }
                "SET" -> requireJsonField("tiers", setOf(JsonArray::class.java))
                "CTR" -> {
                    requireJsonField("duration", setOf(JsonNull::class.java, JsonObject::class.java))
                    requireJsonField("rewards", setOf(JsonArray::class.java))
                }
                "REL" -> {
                    requireJsonField("eligibilityAST", setOf(JsonNull::class.java, JsonObject::class.java))
                    requireJsonField("relationshipEffects", setOf(JsonArray::class.java))
                }
                "EVT", "DNG-EVT" -> {
                    requireJsonField("eligibilityAST", setOf(JsonNull::class.java, JsonObject::class.java))
                    requireJsonField("choices", setOf(JsonArray::class.java))
                    requireJsonField("effects", setOf(JsonArray::class.java))
                }
                "CHAIN" -> {
                    requireJsonField("steps", setOf(JsonArray::class.java))
                    requireJsonField("finalEffects", setOf(JsonArray::class.java))
                }
                "LEG" -> {
                    requireJsonField("eligibilityAST", setOf(JsonNull::class.java, JsonObject::class.java))
                    requireJsonField("chronicleTags", setOf(JsonArray::class.java))
                }
                "EPRE", "ESUF" -> requireJsonField("effects", setOf(JsonArray::class.java))
                "MPRE", "MSUF" -> requireJsonField("effects", setOf(JsonArray::class.java))
                "SPRE", "SSUF" -> {
                    requireJsonField("modificationKind", setOf(JsonNull::class.java, JsonObject::class.java))
                    requireJsonField("amount", setOf(JsonNull::class.java, JsonNumber::class.java, JsonObject::class.java))
                    requireJsonField("applicability", setOf(JsonNull::class.java, JsonObject::class.java))
                }
            }
            validateExecutableFields(template.id.value, definition)
        }
    }

    private fun validateExecutableFields(subject: String, definition: JsonObject) {
        val fields = setOf("effects", "tiers", "choices", "steps", "rewards", "eligibilityAST", "relationshipEffects", "finalEffects", "weaponEffects")
        fields.forEach { field ->
            val value = definition.optional(field) ?: return@forEach
            if (value == JsonNull) return@forEach
            if (field == "eligibilityAST" && value !is JsonObject) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "$subject:$field must be an AST object or null")
            }
            if (field != "eligibilityAST" && value !is JsonArray) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "$subject:$field must be an AST array")
            }
            validateAstNode(subject, field, value, field)
        }
    }

    private fun validateAstNode(subject: String, field: String, node: JsonValue, path: String) {
        when (node) {
            is JsonString -> throw ContentBuildException("CONTENT_VALIDATION_FAILED", "$subject:$field raw string AST node at $path")
            is JsonNumber, is JsonBoolean, JsonNull -> Unit
            is JsonArray -> node.values.forEachIndexed { index, child -> validateAstNode(subject, field, child, "$path[$index]") }
            is JsonObject -> {
                val allowed = setOf(
                    "amount", "condition", "count", "durationMs", "effects", "else", "kind", "level", "name",
                    "nodeType", "pieces", "refId", "steps", "target", "then", "type", "unit", "value"
                )
                val unknown = node.values.keys - allowed
                if (unknown.isNotEmpty()) throw ContentBuildException("CONTENT_VALIDATION_FAILED", "$subject:$field unknown AST keys ${unknown.sorted()}")
                if (node.optional("amount") is JsonNumber && node.optional("unit") !is JsonString) {
                    throw ContentBuildException("CONTENT_VALIDATION_FAILED", "$subject:$field numeric amount requires unit at $path")
                }
                if (node.optional("refId") is JsonString && node.value("refId").asString().isBlank()) {
                    throw ContentBuildException("CONTENT_VALIDATION_FAILED", "$subject:$field typed refId is blank at $path")
                }
                node.values.forEach { (key, child) ->
                    if (key == "unit" && child !is JsonString) throw ContentBuildException("CONTENT_VALIDATION_FAILED", "$subject:$field unit must be a string at $path")
                    validateAstNode(subject, field, child, "$path.$key")
                }
            }
        }
    }

    private fun validateProfileClosure(
        profile: String,
        templates: List<com.imsi.mud.content.ContentTemplate>,
        unresolvedTemplates: List<com.imsi.mud.content.ContentTemplate>,
        decodedReferences: Map<String, Set<String>>,
        recipeEdges: List<RecipeEdge>
    ) {
        val byId = templates.associateBy { it.id.value }
        val unresolvedIds = unresolvedTemplates.map { it.id.value }.toSet()
        val references = templates.associate { template ->
            template.id.value to (decodedReferences[template.id.value].orEmpty() + recipeEdges.filter { it.fromId == template.id.value }.map(RecipeEdge::toId))
        }
        fun reachableFrom(roots: Set<String>): Pair<Set<String>, Set<String>> {
            val visited = linkedSetOf<String>()
            val dangling = linkedSetOf<String>()
            val queue = ArrayDeque(roots.toList())
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (!visited.add(id)) continue
                val template = byId[id]
                if (template == null) {
                    dangling += id
                } else {
                    references[id].orEmpty().forEach(queue::addLast)
                }
            }
            return visited to dangling
        }
        val allIds = byId.keys
        val roots = templates.filter { it.enabled }.map { it.id.value }.toSet()
        val (reachable, reachableDangling) = reachableFrom(roots)
        if (profile == "FULL") {
            if (unresolvedIds.isNotEmpty()) throw ContentBuildException("UNRESOLVED_CONTENT", "${unresolvedIds.size} unresolved rows remain for FULL")
            val (_, allDangling) = reachableFrom(allIds)
            if (allDangling.isNotEmpty()) throw ContentBuildException("DANGLING_REFERENCE", allDangling.sorted().joinToString(","))
        } else if (reachable.intersect(unresolvedIds).isNotEmpty()) {
            throw ContentBuildException("PROFILE_INVALID", "reachable unresolved rows: ${reachable.intersect(unresolvedIds).sorted().joinToString(",")}")
        } else if (reachableDangling.isNotEmpty()) {
            throw ContentBuildException("DANGLING_REFERENCE", reachableDangling.sorted().joinToString(","))
        }
    }

    private fun referencesIn(value: JsonValue): Set<String> {
        val refs = linkedSetOf<String>()
        val directKeys = setOf("refId", "templateId", "contentId", "targetId", "referenceId")
        val collectionKeys = setOf("contentRefs", "templateRefs", "contentIds", "dependsOn")
        fun visit(node: JsonValue, key: String? = null) {
            when (node) {
                is JsonObject -> node.values.forEach { (childKey, child) ->
                    if (childKey in directKeys && child is JsonString) refs += child.value
                    else if (childKey in collectionKeys || childKey.endsWith("References") || childKey.endsWith("RecipeRefs")) visit(child, childKey)
                    else visit(child, childKey)
                }
                is JsonArray -> node.values.forEach { child -> if ((key in collectionKeys || key?.endsWith("References") == true || key?.endsWith("RecipeRefs") == true) && child is JsonString) refs += child.value else visit(child, key) }
                else -> Unit
            }
        }
        visit(value)
        return refs
    }

    private fun prepareStaging(request: ContentBuildRequest): Path {
        val outputRoot = request.outputRoot
        val staging = outputRoot.resolve("staging")
        if (Files.exists(staging)) {
            val nonEmpty = Files.list(staging).use { it.findAny().isPresent }
            if (nonEmpty) {
                val marker = staging.resolve("build-staging.marker")
                if (!Files.isRegularFile(marker)) throw ContentBuildException("STAGING_NOT_EMPTY", staging.toString())
                val orphanRoot = outputRoot.resolve("orphan")
                Files.createDirectories(orphanRoot)
                val orphanId = CanonicalSourceConverter.sha256(Files.readAllBytes(marker)).take(16)
                var orphan = orphanRoot.resolve("staging-$orphanId")
                var suffix = 1
                while (Files.exists(orphan)) orphan = orphanRoot.resolve("staging-$orphanId-${suffix++}")
                try {
                    Files.move(staging, orphan, StandardCopyOption.ATOMIC_MOVE)
                } catch (error: java.nio.file.AtomicMoveNotSupportedException) {
                    throw ContentBuildException("PUBLISH_ATOMIC_UNSUPPORTED", error.message ?: "atomic orphan quarantine unsupported")
                }
                Files.writeString(orphan.resolve("orphan-diagnosis.json"), "{\"code\":\"ORPHAN_STAGING_QUARANTINED\",\"action\":\"quarantine-and-rebuild\",\"marker\":\"${orphan.fileName}\"}\n")
            }
        }
        Files.createDirectories(staging)
        Files.writeString(staging.resolve("build-staging.marker"), "{\"version\":1,\"contentVersion\":\"${request.contentVersion}\",\"balanceVersion\":\"${request.balanceVersion}\"}\n")
        return staging
    }

    private fun validateBundleInputs(
        request: ContentBuildRequest,
        templates: List<com.imsi.mud.content.ContentTemplate>,
        assets: List<AssetPreviewEntry>
    ): ResolvedAliasInputs {
        val templateIds = templates.map { it.id.value }.toSet()
        val assetIds = assets.map(AssetPreviewEntry::id).toSet()
        val registryById = request.licenseRegistry.groupBy(LicenseRegistryEntry::id)
        if (registryById.any { it.value.size > 1 }) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "duplicate license registry id")
        }
        assets.forEach { asset ->
            val license = registryById[asset.licenseId]?.singleOrNull()
                ?: throw ContentBuildException("ASSET_LICENSE_UNAPPROVED", asset.id)
            if (license.approvalStatus != "APPROVED") throw ContentBuildException("ASSET_LICENSE_UNAPPROVED", asset.id)
            if ("ANDROID_APP" !in license.distributionScopes) throw ContentBuildException("ASSET_LICENSE_SCOPE_BLOCKED", asset.id)
        }
        if (request.aliases.map(ContentAliasEntry::id).size != request.aliases.map(ContentAliasEntry::id).toSet().size) {
            throw ContentBuildException("CONTENT_VALIDATION_FAILED", "duplicate content alias id")
        }
        if (request.aliases.map(ContentAliasEntry::oldId).size != request.aliases.map(ContentAliasEntry::oldId).toSet().size) {
            throw ContentBuildException("CONTENT_ALIAS_INVALID", "duplicate content alias old id")
        }
        val aliasesByOldId = request.aliases.associateBy(ContentAliasEntry::oldId)
        val knownKinds = templates.map { it.kind.wireValue }.toSet()
        request.aliases.forEach { alias ->
            if (alias.id.isBlank() || alias.oldId.isBlank() || alias.reason.isBlank() || alias.oldId == alias.newId) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "invalid content alias ${alias.id}")
            }
            if (alias.policy !in setOf("REMAP", "TOMBSTONE") || (alias.policy == "TOMBSTONE" && alias.newId != null)) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "invalid alias policy: ${alias.id}")
            }
            if (alias.oldId in templateIds) throw ContentBuildException("CONTENT_VALIDATION_FAILED", "alias old id is an active content id: ${alias.oldId}")
            if (alias.oldKind !in knownKinds) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "unknown alias old kind: ${alias.oldKind}")
            }
            if (alias.provenanceSection <= 0 || alias.provenanceRow <= 0 || alias.approvalRevision.isBlank()) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "invalid alias provenance or approval revision: ${alias.id}")
            }
        }
        val resolving = mutableSetOf<String>()
        fun terminal(oldId: String): String? {
            if (oldId in templateIds) return oldId
            val alias = aliasesByOldId[oldId] ?: throw ContentBuildException("DANGLING_REFERENCE", "alias target missing: $oldId")
            if (!resolving.add(oldId)) throw ContentBuildException("CONTENT_ALIAS_CYCLE", "alias cycle includes: $oldId")
            val result = if (alias.policy == "TOMBSTONE") null else {
                val target = alias.newId ?: throw ContentBuildException("CONTENT_VALIDATION_FAILED", "REMAP alias target is null: ${alias.id}")
                terminal(target) ?: throw ContentBuildException("CONTENT_VALIDATION_FAILED", "REMAP alias targets a tombstone: ${alias.id}")
            }
            resolving.remove(oldId)
            return result
        }
        val resolvedAliases = request.aliases.map { alias ->
            val resolved = if (alias.policy == "TOMBSTONE") null else terminal(alias.newId!!)
            if (resolved != null) {
                val targetKind = templates.first { it.id.value == resolved }.kind.wireValue
                if (targetKind != alias.oldKind) throw ContentBuildException("CONTENT_VALIDATION_FAILED", "alias cross-kind remap: ${alias.id}")
            }
            alias.copy(newId = resolved)
        }
        val resolutions = request.aliases.zip(resolvedAliases).map { (source, resolved) -> AliasResolution(resolved, source.newId) }
        val usages = setOf("LIST_FACE", "DETAIL_PORTRAIT", "DIALOG_PORTRAIT", "BATTLE_TOKEN", "CHRONICLE_THUMB", "ROOM_BACKGROUND", "KEY_ART", "ICON", "EMBLEM", "EVENT_ART")
        val matchers = setOf("SEX", "ARCHETYPE", "CLASS", "MONSTER_FAMILY", "REGION", "ROOM_THEME", "ITEM_TYPE", "FACILITY_CATEGORY", "CATEGORY_DEFAULT", "GLOBAL_DEFAULT")
        if (request.assetBindings.map(AssetBindingEntry::id).size != request.assetBindings.map(AssetBindingEntry::id).toSet().size ||
            request.assetFallbacks.map(AssetFallbackEntry::id).size != request.assetFallbacks.map(AssetFallbackEntry::id).toSet().size) {
            throw ContentBuildException("CONTENT_VALIDATION_FAILED", "duplicate asset binding or fallback id")
        }
        request.assetBindings.forEach { binding ->
            if (binding.templateId !in templateIds || binding.assetId !in assetIds || binding.usageType !in usages || binding.priority < 0) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "invalid asset binding ${binding.id}")
            }
            val category = assets.first { it.id == binding.assetId }.category
            if (categoryForUsage(binding.usageType) != category) throw ContentBuildException("CONTENT_VALIDATION_FAILED", "asset binding usage/category mismatch: ${binding.id}")
        }
        request.assetFallbacks.forEach { fallback ->
            val validValue = if (fallback.matcherType in setOf("CATEGORY_DEFAULT", "GLOBAL_DEFAULT")) fallback.matcherValue == null else !fallback.matcherValue.isNullOrBlank()
            if (fallback.assetId !in assetIds || fallback.usageType !in usages || fallback.matcherType !in matchers || !validValue || fallback.priority < 0) {
                throw ContentBuildException("CONTENT_VALIDATION_FAILED", "invalid asset fallback ${fallback.id}")
            }
            val category = assets.first { it.id == fallback.assetId }.category
            if (categoryForUsage(fallback.usageType) != category) throw ContentBuildException("CONTENT_VALIDATION_FAILED", "asset fallback usage/category mismatch: ${fallback.id}")
        }
        return ResolvedAliasInputs(resolvedAliases, resolutions)
    }

    private fun categoryForUsage(usageType: String): String = when (usageType) {
        "LIST_FACE", "DETAIL_PORTRAIT", "DIALOG_PORTRAIT", "BATTLE_TOKEN", "CHRONICLE_THUMB" -> "PORTRAIT"
        "ROOM_BACKGROUND" -> "BACKGROUND"
        "ICON" -> "ICON"
        "EMBLEM" -> "EMBLEM"
        "EVENT_ART" -> "EVENT_ART"
        "KEY_ART" -> "KEY_ART"
        else -> ""
    }

    private fun copyAssets(request: ContentBuildRequest, targetRoot: Path) {
        if (request.assetEntries.isEmpty()) return
        val root = request.assetRoot ?: throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset root is required")
        val rootAttributes = try {
            Files.readAttributes(root, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (error: Exception) {
            throw ContentBuildException("INVALID_ASSET_PATH", "asset root could not be inspected: $root")
        }
        if (Files.isSymbolicLink(root) || !rootAttributes.isDirectory) {
            throw ContentBuildException("INVALID_ASSET_PATH", "asset root must be a real directory: $root")
        }
        val rootRealPath = try {
            root.toRealPath()
        } catch (error: Exception) {
            throw ContentBuildException("INVALID_ASSET_PATH", "asset root could not be resolved: $root")
        }
        request.assetEntries.sortedBy(AssetPreviewEntry::relativePath).forEachIndexed { index, entry ->
            val source = root.resolve(entry.relativePath).normalize()
            val target = targetRoot.resolve(entry.relativePath).normalize()
            Files.createDirectories(target.parent)
            val sourceIdentity = copyAssetFromStableHandle(
                root, source, target, rootRealPath, rootAttributes.fileKey(), entry, request.fault,
            )
            if (index == 0 && request.fault == BuildFault.MUTATE_ASSET_SOURCE_AFTER_COPY) {
                Files.write(source, Files.readAllBytes(source) + byteArrayOf(0x00))
            }
            verifyAssetSourceIdentity(source, entry, sourceIdentity, rootRealPath)
        }
        if (request.fault == BuildFault.MUTATE_ASSET_AFTER_COPY) {
            val target = targetRoot.resolve(request.assetEntries.sortedBy(AssetPreviewEntry::relativePath).first().relativePath).normalize()
            Files.write(target, Files.readAllBytes(target) + byteArrayOf(0x00))
        }
        val copiedDiagnostics = AssetValidator.validate(
            request.assetEntries,
            targetRoot,
            request.approvedLicenseIds,
            request.licenseRegistry.associate { it.id to it.distributionScopes }
        )
        if (copiedDiagnostics.isNotEmpty()) {
            throw assetValidationFailure(copiedDiagnostics)
        }
    }

    private fun copyAssetFromStableHandle(
        root: Path,
        source: Path,
        target: Path,
        rootRealPath: Path,
        rootFileKey: Any?,
        entry: AssetPreviewEntry,
        fault: BuildFault?,
    ): AssetSourceIdentity {
        if (Files.isSymbolicLink(source)) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset source is a symbolic link: $source")
        }
        val attributes = try {
            Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (error: Exception) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset source could not be inspected: $source")
        }
        if (!attributes.isRegularFile) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset source is not a regular file: $source")
        }
        val identity = AssetSourceIdentity(
            fileKey = attributes.fileKey(),
            realPath = source.toRealPath(),
            size = attributes.size(),
            lastModifiedMillis = attributes.lastModifiedTime().toMillis(),
        )
        if (!identity.realPath.startsWith(rootRealPath)) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset source escapes the resolved root: $source")
        }
        if (fault == BuildFault.SWAP_ASSET_ROOT_BEFORE_HANDLE) {
            swapAssetRootToExternalLink(root)
        } else if (fault == BuildFault.SWAP_ASSET_PARENT_BEFORE_HANDLE) {
            swapAssetParentToExternalLink(root, source.parent)
        }
        try {
            if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
                copyAssetWithWindowsHandle(source, target, rootRealPath, entry)
            } else {
                copyAssetWithSecureDirectory(root, target, rootFileKey, entry)
            }
        } catch (error: ContentBuildException) {
            throw error
        } catch (error: Exception) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "stable asset copy failed for $source: ${error.message}")
        }
        return identity
    }

    private fun copyAssetWithWindowsHandle(source: Path, target: Path, rootRealPath: Path, entry: AssetPreviewEntry) {
        val handle = Kernel32.INSTANCE.CreateFile(
            source.toAbsolutePath().toString(),
            WinNT.GENERIC_READ,
            WinNT.FILE_SHARE_READ,
            null,
            WINDOWS_OPEN_EXISTING,
            0,
            null,
        )
        if (handle == WinBase.INVALID_HANDLE_VALUE) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "CreateFile failed for asset $source: error=${Kernel32.INSTANCE.GetLastError()}")
        }
        var failure: Throwable? = null
        try {
            val openedPath = finalWindowsPath(handle)
            if (!openedPath.startsWith(rootRealPath)) {
                throw ContentBuildException("INVALID_ASSET_PATH", "opened asset handle escapes the pinned root: $openedPath")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    val read = IntByReference()
                    if (!Kernel32.INSTANCE.ReadFile(handle, bytes, bytes.size, read, null)) {
                        throw ContentBuildException("ASSET_VALIDATION_FAILED", "ReadFile failed for asset $source: error=${Kernel32.INSTANCE.GetLastError()}")
                    }
                    val count = read.value
                    if (count == 0) break
                    digest.update(bytes, 0, count)
                    total += count
                    val buffer = java.nio.ByteBuffer.wrap(bytes, 0, count)
                    while (buffer.hasRemaining()) output.write(buffer)
                }
                output.force(true)
            }
            verifyOpenedAsset(entry, total, digest.digest())
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            if (!Kernel32.INSTANCE.CloseHandle(handle) && failure == null) {
                throw ContentBuildException("ASSET_VALIDATION_FAILED", "CloseHandle failed for asset $source: error=${Kernel32.INSTANCE.GetLastError()}")
            }
        }
    }

    private fun finalWindowsPath(handle: WinNT.HANDLE): Path {
        val buffer = CharArray(32_768)
        val length = windowsFinalPathApi.GetFinalPathNameByHandleW(handle, buffer, buffer.size, 0)
        if (length <= 0 || length >= buffer.size) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "GetFinalPathNameByHandle failed: error=${Kernel32.INSTANCE.GetLastError()}")
        }
        val raw = String(buffer, 0, length)
        val dosPath = when {
            raw.startsWith("\\\\?\\UNC\\", ignoreCase = true) -> "\\\\" + raw.substring(8)
            raw.startsWith("\\\\?\\") -> raw.substring(4)
            else -> raw
        }
        return Path.of(dosPath).toRealPath()
    }

    private fun copyAssetWithSecureDirectory(root: Path, target: Path, rootFileKey: Any?, entry: AssetPreviewEntry) {
        val rootDirectory = Files.newDirectoryStream(root)
        if (rootDirectory !is java.nio.file.SecureDirectoryStream<*>) {
            rootDirectory.close()
            throw ContentBuildException("INVALID_ASSET_PATH", "secure no-follow directory traversal is unavailable for $root")
        }
        @Suppress("UNCHECKED_CAST")
        val secureRoot = rootDirectory as java.nio.file.SecureDirectoryStream<Path>
        val openedRootFileKey = try {
            secureRoot.getFileAttributeView(BasicFileAttributeView::class.java).readAttributes().fileKey()
        } catch (error: Exception) {
            rootDirectory.close()
            throw ContentBuildException("INVALID_ASSET_PATH", "opened asset root identity is unavailable: $root")
        }
        if (rootFileKey == null || openedRootFileKey == null || rootFileKey != openedRootFileKey) {
            rootDirectory.close()
            throw ContentBuildException("INVALID_ASSET_PATH", "asset root changed before secure traversal: $root")
        }
        var current = secureRoot
        val openedDirectories = mutableListOf<DirectoryStream<Path>>(rootDirectory)
        try {
            val segments = entry.relativePath.split('/')
            segments.dropLast(1).forEach { segment ->
                val child = current.newDirectoryStream(Path.of(segment), LinkOption.NOFOLLOW_LINKS)
                openedDirectories += child
                current = child
            }
            val leaf = Path.of(segments.last())
            val attributes = current.getFileAttributeView(leaf, BasicFileAttributeView::class.java, LinkOption.NOFOLLOW_LINKS)
                .readAttributes()
            if (!attributes.isRegularFile) {
                throw ContentBuildException("INVALID_ASSET_PATH", "asset leaf is not a regular file: ${entry.relativePath}")
            }
            current.newByteChannel(leaf, setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use { input ->
                copyOpenedAsset(input, target, entry)
            }
        } finally {
            openedDirectories.asReversed().forEach { runCatching { it.close() } }
        }
    }

    private fun copyOpenedAsset(input: SeekableByteChannel, target: Path, entry: AssetPreviewEntry) {
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileChannel.open(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
            val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
            while (true) {
                buffer.clear()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                total += count
                digest.update(buffer.array(), 0, count)
                buffer.flip()
                while (buffer.hasRemaining()) output.write(buffer)
            }
            output.force(true)
        }
        verifyOpenedAsset(entry, total, digest.digest())
    }

    private fun verifyOpenedAsset(entry: AssetPreviewEntry, size: Long, digest: ByteArray) {
        val hash = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        if (size != entry.byteSize || hash != entry.sha256) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "opened asset bytes do not match the manifest: ${entry.id}")
        }
    }

    private fun verifyAssetSourceIdentity(source: Path, entry: AssetPreviewEntry, before: AssetSourceIdentity, rootRealPath: Path) {
        val attributes = try {
            Files.readAttributes(source, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        } catch (error: Exception) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset source disappeared after copy: $source")
        }
        val realPath = runCatching { source.toRealPath() }.getOrNull()
        val fileKeyChanged = before.fileKey != null && attributes.fileKey() != before.fileKey
        if (
            attributes.isSymbolicLink || !attributes.isRegularFile || attributes.size() != entry.byteSize ||
            attributes.size() != before.size || attributes.lastModifiedTime().toMillis() != before.lastModifiedMillis ||
            realPath != before.realPath || !realPath.startsWith(rootRealPath) || fileKeyChanged
        ) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset source identity changed after copy: $source")
        }
        val actualHash = try {
            FileChannel.open(source, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use { channel ->
                val bytes = ByteArray(channel.size().toInt())
                val buffer = java.nio.ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) break
                }
                CanonicalSourceConverter.sha256(bytes)
            }
        } catch (error: Exception) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset source could not be reverified: $source")
        }
        if (actualHash != entry.sha256) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "asset source changed after copy: ${entry.id}")
        }
    }

    private fun swapAssetParentToExternalLink(root: Path, parent: Path) {
        val external = root.parent.resolve("${root.fileName}-external-parent")
        Files.move(parent, external, StandardCopyOption.ATOMIC_MOVE)
        val linked = runCatching {
            Files.createSymbolicLink(parent, external)
            true
        }.getOrElse {
            if (!System.getProperty("os.name").contains("windows", ignoreCase = true)) return@getOrElse false
            val process = ProcessBuilder("cmd.exe", "/c", "mklink", "/J", parent.toString(), external.toString())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText()
            process.waitFor() == 0
        }
        if (!linked) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "could not create the asset-parent swap fixture")
        }
    }

    private fun swapAssetRootToExternalLink(root: Path) {
        val external = root.parent.resolve("${root.fileName}-external-root")
        val original = root.parent.resolve("${root.fileName}-original-root")
        Files.walk(root).use { paths ->
            paths.forEach { path ->
                val target = external.resolve(root.relativize(path).toString())
                if (Files.isDirectory(path)) Files.createDirectories(target)
                else {
                    Files.createDirectories(target.parent)
                    Files.copy(path, target)
                }
            }
        }
        Files.move(root, original, StandardCopyOption.ATOMIC_MOVE)
        val linked = runCatching {
            Files.createSymbolicLink(root, external)
            true
        }.getOrElse {
            if (!System.getProperty("os.name").contains("windows", ignoreCase = true)) return@getOrElse false
            val process = ProcessBuilder("cmd.exe", "/c", "mklink", "/J", root.toString(), external.toString())
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText()
            process.waitFor() == 0
        }
        if (!linked) {
            throw ContentBuildException("ASSET_VALIDATION_FAILED", "could not create the asset-root swap fixture")
        }
    }

    private fun writeDatabase(
        request: ContentBuildRequest,
        db: Path,
        templates: List<com.imsi.mud.content.ContentTemplate>,
        assets: List<AssetPreviewEntry>,
        sourceHash: String,
        logicalContentHash: String
    ) {
        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys=ON")
                statement.executeQuery("PRAGMA foreign_keys").use { result ->
                    if (!result.next() || result.getInt(1) != 1) throw ContentBuildException("PRAGMA_INVALID", "foreign_keys pragma was not enabled")
                }
                statement.execute(if (request.fault == BuildFault.WAL_REQUEST) "PRAGMA journal_mode=WAL" else "PRAGMA journal_mode=DELETE")
                statement.executeQuery("PRAGMA journal_mode").use { result ->
                    if (!result.next() || !result.getString(1).equals("delete", ignoreCase = true)) throw ContentBuildException("PRAGMA_INVALID", "journal_mode pragma was not DELETE")
                }
            }
            connection.autoCommit = false
            try {
                executeSqlScript(connection, Files.readString(request.ddlPath, StandardCharsets.UTF_8))
                connection.prepareStatement(
                    "INSERT INTO content_manifest(id,content_version,balance_version,schema_version,source_hash,bundle_hash,profile) VALUES(?,?,?,?,?,?,?)"
                ).use { statement ->
                    statement.setString(1, "CONTENT-MANIFEST")
                    statement.setString(2, request.contentVersion)
                    statement.setString(3, request.balanceVersion)
                    statement.setInt(4, 1)
                    statement.setString(5, sourceHash)
                    statement.setString(6, logicalContentHash)
                    statement.setString(7, request.profile)
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO content_template(id,kind,source_display_name,display_name_override,display_name,grade,min_level,tags_json,enabled,definition_json,definition_version) VALUES(?,?,?,?,?,?,?,?,?,?,?)"
                ).use { statement ->
                    templates.sortedBy { it.id.value }.forEach { template ->
                        val displayNameOverride = template.displayNameOverride
                        val grade = template.grade
                        val minLevel = template.minLevel
                        statement.setString(1, template.id.value)
                        statement.setString(2, template.kind.wireValue)
                        statement.setString(3, template.sourceDisplayName)
                        if (displayNameOverride != null) statement.setString(4, displayNameOverride) else statement.setNull(4, java.sql.Types.VARCHAR)
                        statement.setString(5, displayNameOverride ?: template.sourceDisplayName)
                        if (grade != null) statement.setString(6, grade) else statement.setNull(6, java.sql.Types.VARCHAR)
                        if (minLevel != null) statement.setInt(7, minLevel) else statement.setNull(7, java.sql.Types.INTEGER)
                        statement.setString(8, JsonArray(template.tags.map(::JsonString)).render())
                        statement.setInt(9, if (template.enabled) 1 else 0)
                        statement.setString(10, template.definitionJson)
                        statement.setInt(11, template.definitionVersion)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement(
                    "INSERT INTO asset_image(id,relative_path,category,width,height,byte_size,sha256,pool_version,focal_x_ppm,focal_y_ppm) VALUES(?,?,?,?,?,?,?,?,?,?)"
                ).use { statement ->
                    assets.sortedBy(AssetPreviewEntry::id).forEach { asset ->
                        statement.setString(1, asset.id)
                        statement.setString(2, asset.relativePath)
                        statement.setString(3, asset.category)
                        statement.setInt(4, asset.width)
                        statement.setInt(5, asset.height)
                        statement.setLong(6, asset.byteSize)
                        statement.setString(7, asset.sha256)
                        statement.setString(8, asset.poolVersion)
                        if (asset.focalXppm != null) statement.setInt(9, asset.focalXppm) else statement.setNull(9, java.sql.Types.INTEGER)
                        if (asset.focalYppm != null) statement.setInt(10, asset.focalYppm) else statement.setNull(10, java.sql.Types.INTEGER)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement("INSERT INTO content_alias(id,old_id,new_id,policy,reason) VALUES(?,?,?,?,?)").use { statement ->
                    request.aliases.sortedBy(ContentAliasEntry::id).forEach { alias ->
                        statement.setString(1, alias.id)
                        statement.setString(2, alias.oldId)
                        if (alias.newId != null) statement.setString(3, alias.newId) else statement.setNull(3, java.sql.Types.VARCHAR)
                        statement.setString(4, alias.policy)
                        statement.setString(5, alias.reason)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement("INSERT INTO asset_binding(id,template_id,usage_type,asset_id,priority) VALUES(?,?,?,?,?)").use { statement ->
                    request.assetBindings.sortedBy(AssetBindingEntry::id).forEach { binding ->
                        statement.setString(1, binding.id)
                        statement.setString(2, binding.templateId)
                        statement.setString(3, binding.usageType)
                        statement.setString(4, binding.assetId)
                        statement.setInt(5, binding.priority)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                connection.prepareStatement("INSERT INTO asset_fallback(id,usage_type,matcher_type,matcher_value,asset_id,priority) VALUES(?,?,?,?,?,?)").use { statement ->
                    request.assetFallbacks.sortedBy(AssetFallbackEntry::id).forEach { fallback ->
                        statement.setString(1, fallback.id)
                        statement.setString(2, fallback.usageType)
                        statement.setString(3, fallback.matcherType)
                        if (fallback.matcherValue != null) statement.setString(4, fallback.matcherValue) else statement.setNull(4, java.sql.Types.VARCHAR)
                        statement.setString(5, fallback.assetId)
                        statement.setInt(6, fallback.priority)
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                auditConnection(connection, templates.size, request.aliases.size, request.assetBindings.size, request.assetFallbacks.size)
                connection.commit()
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            }
        }
        if (request.fault == BuildFault.FAKE_SIDECAR) {
            Files.writeString(db.resolveSibling("${db.fileName}-wal"), "fault")
        }
        if (request.fault == BuildFault.OPEN_HANDLE) {
            FileChannel.open(db, StandardOpenOption.READ).use {
                throw ContentBuildException("RESOURCE_OPEN", "fault injected with content.db handle open")
            }
        }
        val sidecars = listOf(db.resolveSibling("${db.fileName}-wal"), db.resolveSibling("${db.fileName}-shm"), db.resolveSibling("${db.fileName}-journal"))
        if (sidecars.any(Files::exists)) throw ContentBuildException("SQLITE_SIDECAR", "SQLite sidecar exists after close")
    }

    private fun executeSqlScript(connection: Connection, script: String) {
        script.lineSequence()
            .map { it.substringBefore("--") }
            .joinToString("\n")
            .split(';')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { it.startsWith("PRAGMA", ignoreCase = true) }
            .forEach { sql -> connection.createStatement().use { it.execute(sql) } }
    }

    private fun auditConnection(connection: Connection, expectedRows: Int, expectedAliases: Int, expectedBindings: Int, expectedFallbacks: Int) {
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA foreign_key_check").use { result -> check(!result.next()) { "foreign key audit failed" } }
            statement.executeQuery("PRAGMA integrity_check").use { result -> check(result.next() && result.getString(1) == "ok") { "integrity audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM content_manifest").use { result -> check(result.next() && result.getInt(1) == 1) { "manifest row count audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM content_template").use { result -> check(result.next() && result.getInt(1) == expectedRows) { "template row count audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM content_alias").use { result -> check(result.next() && result.getInt(1) == expectedAliases) { "alias row count audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM asset_binding").use { result -> check(result.next() && result.getInt(1) == expectedBindings) { "binding row count audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM asset_fallback").use { result -> check(result.next() && result.getInt(1) == expectedFallbacks) { "fallback row count audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM content_template WHERE display_name<>COALESCE(display_name_override,source_display_name) OR enabled NOT IN (0,1)").use { result -> check(result.next() && result.getInt(1) == 0) { "template semantic audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM asset_binding WHERE priority<0").use { result -> check(result.next() && result.getInt(1) == 0) { "binding semantic audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM asset_fallback WHERE priority<0").use { result -> check(result.next() && result.getInt(1) == 0) { "fallback semantic audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM asset_binding b JOIN asset_image i ON i.id=b.asset_id WHERE CASE WHEN b.usage_type IN ('LIST_FACE','DETAIL_PORTRAIT','DIALOG_PORTRAIT','BATTLE_TOKEN','CHRONICLE_THUMB') THEN 'PORTRAIT' WHEN b.usage_type='ROOM_BACKGROUND' THEN 'BACKGROUND' WHEN b.usage_type='ICON' THEN 'ICON' WHEN b.usage_type='EMBLEM' THEN 'EMBLEM' WHEN b.usage_type='EVENT_ART' THEN 'EVENT_ART' WHEN b.usage_type='KEY_ART' THEN 'KEY_ART' END<>i.category").use { result -> check(result.next() && result.getInt(1) == 0) { "binding usage/category audit failed" } }
            statement.executeQuery("SELECT COUNT(*) FROM asset_fallback f JOIN asset_image i ON i.id=f.asset_id WHERE CASE WHEN f.usage_type IN ('LIST_FACE','DETAIL_PORTRAIT','DIALOG_PORTRAIT','BATTLE_TOKEN','CHRONICLE_THUMB') THEN 'PORTRAIT' WHEN f.usage_type='ROOM_BACKGROUND' THEN 'BACKGROUND' WHEN f.usage_type='ICON' THEN 'ICON' WHEN f.usage_type='EMBLEM' THEN 'EMBLEM' WHEN f.usage_type='EVENT_ART' THEN 'EVENT_ART' WHEN f.usage_type='KEY_ART' THEN 'KEY_ART' END<>i.category").use { result -> check(result.next() && result.getInt(1) == 0) { "fallback usage/category audit failed" } }
        }
    }

    private fun auditReadOnlyDatabase(
        db: Path,
        expectedRows: Int,
        aliases: List<ContentAliasEntry>,
        bindings: List<AssetBindingEntry>,
        fallbacks: List<AssetFallbackEntry>
    ) {
        val url = "jdbc:sqlite:file:${db.toAbsolutePath().toString().replace('\\', '/')}?mode=ro"
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA query_only=ON")
                statement.executeQuery("SELECT COUNT(*) FROM content_manifest").use { result -> check(result.next() && result.getInt(1) == 1) { "read-only manifest audit failed" } }
                statement.executeQuery("PRAGMA foreign_key_check").use { result -> check(!result.next()) { "read-only foreign key audit failed" } }
                statement.executeQuery("PRAGMA integrity_check").use { result -> check(result.next() && result.getString(1) == "ok") { "read-only integrity audit failed" } }
                statement.executeQuery("SELECT COUNT(*) FROM content_template").use { result -> check(result.next() && result.getInt(1) == expectedRows) { "read-only row audit failed" } }
                statement.executeQuery("SELECT COUNT(*) FROM content_alias").use { result -> check(result.next() && result.getInt(1) == aliases.size) { "read-only alias audit failed" } }
                statement.executeQuery("SELECT COUNT(*) FROM asset_binding").use { result -> check(result.next() && result.getInt(1) == bindings.size) { "read-only binding audit failed" } }
                statement.executeQuery("SELECT COUNT(*) FROM asset_fallback").use { result -> check(result.next() && result.getInt(1) == fallbacks.size) { "read-only fallback audit failed" } }
                statement.executeQuery("SELECT COUNT(*) FROM content_template WHERE display_name<>COALESCE(display_name_override,source_display_name) OR enabled NOT IN (0,1)").use { result -> check(result.next() && result.getInt(1) == 0) { "read-only semantic audit failed" } }
                statement.executeQuery("SELECT COUNT(*) FROM asset_binding b JOIN asset_image i ON i.id=b.asset_id WHERE CASE WHEN b.usage_type IN ('LIST_FACE','DETAIL_PORTRAIT','DIALOG_PORTRAIT','BATTLE_TOKEN','CHRONICLE_THUMB') THEN 'PORTRAIT' WHEN b.usage_type='ROOM_BACKGROUND' THEN 'BACKGROUND' WHEN b.usage_type='ICON' THEN 'ICON' WHEN b.usage_type='EMBLEM' THEN 'EMBLEM' WHEN b.usage_type='EVENT_ART' THEN 'EVENT_ART' WHEN b.usage_type='KEY_ART' THEN 'KEY_ART' END<>i.category").use { result -> check(result.next() && result.getInt(1) == 0) { "read-only binding usage/category audit failed" } }
                statement.executeQuery("SELECT COUNT(*) FROM asset_fallback f JOIN asset_image i ON i.id=f.asset_id WHERE CASE WHEN f.usage_type IN ('LIST_FACE','DETAIL_PORTRAIT','DIALOG_PORTRAIT','BATTLE_TOKEN','CHRONICLE_THUMB') THEN 'PORTRAIT' WHEN f.usage_type='ROOM_BACKGROUND' THEN 'BACKGROUND' WHEN f.usage_type='ICON' THEN 'ICON' WHEN f.usage_type='EMBLEM' THEN 'EMBLEM' WHEN f.usage_type='EVENT_ART' THEN 'EVENT_ART' WHEN f.usage_type='KEY_ART' THEN 'KEY_ART' END<>i.category").use { result -> check(result.next() && result.getInt(1) == 0) { "read-only fallback usage/category audit failed" } }
            }
        }
    }

    private fun readOnlyAudit(db: Path): Map<String, String> {
        val url = "jdbc:sqlite:file:${db.toAbsolutePath().toString().replace('\\', '/')}?mode=ro"
        DriverManager.getConnection(url).use { connection ->
            connection.createStatement().use { it.execute("PRAGMA query_only=ON") }
            val plans = linkedMapOf<String, String>()
            queryInventory.forEach { (id, sql) ->
                connection.prepareStatement("EXPLAIN QUERY PLAN $sql").use { statement ->
                    statement.setString(1, "__plan__")
                    if (id == "CDB-Q04") statement.setString(2, "LIST_FACE")
                    statement.executeQuery().use { result ->
                        val lines = buildList {
                        while (result.next()) add(result.getString("detail"))
                        }
                        val plan = lines.joinToString(" | ")
                        check(lines.none { it.contains("SCAN ", ignoreCase = true) }) {
                            "QUERY_PLAN_FULL_SCAN:$id:$plan"
                        }
                        check(lines.none { it.contains("USE TEMP B-TREE", ignoreCase = true) }) {
                            "QUERY_PLAN_TEMP_SORT:$id:$plan"
                        }
                        plans[id] = plan
                    }
                }
            }
            return plans
        }
    }

    private fun bundleManifest(
        request: ContentBuildRequest,
        source: LoadedSource,
        rowCount: Int,
        bundleId: String,
        logicalHash: String,
        dbSha256: String,
        assetManifestSha256: String,
        staging: Path
    ): JsonObject {
        val files = Files.walk(staging).use { stream ->
            stream.filter(Files::isRegularFile)
                .filter { it.fileName.toString() != "content-bundle-manifest.json" && it.fileName.toString() != "build-staging.marker" }
                .map { file ->
                    JsonObject(mapOf(
                        "path" to JsonString(staging.relativize(file).toString().replace('\\', '/')),
                        "sha256" to JsonString(CanonicalSourceConverter.sha256(Files.readAllBytes(file)))
                    ))
                }
                .sorted(compareBy(JsonValue::render))
                .toList()
        }
        return JsonObject(mapOf(
            "artifactFileSha256" to JsonString(dbSha256),
            "assetManifestSha256" to JsonString(assetManifestSha256),
            "balanceVersion" to JsonString(request.balanceVersion),
            "bundleId" to JsonString(bundleId),
            "contentVersion" to JsonString(request.contentVersion),
            "files" to JsonArray(files),
            "generatedByVersion" to JsonString(request.generatedByVersion),
            "logicalContentHash" to JsonString(logicalHash),
            "manifestVersion" to JsonNumber(java.math.BigDecimal.ONE),
            "profile" to JsonString(request.profile),
            "schemaVersion" to JsonNumber(java.math.BigDecimal.ONE),
            "sourceHash" to JsonString(source.sourceHash),
            "rowCount" to JsonNumber(java.math.BigDecimal(rowCount)),
            "assetCount" to JsonNumber(java.math.BigDecimal(request.assetEntries.size)),
            "sourceFileCount" to JsonNumber(java.math.BigDecimal(source.fileCount))
        ))
    }

    private fun renderMarkdownReport(reportPath: Path): String {
        val report = JsonParser.parse(readUtf8(reportPath)).asObject()
        val status = report.value("status").asString()
        val lines = buildString {
            appendLine("# Content build validation")
            appendLine()
            appendLine("- status: $status")
            report.optional("profile")?.let { appendLine("- profile: ${AssetPreviewRenderer.escapeHtml(it.asString())}") }
            report.optional("contentVersion")?.let { appendLine("- content version: ${AssetPreviewRenderer.escapeHtml(it.asString())}") }
            report.optional("sourceFileCount")?.let { appendLine("- source files: ${it.asInt()}") }
            report.optional("rowCount")?.let { appendLine("- rows: ${it.asInt()}") }
            report.optional("sourceHash")?.let { appendLine("- source hash: `${it.asString()}`") }
            report.optional("logicalContentHash")?.let { appendLine("- logical content hash: `${it.asString()}`") }
            report.optional("bundleId")?.let { appendLine("- bundle id: `${it.asString()}`") }
            val diagnostics = report.value("diagnostics").asArray().values.map(JsonValue::asObject)
            appendLine("- diagnostics: ${diagnostics.size}")
            diagnostics.forEach { diagnostic ->
                val location = listOfNotNull(
                    diagnostic.value("sourceFile").let { if (it == JsonNull) null else it.asString() },
                    diagnostic.value("row").let { if (it == JsonNull) null else "row=${it.asInt()}" },
                    diagnostic.value("column").let { if (it == JsonNull) null else "column=${it.asInt()}" },
                    diagnostic.value("field").let { if (it == JsonNull) null else "field=${it.asString()}" }
                ).joinToString(", ")
                appendLine("  - [${diagnostic.value("severity").asString()}] ${diagnostic.value("code").asString()} ${if (location.isBlank()) "" else "($location) "}${AssetPreviewRenderer.escapeHtml(diagnostic.value("message").asString())}")
            }
        }
        return lines
    }

    private fun <T> withPublishLock(outputRoot: Path, holdMillis: Long, action: () -> T): T {
        FileChannel.open(outputRoot.resolve(".publish.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                throw ContentBuildException("PUBLISH_CONFLICT", "another bundle publish is in progress")
            }
            if (lock == null) throw ContentBuildException("PUBLISH_CONFLICT", "another bundle publish is in progress")
            lock.use {
                if (holdMillis > 0) Thread.sleep(holdMillis)
                return action()
            }
        }
    }

    private fun publish(
        outputRoot: Path,
        staging: Path,
        bundleId: String,
        dbSha256: String,
        assetManifestSha256: String,
        sourceHash: String,
        logicalHash: String,
        request: ContentBuildRequest
    ): ContentBuildResult {
        val bundles = outputRoot.resolve("bundles")
        Files.createDirectories(bundles)
        if (request.fault == BuildFault.DIRECTORY_FSYNC_UNSUPPORTED) {
            throw ContentBuildException("PUBLISH_ATOMIC_UNSUPPORTED", "fault injected for directory fsync capability")
        }
        forceDirectory(outputRoot)
        forceDirectory(bundles)
        val target = bundles.resolve(bundleId)
        if (Files.exists(target)) {
            val existing = target.resolve("content-bundle-manifest.json")
            if (!Files.isRegularFile(existing) || !verifyTarget(target, staging, bundleId, dbSha256, assetManifestSha256)) {
                throw ContentBuildException("INTEGRITY_FAILED", "bundle target exists with different content")
            }
            Files.deleteIfExists(target.resolve("build-staging.marker"))
            forceDirectory(target)
            staging.toFile().deleteRecursively()
        } else {
            if (request.fault == BuildFault.ATOMIC_MOVE_UNSUPPORTED) throw ContentBuildException("PUBLISH_ATOMIC_UNSUPPORTED", "fault injected before atomic bundle move")
            try {
                forceTree(staging)
                awaitExternalKill(request, BuildFault.HOLD_BEFORE_BUNDLE_MOVE_FOR_EXTERNAL_KILL)
                moveDirectory(staging, target)
                Files.deleteIfExists(target.resolve("build-staging.marker"))
                forceDirectory(target)
                forceDirectory(bundles)
                awaitExternalKill(request, BuildFault.HOLD_AFTER_BUNDLE_MOVE_FOR_EXTERNAL_KILL)
                if (request.fault == BuildFault.CRASH_AFTER_BUNDLE_MOVE_BEFORE_POINTER) {
                    throw ContentBuildException("BUILD_CRASHED_AFTER_BUNDLE_MOVE", "fault injected after bundle move before pointer update")
                }
            } catch (error: java.nio.file.AtomicMoveNotSupportedException) {
                throw ContentBuildException("PUBLISH_ATOMIC_UNSUPPORTED", error.message ?: "atomic move unsupported")
            }
        }
        if (request.fault == BuildFault.POINTER_MOVE_FAILURE) throw ContentBuildException("PUBLISH_POINTER_FAILED", "fault injected before current pointer move")
        val pointer = JsonObject(mapOf(
            "bundleId" to JsonString(bundleId),
            "contentVersion" to JsonString(request.contentVersion)
        )).render() + "\n"
        val tempPointer = outputRoot.resolve("current.json.tmp")
        Files.writeString(tempPointer, pointer, StandardCharsets.UTF_8)
        forceFile(tempPointer)
        try {
            Files.move(tempPointer, outputRoot.resolve("current.json"), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            forceDirectory(outputRoot)
            awaitExternalKill(request, BuildFault.HOLD_AFTER_POINTER_FOR_EXTERNAL_KILL)
        } catch (error: java.nio.file.AtomicMoveNotSupportedException) {
            Files.deleteIfExists(tempPointer)
            throw ContentBuildException("PUBLISH_ATOMIC_UNSUPPORTED", error.message ?: "atomic move unsupported")
        }
        return ContentBuildResult(target, bundleId, sourceHash, logicalHash, JsonParser.parse(Files.readString(target.resolve("content-bundle-manifest.json"))).asObject().value("rowCount").asInt(), target.resolve("validation-report.json"))
    }

    private fun awaitExternalKill(request: ContentBuildRequest, fault: BuildFault) {
        if (request.fault != fault) return
        val marker = request.outputRoot.resolve(".${fault.name.lowercase()}.ready")
        Files.writeString(marker, fault.name, StandardCharsets.UTF_8)
        forceFile(marker)
        forceDirectory(request.outputRoot)
        while (true) Thread.sleep(1_000L)
    }

    private fun verifyTarget(
        target: Path,
        staging: Path,
        bundleId: String,
        expectedDbSha256: String,
        expectedAssetManifestSha256: String
    ): Boolean {
        return runCatching {
            val manifest = JsonParser.parse(Files.readString(target.resolve("content-bundle-manifest.json"))).asObject()
            if (manifest.value("bundleId").asString() != bundleId) return@runCatching false
            val actualDbSha256 = CanonicalSourceConverter.sha256(Files.readAllBytes(target.resolve("content.db")))
            if (actualDbSha256 != expectedDbSha256 || manifest.value("artifactFileSha256").asString() != actualDbSha256) return@runCatching false
            if (manifest.value("assetManifestSha256").asString() != expectedAssetManifestSha256) return@runCatching false
            val expected = manifest.value("files").asArray().values.map(JsonValue::asObject)
            val expectedPaths = expected.map { it.value("path").asString() }.toSet()
            val actual = Files.walk(target).use { stream ->
                stream.filter(Files::isRegularFile)
                    .filter { it.fileName.toString() != "content-bundle-manifest.json" && it.fileName.toString() != "build-staging.marker" }
                    .map { target.relativize(it).toString().replace('\\', '/') }
                    .toList()
            }
            if (actual.toSet() != expectedPaths) return@runCatching false
            expected.all { entry ->
                val relative = Path.of(entry.value("path").asString()).normalize()
                val file = target.resolve(relative).normalize()
                file.startsWith(target.normalize()) && Files.isRegularFile(file) && CanonicalSourceConverter.sha256(Files.readAllBytes(file)) == entry.value("sha256").asString()
            } && treeHashes(target) == treeHashes(staging)
        }.getOrDefault(false)
    }

    private fun treeHashes(root: Path): Map<String, String> = Files.walk(root).use { stream ->
        stream.filter(Files::isRegularFile)
            .filter { it.fileName.toString() != "build-staging.marker" }
            .map { file -> root.relativize(file).toString().replace('\\', '/') to CanonicalSourceConverter.sha256(Files.readAllBytes(file)) }
            .sorted(compareBy<Pair<String, String>> { it.first })
            .toList()
            .toMap(linkedMapOf())
    }

    private fun forceTree(root: Path) {
        Files.walk(root).use { stream -> stream.filter(Files::isRegularFile).forEach(::forceFile) }
        Files.walk(root).use { stream ->
            stream.filter(Files::isDirectory)
                .sorted(compareByDescending<Path> { it.nameCount })
                .forEach(::forceDirectory)
        }
    }

    private fun forceFile(path: Path) {
        FileChannel.open(path, StandardOpenOption.WRITE).use { it.force(true) }
    }

    private fun forceDirectory(path: Path) {
        try {
            if (System.getProperty("os.name").contains("windows", ignoreCase = true)) {
                forceWindowsDirectory(path)
            } else {
                FileChannel.open(path, StandardOpenOption.READ).use { it.force(true) }
            }
        } catch (error: ContentBuildException) {
            throw error
        } catch (error: Exception) {
            throw ContentBuildException(
                "PUBLISH_ATOMIC_UNSUPPORTED",
                "directory fsync failed for $path: ${error.message ?: error::class.java.simpleName}"
            )
        }
    }

    private fun forceWindowsDirectory(path: Path) {
        val handle = Kernel32.INSTANCE.CreateFile(
            path.toAbsolutePath().toString(),
            WinNT.GENERIC_WRITE,
            WINDOWS_DIRECTORY_SHARE_MODE,
            null,
            WINDOWS_OPEN_EXISTING,
            WINDOWS_FILE_FLAG_BACKUP_SEMANTICS,
            null
        )
        if (handle == WinBase.INVALID_HANDLE_VALUE) {
            throw windowsDirectoryFailure("CreateFile", path)
        }
        var flushFailure: ContentBuildException? = null
        try {
            if (!Kernel32.INSTANCE.FlushFileBuffers(handle)) {
                flushFailure = windowsDirectoryFailure("FlushFileBuffers", path)
            }
        } finally {
            if (!Kernel32.INSTANCE.CloseHandle(handle) && flushFailure == null) {
                throw windowsDirectoryFailure("CloseHandle", path)
            }
        }
        flushFailure?.let { throw it }
    }

    private fun windowsDirectoryFailure(operation: String, path: Path): ContentBuildException = ContentBuildException(
        "PUBLISH_ATOMIC_UNSUPPORTED",
        "$operation failed for Windows directory $path: error=${Kernel32.INSTANCE.GetLastError()}"
    )

    private fun moveDirectory(source: Path, target: Path) {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE)
    }

    private const val WINDOWS_DIRECTORY_SHARE_MODE = 0x00000007
    private const val WINDOWS_OPEN_EXISTING = 3
    private const val WINDOWS_FILE_FLAG_BACKUP_SEMANTICS = 0x02000000
}
