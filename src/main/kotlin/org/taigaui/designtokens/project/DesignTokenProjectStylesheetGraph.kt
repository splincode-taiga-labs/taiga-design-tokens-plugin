package org.taigaui.designtokens.project

import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path

internal data class ProjectStylesheetIndexRequest(
    val workspaceRoot: Path,
    val projectRoot: Path,
    val entryFiles: List<Path>,
) {
    fun normalized(): ProjectStylesheetIndexRequest =
        copy(
            workspaceRoot = workspaceRoot.toAbsolutePath().normalize(),
            projectRoot = projectRoot.toAbsolutePath().normalize(),
            entryFiles =
                entryFiles
                    .map(Path::toAbsolutePath)
                    .map(Path::normalize)
                    .distinct(),
        )
}

internal data class ProjectStylesheetScope(
    val projectRoot: Path,
    val sourceFiles: List<Path>,
)

internal class DesignTokenProjectStylesheetGraph(
    project: Project,
    readText: (Path) -> String? = { path ->
        runCatching { Files.readString(path) }.getOrNull()
    },
    imports: (Path) -> List<String> = { path ->
        readText(path)
            ?.let(ProjectStylesheetImportParser::parse)
            .orEmpty()
    },
) {
    private val entrypointResolver = ProjectStylesheetEntrypointResolver(project, readText)
    private val importResolver = ProjectStylesheetImportResolver(imports)

    fun createRequest(
        sourceFile: Path,
        workspaceRootHint: Path? = null,
    ): ProjectStylesheetIndexRequest {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val workspaceRoot = findWorkspaceRoot(normalizedSourceFile, workspaceRootHint)
        val projectRoot = findProjectRoot(normalizedSourceFile, workspaceRoot)
        val entryFiles =
            entrypointResolver.find(
                sourceFile = normalizedSourceFile,
                projectRoot = projectRoot,
                workspaceRoot = workspaceRoot,
            )

        return ProjectStylesheetIndexRequest(
            workspaceRoot = workspaceRoot,
            projectRoot = projectRoot,
            entryFiles = entryFiles,
        ).normalized()
    }

    fun buildScope(request: ProjectStylesheetIndexRequest): ProjectStylesheetScope {
        val normalizedRequest = request.normalized()
        val reachableFiles =
            findReachableFiles(
                entryFiles = normalizedRequest.entryFiles,
                projectRoot = normalizedRequest.projectRoot,
                workspaceRoot = normalizedRequest.workspaceRoot,
            )

        return ProjectStylesheetScope(
            projectRoot = normalizedRequest.projectRoot,
            sourceFiles = reachableFiles,
        )
    }

    private fun findReachableFiles(
        entryFiles: Collection<Path>,
        projectRoot: Path,
        workspaceRoot: Path,
    ): List<Path> {
        val visited = linkedSetOf<Path>()
        val ordered = mutableListOf<Path>()

        fun visit(path: Path) {
            val sourceFile = ProjectStylesheetPathResolver.normalize(path)

            if (
                !ProjectStylesheetPathResolver.isStylesheet(sourceFile) ||
                ProjectStylesheetPathResolver.isNodeModulesPath(sourceFile, workspaceRoot) ||
                !visited.add(sourceFile)
            ) {
                return
            }

            importResolver
                .resolveImports(sourceFile, projectRoot, workspaceRoot)
                .forEach(::visit)
            ordered.add(sourceFile)
        }

        entryFiles.forEach(::visit)

        return ordered
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
