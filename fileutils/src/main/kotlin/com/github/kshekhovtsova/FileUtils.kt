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

            val treePrinter = DirectoryTreePrinter(rootPath, zoneId, cliArgs.excludeDirNames)

            Files.walkFileTree(rootPath, treePrinter)

            println(treePrinter.print())
        }
        CliOperation.parsetree -> {
            val treePath = when (args.size) {
                1 -> throw IllegalArgumentException("Not enough args: $args")
                2 -> Paths.get(args[1])
                else -> throw IllegalArgumentException("Too many args: $args")
            }

            print(getDuplicates(treePath))

        }
        else -> UnsupportedOperationException(cliOp.toString())
    }
}

private fun getDuplicates(path: Path): String {
    val allEntries = Files
            .lines(path)
            .map { TreeEntry.parse(it, "_", 2) }
            .collect(Collectors.toList())

    val duplicates = allEntries
            .groupBy { DistinctBy(it.fileName, it.type, it.size) }
            .filter { (_, v) -> v.size > 1 }

    val sb = StringBuilder()

    duplicates.forEach { (k, v) ->
        sb.appendln(k)
        v.forEach { entry -> sb.appendln("Num in tree: " + entry.num) }
    }

    return sb.toString()
}

data class DistinctBy(
        val fileName: String,
        val type: String,
        val size: String
)

data class TreeEntry(
        val num: Int,
        val fileName: String,
        val type: String,
        val size: String,
        val created: String,
        val modified: String
) {
    companion object {
        //    |__dir1-1| /T@D+F-S-O-/S@0/C@2019-01-06T15:54:26.875404+03:00/M@2019-01-06T15:54:41.221862+03:00/A@2019-01-06T15:54:41.221862+03:00
        fun parse(text: String, indentSymbol: String, indentSize: Int): TreeEntry {
            val num = text.substringBefore("| ").toInt()
            val indentPathChars = "|" + indentSymbol.repeat(indentSize)
            var line = text.substringAfter("| ")
            if (line.contains(indentPathChars)) {
                line = text.substringAfter(indentPathChars)
            }
            val fileName = line.substringBefore("|")
            val parsed = line.substringAfter("/").split("/")
            if (parsed.size != 5) throw IllegalArgumentException(text)
            return TreeEntry(
                    num,
                    fileName,
                    parsed[0].substringAfter("T@"),
                    parsed[1].substringAfter("S@"),
                    parsed[2].substringAfter("C@"),
                    parsed[3].substringAfter("M@")
            )
        }
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