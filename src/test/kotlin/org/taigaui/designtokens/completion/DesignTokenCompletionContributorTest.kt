package org.taigaui.designtokens.completion

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.project.DesignTokenIndexService
import java.nio.file.Files
import java.nio.file.Path

class DesignTokenCompletionContributorTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var workspaceRoot: Path
    private lateinit var indexService: DesignTokenIndexService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("design-token-completion")
        workspaceRoot = tempRoot.resolve("workspace")
        indexService = project.getService(DesignTokenIndexService::class.java)
        indexService.clear()

        createFile(workspaceRoot.resolve("package.json"), "{}")
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens/package.json"),
            """{"name":"@taiga-ui/design-tokens","version":"0.310.0"}""",
        )
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens/tokens.css"),
            """
            :root {
                --tui-text-primary: #000;
                --tui-background-base: #fff;
            }
            """.trimIndent(),
        )
        createFile(
            workspaceRoot.resolve("project.json"),
            """
            {
              "targets": {
                "build": {
                  "options": {
                    "styles": ["src/styles.less"]
                  }
                }
              }
            }
            """.trimIndent(),
        )
        createFile(
            workspaceRoot.resolve("src/styles.less"),
            ":root { --tui-team-color: hotpink; }",
        )
    }

    override fun tearDown() {
        try {
            indexService.clear()
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testCompletesInstalledAndProjectTokensByTypedPrefix() {
        val suggestions = complete("--tui-te")

        assertContainsElements(
            suggestions,
            "--tui-team-color",
            "--tui-text-primary",
        )
        assertFalse(suggestions.contains("--tui-background-base"))
    }

    fun testKeepsExactInstalledTokenInCompletion() {
        val suggestions = complete("--tui-text-primary")

        assertContainsElements(suggestions, "--tui-text-primary")
    }

    private fun complete(tokenPrefix: String): List<String> {
        val sourcePath = workspaceRoot.resolve("src/component.less")
        val sourceFile =
            createFile(
                sourcePath,
                ".demo { color: var($tokenPrefix); }",
            )

        myFixture.configureFromExistingVirtualFile(sourceFile)
        val text = myFixture.editor.document.text
        val caretOffset = text.indexOf(tokenPrefix) + tokenPrefix.length

        myFixture.editor.caretModel.moveToOffset(caretOffset)
        indexService.completionTokenNames(sourcePath)
        myFixture.completeBasic()

        return myFixture.lookupElementStrings.orEmpty()
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
}
