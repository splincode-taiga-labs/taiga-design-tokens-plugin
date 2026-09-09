package org.taigaui.designtokens.project

import com.intellij.openapi.project.Project
import java.nio.file.Path

internal class ProjectStylesheetEntrypointResolver private constructor(
    private val providers: List<ProjectStylesheetEntrypointProvider>,
) {
    constructor(
        project: Project,
        readText: (Path) -> String?,
    ) : this(defaultProviders(project, readText))

    internal constructor(vararg providers: ProjectStylesheetEntrypointProvider) : this(
        providers = providers.toList(),
    )

    fun find(
        sourceFile: Path,
        projectRoot: Path,
        workspaceRoot: Path,
    ): List<Path> {
        val context =
            ProjectStylesheetEntrypointContext(
                sourceFile = sourceFile,
                projectRoot = projectRoot,
                workspaceRoot = workspaceRoot,
            )

        return providers
            .flatMap { provider -> provider.find(context) }
            .distinct()
    }

    private companion object {
        fun defaultProviders(
            project: Project,
            readText: (Path) -> String?,
        ): List<ProjectStylesheetEntrypointProvider> {
            val configurationReader =
                ProjectStylesheetConfigurationReader(
                    readText = readText,
                    parser = ProjectStylesheetJsonPsiParser(project),
                )

            return listOf(
                NxProjectStylesheetEntrypointProvider(configurationReader),
                AngularProjectStylesheetEntrypointProvider(configurationReader),
                ConventionalProjectStylesheetEntrypointProvider(),
                CurrentFileProjectStylesheetEntrypointProvider(),
            )
        }
    }
}
