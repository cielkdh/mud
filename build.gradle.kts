import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import java.security.MessageDigest
import java.nio.file.Files
import java.time.Instant
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit
import groovy.json.JsonSlurper
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

private data class Phase1JunitCase(
    val name: String,
    val passed: Boolean,
    val seconds: Double,
    val path: String,
    val modifiedAt: Long
)

private val phase1CoilCacheMaxBytes = 64L * 1024L * 1024L

private fun phase1Normalized(value: String): String = value.uppercase().replace(Regex("[^A-Z0-9]"), "")

private fun phase1Json(value: String): String = buildString {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}

private fun phase1JsonObject(file: File): Map<*, *> =
    (JsonSlurper().parse(file) as? Map<*, *>)
        ?: throw GradleException("Phase 1 evidence is not a JSON object: ${file.path}")

private fun phase1ContainsPlaceholder(value: Any?): Boolean = when (value) {
    is Map<*, *> -> value.values.any(::phase1ContainsPlaceholder)
    is Iterable<*> -> value.any(::phase1ContainsPlaceholder)
    is String -> value.isBlank() || value in setOf("NFR_MEASURE_REQUIRED", "NOT_RUN", "TBD", "UNKNOWN")
    else -> false
}

private fun phase1Sha256(value: Any?): Boolean = value is String && Regex("[0-9a-f]{64}").matches(value)

private fun phase1SafeRelativePath(value: Any?, requiredPrefix: String): Boolean {
    if (value !is String || value.isBlank() || '\\' in value || value.startsWith('/') || value.contains("..")) return false
    return value.startsWith(requiredPrefix)
}

private fun phase1PointerBundleId(value: Any?): String? = runCatching {
    ((JsonSlurper().parseText(value as String) as Map<*, *>)["bundleId"] as? String)
}.getOrNull()

private fun phase1FileSha256(file: File): String = MessageDigest.getInstance("SHA-256")
    .digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun phase1ArtifactFile(root: File?, value: Any?): File? {
    if (root == null || !phase1SafeRelativePath(value, "artifacts/")) return null
    val canonicalRoot = root.canonicalFile
    val candidate = canonicalRoot.resolve(value as String).canonicalFile
    return candidate.takeIf { it.toPath().startsWith(canonicalRoot.toPath()) && it.isFile }
}

