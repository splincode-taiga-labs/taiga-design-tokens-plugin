package org.taigaui.designtokens.project

import org.taigaui.designtokens.cache.GenerationAwareSingleFlight
import org.taigaui.designtokens.cache.PendingBuildAction
import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path

internal data class ProjectStylesheetIndexBuildResult(
    val index: DesignTokenIndex,
    val dependencies: Set<Path>,
) {
    fun normalized(): ProjectStylesheetIndexBuildResult =
        copy(
            dependencies =
                dependencies
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .toSet(),
        )
}

internal fun interface ProjectStylesheetIndexBuilder {
    fun build(request: ProjectStylesheetIndexRequest): ProjectStylesheetIndexBuildResult
}

internal class ProjectStylesheetIndexCache(
    private val indexBuilder: ProjectStylesheetIndexBuilder,
) {
    private val lock = Any()
    private val entries = linkedMapOf<ProjectStylesheetIndexRequest, ProjectStylesheetIndexBuildResult>()
    private val singleFlight =
        GenerationAwareSingleFlight<
            ProjectStylesheetIndexRequest,
            ProjectStylesheetIndexBuildResult,
            PendingInvalidationMetadata,
        >(::PendingInvalidationMetadata)

    val size: Int
        get() = synchronized(lock) { entries.size }

    fun contains(request: ProjectStylesheetIndexRequest): Boolean =
        synchronized(lock) {
            entries.containsKey(request.normalized())
        }

    fun getOrBuild(request: ProjectStylesheetIndexRequest): DesignTokenIndex {
        val normalizedRequest = request.normalized()
        val cached = synchronized(lock) { entries[normalizedRequest] }

        if (cached != null) {
            return cached.index
        }

        return singleFlight
            .getOrBuild(
                key = normalizedRequest,
                build = {
                    synchronized(lock) { entries[normalizedRequest] }
                        ?: indexBuilder.build(normalizedRequest).normalized()
                },
                isCurrent = { pending, result ->
                    !pending.wasInvalidated { changedPath ->
                        result.isAffectedBy(normalizedRequest, changedPath)
                    }
                },
                publish = { result ->
                    synchronized(lock) {
                        entries[normalizedRequest] = result
                    }
                },
            ).index
    }

    fun invalidate(changedPaths: Collection<Path>): Int {
        val normalizedPaths =
            changedPaths
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .distinct()

        singleFlight.updatePending { request, pending ->
            val broadInvalidation =
                normalizedPaths.any { changedPath -> request.isBroadlyAffectedBy(changedPath) }

            if (broadInvalidation) {
                PendingBuildAction.INVALIDATE
            } else {
                pending.recordChanges(normalizedPaths)
                PendingBuildAction.KEEP
            }
        }

        return synchronized(lock) {
            val sizeBefore = entries.size

            entries.entries.removeIf { (request, entry) ->
                normalizedPaths.any { changedPath -> entry.isAffectedBy(request, changedPath) }
            }

            sizeBefore - entries.size
        }
    }

    fun clear() {
        singleFlight.clear()

        synchronized(lock) {
            entries.clear()
        }
    }

    private fun ProjectStylesheetIndexBuildResult.isAffectedBy(
        request: ProjectStylesheetIndexRequest,
        changedPath: Path,
    ): Boolean =
        request.isBroadlyAffectedBy(changedPath) ||
            dependencies.any { dependency ->
                dependency == changedPath || dependency.startsWith(changedPath)
            }

    private fun ProjectStylesheetIndexRequest.isBroadlyAffectedBy(changedPath: Path): Boolean {
        val nodeModulesRoot = workspaceRoot.resolve(NODE_MODULES).toAbsolutePath().normalize()

        if (changedPath.startsWith(nodeModulesRoot)) {
            return false
        }

        val workspaceChanged = changedPath == workspaceRoot || workspaceRoot.startsWith(changedPath)
        val structuralInputChanged =
            changedPath.startsWith(workspaceRoot) && changedPath.isStructuralProjectPath()

        return workspaceChanged || structuralInputChanged
    }

    private fun Path.isStructuralProjectPath(): Boolean {
        val fileName = fileName?.toString()?.lowercase() ?: return true

        return fileName == ANGULAR_JSON ||
            fileName == NX_JSON ||
            fileName == PROJECT_JSON ||
            fileName == PACKAGE_JSON ||
            '.' !in fileName
    }

    private companion object {
        const val ANGULAR_JSON = "angular.json"
        const val NX_JSON = "nx.json"
        const val PROJECT_JSON = "project.json"
        const val PACKAGE_JSON = "package.json"
        const val NODE_MODULES = "node_modules"
    }
}

private class PendingInvalidationMetadata {
    private val changedPaths = linkedSetOf<Path>()

    fun recordChanges(paths: Collection<Path>) {
        changedPaths.addAll(paths)
    }

    fun wasInvalidated(isAffected: (Path) -> Boolean): Boolean = changedPaths.any(isAffected)
}
