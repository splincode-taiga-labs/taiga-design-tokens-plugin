package org.taigaui.designtokens.project

import java.nio.file.Files
import java.nio.file.Path

internal data class RepresentativeAngularNxFixture(
    val projectRoot: Path,
    val sourceFile: Path,
    val reachableStylesheets: List<Path>,
    val unrelatedStylesheet: Path,
) {
    companion object {
        fun create(workspaceRoot: Path): RepresentativeAngularNxFixture {
            val projectRoot = workspaceRoot.resolve("apps/demo")
            val sourceFile = createFile(projectRoot.resolve("src/app/component.css"), "color: var(--tui-token-39);")
            val globalStyles =
                createFile(
                    projectRoot.resolve("src/styles.css"),
                    "@import './theme/token-00.css';",
                )
            val importedStyles = createImportedStyles(projectRoot)
            val unrelatedStylesheet =
                createFile(
                    workspaceRoot.resolve("apps/other/src/styles.css"),
                    ":root { --tui-unrelated: hotpink; }",
                )

            createAngularWorkspaceConfig(workspaceRoot)
            createNxProjectConfig(projectRoot)

            return RepresentativeAngularNxFixture(
                projectRoot = projectRoot,
                sourceFile = sourceFile,
                reachableStylesheets = listOf(sourceFile, globalStyles) + importedStyles,
                unrelatedStylesheet = unrelatedStylesheet,
            )
        }

        private fun createImportedStyles(projectRoot: Path): List<Path> =
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

        private fun createAngularWorkspaceConfig(workspaceRoot: Path) {
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
        }

        private fun createNxProjectConfig(projectRoot: Path) {
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

        private const val IMPORTED_STYLESHEET_COUNT = 40
    }
}
