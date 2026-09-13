package com.imsi.mud.content.builder

import java.nio.file.Files
import java.nio.file.Path

fun main(args: Array<String>) {
    try {
        when (args.firstOrNull()) {
            "convert" -> {
                val options = options(args.drop(1))
                val result = CanonicalSourceConverter.convert(
                    bootstrap = Path.of(options.required("bootstrap")),
                    outputRoot = Path.of(options.required("output")),
                    sourceVersion = options["source-version"] ?: "catalog.v1"
                )
                println("converted ${result.rowCount} rows in ${result.fileCount} files; sourceHash=${result.sourceHash}")
            }
            "build" -> {
                val options = options(args.drop(1))
                val licenseRegistry = options["license-registry"]?.let { LicenseManifestLoader.load(Path.of(it)) } ?: emptyList()
                val assetEntries = options["asset-manifest"]?.let { AssetManifestLoader.load(Path.of(it)) } ?: emptyList()
                val result = ContentBuilder.build(
                    ContentBuildRequest(
                        sourceRoot = Path.of(options.required("source-root")),
                        outputRoot = Path.of(options["output-root"] ?: "build/content-bundles"),
                        ddlPath = Path.of(options.required("ddl")),
                        contentVersion = options["content-version"] ?: "content.v1",
                        balanceVersion = options["balance-version"] ?: "balance.v1",
                        profile = options["profile"] ?: "PROTOTYPE",
                        generatedByVersion = options["generated-by"] ?: "p1-content-builder.v1",
                        assetEntries = assetEntries,
                        assetRoot = options["asset-root"]?.let(Path::of),
                        approvedLicenseIds = licenseRegistry.filter { it.approvalStatus == "APPROVED" && "ANDROID_APP" in it.distributionScopes }.map(LicenseRegistryEntry::id).toSet(),
                        licenseRegistry = licenseRegistry
                    )
                )
                println("published ${result.bundleHash} at ${result.bundleDirectory}")
            }
            "assets" -> {
                val options = options(args.drop(1))
                val entries = AssetManifestLoader.load(Path.of(options.required("manifest")))
                val root = Path.of(options.required("asset-root"))
                val licenses = options["license-registry"]?.let { LicenseManifestLoader.load(Path.of(it)) } ?: emptyList()
                val approved = licenses.filter { it.approvalStatus == "APPROVED" && "ANDROID_APP" in it.distributionScopes }.map(LicenseRegistryEntry::id).toSet() +
                    (options["approved-license"]?.split(',')?.filter(String::isNotBlank)?.toSet() ?: emptySet())
                val diagnostics = AssetValidator.validate(entries, root, approved, licenses.associate { it.id to it.distributionScopes })
                if (diagnostics.isNotEmpty()) {
                    diagnostics.forEach { println("${it.code}:${it.assetId}:${it.message}") }
                    throw assetValidationFailure(diagnostics)
                }
                val result = AssetPreviewRenderer.render(entries, Path.of(options["preview-output"] ?: "build/content-assets-preview"))
                println("validated ${entries.size} assets; preview pages=${result.pageCount}")
            }
            else -> throw IllegalArgumentException("usage: convert|build|assets --key value")
        }
    } catch (error: Throwable) {
        System.err.println(renderCliError(error))
        kotlin.system.exitProcess(2)
    }
}

internal fun renderCliError(error: Throwable): String = if (error is ContentBuildException) {
    "${error.externalResultCode}: ${error.detailMessage} [detail=${error.code}]"
} else {
    error.message ?: error::class.java.simpleName
}

private object AssetManifestLoader {
    fun load(path: Path): List<AssetPreviewEntry> {
        require(Files.isRegularFile(path)) { "asset manifest is missing: $path" }
        val root = JsonParser.parse(Files.readString(path)).asObject()
        return root.value("entries").asArray().values.map { value ->
            val entry = value.asObject()
            AssetPreviewEntry(
                id = (entry.optional("assetId") ?: entry.value("id")).asString(),
                category = entry.value("category").asString(),
                relativePath = entry.value("relativePath").asString(),
                width = entry.value("width").asInt(),
                height = entry.value("height").asInt(),
                byteSize = entry.value("byteSize").asLong(),
                sha256 = (entry.optional("exactFileSha256") ?: entry.value("sha256")).asString(),
                licenseId = entry.value("licenseId").asString(),
                mimeType = entry.value("mimeType").asString(),
                alphaMode = entry.value("alphaMode").asString(),
                colorSpace = entry.value("colorSpace").asString(),
                poolVersion = entry.value("poolVersion").asString(),
                validationStatus = entry.value("validationStatus").asString(),
                focalXppm = entry.optional("focalXppm")?.let { if (it == JsonNull) null else it.asInt() },
                focalYppm = entry.optional("focalYppm")?.let { if (it == JsonNull) null else it.asInt() },
                usageType = entry.optional("usageType")?.asNullableString(),
                cropProfile = entry.optional("cropProfile")?.asNullableString(),
                resolutionReason = entry.optional("resolutionReason")?.asNullableString(),
                sourceDisplayName = entry.optional("sourceDisplayName")?.asNullableString(),
                effectiveDisplayName = entry.optional("effectiveDisplayName")?.asNullableString(),
                missing = entry.optional("missing")?.let { if (it == JsonNull) false else (it as JsonBoolean).value } ?: false,
                duplicate = entry.optional("duplicate")?.let { if (it == JsonNull) false else (it as JsonBoolean).value } ?: false,
                unused = entry.optional("unused")?.let { if (it == JsonNull) false else (it as JsonBoolean).value } ?: false
            )
        }
    }
}

private object LicenseManifestLoader {
    fun load(path: Path): List<LicenseRegistryEntry> {
        require(Files.isRegularFile(path)) { "license registry is missing: $path" }
        val root = JsonParser.parse(Files.readString(path)).asObject()
        val values = (root.optional("entries") ?: root.value("licenses")).asArray().values
        return values.map { value ->
            val entry = value.asObject()
            LicenseRegistryEntry(
                id = entry.value("id").asString(),
                license = entry.value("license").asString(),
                source = entry.value("source").asString(),
                approvalStatus = entry.value("approvalStatus").asString(),
                distributionScopes = entry.value("distributionScopes").asArray().values.map(JsonValue::asString).toSet()
            )
        }
    }
}

private fun options(args: List<String>): Map<String, String> {
    val result = linkedMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index].removePrefix("--")
        require(key != args[index] && index + 1 < args.size) { "expected --key value" }
        result[key] = args[index + 1]
        index += 2
    }
    return result
}

private fun Map<String, String>.required(key: String): String = get(key) ?: error("missing --$key")
