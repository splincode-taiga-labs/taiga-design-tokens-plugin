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
        var declarations: List<DesignTokenDeclaration>? = null

        while (declarations == null) {
            declarations = extractStable(normalizedSourceFile)
        }

        return declarations
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

    private fun extractStable(sourceFile: Path): List<DesignTokenDeclaration>? {
        val stampBefore = modificationStampProvider.get(sourceFile)
        val cachedDeclarations =
            stampBefore?.let { modificationStamp ->
                cachedDeclarations(sourceFile, modificationStamp)
            }

        return when {
            stampBefore == null -> sourceExtractor.extract(sourceFile)
            cachedDeclarations != null -> cachedDeclarations
            else -> extractAndPublishIfStable(sourceFile, stampBefore)
        }
    }

    private fun extractAndPublishIfStable(
        sourceFile: Path,
        stampBefore: Long,
    ): List<DesignTokenDeclaration>? {
        val declarations = sourceExtractor.extract(sourceFile)
        val stampAfter = modificationStampProvider.get(sourceFile)

        return when {
            stampAfter == null -> declarations
            stampBefore != stampAfter -> null
            else -> publish(sourceFile, stampAfter, declarations)
        }
    }

    private fun cachedDeclarations(
        sourceFile: Path,
        modificationStamp: Long,
    ): List<DesignTokenDeclaration>? =
        synchronized(lock) {
            entries[sourceFile]
                ?.takeIf { entry -> entry.modificationStamp == modificationStamp }
                ?.declarations
        }

    private fun publish(
        sourceFile: Path,
        modificationStamp: Long,
        declarations: List<DesignTokenDeclaration>,
    ): List<DesignTokenDeclaration> =
        synchronized(lock) {
            entries[sourceFile]
                ?.takeIf { entry -> entry.modificationStamp == modificationStamp }
                ?.declarations
                ?: declarations.also { cachedDeclarations ->
                    entries[sourceFile] =
                        Entry(
                            modificationStamp = modificationStamp,
                            declarations = cachedDeclarations,
                        )
                }
        }

    private data class Entry(
        val modificationStamp: Long,
        val declarations: List<DesignTokenDeclaration>,
    )
}
