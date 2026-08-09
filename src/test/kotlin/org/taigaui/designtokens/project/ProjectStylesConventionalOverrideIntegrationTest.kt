package org.taigaui.designtokens.project

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.documentation.DesignTokenHoverPopupModel
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import java.nio.file.Files
import java.nio.file.Path

class ProjectStylesConventionalOverrideIntegrationTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var workspaceRoot: Path
    private lateinit var service: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("project-styles-conventional-override")
        workspaceRoot = tempRoot.resolve("workspace")
        service = project.getService(DesignTokenIndexService::class.java)
        service.clear()
    }

    override fun tearDown() {
        try {
            service.clear()
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testConventionalStylesLessOverridesPackageWhenOtherStylesAreConfigured() {
        val projectRoot = workspaceRoot.resolve("apps/demo")
        val sourcePath = projectRoot.resolve("src/component.less")

        createFile(sourcePath, ".demo { color: var($TOKEN_NAME); }")
        createFile(
            projectRoot.resolve("src/styles.less"),
            ":root { $TOKEN_NAME: red; }",
        )
        createFile(projectRoot.resolve("src/vendor.css"), "html { font-family: sans-serif; }")
        createFile(projectRoot.resolve("src/another.css"), "body { margin: 0; }")
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
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens/package.json"),
            """{"name":"@taiga-ui/design-tokens","version":"0.310.0"}""",
        )
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens/palette/light.css"),
            ":root { $TOKEN_NAME: #000000cc; }",
        )
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens/palette/dark.css"),
            ":root { $TOKEN_NAME: #ffffffe6; }",
        )

        val model = DesignTokenHoverPopupModel.create(TOKEN_NAME, service.resolveToken(sourcePath, TOKEN_NAME))
        val projectSection = model.sections.single { section -> section.packageName == PROJECT_STYLES_PACKAGE }
        val packageSection = model.sections.single { section -> section.packageName == DESIGN_TOKENS_PACKAGE }

        assertEquals("red", projectSection.rows.single().resolvedValue)
        assertTrue(
            packageSection.rows.all { row ->
                row.overrideMessage == "Overridden by $PROJECT_STYLES_PACKAGE"
            },
        )
    }

    private fun createFile(
        path: Path,
        content: String,
    ): VirtualFile {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)

        return requireNotNull(
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path),
        )
    }

    private companion object {
        const val TOKEN_NAME = "--tui-text-primary"
        const val DESIGN_TOKENS_PACKAGE = "@taiga-ui/design-tokens"
    }
}