private fun phase1EvidenceSemanticValid(testId: String, content: Map<*, *>, artifactRoot: File? = null): Boolean = when (testId) {
    "P1-IT-002" ->
        phase1Sha256(content["bundleHash"]) && phase1Sha256(content["sealedDbSha256"]) &&
            phase1Sha256(content["queryPlanSha256"]) && content["integrity"] == "ok" &&
            content["foreignKeyCheck"] == true && content["semanticAudit"] == true &&
            (content["queries"] as? List<*>)?.toSet() == (1..6).map { "CDB-Q0$it" }.toSet()
    "P1-IT-003" ->
        phase1Sha256(content["bundleHash"]) && phase1Sha256(content["assetManifestSha256"]) &&
            phase1Sha256(content["assetSha256"]) &&
            ((content["bindingCount"] as? Number)?.toLong() ?: 0L) > 0L &&
            ((content["fallbackCount"] as? Number)?.toLong() ?: 0L) > 0L &&
            phase1SafeRelativePath(content["assetPath"], "assets/") &&
            phase1SafeRelativePath(content["preview"], "asset-preview/")
    "P1-REC-001" -> {
        val requiredStates = setOf(
            "CRASHED_STAGING", "ORPHAN_QUARANTINED", "CRASHED_BEFORE_BUNDLE_MOVE",
            "CRASHED_AFTER_BUNDLE_MOVE_BEFORE_POINTER", "PUBLISHED"
        )
        val requiredKillpoints = setOf(
            "HOLD_AFTER_STAGING_FOR_EXTERNAL_KILL", "HOLD_BEFORE_BUNDLE_MOVE_FOR_EXTERNAL_KILL",
            "HOLD_AFTER_BUNDLE_MOVE_FOR_EXTERNAL_KILL", "HOLD_AFTER_POINTER_FOR_EXTERNAL_KILL"
        )
        val pointerCases = content["externalKillpointPointers"] as? List<*>
        val pointerByFault = pointerCases.orEmpty().mapNotNull { it as? Map<*, *> }
            .associateBy { it["fault"] }
        val pointerArtifact = phase1ArtifactFile(artifactRoot, content["currentPointerArtifact"])
        val manifestArtifact = phase1ArtifactFile(artifactRoot, content["bundleManifestArtifact"])
        val databaseArtifact = phase1ArtifactFile(artifactRoot, content["databaseArtifact"])
        val orphanArtifact = phase1ArtifactFile(artifactRoot, content["orphanDiagnosis"])
        val artifactsMatch = runCatching {
            val pointer = JsonSlurper().parse(pointerArtifact!!) as Map<*, *>
            val manifest = JsonSlurper().parse(manifestArtifact!!) as Map<*, *>
            val bundleId = pointer["bundleId"] as String
            phase1Sha256(bundleId) && manifest["bundleId"] == bundleId &&
                manifest["artifactFileSha256"] == phase1FileSha256(databaseArtifact!!) &&
                (JsonSlurper().parse(orphanArtifact!!) as Map<*, *>)["code"] == "ORPHAN_STAGING_QUARANTINED"
        }.getOrDefault(false)
        (content["states"] as? List<*>)?.toSet() == requiredStates &&
            (content["externalKillpoints"] as? List<*>)?.toSet() == requiredKillpoints &&
            pointerCases?.size == 4 && pointerByFault.keys == requiredKillpoints &&
            pointerByFault.all { (fault, row) ->
                val before = row["beforeBundleId"]
                val afterKill = row["afterKillBundleId"]
                val afterRecovery = row["afterRecoveryBundleId"]
                phase1Sha256(before) && phase1Sha256(afterKill) && phase1Sha256(afterRecovery) &&
                    if (fault == "HOLD_AFTER_POINTER_FOR_EXTERNAL_KILL") {
                        row["pointerState"] == "NEXT" && afterKill == afterRecovery && afterKill != before
                    } else {
                        row["pointerState"] == "PREVIOUS" && afterKill == before && afterRecovery != before
                    }
            } &&
            (content["sidecarCount"] as? Number)?.toLong() == 0L &&
            artifactsMatch &&
            listOf("currentPointer", "beforeMovePointer", "afterMovePointer").all {
                phase1Sha256(phase1PointerBundleId(content[it]))
            }
    }
    "P1-IT-005" -> {
        val bundle = content["bundleHash"]
        phase1Sha256(content["sourceHash"]) && phase1Sha256(content["logicalContentHash"]) &&
            phase1Sha256(bundle) && phase1Sha256(content["dbSha256"]) &&
            content["currentPointerBundleId"] == bundle
    }
    "P1-PT-001" -> {
        val queryMetrics = content["queryMetrics"] as? List<*>
        val queryIds = queryMetrics?.mapNotNull { (it as? Map<*, *>)?.get("id") }?.toSet()
        val decodeMetrics = content["decodeMetrics"] as? List<*>
        val decodeKeys = decodeMetrics?.mapNotNull { metric ->
            val map = metric as? Map<*, *> ?: return@mapNotNull null
            Triple(map["format"], map["qualityMode"], (map["targetPx"] as? Number)?.toLong())
        }?.toSet()
        val expectedDecodeKeys = setOf("PNG", "WEBP").flatMap { format ->
            setOf("FULL", "LOW").flatMap { quality ->
                listOf(128L, 1_024L).map { target -> Triple(format, quality, target) }
            }
        }.toSet()
        ((content["entryCount"] as? Number)?.toLong() == 10_000L) &&
            ((content["assetRowCount"] as? Number)?.toLong() == 10_000L) &&
            ((content["pageCount"] as? Number)?.toLong() == 20L) &&
            ((content["maxEntriesPerPage"] as? Number)?.toLong() == 500L) &&
            queryMetrics?.size == 6 && queryIds == (1..6).map { "CDB-Q0$it" }.toSet() &&
            queryMetrics.orEmpty().all { metric ->
                val map = metric as? Map<*, *> ?: return@all false
                val plan = (map["plan"] as? String).orEmpty().uppercase()
                plan.isNotBlank() && "SCAN " !in plan && "USE TEMP B-TREE" !in plan &&
                    ((map["p50Micros"] as? Number)?.toLong() ?: -1L) >= 0L &&
                    ((map["p95Micros"] as? Number)?.toLong() ?: -1L) >= 0L
            } &&
            decodeMetrics?.size == 8 && decodeKeys == expectedDecodeKeys &&
            decodeMetrics.orEmpty().all { metric ->
                val map = metric as? Map<*, *> ?: return@all false
                ((map["effectiveTargetPx"] as? Number)?.toLong() ?: 0L) > 0L &&
                    ((map["p50Micros"] as? Number)?.toLong() ?: -1L) >= 0L &&
                    ((map["p95Micros"] as? Number)?.toLong() ?: -1L) >= 0L
            } &&
            listOf("previewElapsedMs", "builderWallMs", "builderHeapDeltaBytes", "builderPeakHeapBytes", "dbSizeBytes").all {
                ((content[it] as? Number)?.toLong() ?: -1L) >= 0L
            } &&
            ((content["builderPeakHeapBytes"] as? Number)?.toLong() ?: 0L) > 0L &&
            ((content["dbSizeBytes"] as? Number)?.toLong() ?: 0L) > 0L &&
            (content["textForbiddenIoCount"] as? Number)?.toLong() == 0L &&
            ((content["textAllowedDecodeCount"] as? Number)?.toLong() ?: 0L) > 0L &&
            (content["staleAssetCount"] as? Number)?.toLong() == 0L &&
            (content["cacheKeySampleCount"] as? Number)?.toLong() == 16L &&
            (content["decodedFormats"] as? List<*>)?.toSet() == setOf("PNG", "WEBP")
    }
    else -> true
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

private val phase0Projects = setOf(":app", ":core:simulation")
private val phase1Projects = setOf(":app", ":core:content", ":core:image", ":core:simulation", ":tools:content-builder")
private val phase0Mode = providers.gradleProperty("phase0.skipFixtures").isPresent

private val allowedSimulationPluginDeclarations = setOf("alias(libs.plugins.kotlin.jvm)")
private val simulationDeclaredConfigurationNames = setOf(
    "api", "compileOnly", "implementation", "runtimeOnly", "testCompileOnly", "testImplementation", "testRuntimeOnly"
)
private val appDeclaredConfigurationNames = simulationDeclaredConfigurationNames + setOf(
    "androidTestCompileOnly", "androidTestImplementation", "androidTestRuntimeOnly",
    "debugApi", "debugCompileOnly", "debugImplementation", "debugRuntimeOnly",
    "releaseApi", "releaseCompileOnly", "releaseImplementation", "releaseRuntimeOnly"
)
private val allowedSimulationDependencies = setOf(
    "org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains.kotlinx:kotlinx-coroutines-core", "junit:junit"
)
private val allowedAppDependencies = setOf(
    "androidx.activity:activity", "androidx.compose:compose-bom", "androidx.compose.material3:material3",
    "androidx.compose.ui:ui-test-junit4", "androidx.compose.ui:ui-test-manifest",
    "androidx.lifecycle:lifecycle-runtime-compose", "androidx.test.ext:junit", "androidx.test:runner",
    "org.jetbrains.kotlin:kotlin-stdlib"
)
private val forbiddenSimulationImport = Regex(
    """^\s*import\s+(android\.|androidx\.|java\.net\.|javax\.net\.|okhttp\.|okhttp3\.|io\.ktor\.)"""
)

private fun dependencyNotation(dependency: Dependency): String =
    dependency.group?.let { "$it:${dependency.name}" } ?: dependency.name

private fun unapprovedDependencies(
    project: Project,
    configurationNames: Set<String>,
    allowed: Set<String>
): Set<String> = project.configurations
    .filter { it.name in configurationNames }
    .flatMap { configuration -> configuration.dependencies }
    .filterNot { it is ProjectDependency }
    .map(::dependencyNotation)
    .filterNot(allowed::contains)
    .toSet()

private fun unapprovedSimulationPlugins(buildFile: File): Set<String> {
    var inPluginsBlock = false
    return buildFile.readLines().mapNotNull { line ->
        val trimmed = line.substringBefore("//").trim()
        when {
            trimmed == "plugins {" -> {
                inPluginsBlock = true
                null
            }
            inPluginsBlock && trimmed == "}" -> {
                inPluginsBlock = false
                null
            }
            inPluginsBlock && (trimmed.startsWith("alias(") || trimmed.startsWith("id(") || trimmed.startsWith("kotlin(")) -> trimmed
            trimmed.startsWith("apply(plugin") || trimmed.startsWith("plugins.apply(") -> trimmed
            else -> null
        }
    }.filterNot(allowedSimulationPluginDeclarations::contains).toSet()
}

private fun isForbiddenSimulationImport(line: String): Boolean = forbiddenSimulationImport.containsMatchIn(line)

private val verifyPhase0Architecture = tasks.register("verifyPhase0Architecture") {
    group = "verification"
    description = "Checks the Phase 0 baseline and the approved Phase 1 project graph."

    doLast {
        val expectedProjects = if (phase0Mode) phase0Projects else phase1Projects
        val actualProjects = allprojects
            .filter { it != rootProject && it.subprojects.isEmpty() }
            .map { it.path }
            .toSet()
        check(actualProjects == expectedProjects) {
            "${if (phase0Mode) "Phase 0" else "Phase 1"} permits only $expectedProjects, found $actualProjects"
        }

        val app = project(":app")
        val expectedAppProjects = if (phase0Mode) setOf(":core:simulation") else setOf(":core:content", ":core:image", ":core:simulation")
        val appProjectDependencies = app.configurations.getByName("implementation").dependencies
            .withType(ProjectDependency::class.java)
            .map { it.path }
            .toSet()
        check(appProjectDependencies == expectedAppProjects) {
            if (phase0Mode) ":app may depend on only :core:simulation, found $appProjectDependencies"
            else ":app project dependencies must be $expectedAppProjects, found $appProjectDependencies"
        }
        val unapprovedAppDependencies = unapprovedDependencies(app, appDeclaredConfigurationNames, allowedAppDependencies)
        check(unapprovedAppDependencies.isEmpty()) {
            ":app has unapproved dependency declarations: $unapprovedAppDependencies"
        }

        val simulation = project(":core:simulation")
        val simulationProjectDependencies = simulation.configurations
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { it.path }
            .toSet()
        val expectedSimulationProjects = if (phase0Mode) emptySet() else setOf(":core:content")
        check(simulationProjectDependencies == expectedSimulationProjects) {
            if (phase0Mode) ":core:simulation must not depend on another project"
            else ":core:simulation project dependencies must be $expectedSimulationProjects, found $simulationProjectDependencies"
        }

        val unapprovedPlugins = unapprovedSimulationPlugins(simulation.buildFile)
        check(unapprovedPlugins.isEmpty()) {
            ":core:simulation has unapproved plugin declarations: $unapprovedPlugins"
        }
        val unapprovedSimulationDependencies = unapprovedDependencies(
            simulation, simulationDeclaredConfigurationNames, allowedSimulationDependencies
        )
        check(unapprovedSimulationDependencies.isEmpty()) {
            ":core:simulation has unapproved dependency declarations: $unapprovedSimulationDependencies"
        }

        val importViolations = fileTree(simulation.projectDir) {
            include("src/**/*.kt")
        }.files.flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                if (isForbiddenSimulationImport(line)) "${file.relativeTo(rootDir)}:${index + 1}: $line" else null
            }
        }
        check(importViolations.isEmpty()) {
            "Forbidden :core:simulation import(s):\n${importViolations.joinToString("\n")}"
        }

        check(providers.gradleProperty("phase0.schemaBaselineMode").orNull == "GREENFIELD_V1") {
            "Phase 0 must retain GREENFIELD_V1 schema baseline"
        }
        check(providers.gradleProperty("phase0.futureSaveSchemaDir").orNull == "core/save/schemas") {
            "Phase 0 must reserve core/save/schemas for Phase 3"
        }
        check(!file("core/save/schemas").exists()) {
            "Phase 0 must not create a Room schema directory"
        }

        if (phase0Mode) return@doLast

        fun fixture(
            name: String,
            expectedMessage: String,
            task: String = "verifyPhase0Architecture",
            mutate: (File) -> Unit
        ) {
            val fixtureDir = layout.buildDirectory.dir("phase0-architecture-fixtures/$name").get().asFile
            copy {
                from(rootDir) {
                    include(
                        "settings.gradle.kts", "build.gradle.kts", "gradle.properties", "gradle/libs.versions.toml",
                        "app/build.gradle.kts", "core/simulation/build.gradle.kts", "core/simulation/src/**"
                    )
                }
                into(fixtureDir)
            }
            fixtureDir.resolve("settings.gradle.kts").apply {
                writeText(readText().replace(
                    "include(\":app\", \":core:content\", \":core:image\", \":core:simulation\", \":tools:content-builder\")",
                    "include(\":app\", \":core:simulation\")"
                ))
            }
            fixtureDir.resolve("app/build.gradle.kts").apply {
                writeText(readText()
                    .replace("    implementation(project(\":core:content\"))\n", "")
                    .replace("    implementation(project(\":core:image\"))\n", ""))
            }
            fixtureDir.resolve("core/simulation/build.gradle.kts").apply {
                writeText(readText().replace("    implementation(project(\":core:content\"))\n", ""))
            }
            mutate(fixtureDir)

            val command = listOf(
                "cmd", "/d", "/c", "call", rootDir.resolve("gradlew.bat").absolutePath,
                "-p", fixtureDir.absolutePath, task, "-Pphase0.skipFixtures=true", "--offline", "--no-daemon", "--console=plain"
            )
            val process = ProcessBuilder(command)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            check(exitCode != 0) { "Fixture '$name' unexpectedly passed" }
            check(expectedMessage in output) {
                "Fixture '$name' failed for the wrong reason:\n${output.takeLast(4_000)}"
            }
            logger.lifecycle("Phase 0 fixture '$name' rejected as expected")
        }

        fixture("extra-module", "Phase 0 permits only") { fixtureDir ->
            fixtureDir.resolve("settings.gradle.kts").appendText("\ninclude(\":core:save\")\n")
            fixtureDir.resolve("core/save/build.gradle.kts").apply {
                parentFile.mkdirs()
                writeText("")
            }
        }
        fixture("forbidden-edge", ":core:simulation must not depend on another project") { fixtureDir ->
            fixtureDir.resolve("core/simulation/build.gradle.kts")
                .appendText("\ndependencies { implementation(project(\":app\")) }\n")
        }
        fixture("forbidden-plugin", ":core:simulation has unapproved plugin declarations") { fixtureDir ->
            val buildFile = fixtureDir.resolve("core/simulation/build.gradle.kts")
            buildFile.writeText(buildFile.readText().replace(
                "alias(libs.plugins.kotlin.jvm)", "alias(libs.plugins.kotlin.jvm)\n    alias(libs.plugins.kotlin.compose)"
            ))
        }
        fixture("forbidden-kotlin-plugin", ":core:simulation has unapproved plugin declarations") { fixtureDir ->
            val buildFile = fixtureDir.resolve("core/simulation/build.gradle.kts")
            buildFile.writeText(buildFile.readText()
                .replace("alias(libs.plugins.kotlin.jvm)", "kotlin(\"multiplatform\")")
                .replace(Regex("""dependencies\s*\{(?s:.*?)\}"""), ""))
        }
        fixture("forbidden-dependency", ":core:simulation has unapproved dependency declarations") { fixtureDir ->
            fixtureDir.resolve("core/simulation/build.gradle.kts")
                .appendText("\ndependencies { implementation(\"com.squareup.okhttp3:okhttp:5.1.0\") }\n")
        }
        fixture("forbidden-app-dependency", ":app has unapproved dependency declarations") { fixtureDir ->
            fixtureDir.resolve("app/build.gradle.kts")
                .appendText("\ndependencies { implementation(\"androidx.room:room-runtime:2.7.0\") }\n")
        }
        fixture("forbidden-import", "Forbidden :core:simulation import(s)") { fixtureDir ->
            fixtureDir.resolve("core/simulation/src/main/kotlin/ForbiddenImportFixture.kt").apply {
                parentFile.mkdirs()
                writeText("package fixture\n\nimport okhttp3.OkHttpClient\n")
            }
        }
        fixture("forbidden-unit-mix", "Unresolved reference", ":core:simulation:compileTestKotlin") { fixtureDir ->
            fixtureDir.resolve("core/simulation/src/test/kotlin/com/imsi/mud/simulation/MixedUnitsFixture.kt").apply {
                parentFile.mkdirs()
                writeText(
                    """
                    package com.imsi.mud.simulation

                    fun mixedUnitsMustNotCompile() {
                        val minute = when (val checked = GameMinute.of(1)) {
                            is Checked.Value -> checked.value
                            is Checked.Rejected -> return
                        }
                        val money = when (val checked = Money.of(1)) {
                            is Checked.Value -> checked.value
                            is Checked.Rejected -> return
                        }
                        minute + money
                    }
                    """.trimIndent()
                )
            }
        }
    }
}

