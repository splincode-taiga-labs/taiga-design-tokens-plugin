package org.taigaui.designtokens.project

import java.nio.file.Files
import java.nio.file.Path

internal class ProjectStylesheetEntrypointResolver(
    private val readText: (Path) -> String?,
) {
    fun find(
        sourceFile: Path,
        projectRoot: Path,
        workspaceRoot: Path,
    ): Set<Path> =
        buildSet {
            sourceFile
                .takeIf { path -> ProjectStylesheetPathResolver.isStylesheet(path) }
                ?.takeIf { path -> !ProjectStylesheetPathResolver.isNodeModulesPath(path, workspaceRoot) }
                ?.let(::add)

            addAll(configuredEntryFiles(sourceFile, projectRoot, workspaceRoot))

            if (size <= 1) {
                addAll(conventionalEntryFiles(projectRoot, workspaceRoot))
            }
        }

    private fun configuredEntryFiles(
        sourceFile: Path,
        projectRoot: Path,
        workspaceRoot: Path,
    ): Set<Path> =
        buildSet {
            val projectConfig = projectRoot.resolve(PROJECT_JSON)
            val workspaceConfig = workspaceRoot.resolve(ANGULAR_JSON)

            if (Files.isRegularFile(projectConfig)) {
                addAll(
                    readConfiguredStyleGroups(projectConfig, projectRoot, workspaceRoot)
                        .flatten(),
                )
            }

            if (Files.isRegularFile(workspaceConfig)) {
                val workspaceGroups =
                    readConfiguredStyleGroups(workspaceConfig, projectRoot, workspaceRoot)
                val projectEntries =
                    if (projectRoot == workspaceRoot) {
                        workspaceGroups.closestTo(sourceFile).flatten()
                    } else {
                        workspaceGroups
                            .map { group -> group.filter { path -> path.startsWith(projectRoot) } }
                            .flatten()
                    }

                addAll(projectEntries)
            }
        }

    private fun readConfiguredStyleGroups(
        configFile: Path,
        projectRoot: Path,
        workspaceRoot: Path,
    ): List<Set<Path>> =
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
                    }.toSet()
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

    private fun conventionalEntryFiles(
        projectRoot: Path,
        workspaceRoot: Path,
    ): Set<Path> =
        buildSet {
            listOf(projectRoot, workspaceRoot)
                .distinct()
                .forEach { root ->
                    ProjectStylesheetPathResolver.supportedExtensions.forEach { extension ->
                        listOf(
                            root.resolve("src/styles.$extension"),
                            root.resolve("styles.$extension"),
                        ).mapNotNull(ProjectStylesheetPathResolver::resolveSourceFile)
                            .filterNot { path -> ProjectStylesheetPathResolver.isNodeModulesPath(path, workspaceRoot) }
                            .forEach(::add)
                    }
                }
        }

    private fun List<Set<Path>>.closestTo(sourceFile: Path): List<Set<Path>> {
        val groupsWithScore =
            map { group ->
                group to group.maxOf { path -> sharedPrefixSize(sourceFile, path) }
            }
        val highestScore = groupsWithScore.maxOfOrNull { (_, score) -> score } ?: return emptyList()

        return groupsWithScore
            .filter { (_, score) -> score == highestScore }
            .map(Pair<Set<Path>, Int>::first)
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
