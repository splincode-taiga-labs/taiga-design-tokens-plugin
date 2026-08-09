package org.taigaui.designtokens.project

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.documentation.DesignTokenHoverPopupModel
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import java.nio.file.Files
import java.nio.file.Path

class ProjectStylesUnsavedOverrideIntegrationTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var workspaceRoot: Path
    private lateinit var service: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("project-styles-unsaved-override")
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

    fun testUnsavedStylesLessOverrideReplacesPreviouslyCachedPackageValue() {
        val sourcePath = workspaceRoot.resolve("src/styles.less")
        val sourceFile = createFile(sourcePath, ".demo { color: var($TOKEN_NAME); }")

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

        val initialModel = model(sourcePath)

        assertTrue(initialModel.sections.none { section -> section.packageName == PROJECT_STYLES_PACKAGE })
        assertTrue(service.isIndexCached(sourcePath))

        val document = requireNotNull(FileDocumentManager.getInstance().getDocument(sourceFile))

        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(
                """
                :root {
                    $TOKEN_NAME: red;
                }

                .demo {
                    color: var($TOKEN_NAME);
                }
                """.trimIndent(),
            )
        }

        assertFalse(service.isIndexCached(sourcePath))
        PsiDocumentManager.getInstance(project).commitAllDocuments()

        val updatedModel = model(sourcePath)
        val projectSection = updatedModel.sections.single { section -> section.packageName == PROJECT_STYLES_PACKAGE }
        val packageSection = updatedModel.sections.single { section -> section.packageName == DESIGN_TOKENS_PACKAGE }

        assertEquals("red", projectSection.rows.single().resolvedValue)
        assertTrue(
            packageSection.rows.all { row ->
                row.overrideMessage == "Overridden by $PROJECT_STYLES_PACKAGE"
            },
        )
        assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    private fun model(sourcePath: Path): DesignTokenHoverPopupModel =
        DesignTokenHoverPopupModel.create(TOKEN_NAME, service.resolveToken(sourcePath, TOKEN_NAME))

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
