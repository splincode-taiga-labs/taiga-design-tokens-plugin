package org.taigaui.designtokens.project

import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

internal fun interface ProjectStylesheetIndexBuilder {
    fun build(request: ProjectStylesheetIndexRequest): DesignTokenIndex
}

internal class ProjectStylesheetIndexCache(
    private val indexBuilder: ProjectStylesheetIndexBuilder,
) {
    private val lock = Any()
    private val entries = linkedMapOf<ProjectStylesheetIndexRequest, DesignTokenIndex>()
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
                entries[normalizedRequest]?.let { index ->
                    return index
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

            entries.keys.removeIf { request ->
                normalizedPaths.any { changedPath -> request.isAffectedBy(changedPath) }
            }
            pendingBuilds.keys.removeIf { request ->
                normalizedPaths.any { changedPath -> request.isAffectedBy(changedPath) }
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
        runCatching { indexBuilder.build(request) }
            .fold(
                onSuccess = { index -> publishSuccess(request, pendingBuild, index) },
                onFailure = { error -> publishFailure(request, pendingBuild, error) },
            )

    private fun publishSuccess(
        request: ProjectStylesheetIndexRequest,
        pendingBuild: PendingBuild,
        index: DesignTokenIndex,
    ): DesignTokenIndex {
        synchronized(lock) {
            if (pendingBuilds[request] === pendingBuild) {
                pendingBuilds.remove(request)
                entries[request] = index
            }
        }
        pendingBuild.complete(index)

        return index
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

    private fun ProjectStylesheetIndexRequest.isAffectedBy(changedPath: Path): Boolean {
        val nodeModulesRoot = workspaceRoot.resolve(NODE_MODULES).toAbsolutePath().normalize()

        if (changedPath.startsWith(nodeModulesRoot)) {
            return false
        }

        val workspaceChanged = changedPath == workspaceRoot || workspaceRoot.startsWith(changedPath)
        val relevantProjectFileChanged =
            changedPath.startsWith(workspaceRoot) && changedPath.isRelevantProjectPath()

        return workspaceChanged || relevantProjectFileChanged
    }

    private fun Path.isRelevantProjectPath(): Boolean {
        val fileName = fileName?.toString()?.lowercase() ?: return true
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")

        return fileName == ANGULAR_JSON ||
            fileName == NX_JSON ||
            fileName == PROJECT_JSON ||
            fileName == PACKAGE_JSON ||
            extension in STYLESHEET_EXTENSIONS ||
            '.' !in fileName
    }

    private class PendingBuild {
        private val future = CompletableFuture<DesignTokenIndex>()

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
        val STYLESHEET_EXTENSIONS = setOf("css", "less", "scss")
    }
}
