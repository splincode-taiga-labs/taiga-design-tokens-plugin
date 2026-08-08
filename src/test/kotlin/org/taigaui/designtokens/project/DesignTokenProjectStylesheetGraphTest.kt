package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DesignTokenProjectStylesheetGraphTest {
    @Test
    fun `follows angular global styles and sass imports without scanning unrelated files`() =
        withWorkspace { workspaceRoot ->
            val sourceFile = createFile(workspaceRoot.resolve("src/app/component.scss"), "color: var(--tui-test);")
            val stylesFile = createFile(workspaceRoot.resolve("src/styles.scss"), "@use './theme';")
            val themeFile =
                createFile(
                    workspaceRoot.resolve("src/_theme.scss"),
                    ":root { --tui-test: #123; }",
                )

            createFile(
                workspaceRoot.resolve("src/unrelated.scss"),
                ":root { --tui-test: hotpink; }",
            )
            createFile(
                workspaceRoot.resolve("angular.json"),
                """
                {
                  "projects": {
                    "demo": {
                      "architect": {
                        "build": {
                          "options": {
                            "styles": ["src/styles.scss"]
                          }
                        }
                      }
                    }
                  }
                }
                """.trimIndent(),
            )

            val graph = DesignTokenProjectStylesheetGraph()
            val scope =
                graph.buildScope(
                    graph.createRequest(
                        sourceFile = sourceFile,
                        workspaceRootHint = workspaceRoot,
                    ),
                )

            assertEquals(
                setOf(sourceFile, stylesFile, themeFile).map { path -> path.normalized() }.toSet(),
                scope.sourceFiles.toSet(),
            )
            assertFalse(
                scope.sourceFiles.contains(workspaceRoot.resolve("src/unrelated.scss").normalized()),
            )
        }

    @Test
    fun `supports nx project styles plus less and css imports`() =
        withWorkspace { workspaceRoot ->
            val projectRoot = workspaceRoot.resolve("apps/demo")
            val sourceFile = createFile(projectRoot.resolve("src/component.css"), "color: var(--tui-test);")
            val stylesFile =
                createFile(
                    projectRoot.resolve("src/styles.less"),
                    "@import (reference) './theme.less';",
                )
            val themeFile =
                createFile(
                    projectRoot.resolve("src/theme.less"),
                    "@import './palette.css';",
                )
            val paletteFile =
                createFile(
                    projectRoot.resolve("src/palette.css"),
                    ":root { --tui-test: #456; }",
                )

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
            val scope =
                graph.buildScope(
                    graph.createRequest(
                        sourceFile = sourceFile,
                        workspaceRootHint = workspaceRoot,
                    ),
                )

            assertEquals(projectRoot.normalized(), scope.projectRoot)
            assertEquals(
                setOf(sourceFile, stylesFile, themeFile, paletteFile).map { path -> path.normalized() }.toSet(),
                scope.sourceFiles.toSet(),
            )
        }

    @Test
    fun `follows imports from the current stylesheet`() =
        withWorkspace { workspaceRoot ->
            val sourceFile =
                createFile(
                    workspaceRoot.resolve("src/component.scss"),
                    "@forward './tokens';\ncolor: var(--tui-local);",
                )
            val importedFile =
                createFile(
                    workspaceRoot.resolve("src/_tokens.scss"),
                    ":root { --tui-local: tomato; }",
                )

            val graph = DesignTokenProjectStylesheetGraph()
            val scope =
                graph.buildScope(
                    graph.createRequest(
                        sourceFile = sourceFile,
                        workspaceRootHint = workspaceRoot,
                    ),
                )

            assertTrue(scope.sourceFiles.contains(importedFile.normalized()))
        }

    private fun withWorkspace(block: (Path) -> Unit) {
        val workspaceRoot = Files.createTempDirectory("project-stylesheet-graph")

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
