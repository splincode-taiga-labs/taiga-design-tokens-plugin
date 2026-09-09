package org.taigaui.designtokens.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class ProjectStylesheetConventionalEntrypointTest : BasePlatformTestCase() {
    fun testKeepsSrcStylesLessWhenProjectHasOtherConfiguredStyleEntries() {
        val workspaceRoot = Files.createTempDirectory("project-stylesheet-conventional")

        try {
            val projectRoot = workspaceRoot.resolve("apps/demo")
            val sourceFile = createFile(projectRoot.resolve("src/component.less"), "color: var(--tui-text-primary);")
            val globalStyles =
                createFile(
                    projectRoot.resolve("src/styles.less"),
                    ":root { --tui-text-primary: red; }",
                )
            createFile(projectRoot.resolve("src/vendor.css"), "html { font-family: sans-serif; }")
            createFile(
                projectRoot.resolve("project.json"),
                """
                {
                  "targets": {
                    "build": {
                      "options": {
                        "styles": [
                          "apps/demo/src/vendor.css",
                          "apps/demo/src/another.css"
                        ]
                      }
                    }
                  }
                }
                """.trimIndent(),
            )
            createFile(projectRoot.resolve("src/another.css"), "body { margin: 0; }")

            val graph = DesignTokenProjectStylesheetGraph(project)
            val scope =
                graph.buildScope(
                    graph.createRequest(
                        sourceFile = sourceFile,
                        workspaceRootHint = workspaceRoot,
                    ),
                )

            assertTrue(scope.sourceFiles.contains(globalStyles.toAbsolutePath().normalize()))
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
