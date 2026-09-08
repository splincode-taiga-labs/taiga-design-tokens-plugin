package org.taigaui.designtokens.project

import java.nio.file.Path

internal data class ProjectStylesheetEntrypointContext(
    val sourceFile: Path,
    val projectRoot: Path,
    val workspaceRoot: Path,
)

internal fun interface ProjectStylesheetEntrypointProvider {
    fun find(context: ProjectStylesheetEntrypointContext): List<Path>
}
