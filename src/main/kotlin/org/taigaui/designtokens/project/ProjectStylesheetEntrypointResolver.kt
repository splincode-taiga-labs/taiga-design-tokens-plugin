package org.taigaui.designtokens.project

import java.nio.file.Path

internal class ProjectStylesheetEntrypointResolver private constructor(
    private val providers: List<ProjectStylesheetEntrypointProvider>,
) {
    constructor(readText: (Path) -> String?) : this(defaultProviders(readText))

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
        fun defaultProviders(readText: (Path) -> String?): List<ProjectStylesheetEntrypointProvider> {
            val configurationReader = ProjectStylesheetConfigurationReader(readText)

            return listOf(
                NxProjectStylesheetEntrypointProvider(configurationReader),
                AngularProjectStylesheetEntrypointProvider(configurationReader),
                ConventionalProjectStylesheetEntrypointProvider(),
                CurrentFileProjectStylesheetEntrypointProvider(),
            )
        }
    }
}
