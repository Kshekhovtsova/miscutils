package com.github.kshekhovtsova

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneId

fun main(args: Array<String>) {

    try {
        main0(args)
    }
    catch (t: Throwable) {
        t.printStackTrace()
        System.exit(1)
    }
}

private fun main0(args: Array<String>) {
    //todo: parse cli and make help
    val cliArgs = when (args.size) {
        0, 1 -> throw IllegalArgumentException("Not enough args: $args")
        2 -> CliArgs(CliOperation.valueOf(args[0]), Paths.get(args[1]))
        3 -> CliArgs(CliOperation.valueOf(args[0]), Paths.get(args[1]), args[2].split(",").toSet())
        4 -> CliArgs(CliOperation.valueOf(args[0]), Paths.get(args[1]), args[2].split(",").toSet(), ZoneId.of(args[3]))
        else -> throw IllegalArgumentException("Too many args: $args")
    }

    val cliOp = cliArgs.op

    when (cliOp) {
        CliOperation.maketree -> {
            val rootPath = cliArgs.rootPath
            val zoneId = cliArgs.zoneId

            val treePrinter = DirectoryTreePrinter(rootPath, zoneId, cliArgs.excludeDirNames)

            Files.walkFileTree(rootPath, treePrinter)

            println(treePrinter.print())
        }
        else -> UnsupportedOperationException(cliOp.toString())
    }
}

enum class CliOperation {
    maketree,
    parsetree
}

data class CliArgs(
        val op: CliOperation,
        val rootPath: Path,
        val excludeDirNames: Set<String> = emptySet(),
        val zoneId: ZoneId = ZoneId.systemDefault()
)