package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class ProjectStylesheetEntrypointResolverTest {
    @Test
    fun `keeps provider order and deduplicates entrypoints`() {
        val workspaceRoot = path("workspace")
        val projectRoot = workspaceRoot.resolve("apps/demo")
        val sourceFile = projectRoot.resolve("src/app/component.ts")
        val nx = projectRoot.resolve("src/nx.css")
        val angular = projectRoot.resolve("src/angular.css")
        val conventional = projectRoot.resolve("src/styles.less")
        val current = projectRoot.resolve("src/component.scss")
        val calls = mutableListOf<String>()
        val resolver =
            ProjectStylesheetEntrypointResolver(
                provider("nx", calls, nx, conventional),
                provider("angular", calls, angular, conventional),
                provider("conventional", calls, conventional),
                provider("current", calls, current),
            )

        val result =
            resolver.find(
                sourceFile = sourceFile,
                projectRoot = projectRoot,
                workspaceRoot = workspaceRoot,
            )

        assertEquals(listOf("nx", "angular", "conventional", "current"), calls)
        assertEquals(listOf(nx, conventional, angular, current), result)
    }

    private fun provider(
        name: String,
        calls: MutableList<String>,
        vararg paths: Path,
    ): ProjectStylesheetEntrypointProvider =
        ProjectStylesheetEntrypointProvider {
            calls += name
            paths.toList()
        }

    private fun path(value: String): Path =
        Path
            .of("build", "entrypoint-providers", value)
            .toAbsolutePath()
            .normalize()
}
