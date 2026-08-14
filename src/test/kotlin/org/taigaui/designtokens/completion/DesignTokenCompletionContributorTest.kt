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
        myFixture.enableInspections(UnknownDesignTokenInspection())

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

    fun testCompletesSingleInstalledTokenMatch() {
        val tokenPrefix = "--tui-text-prima"
        val expectedToken = "--tui-text-primary"
        val sourcePath = configureCompletion(tokenPrefix)

        indexService.completionTokenNames(sourcePath)
        val variants = myFixture.completeBasic()

        if (variants == null) {
            assertEquals(
                ".demo { color: var($expectedToken); }",
                myFixture.editor.document.text,
            )
        } else {
            assertContainsElements(
                variants.map { variant -> variant.lookupString },
                expectedToken,
            )
        }
    }

    fun testHighlightsOnlyUnknownTaigaToken() {
        val sourcePath = workspaceRoot.resolve("src/inspection.less")
        val sourceFile =
            createFile(
                sourcePath,
                """
                .demo {
                    color: var(--tui-text-primary);
                    background: var(--tui-team-color);
                    border-color: var(--tui-text-primari);
                }
                """.trimIndent(),
            )

        myFixture.configureFromExistingVirtualFile(sourceFile)
        indexService.completionTokenNames(sourcePath)

        val problems =
            myFixture
                .doHighlighting()
                .filter { info -> info.description == UNKNOWN_TOKEN_MESSAGE }

        assertEquals(1, problems.size)
        val problem = problems.single()
        val highlightedText =
            myFixture.editor.document.charsSequence
                .subSequence(problem.startOffset, problem.endOffset)
                .toString()

        assertEquals("--tui-text-primari", highlightedText)
    }

    fun testReplacesUnknownTokenWithClosestKnownToken() {
        val unknownToken = "--tui-text-primari"
        val sourcePath = workspaceRoot.resolve("src/quick-fix.less")
        val sourceFile =
            createFile(
                sourcePath,
                ".demo { color: var($unknownToken); }",
            )

        myFixture.configureFromExistingVirtualFile(sourceFile)
        val tokenOffset =
            myFixture.editor.document.text
                .indexOf(unknownToken)

        myFixture.editor.caretModel.moveToOffset(tokenOffset + unknownToken.length / 2)
        indexService.completionTokenNames(sourcePath)
        myFixture.doHighlighting()

        val quickFix = myFixture.findSingleIntention("Replace with --tui-text-primary")

        myFixture.launchAction(quickFix)
        assertEquals(
            ".demo { color: var(--tui-text-primary); }",
            myFixture.editor.document.text,
        )
    }

    private fun complete(tokenPrefix: String): List<String> {
        val sourcePath = configureCompletion(tokenPrefix)

        indexService.completionTokenNames(sourcePath)
        myFixture.completeBasic()

        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun configureCompletion(tokenPrefix: String): Path {
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

        return sourcePath
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
        const val UNKNOWN_TOKEN_MESSAGE = "Unknown Taiga UI design token"
    }
}
