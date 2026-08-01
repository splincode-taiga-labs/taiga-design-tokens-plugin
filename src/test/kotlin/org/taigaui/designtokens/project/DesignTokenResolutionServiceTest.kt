package org.taigaui.designtokens.project

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.nio.file.Files
import java.nio.file.Path

class DesignTokenResolutionServiceTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var sourcePath: Path
    private lateinit var tokenFile: VirtualFile
    private lateinit var service: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("design-token-resolution-service")
        service = project.getService(DesignTokenIndexService::class.java)
        service.clear()

        val workspace = tempRoot.resolve("workspace")
        val packageRoot = workspace.resolve("node_modules/@taiga-ui/design-tokens")
        sourcePath = workspace.resolve("src/app.txt")

        createFile(sourcePath, "Application source")
        createFile(
            packageRoot.resolve("package.json"),
            """{"name":"@taiga-ui/design-tokens","version":"0.310.0"}""",
        )
        tokenFile =
            createFile(
                packageRoot.resolve("palette/light.css"),
                stylesheet("#fff"),
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

    fun testResolvesAndRefreshesTokenValueThroughCachedProjectIndex() {
        val first = resolvedValue()

        assertEquals("#fff", first.value)
        assertEquals("#ffffff", requireNotNull(first.color).canonicalValue)
        assertEquals(1, service.cachedPackageCount)

        writeFile(tokenFile, stylesheet("#000"))

        assertEquals(0, service.cachedPackageCount)

        val rebuilt = resolvedValue()

        assertEquals("#000", rebuilt.value)
        assertEquals("#000000", requireNotNull(rebuilt.color).canonicalValue)
        assertEquals(1, service.cachedPackageCount)
    }

    private fun resolvedValue(): DesignTokenValueResolution.Resolved {
        val group = service.resolveToken(sourcePath, ROOT_TOKEN).single()
        val result = group.representative

        assertTrue(result is DesignTokenValueResolution.Resolved)

        return result as DesignTokenValueResolution.Resolved
    }

    private fun stylesheet(terminalValue: String): String =
        """
        :root {
            $TERMINAL_TOKEN: $terminalValue;
            $ROOT_TOKEN: var($TERMINAL_TOKEN);
        }
        """.trimIndent()

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
        const val ROOT_TOKEN = "--tui-root"
        const val TERMINAL_TOKEN = "--tui-terminal"
    }
}
