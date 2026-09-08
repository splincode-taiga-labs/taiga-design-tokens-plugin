package org.taigaui.designtokens.project

import java.nio.file.Files
import java.nio.file.Path

internal class AngularProjectStylesheetEntrypointProvider(
    private val configurationReader: ProjectStylesheetConfigurationReader,
) : ProjectStylesheetEntrypointProvider {
    override fun find(context: ProjectStylesheetEntrypointContext): List<Path> {
        val workspaceConfig = context.workspaceRoot.resolve(ANGULAR_JSON)

        if (!Files.isRegularFile(workspaceConfig)) {
            return emptyList()
        }

        val workspaceGroups =
            configurationReader.readStyleGroups(
                configFile = workspaceConfig,
                projectRoot = context.projectRoot,
                workspaceRoot = context.workspaceRoot,
            )

        return if (context.projectRoot == context.workspaceRoot) {
            workspaceGroups.closestTo(context.sourceFile).flatten()
        } else {
            workspaceGroups
                .map { group -> group.filter { path -> path.startsWith(context.projectRoot) } }
                .flatten()
        }
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
    }
}
