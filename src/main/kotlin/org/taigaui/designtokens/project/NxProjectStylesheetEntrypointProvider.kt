package org.taigaui.designtokens.project

import java.nio.file.Files
import java.nio.file.Path

internal class NxProjectStylesheetEntrypointProvider(
    private val configurationReader: ProjectStylesheetConfigurationReader,
) : ProjectStylesheetEntrypointProvider {
    override fun find(context: ProjectStylesheetEntrypointContext): List<Path> {
        val projectConfig = context.projectRoot.resolve(PROJECT_JSON)

        return if (Files.isRegularFile(projectConfig)) {
            configurationReader
                .readStyleGroups(
                    configFile = projectConfig,
                    projectRoot = context.projectRoot,
                    workspaceRoot = context.workspaceRoot,
                ).flatten()
        } else {
            emptyList()
        }
    }

    private companion object {
        const val PROJECT_JSON = "project.json"
    }
}
