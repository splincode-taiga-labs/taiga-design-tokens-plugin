package org.taigaui.designtokens.icons

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path

class IconCompletionContributorTest : BasePlatformTestCase() {
    private lateinit var workspaceRoot: Path

    override fun setUp() {
        super.setUp()
        workspaceRoot = Files.createTempDirectory("taiga-ui-icon-completion")
    }

    override fun tearDown() {
        try {
            workspaceRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testCompletesPublicIconsFromInstalledPackage() {
        createIcon("icons/src/a-arrow-down.svg")
        createIcon("icons/src/flags/ab.svg")

        val suggestions = complete("@tui.")

        assertContainsElements(
            suggestions,
            "@tui.a-arrow-down",
            "@tui.flags.ab",
        )
    }

    fun testCompletesInstalledProprietaryIconsWithNestedPath() {
        createPackage("proprietary")
        createIcon("tds-icons/src/fancy/medium/info-circle.svg")
        createIcon("tds-icons/src/fancy/medium/alert.svg")
        createIcon("tds-icons/src/fancy/medium/check-circle.svg")

        val suggestions = complete("@tui.fancy.medium.")

        assertContainsElements(
            suggestions,
            "@tui.fancy.medium.info-circle",
            "@tui.fancy.medium.alert",
            "@tui.fancy.medium.check-circle",
        )
    }

    private fun complete(prefix: String): List<String> {
        val sourcePath = workspaceRoot.resolve("src/icons.html")
        val sourceFile =
            createFile(
                sourcePath,
                "<tui-icon icon=\"$prefix\"></tui-icon>",
            )

        myFixture.configureFromExistingVirtualFile(sourceFile)
        val caretOffset =
            myFixture.editor.document.text
                .indexOf(prefix) + prefix.length

        myFixture.editor.caretModel.moveToOffset(caretOffset)
        project.service<IconCompletionService>().loadNow(sourcePath)
        myFixture.completeBasic()

        return myFixture.lookupElementStrings.orEmpty()
    }

    private fun createPackage(name: String) {
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/$name/package.json"),
            "{\"name\":\"@taiga-ui/$name\"}",
        )
    }

    private fun createIcon(relativePath: String) {
        createFile(
            workspaceRoot.resolve("node_modules/@taiga-ui/$relativePath"),
            "<svg></svg>",
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
