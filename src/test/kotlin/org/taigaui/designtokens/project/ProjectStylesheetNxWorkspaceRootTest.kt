package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ProjectStylesheetNxWorkspaceRootTest {
    @Test
    fun `nx workspace marker wins over nested package and node modules hint`() {
        val workspaceRoot = Files.createTempDirectory("project-stylesheet-nx-root")

        try {
            val projectRoot = workspaceRoot.resolve("apps/demo")
            val sourceFile = createFile(projectRoot.resolve("src/component.less"), "color: var(--tui-test);")
            val stylesFile =
                createFile(
                    projectRoot.resolve("src/styles.less"),
                    ":root { --tui-test: red; }",
                )

            createFile(workspaceRoot.resolve("nx.json"), "{}")
            createFile(projectRoot.resolve("package.json"), "{}")
            createFile(
                projectRoot.resolve("project.json"),
                """
                {
                  "targets": {
                    "build": {
                      "options": {
                        "styles": ["apps/demo/src/styles.less"]
                      }
                    }
                  }
                }
                """.trimIndent(),
            )

            val graph = DesignTokenProjectStylesheetGraph()
            val request =
                graph.createRequest(
                    sourceFile = sourceFile,
                    workspaceRootHint = projectRoot,
                )
            val scope = graph.buildScope(request)

            assertEquals(workspaceRoot.toAbsolutePath().normalize(), request.workspaceRoot)
            assertEquals(projectRoot.toAbsolutePath().normalize(), scope.projectRoot)
            assertTrue(scope.sourceFiles.contains(stylesFile.toAbsolutePath().normalize()))
        } finally {
            workspaceRoot.toFile().deleteRecursively()
        }
    }

    private fun createFile(
        path: Path,
        content: String,
    ): Path {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)

        return path
    }
}
