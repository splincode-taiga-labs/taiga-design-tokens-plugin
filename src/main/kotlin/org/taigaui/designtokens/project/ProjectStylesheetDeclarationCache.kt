package org.taigaui.designtokens.project

import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenSourceExtractor
import java.nio.file.Path

internal fun interface ProjectStylesheetModificationStampProvider {
    fun get(sourceFile: Path): Long?
}

internal class ProjectStylesheetDeclarationCache(
    private val sourceExtractor: DesignTokenSourceExtractor,
    private val modificationStampProvider: ProjectStylesheetModificationStampProvider,
) {
    private val lock = Any()
    private val entries = linkedMapOf<Path, Entry>()

    fun extract(sourceFile: Path): List<DesignTokenDeclaration> {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()

        while (true) {
            val stampBefore = modificationStampProvider.get(normalizedSourceFile)
                ?: return sourceExtractor.extract(normalizedSourceFile)

            synchronized(lock) {
                entries[normalizedSourceFile]
                    ?.takeIf { entry -> entry.modificationStamp == stampBefore }
                    ?.let { entry -> return entry.declarations }
            }

            val declarations = sourceExtractor.extract(normalizedSourceFile)
            val stampAfter = modificationStampProvider.get(normalizedSourceFile)
                ?: return declarations

            if (stampBefore != stampAfter) {
                continue
            }

            synchronized(lock) {
                entries[normalizedSourceFile]
                    ?.takeIf { entry -> entry.modificationStamp == stampAfter }
                    ?.let { entry -> return entry.declarations }

                entries[normalizedSourceFile] =
                    Entry(
                        modificationStamp = stampAfter,
                        declarations = declarations,
                    )
            }

            return declarations
        }
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

    private data class Entry(
        val modificationStamp: Long,
        val declarations: List<DesignTokenDeclaration>,
    )
}