val verifyPhase1Architecture = tasks.register("verifyPhase1Architecture") {
    group = "verification"
    description = "Checks the approved Phase 1 graph and module boundaries."
    dependsOn(verifyPhase0Architecture)

    doLast {
        check(!phase0Mode) { "verifyPhase1Architecture must run in the Phase 1 graph" }
        val content = project(":core:content")
        val image = project(":core:image")
        val builder = project(":tools:content-builder")
        check(content.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) { ":core:content must use Kotlin/JVM" }
        check(image.plugins.hasPlugin("com.android.library")) { ":core:image must use the Android library plugin" }
        check(builder.plugins.hasPlugin("org.jetbrains.kotlin.jvm")) { ":tools:content-builder must use Kotlin/JVM" }
        check(builder.plugins.hasPlugin("org.gradle.application")) { ":tools:content-builder must be an application" }

        val declaredProjectConfigurations = setOf(
            "api", "compileOnly", "implementation", "runtimeOnly", "testCompileOnly", "testImplementation", "testRuntimeOnly",
            "androidTestCompileOnly", "androidTestImplementation", "androidTestRuntimeOnly",
            "debugApi", "debugCompileOnly", "debugImplementation", "debugRuntimeOnly",
            "releaseApi", "releaseCompileOnly", "releaseImplementation", "releaseRuntimeOnly"
        )
        fun projectDependencies(project: Project): Set<String> = project.configurations
            .filter { it.name in declaredProjectConfigurations }
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
            .map { it.path }
            .toSet()
        check(projectDependencies(content).isEmpty()) { ":core:content must not depend on another project" }
        check(projectDependencies(image) == setOf(":core:content")) { ":core:image must depend only on :core:content, found ${projectDependencies(image)}" }
        check(projectDependencies(builder) == setOf(":core:content")) { ":tools:content-builder must depend only on :core:content, found ${projectDependencies(builder)}" }

        val contentExternal = unapprovedDependencies(content, simulationDeclaredConfigurationNames, setOf("junit:junit", "org.jetbrains.kotlin:kotlin-stdlib"))
        check(contentExternal.isEmpty()) { ":core:content has unapproved dependency declarations: $contentExternal" }
        val imageExternal = unapprovedDependencies(
            image,
            simulationDeclaredConfigurationNames + setOf("androidTestImplementation", "debugImplementation"),
            setOf("io.coil-kt.coil3:coil-compose", "junit:junit", "org.jetbrains.kotlin:kotlin-stdlib")
        )
        check(imageExternal.isEmpty()) { ":core:image has unapproved dependency declarations: $imageExternal" }
        val builderExternal = unapprovedDependencies(
            builder,
            simulationDeclaredConfigurationNames,
            setOf(
                "net.java.dev.jna:jna-platform",
                "org.sejda.imageio:webp-imageio",
                "org.xerial:sqlite-jdbc",
                "junit:junit",
                "org.jetbrains.kotlin:kotlin-stdlib"
            )
        )
        check(builderExternal.isEmpty()) { ":tools:content-builder has unapproved dependency declarations: $builderExternal" }

        val report = layout.buildDirectory.file("reports/phase1/P1-RT-001-architecture.txt").get().asFile
        report.parentFile.mkdirs()
        report.writeText(
            "testId=P1-RT-001\nresult=PASS\nprojects=${phase1Projects.sorted().joinToString(",")}\n" +
                "contentDependencies=${projectDependencies(content).sorted().joinToString(",")}\n" +
                "imageDependencies=${projectDependencies(image).sorted().joinToString(",")}\n" +
                "builderDependencies=${projectDependencies(builder).sorted().joinToString(",")}\n"
        )
    }
}

