package org.taigaui.designtokens.documentation

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.project.DesignTokenIndexService
import java.nio.file.Files
import java.nio.file.Path

class DesignTokenDocumentationTargetProviderTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var workspace: Path
    private lateinit var service: DesignTokenIndexService
    private val provider = DesignTokenDocumentationTargetProvider()

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("design-token-documentation")
        workspace = tempRoot.resolve("workspace")
        service = project.getService(DesignTokenIndexService::class.java)
        service.clear()

        val packageRoot = workspace.resolve("node_modules/@taiga-ui/design-tokens")

        createFile(
            packageRoot.resolve("package.json"),
            """{"name":"@taiga-ui/design-tokens","version":"0.310.0"}""",
        )
        createFile(
            packageRoot.resolve("palette/light.css"),
            """
            :root {
                --tui-const-white: #fff;
                --tui-background-base: var(--tui-const-white);
            }
            """.trimIndent(),
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

    fun testProvidesResolvedColorDocumentationForCssScssAndLess() {
        listOf("css", "scss", "less").forEach { extension ->
            val content = ".button { color: var($TOKEN); }"
            val psiFile = createPsiFile("src/styles.$extension", content)
            val offset = content.indexOf(TOKEN) + 5
            val target = provider.documentationTargets(psiFile, offset).single()
            val hint = requireNotNull(target.computeDocumentationHint())

            assertTrue(hint.contains(TOKEN))
            assertTrue(hint.contains("#fff"))
            assertTrue(hint.contains("background-color:#ffffff"))
            assertNotNull(target.computeDocumentation())
        }

        assertEquals(1, service.cachedPackageCount)
    }

    fun testDoesNotProvideDocumentationOutsideVarOrForUnknownToken() {
        val declaration = createPsiFile("src/declaration.css", ":root { $TOKEN: #fff; }")
        val unknown = createPsiFile("src/unknown.css", ".x { color: var(--tui-unknown); }")

        assertEmpty(
            provider.documentationTargets(
                declaration,
                declaration.text.indexOf(TOKEN) + 2,
            ),
        )
        assertEmpty(
            provider.documentationTargets(
                unknown,
                unknown.text.indexOf("--tui-unknown") + 2,
            ),
        )
    }

    fun testProvidesNestedFallbackTokenUnderCaret() {
        val content = ".x { color: var(--tui-missing, var($TOKEN)); }"
        val psiFile = createPsiFile("src/fallback.scss", content)
        val target =
            provider.documentationTargets(
                psiFile,
                content.indexOf(TOKEN) + 4,
            ).single()

        assertTrue(requireNotNull(target.computeDocumentationHint()).contains("#fff"))
    }

    private fun createPsiFile(
        relativePath: String,
        content: String,
    ): PsiFile {
        val virtualFile = createFile(workspace.resolve(relativePath), content)

        return requireNotNull(PsiManager.getInstance(project).findFile(virtualFile))
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
        const val TOKEN = "--tui-background-base"
    }
}
