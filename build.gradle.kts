import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}

private val phase0Projects = setOf(":app", ":core:simulation")
private val allowedSimulationPluginDeclarations = setOf("alias(libs.plugins.kotlin.jvm)")
private val allowedSimulationDependencies = setOf(
    "org.jetbrains.kotlin:kotlin-stdlib",
    "org.jetbrains.kotlinx:kotlinx-coroutines-core",
    "junit:junit"
)
private val allowedAppDependencies = setOf(
    "androidx.activity:activity",
    "androidx.compose:compose-bom",
    "androidx.compose.material3:material3",
    "androidx.compose.ui:ui-test-junit4",
    "androidx.compose.ui:ui-test-manifest",
    "androidx.lifecycle:lifecycle-runtime-compose",
    "androidx.test.ext:junit",
    "androidx.test:runner",
    "org.jetbrains.kotlin:kotlin-stdlib"
)
private val simulationDeclaredConfigurationNames = setOf(
    "api",
    "compileOnly",
    "implementation",
    "runtimeOnly",
    "testCompileOnly",
    "testImplementation",
    "testRuntimeOnly"
)
private val appDeclaredConfigurationNames = simulationDeclaredConfigurationNames + setOf(
    "androidTestCompileOnly",
    "androidTestImplementation",
    "androidTestRuntimeOnly",
    "debugApi",
    "debugCompileOnly",
    "debugImplementation",
    "debugRuntimeOnly",
    "releaseApi",
    "releaseCompileOnly",
    "releaseImplementation",
    "releaseRuntimeOnly"
)
private val forbiddenSimulationImport = Regex(
    """^\s*import\s+(android\.|androidx\.|java\.net\.|javax\.net\.|okhttp\.|okhttp3\.|io\.ktor\.)"""
)

private fun projectGraphViolation(projects: Set<String>): String? =
    if (projects == phase0Projects) null else "Phase 0 permits only $phase0Projects, found $projects"

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

val verifyPhase0Architecture = tasks.register("verifyPhase0Architecture") {
    group = "verification"
    description = "Checks the Phase 0 project graph and pure simulation boundary with failing Gradle fixtures."

    doLast {
        val actualProjects = allprojects
            .filter { it != rootProject && it.subprojects.isEmpty() }
            .map { it.path }
            .toSet()
        check(projectGraphViolation(actualProjects) == null) { projectGraphViolation(actualProjects)!! }

        val app = project(":app")
        val appProjectDependencies = app.configurations.getByName("implementation").dependencies
            .withType(ProjectDependency::class.java)
            .map { it.path }
            .toSet()
        check(appProjectDependencies == setOf(":core:simulation")) {
            ":app may depend on only :core:simulation, found $appProjectDependencies"
        }
        val unapprovedAppDependencies = unapprovedDependencies(app, appDeclaredConfigurationNames, allowedAppDependencies)
        check(unapprovedAppDependencies.isEmpty()) {
            ":app has unapproved dependency declarations: $unapprovedAppDependencies"
        }

        val simulation = project(":core:simulation")
        val simulationProjectDependencies = simulation.configurations
            .flatMap { configuration -> configuration.dependencies.withType(ProjectDependency::class.java) }
        check(simulationProjectDependencies.isEmpty()) {
            ":core:simulation must not depend on another project"
        }

        val unapprovedPlugins = unapprovedSimulationPlugins(simulation.buildFile)
        check(unapprovedPlugins.isEmpty()) {
            ":core:simulation has unapproved plugin declarations: $unapprovedPlugins"
        }

        val unapprovedSimulationDependencies = unapprovedDependencies(
            simulation,
            simulationDeclaredConfigurationNames,
            allowedSimulationDependencies
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

        if (providers.gradleProperty("phase0.skipFixtures").isPresent) return@doLast

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
                        "settings.gradle.kts",
                        "build.gradle.kts",
                        "gradle.properties",
                        "gradle/libs.versions.toml",
                        "app/build.gradle.kts",
                        "core/simulation/build.gradle.kts",
                        "core/simulation/src/**"
                    )
                }
                into(fixtureDir)
            }
            mutate(fixtureDir)

            val command = listOf(
                "cmd", "/d", "/c", "call", rootDir.resolve("gradlew.bat").absolutePath,
                "-p", fixtureDir.absolutePath,
                task,
                "-Pphase0.skipFixtures=true",
                "--offline", "--no-daemon", "--console=plain"
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
            buildFile.writeText(
                buildFile.readText().replace(
                    "alias(libs.plugins.kotlin.jvm)",
                    "alias(libs.plugins.kotlin.jvm)\n    alias(libs.plugins.kotlin.compose)"
                )
            )
        }
        fixture("forbidden-kotlin-plugin", ":core:simulation has unapproved plugin declarations") { fixtureDir ->
            val buildFile = fixtureDir.resolve("core/simulation/build.gradle.kts")
            buildFile.writeText(
                buildFile.readText()
                    .replace("alias(libs.plugins.kotlin.jvm)", "kotlin(\"multiplatform\")")
                    .replace(Regex("""dependencies\s*\{(?s:.*?)\}"""), "")
            )
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

tasks.maybeCreate("check").dependsOn(verifyPhase0Architecture)
