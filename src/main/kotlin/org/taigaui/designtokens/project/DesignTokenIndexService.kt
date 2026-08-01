package org.taigaui.designtokens.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
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
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class DesignTokenIndexService(
    project: Project,
) {
    private val packageResolver = DesignTokensPackageResolver()
    private val packageScanner =
        DesignTokensPackageScanner(
            sourceExtractor = PsiDesignTokenSourceExtractor(project),
        )
    private val cache =
        DesignTokenIndexCache { designTokensPackage ->
            DesignTokenIndex.build(
                packageRoot = designTokensPackage.realRoot,
                declarations = packageScanner.scan(designTokensPackage),
            )
        }

    internal val cachedPackageCount: Int
        get() = cache.size

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
    }

    fun getIndex(sourceFile: Path): DesignTokenIndex? {
        val designTokensPackage = packageResolver.resolve(sourceFile) ?: return null

        return runCatching {
            cache.getOrBuild(designTokensPackage)
        }.onFailure { error ->
            LOG.warn(
                "Failed to build the @taiga-ui/design-tokens index from ${designTokensPackage.realRoot}",
                error,
            )
        }.getOrNull()
    }

    internal fun invalidate(changedPaths: Collection<Path>): Int = cache.invalidate(changedPaths)

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

    private fun String.toPathOrNull(): Path? = runCatching(Path::of).getOrNull()
}
