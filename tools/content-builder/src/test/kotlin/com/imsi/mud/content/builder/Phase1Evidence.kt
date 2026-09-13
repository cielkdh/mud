package com.imsi.mud.content.builder

import java.nio.file.Files
import java.nio.file.Path

internal fun writePhase1Evidence(testId: String, evidenceJson: String) {
    val repositoryRoot = Path.of("..", "..").toAbsolutePath().normalize()
    val contract = repositoryRoot.resolve("phase1-fixtures").resolve(testId).resolve("README.md")
    check(Files.isRegularFile(contract)) { "missing fixture contract: $contract" }
    check(Files.readString(contract).contains("# $testId fixture contract")) { "fixture contract id mismatch: $testId" }
    val output = phase1EvidenceDirectory(testId)
    output.toFile().deleteRecursively()
    Files.createDirectories(output)
    Files.writeString(output.resolve("evidence.json"), evidenceJson.trimEnd() + "\n")
}

internal fun phase1EvidenceDirectory(testId: String): Path =
    Path.of("..", "..").toAbsolutePath().normalize()
        .resolve("build").resolve("phase1-fixtures").resolve(testId)
