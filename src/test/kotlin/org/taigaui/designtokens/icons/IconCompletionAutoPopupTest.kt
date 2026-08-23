package org.taigaui.designtokens.icons

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.CompletionAutoPopupTestCase
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

    fun testOpensIconCompletionImmediatelyAfterTuiPrefixInAngularStaticAttribute() {
        createPackage("proprietary")
        createAngularPackage()
        createIcon("tds-icons/src/pragmatic/small/clock.svg")
        createIcon("tds-icons/src/pragmatic/small/print.svg")

        val sourcePath = workspaceRoot.resolve("src/button.component.html")
        val sourceFile =
            createFile(
                sourcePath,
                "<button iconStart=\"<caret>\"></button>",
            )

        myFixture.configureFromExistingVirtualFile(sourceFile)
        project.service<IconCompletionService>().loadNow(sourcePath)

        type("@tui.")

        val suggestions = myFixture.lookupElementStrings.orEmpty()

        assertContainsElements(
            suggestions,
            "@tui.pragmatic.small.clock",
            "@tui.pragmatic.small.print",
        )
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
