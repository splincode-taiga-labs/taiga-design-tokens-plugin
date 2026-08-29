package org.taigaui.designtokens.icons

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
import com.intellij.testFramework.runInEdtAndGet
import org.junit.Assert.assertFalse
import java.nio.file.Files
import java.nio.file.Path

class IconCompletionAutoPopupTest : CompletionAutoPopupTestCase() {
    private lateinit var workspaceRoot: Path

    override fun setUp() {
        super.setUp()
        workspaceRoot = Files.createTempDirectory("taiga-ui-icon-autopopup")
    }

    override fun tearDown() {
        try {
            workspaceRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testOpensIconCompletionAfterColdCatalogWarmupInAngularStaticAttribute() {
        createPackage("proprietary")
        createAngularPackage()
        createIcon("tds-icons/src/pragmatic/small/clock.svg")
        createIcon("tds-icons/src/pragmatic/small/print.svg")

        val sourceFile =
            createFile(
                workspaceRoot.resolve("src/button.component.html"),
                "<button iconStart=\"<caret>\"></button>",
            )

        myFixture.configureFromExistingVirtualFile(sourceFile)

        type("@tui.")

        val suggestions = waitForSuggestions()

        assertContainsElements(
            suggestions,
            "@tui.pragmatic.small.clock",
            "@tui.pragmatic.small.print",
        )
        assertFalse(
            "Taiga UI icon lookup must finish calculating after icon suggestions are available",
            runInEdtAndGet { getLookup()?.isCalculating == true },
        )
    }

    private fun waitForSuggestions(): List<String> {
        repeat(200) {
            myTester.joinAutopopup()
            myTester.joinCompletion()

            val suggestions =
                runInEdtAndGet {
                    myFixture.lookupElementStrings.orEmpty()
                }

            if (suggestions.isNotEmpty()) {
                return suggestions
            }

            Thread.sleep(10)
        }

        return runInEdtAndGet {
            myFixture.lookupElementStrings.orEmpty()
        }
    }

    private fun createPackage(name: String) {
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/$name/package.json"),
            "{\"name\":\"@taiga-ui/$name\"}",
        )
    }

    private fun createAngularPackage() {
        createFile(
            workspaceRoot.resolve("node_modules/@angular/core/package.json"),
            "{\"name\":\"@angular/core\",\"version\":\"22.0.0\"}",
        )
    }

    private fun createIcon(relativePath: String) {
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/$relativePath"),
            "<svg viewBox=\"0 0 24 24\"><path d=\"M4 12h16\"/></svg>",
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
}
