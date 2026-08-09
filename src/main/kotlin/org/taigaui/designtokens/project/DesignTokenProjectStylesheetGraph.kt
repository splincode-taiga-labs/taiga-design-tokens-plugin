package org.taigaui.designtokens.project

import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque

internal data class ProjectStylesheetIndexRequest(
    val sourceFile: Path,
    val workspaceRoot: Path,
) {
    fun normalized(): ProjectStylesheetIndexRequest =
        copy(
            sourceFile = sourceFile.toAbsolutePath().normalize(),
            workspaceRoot = workspaceRoot.toAbsolutePath().normalize(),
        )
}

internal data class ProjectStylesheetScope(
    val projectRoot: Path,
    val sourceFiles: List<Path>,
)

internal class DesignTokenProjectStylesheetGraph(
    readText: (Path) -> String? = { path ->
        runCatching { Files.readString(path) }.getOrNull()
    },
) {
    private val entrypointResolver = ProjectStylesheetEntrypointResolver(readText)
    private val importResolver = ProjectStylesheetImportResolver(readText)

    fun createRequest(
        sourceFile: Path,
        workspaceRootHint: Path? = null,
    ): ProjectStylesheetIndexRequest {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val workspaceRoot = findWorkspaceRoot(normalizedSourceFile, workspaceRootHint)

        return ProjectStylesheetIndexRequest(
            sourceFile = normalizedSourceFile,
            workspaceRoot = workspaceRoot,
        )
    }

    fun buildScope(request: ProjectStylesheetIndexRequest): ProjectStylesheetScope {
        val normalizedRequest = request.normalized()
        val projectRoot = findProjectRoot(normalizedRequest.sourceFile, normalizedRequest.workspaceRoot)
        val entryFiles =
            entrypointResolver.find(
                sourceFile = normalizedRequest.sourceFile,
                projectRoot = projectRoot,
                workspaceRoot = normalizedRequest.workspaceRoot,
            )
        val reachableFiles =
            findReachableFiles(
                entryFiles = entryFiles,
                projectRoot = projectRoot,
                workspaceRoot = normalizedRequest.workspaceRoot,
            )

        return ProjectStylesheetScope(
            projectRoot = projectRoot,
            sourceFiles = reachableFiles,
        )
    }

    private fun findReachableFiles(
        entryFiles: Collection<Path>,
        projectRoot: Path,
        workspaceRoot: Path,
    ): List<Path> {
        val queue = ArrayDeque(entryFiles.map(ProjectStylesheetPathResolver::normalize))
        val visited = linkedSetOf<Path>()

        while (queue.isNotEmpty()) {
            val sourceFile = queue.removeFirst()

            if (
                visited.add(sourceFile) &&
                ProjectStylesheetPathResolver.isStylesheet(sourceFile) &&
                !ProjectStylesheetPathResolver.isNodeModulesPath(sourceFile, workspaceRoot)
            ) {
                importResolver
                    .resolveImports(sourceFile, projectRoot, workspaceRoot)
                    .forEach(queue::addLast)
            }
        }

        return visited
            .filter(ProjectStylesheetPathResolver::isStylesheet)
            .filterNot { path -> ProjectStylesheetPathResolver.isNodeModulesPath(path, workspaceRoot) }
            .sortedBy(Path::toString)
    }

    private fun findProjectRoot(
        sourceFile: Path,
        workspaceRoot: Path,
    ): Path {
        val sourceDirectory = sourceFile.parent ?: workspaceRoot
        val directories = sourceDirectory.ancestorsUntil(workspaceRoot)

        return directories.firstOrNull { directory -> Files.isRegularFile(directory.resolve(PROJECT_JSON)) }
            ?: directories.firstOrNull { directory -> Files.isRegularFile(directory.resolve(PACKAGE_JSON)) }
            ?: workspaceRoot
    }

    private fun findWorkspaceRoot(
        sourceFile: Path,
        workspaceRootHint: Path?,
    ): Path {
        val sourceDirectory = sourceFile.parent ?: sourceFile
        val ancestors = generateSequence(sourceDirectory, Path::getParent).toList()
        val markerRoot =
            ancestors.firstOrNull { directory ->
                Files.isRegularFile(directory.resolve(ANGULAR_JSON)) ||
                    Files.isRegularFile(directory.resolve(NX_JSON))
            }

        if (markerRoot != null) {
            return markerRoot
        }

        val normalizedHint =
            workspaceRootHint
                ?.toAbsolutePath()
                ?.normalize()
                ?.takeIf(sourceFile::startsWith)

        return normalizedHint
            ?: ancestors.firstOrNull { directory -> Files.isRegularFile(directory.resolve(PACKAGE_JSON)) }
            ?: sourceDirectory
    }

    private fun Path.ancestorsUntil(limit: Path): List<Path> {
        val normalizedLimit = ProjectStylesheetPathResolver.normalize(limit)

        return generateSequence(ProjectStylesheetPathResolver.normalize(this)) { path ->
            path.parent?.takeIf { parent -> path != normalizedLimit && parent.startsWith(normalizedLimit) }
        }.toList()
    }

    private companion object {
        const val ANGULAR_JSON = "angular.json"
        const val NX_JSON = "nx.json"
        const val PROJECT_JSON = "project.json"
        const val PACKAGE_JSON = "package.json"
    }
}