private fun pythonCommand(): List<String> {
    val explicit = providers.environmentVariable("PYTHON").orNull?.trim().orEmpty()
    if (explicit.isNotEmpty()) return listOf(explicit)

    val candidates = if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        listOf(listOf("py", "-3"), listOf("python"), listOf("python3"))
    } else {
        listOf(listOf("python3"), listOf("python"))
    }
    candidates.firstOrNull { candidate ->
        runCatching {
            val process = ProcessBuilder(candidate + "--version")
                .redirectErrorStream(true)
                .start()
            try {
                process.waitFor(5, TimeUnit.SECONDS) && process.exitValue() == 0
            } finally {
                process.destroyForcibly()
            }
        }.getOrDefault(false)
    }?.let { return it }

    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        val registryPython = runCatching {
            val process = ProcessBuilder(
                "reg", "query", "HKCU\\Software\\Python\\PythonCore", "/s", "/v", "ExecutablePath"
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) null else {
                Regex("""ExecutablePath\s+REG_SZ\s+(.+python(?:3(?:\.\d+)?)?\.exe)\s*$""", RegexOption.MULTILINE)
                    .findAll(output)
                    .map { it.groupValues[1].trim() }
                    .firstOrNull { File(it).isFile }
            }
        }.getOrNull()
        if (registryPython != null) return listOf(registryPython)
    }

    throw GradleException(
        "Python is required for phase1DocumentationValidation; set PYTHON to a Python executable"
    )
}

val phase1DocumentationValidation = tasks.register<org.gradle.api.tasks.Exec>("phase1DocumentationValidation") {
    group = "verification"
    description = "Runs the authoritative repository documentation validator for the Phase 1 gate."
    workingDir(rootDir.resolve("docs/검증도구"))
    doFirst {
        commandLine(pythonCommand() + "validate_docs.py")
    }
}

val phase1VerificationTaskPaths = listOf(
    ":core:content:test",
    ":tools:content-builder:test",
    ":core:simulation:test",
    ":core:image:testDebugUnitTest",
    ":app:testDebugUnitTest",
    ":core:image:lintDebug",
    ":app:lintDebug",
    ":app:assembleDebug",
    ":app:assembleRelease"
)

val phase1EvidenceStart = tasks.register("phase1EvidenceStart") {
    group = "verification"
    description = "Captures the immutable save sentinel before any Phase 1 verification task runs."
    val output = layout.buildDirectory.file("reports/phase1/sentinel-before.txt")
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        val sentinel = rootDir.resolve("phase1-fixtures/common/save-sentinel.db")
        val hash = MessageDigest.getInstance("SHA-256").digest(sentinel.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val sidecars = listOf("-wal", "-shm", "-journal").filter { rootDir.resolve("${sentinel.relativeTo(rootDir)}$it").exists() }
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            "runId=${UUID.randomUUID()}\n" +
                "startedAtEpochMillis=${System.currentTimeMillis()}\n" +
                "sha256=$hash\n" +
                "sidecars=${sidecars.joinToString(",")}\n"
        )
    }
}

val phase1AndroidEvidenceStart = tasks.register("phase1AndroidEvidenceStart") {
    group = "verification"
    description = "Captures an immutable run token and save sentinel before connected Phase 1 tests."
    val output = layout.buildDirectory.file("reports/phase1/android-run.properties")
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        val sentinel = rootDir.resolve("phase1-fixtures/common/save-sentinel.db")
        val hash = MessageDigest.getInstance("SHA-256").digest(sentinel.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val sidecars = listOf("-wal", "-shm", "-journal")
            .filter { rootDir.resolve("${sentinel.relativeTo(rootDir)}$it").exists() }
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            "runId=${UUID.randomUUID()}\n" +
                "startedAtEpochMillis=${System.currentTimeMillis()}\n" +
                "sha256=$hash\n" +
                "sidecars=${sidecars.joinToString(",")}\n"
        )
    }
}

val phase1AndroidPerformanceEvidence = tasks.register("phase1AndroidPerformanceEvidence") {
    group = "verification"
    description = "Captures a numeric emulator PSS baseline after connected Phase 1 tests."
    dependsOn(":app:assembleDebug")
    val output = layout.buildDirectory.file("reports/phase1/android-performance.json")
    outputs.file(output)
    outputs.upToDateWhen { false }
    doLast {
        val localProperties = rootDir.resolve("local.properties")
        val sdkDirectory = (if (localProperties.isFile) {
            Properties().apply { localProperties.inputStream().use { load(it) } }.getProperty("sdk.dir")
        } else null)
            ?: System.getenv("ANDROID_HOME")
            ?: System.getenv("ANDROID_SDK_ROOT")
            ?: File(System.getProperty("user.home"), "AppData/Local/Android/Sdk").takeIf(File::isDirectory)?.absolutePath
            ?: throw GradleException("Android SDK is required for Android performance evidence")
        val adb = File(sdkDirectory, "platform-tools/adb.exe")
        check(adb.isFile) { "adb.exe is missing: ${adb.path}" }

        fun adbOutput(vararg args: String): String {
            val process = ProcessBuilder(listOf(adb.absolutePath) + args)
                .directory(rootDir)
                .redirectErrorStream(true)
                .start()
            val text = process.inputStream.bufferedReader().readText()
            check(process.waitFor(30, TimeUnit.SECONDS)) { "adb timed out: ${args.joinToString(" ")}" }
            check(process.exitValue() == 0) { "adb failed: ${args.joinToString(" ")}\n$text" }
            return text
        }

        val serial = adbOutput("get-serialno").trim()
        check(serial.isNotBlank() && serial != "unknown") { "no adb target for Phase 1 PSS evidence" }
        val workloadLog = rootDir.resolve("app/build/outputs/androidTest-results/connected/debug")
            .walkTopDown()
            .filter { it.isFile && it.name.startsWith("logcat-") && it.name.contains("p1Pt001_") }
            .maxByOrNull(File::lastModified)
            ?: throw GradleException("P1-PT-001 instrumentation workload log is missing")
        val workloadMatch = Regex(
            "P1_PT_METRICS\\s+steadyPssKb=(\\d+)\\s+transientPeakPssKb=(\\d+)\\s+" +
                "sampleCount=(\\d+)\\s+decodeCombinationCount=(\\d+)\\s+" +
                "bundleCombinationCount=(\\d+)\\s+memoryCacheHitCount=(\\d+)\\s+coilCacheMaxBytes=(\\d+)"
        ).find(workloadLog.readText())
            ?: throw GradleException("P1-PT-001 instrumentation metrics marker is missing from ${workloadLog.path}")
        val steadyPssKb = workloadMatch.groupValues[1].toLong()
        val transientPeakPssKb = workloadMatch.groupValues[2].toLong()
        val sampleCount = workloadMatch.groupValues[3].toLong()
        val decodeCombinationCount = workloadMatch.groupValues[4].toLong()
        val bundleCombinationCount = workloadMatch.groupValues[5].toLong()
        val memoryCacheHitCount = workloadMatch.groupValues[6].toLong()
        val coilCacheMaxBytes = workloadMatch.groupValues[7].toLong()
        val steadyPssLimitKb = 384L * 1_024L
        val transientPssLimitKb = 512L * 1_024L
        check(sampleCount >= 3L) { "at least three in-workload Android PSS samples are required" }
        check(decodeCombinationCount == 8L && bundleCombinationCount == 16L) {
            "P1-PT-001 must execute all eight decode combinations in both bundles"
        }
        check(memoryCacheHitCount > 0L) { "P1-PT-001 must observe Coil memory cache hits" }
        check(coilCacheMaxBytes == phase1CoilCacheMaxBytes) {
            "P1-PT-001 Coil cache bound differs from the approved limit"
        }
        check(steadyPssKb in 1..steadyPssLimitKb) {
            "Phase 1 steady emulator PSS $steadyPssKb KiB exceeds $steadyPssLimitKb KiB"
        }
        check(transientPeakPssKb in 1..transientPssLimitKb) {
            "Phase 1 transient emulator PSS $transientPeakPssKb KiB exceeds $transientPssLimitKb KiB"
        }
        val apiLevel = adbOutput("shell", "getprop", "ro.build.version.sdk").trim()
        val file = output.get().asFile
        file.parentFile.mkdirs()
        val workloadLogPath = rootDir.toPath().relativize(workloadLog.toPath()).toString().replace('\\', '/')
        file.writeText(
            "{\"apiLevel\":${phase1Json(apiLevel)},\"deviceSerial\":${phase1Json(serial)}," +
                "\"bundleCombinationCount\":$bundleCombinationCount,\"coilCacheMaxBytes\":$coilCacheMaxBytes," +
                "\"decodeCombinationCount\":$decodeCombinationCount,\"environment\":\"EMULATOR_DEBUG\"," +
                "\"measurementSource\":\"INSTRUMENTATION_DECODE_WORKLOAD\"," +
                "\"measuredAt\":${phase1Json(Instant.now().toString())}," +
                "\"memoryCacheHitCount\":$memoryCacheHitCount,\"sampleCount\":$sampleCount," +
                "\"steadyPssKb\":$steadyPssKb," +
                "\"steadyPssLimitKb\":$steadyPssLimitKb,\"transientPeakPssKb\":$transientPeakPssKb," +
                "\"transientPssLimitKb\":$transientPssLimitKb,\"result\":\"PASS\"," +
                "\"testId\":\"P1-PT-001\",\"workloadLog\":${phase1Json(workloadLogPath)}}\n"
        )
    }
}

