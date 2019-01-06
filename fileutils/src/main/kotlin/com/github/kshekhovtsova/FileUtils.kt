package com.github.kshekhovtsova

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneId
import java.util.stream.Collectors

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
    if (args.isEmpty()) throw IllegalArgumentException("No args")

    val cliOp = CliOperation.valueOf(args[0])

    when (cliOp) {
        CliOperation.maketree -> {
            val cliArgs = when (args.size) {
                1 -> throw IllegalArgumentException("Not enough args: $args")
                2 -> CliArgs(CliOperation.valueOf(args[0]), Paths.get(args[1]))
                3 -> CliArgs(CliOperation.valueOf(args[0]), Paths.get(args[1]), args[2].split(",").toSet())
                4 -> CliArgs(CliOperation.valueOf(args[0]), Paths.get(args[1]), args[2].split(",").toSet(), ZoneId.of(args[3]))
                else -> throw IllegalArgumentException("Too many args: $args")
            }

            val rootPath = cliArgs.rootPath
            val zoneId = cliArgs.zoneId

            val treeBuilder = DirectoryTreeBuilder(rootPath, zoneId, cliArgs.excludeDirNames)

            Files.walkFileTree(rootPath, treeBuilder)

            println(treeBuilder.tree())
            println("Duplicates:")
            println(treeBuilder.duplicates())
        }
        else -> UnsupportedOperationException(cliOp.toString())
    }
}

data class DistinctBy(
        val fileName: String,
        val size: Long
)

data class TreeEntry(
        val fileName: String,
        val size: Long,
        val path: Path
)

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