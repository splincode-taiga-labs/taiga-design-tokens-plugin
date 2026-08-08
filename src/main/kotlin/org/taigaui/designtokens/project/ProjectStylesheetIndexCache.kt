package org.taigaui.designtokens.project

import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path

internal fun interface ProjectStylesheetIndexBuilder {
    fun build(request: ProjectStylesheetIndexRequest): DesignTokenIndex
}

internal class ProjectStylesheetIndexCache(
    private val indexBuilder: ProjectStylesheetIndexBuilder,
) {
    private val lock = Any()
    private val entries = linkedMapOf<ProjectStylesheetIndexRequest, DesignTokenIndex>()

    val size: Int
        get() = synchronized(lock) { entries.size }

    fun contains(request: ProjectStylesheetIndexRequest): Boolean =
        synchronized(lock) {
            entries.containsKey(request.normalized())
        }

    fun getOrBuild(request: ProjectStylesheetIndexRequest): DesignTokenIndex =
        synchronized(lock) {
            val normalizedRequest = request.normalized()

            entries[normalizedRequest]
                ?: indexBuilder.build(normalizedRequest).also { index ->
                    entries[normalizedRequest] = index
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

            entries.entries.removeIf { (request, _) ->
                normalizedPaths.any { changedPath -> request.isAffectedBy(changedPath) }
            }

            sizeBefore - entries.size
        }

    fun clear() {
        synchronized(lock) {
            entries.clear()
        }
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
            fileName == PROJECT_JSON ||
            fileName == PACKAGE_JSON ||
            extension in STYLESHEET_EXTENSIONS ||
            '.' !in fileName
    }

    private companion object {
        const val ANGULAR_JSON = "angular.json"
        const val PROJECT_JSON = "project.json"
        const val PACKAGE_JSON = "package.json"
        const val NODE_MODULES = "node_modules"
        val STYLESHEET_EXTENSIONS = setOf("css", "less", "scss")
    }
}
