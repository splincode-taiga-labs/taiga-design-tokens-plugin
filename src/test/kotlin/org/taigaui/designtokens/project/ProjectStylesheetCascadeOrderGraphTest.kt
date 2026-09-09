package org.taigaui.designtokens.project

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class ProjectStylesheetCascadeOrderGraphTest : BasePlatformTestCase() {
    fun testPreservesConfiguredEntrypointAndImportOrderBeforeCurrentStylesheet() =
        withWorkspace { workspaceRoot ->
            val baseFile = createFile(workspaceRoot.resolve("src/base.less"), ":root { --tui-test: blue; }")
            val themeFile = createFile(workspaceRoot.resolve("src/theme.less"), ":root { --tui-test: green; }")
            val stylesFile =
                createFile(
                    workspaceRoot.resolve("src/styles.less"),
                    "@import './theme.less';\n:root { --tui-test: red; }",
                )
            val sourceFile =
                createFile(
                    workspaceRoot.resolve("src/app/component.less"),
                    ".demo { color: var(--tui-test); }",
                )

            createFile(
                workspaceRoot.resolve("project.json"),
                """
                {
                  "targets": {
                    "build": {
                      "options": {
                        "styles": ["src/base.less", "src/styles.less"]
                      }
                    }
                  }
                }
                """.trimIndent(),
            )

            val graph = DesignTokenProjectStylesheetGraph(project)
            val scope =
                graph.buildScope(
                    graph.createRequest(
                        sourceFile = sourceFile,
                        workspaceRootHint = workspaceRoot,
                    ),
                )

            assertEquals(
                listOf(baseFile, themeFile, stylesFile, sourceFile).map { path -> path.normalized() },
                scope.sourceFiles,
            )
        }

    private fun withWorkspace(block: (Path) -> Unit) {
        val workspaceRoot = Files.createTempDirectory("project-stylesheet-cascade-order")

        try {
            block(workspaceRoot)
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

    private fun Path.normalized(): Path = toAbsolutePath().normalize()
}
