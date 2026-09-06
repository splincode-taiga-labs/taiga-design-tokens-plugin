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
import org.taigaui.designtokens.diagnostics.PerformanceDiagnostics
import org.taigaui.designtokens.diagnostics.PerformanceMetric
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokensPackageScanner
import org.taigaui.designtokens.packageinfo.DesignTokensPackage
import org.taigaui.designtokens.packageinfo.DesignTokensPackageResolver
import org.taigaui.designtokens.psi.PsiDesignTokenSourceExtractor
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
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
            val declarations =
                PerformanceDiagnostics.measure(PerformanceMetric.PACKAGE_SCAN) {
                    packageScanner.scan(designTokensPackage)
                }

            DesignTokenIndex.build(
                packageRoot = designTokensPackage.realRoot,
                declarations = declarations,
            )
        }
    private val packageNameCatalogCache =
        DesignTokenIndexCache { designTokensPackage ->
            val declarations =
                PerformanceDiagnostics.measure(PerformanceMetric.PACKAGE_SCAN) {
                    packageScanner.scanAll(designTokensPackage)
                }

            DesignTokenIndex.build(
                packageRoot = designTokensPackage.realRoot,
                declarations = declarations,
            )
        }
    private val projectStylesheetIndexProvider =
        ProjectStylesheetIndexProvider(
            packageResolver = packageResolver,
            sourceExtractor = sourceExtractor,
        )
    private val resolutionSnapshotCache = DesignTokenResolutionSnapshotCache()

    internal val cachedPackageCount: Int
        get() = cache.size

    internal val cachedProjectIndexCount: Int
        get() = projectStylesheetIndexProvider.size

    internal val cachedResolutionSnapshotCount: Int
        get() = resolutionSnapshotCache.size

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
        resolutionSnapshot(sourceFile)
            .resolver
            ?.let { resolver ->
                PerformanceDiagnostics.measure(PerformanceMetric.VALUE_RESOLUTION) {
                    resolver.resolveGrouped(tokenName)
                }
            }.orEmpty()

    internal fun completionTokenNames(sourceFile: Path): List<String> =
        resolutionSnapshot(
            sourceFile = sourceFile,
            requireCompleteNameCatalog = true,
        ).tokenNames

    internal fun isIndexCached(sourceFile: Path): Boolean =
        runCatching {
            val designTokensPackage = packageResolver.resolve(sourceFile)
            val packageIndexCached = designTokensPackage?.let(cache::contains) ?: true
            val packageNameCatalogCached =
                designTokensPackage
                    ?.takeIf { packageSet -> packageSet.needsCompleteNameCatalog() }
                    ?.let(packageNameCatalogCache::contains)
                    ?: true
            val projectIndexCached =
                projectStylesheetIndexProvider.isCached(sourceFile, designTokensPackage)

            packageIndexCached && packageNameCatalogCached && projectIndexCached
        }.getOrDefault(false)

    internal fun getIndexOrThrow(sourceFile: Path): DesignTokenIndex? =
        packageResolver
            .resolve(sourceFile)
            ?.let(cache::getOrBuild)

    internal fun invalidate(changedPaths: Collection<Path>): Int {
        val packageInvalidated = cache.invalidate(changedPaths)

        packageNameCatalogCache.invalidate(changedPaths)

        return packageInvalidated + projectStylesheetIndexProvider.invalidate(changedPaths)
    }

    internal fun clear() {
        cache.clear()
        packageNameCatalogCache.clear()
        projectStylesheetIndexProvider.clear()
        resolutionSnapshotCache.clear()
    }

    private fun resolutionSnapshot(
        sourceFile: Path,
        requireCompleteNameCatalog: Boolean = false,
    ): DesignTokenResolutionSnapshot {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val installedIndex = getIndex(normalizedSourceFile)
        val designTokensPackage = packageResolver.resolve(normalizedSourceFile)
        val nameCatalogIndex =
            designTokensPackage
                ?.takeIf { packageSet -> packageSet.needsCompleteNameCatalog() }
                ?.takeIf { packageSet ->
                    requireCompleteNameCatalog || packageNameCatalogCache.contains(packageSet)
                }?.let(packageNameCatalogCache::getOrBuild)
                ?: installedIndex
        val projectIndex = projectStylesheetIndexProvider.getIndex(normalizedSourceFile)

        return resolutionSnapshotCache.getOrBuild(
            sourceFile = normalizedSourceFile,
            inputs =
                DesignTokenResolutionSnapshotInputs(
                    installedIndex = installedIndex,
                    projectIndex = projectIndex,
                    nameCatalogIndex = nameCatalogIndex,
                ),
        )
    }

    private fun DesignTokensPackage.needsCompleteNameCatalog(): Boolean =
        sourcePackages.any { sourcePackage -> sourcePackage.name == PROPRIETARY_PACKAGE }

    private companion object {
        val LOG = Logger.getInstance(DesignTokenIndexService::class.java)
        const val PROPRIETARY_PACKAGE = "@taiga-ui/proprietary"
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
