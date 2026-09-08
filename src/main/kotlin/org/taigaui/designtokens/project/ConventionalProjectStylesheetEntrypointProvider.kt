package org.taigaui.designtokens.project

import java.nio.file.Path

internal class ConventionalProjectStylesheetEntrypointProvider : ProjectStylesheetEntrypointProvider {
    override fun find(context: ProjectStylesheetEntrypointContext): List<Path> =
        buildList {
            listOf(context.projectRoot, context.workspaceRoot)
                .distinct()
                .forEach { root ->
                    ProjectStylesheetPathResolver.supportedExtensions.forEach { extension ->
                        listOf(
                            root.resolve("src/styles.$extension"),
                            root.resolve("styles.$extension"),
                        ).mapNotNull(ProjectStylesheetPathResolver::resolveSourceFile)
                            .filterNot { path ->
                                ProjectStylesheetPathResolver.isNodeModulesPath(
                                    path,
                                    context.workspaceRoot,
                                )
                            }.forEach(::add)
                    }
                }
        }.distinct()
}