gradle.projectsEvaluated {
    phase1VerificationTaskPaths.forEach { path ->
        val projectPath = path.substringBeforeLast(':')
        val taskName = path.substringAfterLast(':')
        findProject(projectPath)?.tasks?.findByName(taskName)?.let { task ->
            task.dependsOn(phase1EvidenceStart)
            task.outputs.upToDateWhen { false }
        }
    }
    phase1DocumentationValidation.configure { dependsOn(phase1EvidenceStart) }
    verifyPhase1Architecture.configure { dependsOn(phase1EvidenceStart) }
    project(":app").tasks.findByName("connectedDebugAndroidTest")?.let { task ->
        task.dependsOn(phase1EvidenceStart)
        task.dependsOn(phase1AndroidEvidenceStart)
        task.outputs.upToDateWhen { false }
        task.finalizedBy(phase1AndroidPerformanceEvidence)
    }
}

val phase1VerificationTasks = phase1VerificationTaskPaths + phase1DocumentationValidation

val verifyPhase1EvidenceSemantics = tasks.register("verifyPhase1EvidenceSemantics") {
    group = "verification"
    description = "Rejects structurally present but semantically false Phase 1 evidence."
    doLast {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        fun assertValid(testId: String, evidence: Map<String, Any>, artifactRoot: File? = null) {
            check(phase1EvidenceSemanticValid(testId, evidence, artifactRoot)) {
                "semantic evidence validator rejected the valid $testId control fixture"
            }
        }
        fun assertRejected(
            testId: String,
            invariant: String,
            evidence: Map<String, Any>,
            artifactRoot: File? = null,
        ) {
            check(!phase1EvidenceSemanticValid(testId, evidence, artifactRoot)) {
                "semantic evidence validator accepted invalid $testId invariant: $invariant"
            }
        }

        val integration = mapOf<String, Any>(
            "bundleHash" to hashA,
            "sealedDbSha256" to hashB,
            "queryPlanSha256" to "c".repeat(64),
            "integrity" to "ok",
            "foreignKeyCheck" to true,
            "semanticAudit" to true,
            "queries" to (1..6).map { "CDB-Q0$it" },
        )
        assertValid("P1-IT-002", integration)
        assertRejected("P1-IT-002", "foreign-key-check", integration + ("foreignKeyCheck" to false))
        assertRejected("P1-IT-002", "semantic-audit", integration + ("semanticAudit" to false))
        assertRejected("P1-IT-002", "query-set", integration + ("queries" to (1..5).map { "CDB-Q0$it" }))
        assertRejected("P1-IT-002", "sealed-db-hash", integration + ("sealedDbSha256" to "bad"))

        val asset = mapOf<String, Any>(
            "bundleHash" to hashA,
            "assetManifestSha256" to hashB,
            "assetSha256" to "c".repeat(64),
            "bindingCount" to 1,
            "fallbackCount" to 1,
            "assetPath" to "assets/portrait/test.png",
            "preview" to "asset-preview/PORTRAIT-001.html",
        )
        assertValid("P1-IT-003", asset)
        assertRejected("P1-IT-003", "asset-path-containment", asset + ("assetPath" to "../save.db"))
        assertRejected("P1-IT-003", "fallback-count", asset + ("fallbackCount" to 0))
        assertRejected("P1-IT-003", "asset-hash", asset + ("assetSha256" to "bad"))

        val deterministic = mapOf<String, Any>(
            "sourceHash" to hashA,
            "logicalContentHash" to hashB,
            "bundleHash" to "c".repeat(64),
            "dbSha256" to "d".repeat(64),
            "currentPointerBundleId" to "c".repeat(64),
        )
        assertValid("P1-IT-005", deterministic)
        assertRejected(
            "P1-IT-005",
            "pointer-bundle-match",
            deterministic + ("currentPointerBundleId" to hashA),
        )
        assertRejected("P1-IT-005", "logical-content-hash", deterministic + ("logicalContentHash" to "bad"))

        val queryMetrics = (1..6).map { index ->
            mapOf<String, Any>(
                "id" to "CDB-Q0$index",
                "plan" to "SEARCH content USING INDEX idx_content_id (id=?)",
                "p50Micros" to 1,
                "p95Micros" to 2,
            )
        }
        val decodeMetrics = listOf("PNG", "WEBP").flatMap { format ->
            listOf("FULL", "LOW").flatMap { quality ->
                listOf(128, 1_024).map { target ->
                    mapOf<String, Any>(
                        "format" to format,
                        "qualityMode" to quality,
                        "targetPx" to target,
                        "effectiveTargetPx" to target,
                        "p50Micros" to 1,
                        "p95Micros" to 2,
                    )
                }
            }
        }
        val performance = mapOf<String, Any>(
            "entryCount" to 10_000,
            "assetRowCount" to 10_000,
            "pageCount" to 20,
            "maxEntriesPerPage" to 500,
            "previewElapsedMs" to 1,
            "builderWallMs" to 1,
            "builderHeapDeltaBytes" to 0,
            "builderPeakHeapBytes" to 1,
            "dbSizeBytes" to 1,
            "queryMetrics" to queryMetrics,
            "decodeMetrics" to decodeMetrics,
            "textForbiddenIoCount" to 0,
            "textAllowedDecodeCount" to 1,
            "cacheKeySampleCount" to 16,
            "staleAssetCount" to 0,
            "decodedFormats" to listOf("PNG", "WEBP"),
        )
        assertValid("P1-PT-001", performance)
        assertRejected(
            "P1-PT-001",
            "query-plan-scan",
            performance + ("queryMetrics" to queryMetrics.mapIndexed { index, metric ->
                if (index == 0) metric + ("plan" to "SCAN content") else metric
            }),
        )
        assertRejected("P1-PT-001", "cache-key-sample", performance + ("cacheKeySampleCount" to 15))
        assertRejected("P1-PT-001", "stale-bundle-key", performance + ("staleAssetCount" to 1))
        assertRejected("P1-PT-001", "forbidden-text-io", performance + ("textForbiddenIoCount" to 1))
        assertRejected("P1-PT-001", "decode-matrix", performance + ("decodeMetrics" to decodeMetrics.dropLast(1)))

        val recoveryRoot = temporaryDir.resolve("recovery-control").apply {
            deleteRecursively()
            resolve("artifacts").mkdirs()
        }
        val recoveryArtifacts = recoveryRoot.resolve("artifacts")
        val database = recoveryArtifacts.resolve("content.db").apply { writeBytes("sealed-db".toByteArray()) }
        recoveryArtifacts.resolve("current.json").writeText("{\"bundleId\":${phase1Json(hashB)}}")
        recoveryArtifacts.resolve("content-bundle-manifest.json").writeText(
            "{\"bundleId\":${phase1Json(hashB)},\"artifactFileSha256\":${phase1Json(phase1FileSha256(database))}}"
        )
        recoveryArtifacts.resolve("orphan-diagnosis.json").writeText(
            "{\"code\":\"ORPHAN_STAGING_QUARANTINED\"}"
        )
        val killpoints = listOf(
            "HOLD_AFTER_STAGING_FOR_EXTERNAL_KILL",
            "HOLD_BEFORE_BUNDLE_MOVE_FOR_EXTERNAL_KILL",
            "HOLD_AFTER_BUNDLE_MOVE_FOR_EXTERNAL_KILL",
            "HOLD_AFTER_POINTER_FOR_EXTERNAL_KILL",
        )
        val pointerCases = killpoints.map { fault ->
            if (fault == "HOLD_AFTER_POINTER_FOR_EXTERNAL_KILL") {
                mapOf<String, Any>(
                    "fault" to fault,
                    "pointerState" to "NEXT",
                    "beforeBundleId" to hashA,
                    "afterKillBundleId" to hashB,
                    "afterRecoveryBundleId" to hashB,
                )
            } else {
                mapOf<String, Any>(
                    "fault" to fault,
                    "pointerState" to "PREVIOUS",
                    "beforeBundleId" to hashA,
                    "afterKillBundleId" to hashA,
                    "afterRecoveryBundleId" to hashB,
                )
            }
        }
        val pointerJson = "{\"bundleId\":${phase1Json(hashB)}}"
        val recovery = mapOf<String, Any>(
            "states" to listOf(
                "CRASHED_STAGING", "ORPHAN_QUARANTINED", "CRASHED_BEFORE_BUNDLE_MOVE",
                "CRASHED_AFTER_BUNDLE_MOVE_BEFORE_POINTER", "PUBLISHED",
            ),
            "externalKillpoints" to killpoints,
            "externalKillpointPointers" to pointerCases,
            "sidecarCount" to 0,
            "currentPointerArtifact" to "artifacts/current.json",
            "bundleManifestArtifact" to "artifacts/content-bundle-manifest.json",
            "databaseArtifact" to "artifacts/content.db",
            "orphanDiagnosis" to "artifacts/orphan-diagnosis.json",
            "currentPointer" to pointerJson,
            "beforeMovePointer" to pointerJson,
            "afterMovePointer" to pointerJson,
        )
        assertValid("P1-REC-001", recovery, recoveryRoot)
        assertRejected("P1-REC-001", "sidecars", recovery + ("sidecarCount" to 1), recoveryRoot)
        assertRejected(
            "P1-REC-001",
            "pointer-case",
            recovery + ("externalKillpointPointers" to pointerCases.mapIndexed { index, row ->
                if (index == 0) row + ("pointerState" to "NEXT") else row
            }),
            recoveryRoot,
        )
        assertRejected(
            "P1-REC-001",
            "artifact-containment",
            recovery + ("databaseArtifact" to "../content.db"),
            recoveryRoot,
        )
        database.writeBytes("tampered-db".toByteArray())
        assertRejected("P1-REC-001", "database-manifest-hash", recovery, recoveryRoot)
    }
}

