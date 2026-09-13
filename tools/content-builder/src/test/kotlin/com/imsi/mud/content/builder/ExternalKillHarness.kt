package com.imsi.mud.content.builder

import java.nio.file.Path

object ExternalKillHarness {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 6)
        ContentBuilder.build(
            ContentBuildRequest(
                sourceRoot = Path.of(args[0]),
                outputRoot = Path.of(args[1]),
                ddlPath = Path.of(args[2]),
                contentVersion = args[3],
                balanceVersion = "balance.test.v1",
                fault = BuildFault.valueOf(args[4]),
                generatedByVersion = args[5],
            )
        )
    }
}
