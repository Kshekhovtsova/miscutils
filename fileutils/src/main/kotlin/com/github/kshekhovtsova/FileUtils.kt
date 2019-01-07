package com.github.kshekhovtsova

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneId
import java.util.stream.Collectors
import java.util.stream.Stream

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

            val treeBuilder = DirectoryTreeBuilder(rootPath, zoneId, cliArgs.excludeDirNames, true)

            Files.walkFileTree(rootPath, treeBuilder)

            print(treeBuilder.tree())
        }
        CliOperation.parsetree -> {
            val treePath = when (args.size) {
                1 -> throw IllegalArgumentException("Not enough args: $args")
                2 -> Paths.get(args[1])
                else -> throw IllegalArgumentException("Too many args: $args")
            }

            print(getDuplicates(treePath))

        }
        CliOperation.findintree -> {
            val treePathToDirs = when (args.size) {
                1,2 -> throw IllegalArgumentException("Not enough args: $args")
                3 -> Paths.get(args[1]) to args[2].split(",").toSet()
                else -> throw IllegalArgumentException("Too many args: $args")
            }

            val treePath = treePathToDirs.first
            val dirNames = treePathToDirs.second

            val sb = StringBuilder()
            val allPaths = getAllPaths(treePath)
                    .filter { it.type.contains("D+") && dirNames.contains(it.fileName) }
                    .sorted { o1, o2 -> o1.num.compareTo(o2.num) }

            allPaths.forEach { it.path?.let { found -> sb.appendln(found)} }

            println(sb.toString())

        }
        else -> UnsupportedOperationException(cliOp.toString())
    }
}

private fun getDuplicates(treePath: Path): String {

    val allEntries = getAllPaths(treePath).collect(Collectors.toList())

    val fileDuplicates = allEntries
            .groupBy { DistinctBy(it.fileName, it.size, it.type) }
            .filter { (k, v) -> v.size > 1 && k.type.contains("F+") }
            .mapValues { e -> e.value.toSet() }

    val parents = HashMap<Set<FilePath>, Long>()

    fileDuplicates.forEach { (_, v) ->
        val setOfParents = v.map { it.path?.parent ?: FilePath.empty }.toSet()
        parents.compute(setOfParents, { _, v2 -> if (v2 == null) 1L else v2 + 1 })
    }

    val sb = StringBuilder()

    parents.entries
            .sortedByDescending { e -> e.value }
            .forEach { e -> sb.append(e.key.joinToString("\n", "[", "]")).appendln(": ${e.value}") }

    return sb.toString()
}

private fun getAllPaths(treePath: Path): Stream<TreeEntry> {
    return Files
            .lines(treePath)
            .skip(1)
            .map { TreeEntry.parse(it, "_", 2, true, "\\") }
}

data class DistinctBy(
        val fileName: String,
        val size: Long,
        val type: String
)

class TreeEntry(
        val num: Long,
        val fileName: String,
        val type: String,
        val size: Long,
        val path: FilePath?,
        private val text: String = path?.toString() ?: fileName
) {
    override fun toString(): String {
        return text
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TreeEntry

        if (text != other.text) return false

        return true
    }

    override fun hashCode(): Int {
        return text.hashCode()
    }

    companion object {
        //    |__F://dir1-1| /T@D+F-S-O-/S@0/C@2019-01-06T15:54:26.875404+03:00/M@2019-01-06T15:54:41.221862+03:00/A@2019-01-06T15:54:41
        // .221862+03:00
        fun parse(
                text: String,
                indentSymbol: String,
                indentSize: Int,
                fullPath: Boolean,
                pathSeparator: String
        ): TreeEntry {
            val num = text.substringBefore("| ").trim().toLong()
            val indentPathChars = "|" + indentSymbol.repeat(indentSize)
            var line = text.substringAfter("| ")
            if (line.contains(indentPathChars)) {
                line = text.substringAfter(indentPathChars)
            }
            val fileNameOrPath = line.substringBefore("|")
            line = line.substringAfter("| ")
            val parsed = line.substringAfter("/").split("/")
            if (parsed.size != 5) throw IllegalArgumentException(text)

            val path = if (fullPath) {
                FilePath.parse(fileNameOrPath, pathSeparator)
            }
            else {
                null
            }

            return TreeEntry(
                    num,
                    path?.fileName ?: fileNameOrPath,
                    parsed[0].substringAfter("T@"),
                    parsed[1].substringAfter("S@").toLong(),
                    path
            )
        }
    }
}

enum class CliOperation {
    maketree,
    parsetree,
    findintree
}

data class CliArgs(
        val op: CliOperation,
        val rootPath: Path,
        val excludeDirNames: Set<String> = emptySet(),
        val zoneId: ZoneId = ZoneId.systemDefault()
)

class FilePath(
        separator: String? = null,
        parentText: String?,
        private val pathText: String,
        val fileName: String?
) {
    val parent: FilePath? by lazy {
        parentText?.let {
            FilePath.parse(parentText, separator)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FilePath

        if (pathText != other.pathText) return false

        return true
    }

    override fun hashCode(): Int {
        return pathText.hashCode()
    }

    override fun toString(): String {
        return pathText
    }

    companion object {
        val empty: FilePath = parse("/", "/")
        fun parse(text: String, separator: String? = null): FilePath {
            val dirSeparator = separator ?: if (text.contains("\\")) "\\" else "/"

            val pathParts = text.split(dirSeparator)

            if (pathParts.isEmpty()) throw IllegalArgumentException("Invalid path: $text")

            val parentTextToFileName = if (pathParts.size == 1) {
                if (hasWindowsDrive(pathParts)) {
                    //windows path
                    null to null
                }
                else {
                    null to pathParts[0]
                }
            }
            else if (pathParts.size == 2) {
                if (hasWindowsDrive(pathParts)) {
                    //windows path
                    null to pathParts[1]
                }
                else {
                    pathParts[0].nullIfEmpty() to pathParts[1]
                }
            }
            else {
                text.substringBeforeLast(dirSeparator) to pathParts[pathParts.size - 1]
            }

            return FilePath(
                    dirSeparator,
                    parentTextToFileName.first,
                    text,
                    parentTextToFileName.second
            )
        }

        private fun hasWindowsDrive(pathParts: List<String>) = pathParts[0].endsWith(":")
    }
}

fun String.nullIfEmpty(): String? {
    if (this.isEmpty()) return null
    return this
}