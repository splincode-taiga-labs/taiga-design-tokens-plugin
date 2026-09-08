package org.taigaui.designtokens.project

internal class CurrentFileProjectStylesheetEntrypointProvider : ProjectStylesheetEntrypointProvider {
    override fun find(context: ProjectStylesheetEntrypointContext) =
        context.sourceFile
            .takeIf(ProjectStylesheetPathResolver::isStylesheet)
            ?.takeIf { path ->
                !ProjectStylesheetPathResolver.isNodeModulesPath(path, context.workspaceRoot)
            }
            ?.let(::listOf)
            .orEmpty()
}
