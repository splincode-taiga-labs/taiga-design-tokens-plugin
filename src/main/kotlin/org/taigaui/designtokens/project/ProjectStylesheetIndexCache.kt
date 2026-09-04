package org.taigaui.designtokens.project

import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

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
    private val pendingBuilds = linkedMapOf<ProjectStylesheetIndexRequest, PendingBuild>()

    val size: Int
        get() = synchronized(lock) { entries.size }

    fun contains(request: ProjectStylesheetIndexRequest): Boolean =
        synchronized(lock) {
            entries.containsKey(request.normalized())
        }

    fun getOrBuild(request: ProjectStylesheetIndexRequest): DesignTokenIndex {
        val normalizedRequest = request.normalized()
        val access =
            synchronized(lock) {
                entries[normalizedRequest]?.let { entry ->
                    return entry.index
                }

                pendingBuilds[normalizedRequest]
                    ?.let { pendingBuild -> BuildAccess(pendingBuild, shouldBuild = false) }
                    ?: PendingBuild()
                        .also { pendingBuild -> pendingBuilds[normalizedRequest] = pendingBuild }
                        .let { pendingBuild -> BuildAccess(pendingBuild, shouldBuild = true) }
            }

        return if (access.shouldBuild) {
            buildAndPublish(normalizedRequest, access.pendingBuild)
        } else {
            access.pendingBuild.await()
        }
    }

    fun invalidate(changedPaths: Collection<Path>): Int =
        synchronized(lock) {
            val normalizedPaths =
                changedPaths
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .distinct()
            val sizeBefore = entries.size

            entries.entries.removeIf { (request, entry) ->
                normalizedPaths.any { changedPath -> entry.isAffectedBy(request, changedPath) }
            }

            pendingBuilds.entries.removeIf { (request, pendingBuild) ->
                val broadInvalidation =
                    normalizedPaths.any { changedPath -> request.isBroadlyAffectedBy(changedPath) }

                if (broadInvalidation) {
                    true
                } else {
                    pendingBuild.recordChanges(normalizedPaths)
                    false
                }
            }

            sizeBefore - entries.size
        }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            pendingBuilds.clear()
        }
    }

    private fun buildAndPublish(
        request: ProjectStylesheetIndexRequest,
        pendingBuild: PendingBuild,
    ): DesignTokenIndex =
        runCatching { indexBuilder.build(request).normalized() }
            .fold(
                onSuccess = { result -> publishSuccess(request, pendingBuild, result) },
                onFailure = { error -> publishFailure(request, pendingBuild, error) },
            )

    private fun publishSuccess(
        request: ProjectStylesheetIndexRequest,
        pendingBuild: PendingBuild,
        result: ProjectStylesheetIndexBuildResult,
    ): DesignTokenIndex {
        synchronized(lock) {
            if (pendingBuilds[request] === pendingBuild) {
                pendingBuilds.remove(request)

                if (
                    !pendingBuild.wasInvalidated { changedPath ->
                        result.isAffectedBy(request, changedPath)
                    }
                ) {
                    entries[request] = result
                }
            }
        }
        pendingBuild.complete(result.index)

        return result.index
    }

    private fun publishFailure(
        request: ProjectStylesheetIndexRequest,
        pendingBuild: PendingBuild,
        error: Throwable,
    ): Nothing {
        synchronized(lock) {
            if (pendingBuilds[request] === pendingBuild) {
                pendingBuilds.remove(request)
            }
        }
        pendingBuild.completeExceptionally(error)

        throw error
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

    private class PendingBuild {
        private val future = CompletableFuture<DesignTokenIndex>()
        private val changedPaths = linkedSetOf<Path>()

        fun recordChanges(paths: Collection<Path>) {
            changedPaths.addAll(paths)
        }

        fun wasInvalidated(isAffected: (Path) -> Boolean): Boolean = changedPaths.any(isAffected)

        fun complete(index: DesignTokenIndex) {
            future.complete(index)
        }

        fun completeExceptionally(error: Throwable) {
            future.completeExceptionally(error)
        }

        fun await(): DesignTokenIndex =
            try {
                future.join()
            } catch (error: CompletionException) {
                throw error.cause ?: error
            }
    }

    private data class BuildAccess(
        val pendingBuild: PendingBuild,
        val shouldBuild: Boolean,
    )

    private companion object {
        const val ANGULAR_JSON = "angular.json"
        const val NX_JSON = "nx.json"
        const val PROJECT_JSON = "project.json"
        const val PACKAGE_JSON = "package.json"
        const val NODE_MODULES = "node_modules"
    }
}
