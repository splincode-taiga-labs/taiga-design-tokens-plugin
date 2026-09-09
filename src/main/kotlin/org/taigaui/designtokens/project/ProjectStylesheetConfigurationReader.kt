package org.taigaui.designtokens.project

import java.nio.file.Path

internal class ProjectStylesheetConfigurationReader(
    private val readText: (Path) -> String?,
    private val parser: ProjectStylesheetJsonPsiParser,
) {
    fun readStyleGroups(
        configFile: Path,
        projectRoot: Path,
        workspaceRoot: Path,
    ): List<List<Path>> =
        readText(configFile)
            ?.let(parser::parseStyleGroups)
            ?.map { group ->
                group.mapNotNull { configuredPath ->
                    resolveConfiguredPath(
                        configFile = configFile,
                        configuredPath = configuredPath,
                        projectRoot = projectRoot,
                        workspaceRoot = workspaceRoot,
                    )
                }
            }?.filter { group -> group.isNotEmpty() }
            .orEmpty()

    private fun resolveConfiguredPath(
        configFile: Path,
        configuredPath: String,
        projectRoot: Path,
        workspaceRoot: Path,
    ): Path? {
        val normalizedPath = configuredPath.substringBefore('?').substringBefore('#').trim()

        if (
            ProjectStylesheetPathResolver.isExternalImport(normalizedPath) ||
            ProjectStylesheetPathResolver.isPackageImport(normalizedPath)
        ) {
            return null
        }

        return buildList {
            configFile.parent?.let { directory -> add(directory.resolve(normalizedPath)) }
            add(projectRoot.resolve(normalizedPath))
            add(workspaceRoot.resolve(normalizedPath))
        }.asSequence()
            .map(ProjectStylesheetPathResolver::normalize)
            .filter { path -> path.startsWith(workspaceRoot) }
            .filterNot { path -> ProjectStylesheetPathResolver.isNodeModulesPath(path, workspaceRoot) }
            .mapNotNull(ProjectStylesheetPathResolver::resolveSourceFile)
            .firstOrNull()
    }
}
