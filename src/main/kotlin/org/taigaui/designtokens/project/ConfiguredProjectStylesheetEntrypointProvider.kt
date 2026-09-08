package org.taigaui.designtokens.project

import java.nio.file.Files
import java.nio.file.Path

internal class ConfiguredProjectStylesheetEntrypointProvider(
    private val readText: (Path) -> String?,
) : ProjectStylesheetEntrypointProvider {
    override fun find(context: ProjectStylesheetEntrypointContext): List<Path> =
        buildList {
            val projectConfig = context.projectRoot.resolve(PROJECT_JSON)
            val workspaceConfig = context.workspaceRoot.resolve(ANGULAR_JSON)

            if (Files.isRegularFile(projectConfig)) {
                addAll(
                    readConfiguredStyleGroups(
                        configFile = projectConfig,
                        projectRoot = context.projectRoot,
                        workspaceRoot = context.workspaceRoot,
                    ).flatten(),
                )
            }

            if (Files.isRegularFile(workspaceConfig)) {
                val workspaceGroups =
                    readConfiguredStyleGroups(
                        configFile = workspaceConfig,
                        projectRoot = context.projectRoot,
                        workspaceRoot = context.workspaceRoot,
                    )
                val projectEntries =
                    if (context.projectRoot == context.workspaceRoot) {
                        workspaceGroups.closestTo(context.sourceFile).flatten()
                    } else {
                        workspaceGroups
                            .map { group -> group.filter { path -> path.startsWith(context.projectRoot) } }
                            .flatten()
                    }

                addAll(projectEntries)
            }
        }.distinct()

    private fun readConfiguredStyleGroups(
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

    private fun List<List<Path>>.closestTo(sourceFile: Path): List<List<Path>> {
        val groupsWithScore =
            map { group ->
                group to group.maxOf { path -> sharedPrefixSize(sourceFile, path) }
            }
        val highestScore = groupsWithScore.maxOfOrNull { (_, score) -> score } ?: return emptyList()

        return groupsWithScore
            .filter { (_, score) -> score == highestScore }
            .map(Pair<List<Path>, Int>::first)
    }

    private fun sharedPrefixSize(
        first: Path,
        second: Path,
    ): Int {
        val firstPath = ProjectStylesheetPathResolver.normalize(first)
        val secondPath = ProjectStylesheetPathResolver.normalize(second)
        val maxLength = minOf(firstPath.nameCount, secondPath.nameCount)

        return (0 until maxLength)
            .takeWhile { index -> firstPath.getName(index) == secondPath.getName(index) }
            .count()
    }

    private companion object {
        const val ANGULAR_JSON = "angular.json"
        const val PROJECT_JSON = "project.json"
        val STYLES_ARRAY_PATTERN = Regex("""(?s)[\"']styles[\"']\s*:\s*\[(.*?)]""")
        val QUOTED_STYLESHEET_PATTERN =
            Regex(
                pattern = """[\"']([^\"']+\.(?:css|less|scss))[\"']""",
                option = RegexOption.IGNORE_CASE,
            )
    }
}
