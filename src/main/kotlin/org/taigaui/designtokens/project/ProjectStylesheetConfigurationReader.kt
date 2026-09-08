package org.taigaui.designtokens.project

import java.nio.file.Path

internal class ProjectStylesheetConfigurationReader(
    private val readText: (Path) -> String?,
) {
    fun readStyleGroups(
        configFile: Path,
        projectRoot: Path,
        workspaceRoot: Path,
    ): List<List<Path>> =
        readText(configFile)
            ?.let { content -> STYLES_ARRAY_PATTERN.findAll(content) }
            ?.map { match ->
                QUOTED_STYLESHEET_PATTERN
                    .findAll(match.groupValues[1])
                    .map { styleMatch -> styleMatch.groupValues[1] }
                    .mapNotNull { configuredPath ->
                        resolveConfiguredPath(
                            configFile = configFile,
                            configuredPath = configuredPath,
                            projectRoot = projectRoot,
                            workspaceRoot = workspaceRoot,
                        )
                    }.toList()
            }?.filter { group -> group.isNotEmpty() }
            ?.toList()
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

    private companion object {
        val STYLES_ARRAY_PATTERN = Regex("""(?s)[\"']styles[\"']\s*:\s*\[(.*?)]""")
        val QUOTED_STYLESHEET_PATTERN =
            Regex(
                pattern = """[\"']([^\"']+\.(?:css|less|scss))[\"']""",
                option = RegexOption.IGNORE_CASE,
            )
    }
}
