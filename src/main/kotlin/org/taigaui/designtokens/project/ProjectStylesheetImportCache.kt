package org.taigaui.designtokens.project

import java.nio.file.Path

internal class ProjectStylesheetImportCache(
    private val readText: (Path) -> String?,
    private val modificationStampProvider: ProjectStylesheetModificationStampProvider,
) {
    private val lock = Any()
    private val entries = linkedMapOf<Path, Entry>()

    fun imports(sourceFile: Path): List<String> {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        var imports: List<String>? = null

        while (imports == null) {
            imports = importsStable(normalizedSourceFile)
        }

        return imports
    }

    fun invalidate(changedPaths: Collection<Path>) {
        val normalizedPaths =
            changedPaths
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .distinct()

        synchronized(lock) {
            entries.keys.removeIf { sourceFile ->
                normalizedPaths.any { changedPath ->
                    sourceFile == changedPath || sourceFile.startsWith(changedPath)
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
    }

    private fun importsStable(sourceFile: Path): List<String>? {
        val stampBefore = modificationStampProvider.get(sourceFile)
        val cachedImports =
            stampBefore?.let { modificationStamp ->
                cachedImports(sourceFile, modificationStamp)
            }

        return when {
            stampBefore == null -> parse(sourceFile)
            cachedImports != null -> cachedImports
            else -> parseAndPublishIfStable(sourceFile, stampBefore)
        }
    }

    private fun parseAndPublishIfStable(
        sourceFile: Path,
        stampBefore: Long,
    ): List<String>? {
        val imports = parse(sourceFile)
        val stampAfter = modificationStampProvider.get(sourceFile)

        return when {
            stampAfter == null -> imports
            stampBefore != stampAfter -> null
            else -> publish(sourceFile, stampAfter, imports)
        }
    }

    private fun parse(sourceFile: Path): List<String> =
        readText(sourceFile)
            ?.let(ProjectStylesheetImportParser::parse)
            .orEmpty()

    private fun cachedImports(
        sourceFile: Path,
        modificationStamp: Long,
    ): List<String>? =
        synchronized(lock) {
            entries[sourceFile]
                ?.takeIf { entry -> entry.modificationStamp == modificationStamp }
                ?.imports
        }

    private fun publish(
        sourceFile: Path,
        modificationStamp: Long,
        imports: List<String>,
    ): List<String> =
        synchronized(lock) {
            entries[sourceFile]
                ?.takeIf { entry -> entry.modificationStamp == modificationStamp }
                ?.imports
                ?: imports.also { cachedImports ->
                    entries[sourceFile] =
                        Entry(
                            modificationStamp = modificationStamp,
                            imports = cachedImports,
                        )
                }
        }

    private data class Entry(
        val modificationStamp: Long,
        val imports: List<String>,
    )
}
