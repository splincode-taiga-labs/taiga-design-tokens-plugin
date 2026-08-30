package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RepresentativeAngularNxFixtureTest {
    @Test
    fun `builds a large reachable stylesheet scope without unrelated app styles`() =
        withWorkspace { workspaceRoot ->
            val fixture = RepresentativeAngularNxFixture.create(workspaceRoot)
            val graph = DesignTokenProjectStylesheetGraph()
            val scope =
                graph.buildScope(
                    graph.createRequest(
                        sourceFile = fixture.sourceFile,
                        workspaceRootHint = workspaceRoot,
                    ),
                )

            assertEquals(fixture.projectRoot.normalized(), scope.projectRoot)
            assertEquals(fixture.reachableStylesheets.map(Path::normalized).toSet(), scope.sourceFiles.toSet())
            assertFalse(scope.sourceFiles.contains(fixture.unrelatedStylesheet.normalized()))
        }

    private fun withWorkspace(block: (Path) -> Unit) {
        val workspaceRoot = Files.createTempDirectory("representative-angular-nx")

        try {
            block(workspaceRoot)
        } finally {
            workspaceRoot.toFile().deleteRecursively()
        }
    }
}

internal data class RepresentativeAngularNxFixture(
    val projectRoot: Path,
    val sourceFile: Path,
    val reachableStylesheets: List<Path>,
    val unrelatedStylesheet: Path,
) {
    companion object {
        private const val IMPORTED_STYLESHEET_COUNT = 40

        fun create(workspaceRoot: Path): RepresentativeAngularNxFixture {
            val projectRoot = workspaceRoot.resolve("apps/demo")
            val sourceFile = createFile(projectRoot.resolve("src/app/component.css"), "color: var(--tui-token-39);")
            val globalStyles =
                createFile(
                    projectRoot.resolve("src/styles.css"),
                    "@import './theme/token-00.css';",
                )
            val importedStyles =
                List(IMPORTED_STYLESHEET_COUNT) { index ->
                    val nextImport =
                        if (index + 1 < IMPORTED_STYLESHEET_COUNT) {
                            "@import './token-${(index + 1).padded()}.css';\n"
                        } else {
                            ""
                        }

                    createFile(
                        projectRoot.resolve("src/theme/token-${index.padded()}.css"),
                        "$nextImport:root { --tui-token-${index.padded()}: $index; }",
                    )
                }
            val unrelatedStylesheet =
                createFile(
                    workspaceRoot.resolve("apps/other/src/styles.css"),
                    ":root { --tui-unrelated: hotpink; }",
                )

            createFile(
                workspaceRoot.resolve("angular.json"),
                """
                {
                  "projects": {
                    "demo": {
                      "root": "apps/demo"
                    }
                  }
                }
                """.trimIndent(),
            )
            createFile(
                projectRoot.resolve("project.json"),
                """
                {
                  "targets": {
                    "build": {
                      "options": {
                        "styles": ["apps/demo/src/styles.css"]
                      }
                    }
                  }
                }
                """.trimIndent(),
            )

            return RepresentativeAngularNxFixture(
                projectRoot = projectRoot,
                sourceFile = sourceFile,
                reachableStylesheets = listOf(sourceFile, globalStyles) + importedStyles,
                unrelatedStylesheet = unrelatedStylesheet,
            )
        }

        private fun createFile(
            path: Path,
            content: String,
        ): Path {
            Files.createDirectories(path.parent)
            Files.writeString(path, content)

            return path
        }

        private fun Int.padded(): String = toString().padStart(2, '0')
    }
}

private fun Path.normalized(): Path = toAbsolutePath().normalize()
