package org.taigaui.designtokens.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokensPackageScanner
import org.taigaui.designtokens.packageinfo.DesignTokensPackageResolver
import org.taigaui.designtokens.psi.PsiDesignTokenSourceExtractor
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenValueResolver
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class DesignTokenIndexService(
    project: Project,
) {
    private val packageResolver = DesignTokensPackageResolver()
    private val sourceExtractor = PsiDesignTokenSourceExtractor(project)
    private val packageScanner =
        DesignTokensPackageScanner(
            sourceExtractor = sourceExtractor,
        )
    private val cache =
        DesignTokenIndexCache { designTokensPackage ->
            DesignTokenIndex.build(
                packageRoot = designTokensPackage.realRoot,
                declarations = packageScanner.scan(designTokensPackage),
            )
        }
    private val projectStylesheetIndexProvider =
        ProjectStylesheetIndexProvider(
            packageResolver = packageResolver,
            sourceExtractor = sourceExtractor,
        )

    internal val cachedPackageCount: Int
        get() = cache.size

    internal val cachedProjectIndexCount: Int
        get() = projectStylesheetIndexProvider.size

    init {
        project.messageBus
            .connect()
            .subscribe(
                VirtualFileManager.VFS_CHANGES,
                object : BulkFileListener {
                    override fun after(events: List<VFileEvent>) {
                        invalidate(events.flatMap(VfsEventPaths::from))
                    }
                },
            )

        EditorFactory
            .getInstance()
            .eventMulticaster
            .addDocumentListener(
                object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        FileDocumentManager
                            .getInstance()
                            .getFile(event.document)
                            ?.path
                            ?.toPathOrNull()
                            ?.let { path -> invalidate(listOf(path)) }
                    }
                },
                project,
            )
    }

    fun getIndex(sourceFile: Path): DesignTokenIndex? =
        runCatching {
            getIndexOrThrow(sourceFile)
        }.onFailure { error ->
            LOG.warn(
                "Failed to build the installed Taiga UI style index for $sourceFile",
                error,
            )
        }.getOrNull()

    fun resolveToken(
        sourceFile: Path,
        tokenName: String,
    ): List<DesignTokenResolutionGroup> =
        resolutionIndex(sourceFile)
            ?.let { index -> DesignTokenValueResolver(index).resolveGrouped(tokenName) }
            .orEmpty()

    internal fun completionTokenNames(sourceFile: Path): List<String> =
        buildList {
            getIndexOrThrow(sourceFile)?.names?.let(::addAll)
            projectStylesheetIndexProvider.getIndex(sourceFile)?.names?.let(::addAll)
        }.distinct()
            .sorted()

    internal fun isIndexCached(sourceFile: Path): Boolean =
        runCatching {
            val designTokensPackage = packageResolver.resolve(sourceFile)
            val packageIndexCached = designTokensPackage?.let(cache::contains) ?: true
            val projectIndexCached =
                projectStylesheetIndexProvider.isCached(sourceFile, designTokensPackage)

            packageIndexCached && projectIndexCached
        }.getOrDefault(false)

    internal fun getIndexOrThrow(sourceFile: Path): DesignTokenIndex? =
        packageResolver
            .resolve(sourceFile)
            ?.let(cache::getOrBuild)

    internal fun invalidate(changedPaths: Collection<Path>): Int =
        cache.invalidate(changedPaths) + projectStylesheetIndexProvider.invalidate(changedPaths)

    internal fun clear() {
        cache.clear()
        projectStylesheetIndexProvider.clear()
    }

    private fun resolutionIndex(sourceFile: Path): DesignTokenIndex? {
        val indexes =
            buildList {
                getIndex(sourceFile)?.let(::add)
                projectStylesheetIndexProvider.getIndex(sourceFile)?.let(::add)
            }

        return indexes
            .takeIf { values -> values.isNotEmpty() }
            ?.let { values -> DesignTokenIndex.merge(values) }
    }

    private companion object {
        val LOG = Logger.getInstance(DesignTokenIndexService::class.java)
    }
}

internal object VfsEventPaths {
    fun from(event: VFileEvent): Set<Path> =
        buildSet {
            event.path.toPathOrNull()?.let(::add)

            when (event) {
                is VFileMoveEvent -> {
                    Path.of(event.oldParent.path, event.file.name).let(::add)
                    Path.of(event.newParent.path, event.file.name).let(::add)
                }

                is VFilePropertyChangeEvent -> addRenamePaths(event)
            }
        }

    private fun MutableSet<Path>.addRenamePaths(event: VFilePropertyChangeEvent) {
        if (event.propertyName == VirtualFile.PROP_NAME) {
            val parentPath = event.file.parent?.path
            val oldName = event.oldValue as? String
            val newName = event.newValue as? String

            if (parentPath != null && oldName != null && newName != null) {
                add(Path.of(parentPath, oldName))
                add(Path.of(parentPath, newName))
            }
        }
    }
}

private fun String.toPathOrNull(): Path? = runCatching { Path.of(this) }.getOrNull()
