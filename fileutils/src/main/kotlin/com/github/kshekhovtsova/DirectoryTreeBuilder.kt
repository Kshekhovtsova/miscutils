package com.github.kshekhovtsova

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Map.Entry.comparingByValue
import java.util.stream.Collectors

class DirectoryTreeBuilder(
        rootPath: Path,
        val timezoneId: ZoneId,
        val excludedDirNames: Set<String>,
        val indentSymbol: String = "_",
        val indentSize: Int = 2
) : SimpleFileVisitor<Path>() {
    private val rootPathNameCount = rootPath.nameCount
    private val treeBuilder = StringBuilder()
    private var counter = 0L
    private val forbiddenPaths = mutableMapOf<String, String>()
    private val allFiles = mutableListOf<TreeEntry>()

    init {
        if (!Files.isDirectory(rootPath)) {
            throw IllegalArgumentException("Root path $rootPath should be directory")
        }
    }

    /**
     * rootdir(1)
     * |__file1(2)
     * |__file2(2)
     * |__dir1(2)
     *    |__dir1-1(3)
     *       |__file1-1-1(4)
     * |__dir2(2)
     */
    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        appendToTree(file, attrs, true)
        return FileVisitResult.CONTINUE
    }

    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
        val dirName = dir.fileName?.toString()

        if (excludedDirNames.any { excludedDir -> dirName == excludedDir }) {
            return FileVisitResult.SKIP_SUBTREE
        }

        appendToTree(dir, attrs, false)
        return FileVisitResult.CONTINUE
    }

    override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
        exc?.let {
            forbiddenPaths.put(dir.toString(), it.cause?.message ?: (it.message ?: ""))
        }
        return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
        exc?.let {
            forbiddenPaths.put(file.toString(), it.cause?.message ?: (it.message ?: ""))
        }
        return FileVisitResult.CONTINUE
    }

    private fun appendToTree(path: Path, attrs: BasicFileAttributes, isFile: Boolean) {
        val nameCount = path.nameCount
        val interval = nameCount - rootPathNameCount

        treeBuilder.append(String.format("% 9d", counter++)).append("| ")

        if (interval == 0) {
            treeBuilder.append("Tree excluding: $excludedDirNames, root directory: ").append(path.parent ?: path).append("\\")
        }

        if (interval > 1) {
            // file NOT in root path
            val depth = interval - 1
            val indentFromRoot = depth * indentSize + depth
            treeBuilder
                    .append(" ".repeat(indentFromRoot))
                    .append("|")
                    .append(indentSymbol.repeat(indentSize))
        }

        val fileName: String = path.fileName?.toString() ?: ""

        if (isFile) {
            allFiles.add(TreeEntry(fileName, attrs.size(), path))
        }

        treeBuilder
                .append(fileName)
                .append("| /T@${attrs.printFileType()}")
                .append("/S@${if (isFile) attrs.size() else 0}")
                .append("/C@${attrs.creationTime().format(timezoneId)}")
                .append("/M@${attrs.lastModifiedTime().format(timezoneId)}")
                .append("/A@${attrs.lastAccessTime().format(timezoneId)}")
                .appendln()
    }

    fun tree() = treeBuilder.toString()

    fun duplicates() = getDuplicates(allFiles)

    fun errors() = forbiddenPaths.toString()
}

private fun getDuplicates(files: List<TreeEntry>): String {
    val duplicates = files
            .groupBy { DistinctBy(it.fileName, it.size) }
            .filter { (_, v) -> v.isNotEmpty() }

    val parents = HashMap<String, Long>()

    duplicates.forEach { (_, v) ->
        if (v.size > 1) {
            v.forEach { entry ->
                entry.path.parent?.let {
                    val parentDir = entry.path.parent.toString()
                    parents.compute(parentDir, { _, v -> if (v == null) 1L else v + 1 })
                }
            }
        }
    }

    val sb = StringBuilder()

    parents.entries
            .sortedByDescending { e -> e.value }
            .forEach { e -> sb.appendln(e) }

    return sb.toString()
}

private fun FileTime.format(timezoneId: ZoneId) =
        this.toInstant().atZone(timezoneId).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

fun BasicFileAttributes.printFileType() =
        "D${if (this.isDirectory) '+' else '-'}F${if (this.isRegularFile) '+' else '-'}" +
                "S${if (this.isSymbolicLink) '+' else '-'}O${if (this.isOther) '+' else '-'}"
