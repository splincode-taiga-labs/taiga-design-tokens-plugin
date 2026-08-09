package org.taigaui.designtokens.project

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.nio.file.Files
import java.nio.file.Path

class ProjectStylesheetResolutionIntegrationTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var workspaceRoot: Path
    private lateinit var service: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("project-stylesheet-resolution")
        workspaceRoot = tempRoot.resolve("workspace")
        service = project.getService(DesignTokenIndexService::class.java)
        service.clear()

        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens/package.json"),
            """{"name":"@taiga-ui/design-tokens","version":"0.310.0"}""",
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

    fun testResolvesTokenFromImportedGlobalProjectStylesheet() {
        val sourcePath = workspaceRoot.resolve("src/app/component.scss")
        val themeFile = prepareGlobalStyles(sourcePath, "#123")
        val resolutions = resolve(sourcePath)

        assertTrue(
            resolutions.any { resolution ->
                (resolution.result as? DesignTokenValueResolution.Resolved)?.value == "#123"
            },
        )
        assertTrue(
            resolutions
                .flatMap { resolution -> resolution.variant.origins }
                .any { origin ->
                    origin.packageName == PROJECT_STYLES_PACKAGE &&
                        origin.sourceFile == Path.of(themeFile.path).toAbsolutePath().normalize()
                },
        )
    }

    fun testInvalidatesProjectGraphWhenImportedStylesheetChanges() {
        val sourcePath = workspaceRoot.resolve("src/app/component.scss")
        val themeFile = prepareGlobalStyles(sourcePath, "#123")

        assertFalse(service.isIndexCached(sourcePath))
        assertEquals("#123", resolvedValue(sourcePath))
        assertTrue(service.isIndexCached(sourcePath))
        assertEquals(1, service.cachedProjectIndexCount)

        writeFile(themeFile, ":root { $TOKEN_NAME: #456; }")

        assertFalse(service.isIndexCached(sourcePath))
        assertEquals(0, service.cachedProjectIndexCount)
        assertEquals("#456", resolvedValue(sourcePath))
        assertTrue(service.isIndexCached(sourcePath))
    }

    private fun prepareGlobalStyles(
        sourcePath: Path,
        tokenValue: String,
    ): VirtualFile {
        createFile(sourcePath, "color: var($TOKEN_NAME);")
        createFile(workspaceRoot.resolve("src/styles.scss"), "@use './theme';")
        val themeFile =
            createFile(
                workspaceRoot.resolve("src/_theme.scss"),
                ":root { $TOKEN_NAME: $tokenValue; }",
            )
        createFile(
            workspaceRoot.resolve("src/unrelated.scss"),
            ":root { $TOKEN_NAME: hotpink; }",
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

        return themeFile
    }

    private fun resolve(sourcePath: Path) =
        service
            .resolveToken(sourcePath, TOKEN_NAME)
            .flatMap { group -> group.resolutions }

    private fun resolvedValue(sourcePath: Path): String =
        resolve(sourcePath)
            .mapNotNull { resolution ->
                (resolution.result as? DesignTokenValueResolution.Resolved)?.value
            }.distinct()
            .single()

    private fun writeFile(
        file: VirtualFile,
        content: String,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(file, content)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
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
        const val TOKEN_NAME = "--tui-project-imported"
    }
}
