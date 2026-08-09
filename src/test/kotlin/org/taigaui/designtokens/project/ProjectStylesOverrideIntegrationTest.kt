package org.taigaui.designtokens.project

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.documentation.DesignTokenHoverPopupModel
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import java.nio.file.Files
import java.nio.file.Path

class ProjectStylesOverrideIntegrationTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var workspaceRoot: Path
    private lateinit var service: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("project-styles-override")
        workspaceRoot = tempRoot.resolve("workspace")
        service = project.getService(DesignTokenIndexService::class.java)
        service.clear()

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
    }

    override fun tearDown() {
        try {
            service.clear()
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testProjectRootOverrideMutesInstalledPackageDeclarations() {
        val sourcePath = workspaceRoot.resolve("src/styles.less")

        createFile(
            sourcePath,
            """
            :root {
                $TOKEN_NAME: red;
            }

            .demo {
                color: var($TOKEN_NAME);
            }
            """.trimIndent(),
        )

        val model = DesignTokenHoverPopupModel.create(TOKEN_NAME, service.resolveToken(sourcePath, TOKEN_NAME))
        val projectSection = model.sections.single { section -> section.packageName == PROJECT_STYLES_PACKAGE }
        val packageSection = model.sections.single { section -> section.packageName == DESIGN_TOKENS_PACKAGE }

        assertEquals("red", projectSection.rows.single().resolvedValue)
        assertNull(projectSection.rows.single().overrideMessage)
        assertTrue(packageSection.rows.isNotEmpty())
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