val phase1Evidence = tasks.register("phase1Evidence") {
    group = "verification"
    description = "Writes deterministic Phase 1 Test ID evidence and enforces no unverified PASS claims."
    dependsOn(verifyPhase1Architecture)
    dependsOn(verifyPhase1EvidenceSemantics)
    dependsOn(phase1VerificationTasks)
    doLast {
        val evidenceRoot = rootDir.resolve("build/reports/phase1")
        evidenceRoot.mkdirs()
        val sentinel = rootDir.resolve("phase1-fixtures/common/save-sentinel.db")
        val sentinelHash = MessageDigest.getInstance("SHA-256").digest(sentinel.readBytes()).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val expectedSentinelHash = rootDir.resolve("phase1-fixtures/common/save-sentinel.sha256").readText().trim()
        val beforeFile = rootDir.resolve("build/reports/phase1/sentinel-before.txt")
        check(beforeFile.isFile) { "Phase 1 sentinel before-capture is missing" }
        val beforeProperties = beforeFile.readLines().associate { line -> line.substringBefore('=') to line.substringAfter('=') }
        val runId = beforeProperties.getValue("runId")
        val gateStartedAt = beforeProperties.getValue("startedAtEpochMillis").toLong()
        val beforeSentinelHash = beforeProperties.getValue("sha256")
        val beforeSidecars = beforeProperties.getValue("sidecars")
        val afterSidecars = listOf("-wal", "-shm", "-journal")
            .filter { rootDir.resolve("${sentinel.relativeTo(rootDir)}$it").exists() }
            .joinToString(",")
        check(beforeSentinelHash == expectedSentinelHash) { "Phase 1 save sentinel did not match its baseline before tests" }
        check(sentinelHash == expectedSentinelHash) { "Phase 1 save sentinel changed: $sentinelHash" }
        check(beforeSidecars.isEmpty() && afterSidecars.isEmpty()) { "Phase 1 save sentinel SQLite sidecar was present" }
        val androidRunFile = rootDir.resolve("build/reports/phase1/android-run.properties")
        val androidProperties = if (androidRunFile.isFile) {
            androidRunFile.readLines().associate { line -> line.substringBefore('=') to line.substringAfter('=') }
        } else {
            emptyMap()
        }
        val androidRunId = androidProperties["runId"].orEmpty()
        val androidStartedAt = androidProperties["startedAtEpochMillis"]?.toLongOrNull() ?: Long.MAX_VALUE
        if (androidProperties.isNotEmpty()) {
            check(androidProperties["sha256"] == expectedSentinelHash && androidProperties["sidecars"].isNullOrEmpty()) {
                "Phase 1 Android run started with a modified or open save sentinel"
            }
        }
        val testIds = listOf(
            "P1-UT-001", "P1-BT-001", "P1-FT-001", "P1-CT-001", "P1-IT-001",
            "P1-UT-002", "P1-BT-002", "P1-FT-002", "P1-CT-002", "P1-IT-002",
            "P1-UT-003", "P1-BT-003", "P1-FT-003", "P1-CT-003", "P1-IT-003",
            "P1-UT-004", "P1-BT-004", "P1-FT-004", "P1-CT-004", "P1-IT-004",
            "P1-RT-001", "P1-CN-001", "P1-REC-001", "P1-PT-001", "P1-OP-001", "P1-ET-001", "P1-IT-005"
        )
        val resultRoots = listOf(
            rootDir.resolve("core/content/build/test-results"),
            rootDir.resolve("tools/content-builder/build/test-results"),
            rootDir.resolve("core/simulation/build/test-results"),
            rootDir.resolve("core/image/build/test-results"),
            rootDir.resolve("app/build/test-results"),
            rootDir.resolve("app/build/outputs/androidTest-results")
        )
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val xmlFiles = resultRoots.asSequence().filter(File::isDirectory).flatMap { directory ->
            directory.walkTopDown().filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
        }.toList()
        val junitCases = xmlFiles.flatMap { file ->
            runCatching {
                val document = factory.newDocumentBuilder().parse(file)
                val nodes = document.getElementsByTagName("testcase")
                (0 until nodes.length).mapNotNull { index ->
                    val element = nodes.item(index) as? Element ?: return@mapNotNull null
                    val passed = listOf("failure", "error", "skipped").all { element.getElementsByTagName(it).length == 0 }
                    Phase1JunitCase(
                        name = "${element.getAttribute("classname")}.${element.getAttribute("name")}",
                        passed = passed,
                        seconds = element.getAttribute("time").toDoubleOrNull() ?: 0.0,
                        path = rootDir.toPath().relativize(file.toPath()).toString().replace('\\', '/'),
                        modifiedAt = file.lastModified()
                    )
                }
            }.getOrElse { emptyList() }
        }
        val verificationInputs = fileTree(rootDir) {
            include("build.gradle.kts", "settings.gradle.kts", "gradle/libs.versions.toml")
            include("app/build.gradle.kts", "core/*/build.gradle.kts", "tools/*/build.gradle.kts")
            include("**/gradle.lockfile", "settings-gradle.lockfile")
            include("app/src/**", "core/*/src/**", "tools/*/src/**")
            include("content/source/**", "phase1-fixtures/**", "docs/설계부록/**")
        }
        check(verificationInputs.files.contains(rootDir.resolve("phase1-fixtures/P1-IT-003/README.md"))) {
            "Phase 1 freshness inputs must include fixture contracts"
        }
        check(verificationInputs.files.contains(rootDir.resolve("content/source/catalog-manifest.json"))) {
            "Phase 1 freshness inputs must include canonical content source"
        }
        val latestCodeModifiedAt = verificationInputs.files.maxOfOrNull(File::lastModified) ?: 0L
        val sourceManifest = rootDir.resolve("content/source/catalog-manifest.json")
        val sourceManifestSha256 = MessageDigest.getInstance("SHA-256").digest(sourceManifest.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val architectureReport = rootDir.resolve("build/reports/phase1/P1-RT-001-architecture.txt")
        val requiredResultFragments = mapOf(
            "P1-UT-001" to listOf("core/content/build/test-results", "tools/content-builder/build/test-results"),
            "P1-BT-001" to listOf("tools/content-builder/build/test-results"),
            "P1-FT-001" to listOf("tools/content-builder/build/test-results"),
            "P1-CT-001" to listOf("core/content/build/test-results", "tools/content-builder/build/test-results"),
            "P1-IT-001" to listOf("tools/content-builder/build/test-results"),
            "P1-UT-002" to listOf("core/content/build/test-results"),
            "P1-BT-002" to listOf("tools/content-builder/build/test-results"),
            "P1-FT-002" to listOf("tools/content-builder/build/test-results"),
            "P1-CT-002" to listOf("tools/content-builder/build/test-results"),
            "P1-IT-002" to listOf("tools/content-builder/build/test-results"),
            "P1-UT-003" to listOf("core/content/build/test-results", "core/image/build/test-results"),
            "P1-BT-003" to listOf("core/image/build/test-results"),
            "P1-FT-003" to listOf("tools/content-builder/build/test-results"),
            "P1-CT-003" to listOf("core/content/build/test-results", "core/image/build/test-results", "app/build/outputs/androidTest-results"),
            "P1-IT-003" to listOf("tools/content-builder/build/test-results", "app/build/outputs/androidTest-results"),
            "P1-UT-004" to listOf("core/content/build/test-results"),
            "P1-BT-004" to listOf("core/content/build/test-results"),
            "P1-FT-004" to listOf("core/content/build/test-results"),
            "P1-CT-004" to listOf("core/content/build/test-results"),
            "P1-IT-004" to listOf("core/content/build/test-results"),
            "P1-CN-001" to listOf("core/content/build/test-results", "tools/content-builder/build/test-results"),
            "P1-REC-001" to listOf("tools/content-builder/build/test-results"),
            "P1-PT-001" to listOf("tools/content-builder/build/test-results", "core/image/build/test-results", "app/build/outputs/androidTest-results"),
            "P1-OP-001" to listOf("core/image/build/test-results"),
            "P1-ET-001" to listOf("tools/content-builder/build/test-results", "app/build/outputs/androidTest-results"),
            "P1-IT-005" to listOf("core/content/build/test-results", "tools/content-builder/build/test-results")
        )
        val requiredEvidenceArtifacts = mapOf(
            "P1-IT-002" to listOf("testId", "bundleHash", "sealedDbSha256", "queryPlanSha256", "integrity", "foreignKeyCheck", "semanticAudit", "queries"),
            "P1-IT-003" to listOf("testId", "bundleHash", "assetManifestSha256", "assetPath", "assetSha256", "bindingCount", "fallbackCount", "preview"),
            "P1-REC-001" to listOf(
                "testId", "states", "orphanDiagnosis", "currentPointer", "externalKillpoints",
                "externalKillpointPointers", "sidecarCount", "currentPointerArtifact",
                "bundleManifestArtifact", "databaseArtifact"
            ),
            "P1-PT-001" to listOf("testId", "entryCount", "assetRowCount", "pageCount", "maxEntriesPerPage", "previewElapsedMs", "builderWallMs", "builderHeapDeltaBytes", "builderPeakHeapBytes", "dbSizeBytes", "queryMetrics", "decodeMetrics", "textForbiddenIoCount", "textAllowedDecodeCount", "cacheKeySampleCount", "staleAssetCount", "decodedFormats"),
            "P1-IT-005" to listOf("testId", "sourceHash", "logicalContentHash", "bundleHash", "dbSha256", "currentPointerBundleId")
        )
        val entries = testIds.map { testId ->
            val fixture = rootDir.resolve("phase1-fixtures/$testId")
            val fixtureContract = fixture.resolve("README.md")
            val matching = junitCases.filter {
                val requiredStart = if (it.path.contains("app/build/outputs/androidTest-results")) androidStartedAt else gateStartedAt
                phase1Normalized(it.name).contains(phase1Normalized(testId)) &&
                    it.modifiedAt + 2_000L >= latestCodeModifiedAt &&
                    it.modifiedAt + 2_000L >= requiredStart
            }
            val specialPaths = if (
                testId == "P1-RT-001" && architectureReport.isFile &&
                architectureReport.lastModified() + 2_000L >= latestCodeModifiedAt &&
                architectureReport.lastModified() + 2_000L >= gateStartedAt &&
                architectureReport.readText().contains("result=PASS")
            ) {
                listOf(rootDir.toPath().relativize(architectureReport.toPath()).toString().replace('\\', '/'))
            } else emptyList()
            val requiredFragments = requiredResultFragments[testId].orEmpty()
            val hasRequiredCoverage = requiredFragments.all { fragment -> matching.any { it.path.contains(fragment) } }
            val requiredArtifactKeys = requiredEvidenceArtifacts[testId].orEmpty()
            val testArtifact = rootDir.resolve("build/phase1-fixtures/$testId/evidence.json")
            val androidPerformanceArtifact = rootDir.resolve("build/reports/phase1/android-performance.json")
            val hasRequiredArtifact = if (requiredArtifactKeys.isEmpty()) {
                true
            } else {
                testArtifact.isFile &&
                    testArtifact.lastModified() + 2_000L >= gateStartedAt &&
                    testArtifact.lastModified() + 2_000L >= latestCodeModifiedAt &&
                    (runCatching { phase1JsonObject(testArtifact) }.getOrNull()?.let { content ->
                        val keysPresent = content["testId"] == testId && requiredArtifactKeys.all(content::containsKey)
                        val valuesMeasured = !phase1ContainsPlaceholder(content)
                        val testSpecific = if (testId == "P1-PT-001") {
                            fun nonNegativeNumber(key: String): Boolean =
                                (content[key] as? Number)?.toLong()?.let { it >= 0L } == true
                            val builderMetrics = phase1EvidenceSemanticValid(testId, content) &&
                                listOf(
                                    "previewElapsedMs", "builderWallMs", "builderHeapDeltaBytes", "builderPeakHeapBytes", "dbSizeBytes"
                                ).all(::nonNegativeNumber) &&
                                ((content["dbSizeBytes"] as? Number)?.toLong() ?: 0L) > 0L &&
                                (content["decodedFormats"] as? List<*>)?.toSet() == setOf("PNG", "WEBP")
                            val androidMetrics = androidPerformanceArtifact.isFile &&
                                androidPerformanceArtifact.lastModified() + 2_000L >= androidStartedAt &&
                                androidPerformanceArtifact.lastModified() + 2_000L >= latestCodeModifiedAt &&
                                runCatching { phase1JsonObject(androidPerformanceArtifact) }.getOrNull()?.let { android ->
                                    val steadyPss = (android["steadyPssKb"] as? Number)?.toLong() ?: -1L
                                    val transientPss = (android["transientPeakPssKb"] as? Number)?.toLong() ?: -1L
                                    val steadyLimit = (android["steadyPssLimitKb"] as? Number)?.toLong() ?: -1L
                                    val transientLimit = (android["transientPssLimitKb"] as? Number)?.toLong() ?: -1L
                                    val coilCacheMaxBytes = (android["coilCacheMaxBytes"] as? Number)?.toLong() ?: -1L
                                    val sampleCount = (android["sampleCount"] as? Number)?.toLong() ?: -1L
                                    val decodeCombinationCount = (android["decodeCombinationCount"] as? Number)?.toLong() ?: -1L
                                    val bundleCombinationCount = (android["bundleCombinationCount"] as? Number)?.toLong() ?: -1L
                                    val memoryCacheHitCount = (android["memoryCacheHitCount"] as? Number)?.toLong() ?: -1L
                                    android["testId"] == "P1-PT-001" && android["result"] == "PASS" &&
                                        android["measurementSource"] == "INSTRUMENTATION_DECODE_WORKLOAD" &&
                                        !phase1ContainsPlaceholder(android) &&
                                        steadyPss in 1..steadyLimit && transientPss in 1..transientLimit &&
                                        steadyLimit == 384L * 1_024L && transientLimit == 512L * 1_024L &&
                                        sampleCount >= 3L && decodeCombinationCount == 8L &&
                                        bundleCombinationCount == 16L && memoryCacheHitCount > 0L &&
                                        coilCacheMaxBytes == phase1CoilCacheMaxBytes &&
                                        phase1SafeRelativePath(android["workloadLog"], "app/build/outputs/androidTest-results/")
                                } == true
                            builderMetrics && androidMetrics
                        } else true
                        keysPresent && valuesMeasured && phase1EvidenceSemanticValid(testId, content, testArtifact.parentFile) && testSpecific
                    } == true)
            }
            val result = when {
                !fixture.isDirectory || !fixtureContract.isFile || fixtureContract.readText().isBlank() ||
                    !fixtureContract.readText().contains(testId) -> "NOT_RUN"
                matching.any { !it.passed } -> "FAIL"
                testId == "P1-RT-001" && specialPaths.isNotEmpty() -> "PASS"
                matching.isNotEmpty() && hasRequiredCoverage && hasRequiredArtifact -> "PASS"
                else -> "NOT_RUN"
            }
            val requiredArtifactPaths = if (hasRequiredArtifact && requiredArtifactKeys.isNotEmpty()) {
                (listOf(testArtifact) + if (testId == "P1-PT-001") listOf(androidPerformanceArtifact) else emptyList())
                    .map { rootDir.toPath().relativize(it.toPath()).toString().replace('\\', '/') }
            } else emptyList()
            val sourceArtifactPaths = (matching.map(Phase1JunitCase::path) + specialPaths + requiredArtifactPaths).distinct().sorted()
            val startedAt = matching.minOfOrNull(Phase1JunitCase::modifiedAt)?.let(Instant::ofEpochMilli)?.toString()
                ?: if (specialPaths.isNotEmpty()) Instant.ofEpochMilli(architectureReport.lastModified()).toString() else ""
            val durationMs = matching.sumOf { (it.seconds * 1_000.0).toLong() }
            val itemRoot = rootDir.resolve("build/phase1-fixtures/$testId")
            itemRoot.mkdirs()
            val resultRoot = itemRoot.resolve("results")
            resultRoot.deleteRecursively()
            resultRoot.mkdirs()
            val copiedResults = sourceArtifactPaths.mapIndexed { index, relativePath ->
                val source = rootDir.resolve(relativePath)
                val bytes = source.readBytes()
                val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val extension = source.extension.ifBlank { "txt" }
                val target = resultRoot.resolve("result-${index + 1}-${sha.take(12)}.$extension")
                target.writeBytes(bytes)
                rootDir.toPath().relativize(target.toPath()).toString().replace('\\', '/') to sha
            }
            val fixtureContractSha256 = MessageDigest.getInstance("SHA-256").digest(fixtureContract.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val artifactPaths = copiedResults.map { it.first }
            val itemEvidence = itemRoot.resolve("evidence.json")
            val command = ".\\gradlew.bat phase1Gate --no-daemon --console=plain"
            val item = """{"androidRunId":${phase1Json(androidRunId)},"artifactPaths":[${artifactPaths.joinToString(",", transform = ::phase1Json)}],"artifactSha256":[${copiedResults.joinToString(",") { phase1Json(it.second) }}],"afterSaveSha256":${phase1Json(sentinelHash)},"beforeSaveSha256":${phase1Json(beforeSentinelHash)},"caseCount":${matching.size},"command":${phase1Json(command)},"durationMs":$durationMs,"fixtureContractSha256":${phase1Json(fixtureContractSha256)},"fixtureProfile":"PROTOTYPE","gateStartedAt":${phase1Json(Instant.ofEpochMilli(gateStartedAt).toString())},"junitCases":[${matching.map(Phase1JunitCase::name).distinct().sorted().joinToString(",", transform = ::phase1Json)}],"requiredArtifactKeys":[${requiredArtifactKeys.joinToString(",", transform = ::phase1Json)}],"requiredResultFragments":[${requiredFragments.joinToString(",", transform = ::phase1Json)}],"result":${phase1Json(result)},"runId":${phase1Json(runId)},"sourceArtifactPaths":[${sourceArtifactPaths.joinToString(",", transform = ::phase1Json)}],"sourceManifestSha256":${phase1Json(sourceManifestSha256)},"startedAt":${phase1Json(startedAt)},"testId":${phase1Json(testId)}}"""
            itemEvidence.writeText("$item\n")
            item
        }
        val allPass = entries.all { "\"result\":\"PASS\"" in it }
        val gateStatus = if (allPass) "PROTOTYPE_ACCEPTED" else "REJECTED"
        evidenceRoot.resolve("phase1-test-evidence.json").writeText(
            "{\"assetReadiness\":\"BLOCKED_ASSET\",\"gateStatus\":\"$gateStatus\",\"runId\":" + phase1Json(runId) + ",\"tests\":[" + entries.joinToString() + "]}\n"
        )
        check(allPass) {
            "Phase 1 evidence contains NOT_RUN tests; no unverified Test ID may be reported as PASS"
        }
    }
}

phase1AndroidPerformanceEvidence.configure {
    mustRunAfter(":app:connectedDebugAndroidTest")
}

phase1Evidence.configure {
    dependsOn(":app:connectedDebugAndroidTest")
    dependsOn(phase1AndroidPerformanceEvidence)
}

tasks.register("phase1Gate") {
    group = "verification"
    description = "Runs the Phase 1 architecture, JVM, Android, builder, and documentation gates."
    dependsOn(phase1Evidence)
}

tasks.maybeCreate("check").dependsOn(verifyPhase0Architecture)
