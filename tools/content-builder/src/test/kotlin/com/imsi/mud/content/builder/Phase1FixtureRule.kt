package com.imsi.mud.content.builder

import java.nio.file.Files
import java.nio.file.Path
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class Phase1FixtureRule : TestWatcher() {
    override fun starting(description: Description) {
        val testId = Regex("P1-[A-Z]{2,3}-[0-9]{3}").find(description.methodName.uppercase())?.value
            ?: error("Phase 1 executable test name must contain a Test ID: ${description.methodName}")
        val repositoryRoot = generateSequence(Path.of("").toAbsolutePath().normalize()) { it.parent }
            .firstOrNull { Files.isRegularFile(it.resolve("settings.gradle.kts")) }
            ?: error("repository root not found")
        val contract = repositoryRoot.resolve("phase1-fixtures").resolve(testId).resolve("README.md")
        check(Files.isRegularFile(contract)) { "missing fixture contract: $contract" }
        check(Files.readString(contract).contains("# $testId fixture contract")) {
            "fixture contract id mismatch: $testId"
        }
    }
}
