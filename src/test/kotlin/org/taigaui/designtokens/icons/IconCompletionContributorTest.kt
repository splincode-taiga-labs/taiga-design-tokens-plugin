package org.taigaui.designtokens.icons

import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertFalse
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

    fun testCompletesOnlyPublicIconsWhenProprietaryPackageIsAbsent() {
        createIcon("icons/src/a-arrow-down.svg")
        createIcon("icons/src/flags/ab.svg")
        createIcon("tds-icons/src/fancy/medium/info-circle.svg")

        val suggestions = complete("@tui.")

        assertContainsElements(
            suggestions,
            "@tui.a-arrow-down",
            "@tui.flags.ab",
        )
        assertFalse("@tui.fancy.medium.info-circle" in suggestions)
    }

    fun testIconCompletionSuppressesNativeDocumentationPopups() {
        val item = createIconLookupElement("@tui.a-arrow-down")
        val autoPopupKey = Key.create<Boolean>("LookupManagerImpl.suppressAutopopupJavadoc")
        val quickDocumentationKey = Key.create<Boolean>("lookup.suppress.quick.documentation")

        assertEquals(true, item.getUserData(autoPopupKey))
        assertEquals(true, item.getUserData(quickDocumentationKey))
    }

    fun testIconCompletionDoesNotExposeNativeDocumentationTarget() {
        createIcon("icons/src/a-arrow-down.svg")
        createIcon("icons/src/flags/ab.svg")

        val sourcePath = workspaceRoot.resolve("src/icons.html")
        val sourceFile =
            createFile(
                sourcePath,
                "<button iconStart=\"@tui.\"></button>",
            )

        myFixture.configureFromExistingVirtualFile(sourceFile)
        val caretOffset =
            myFixture.editor.document.text
                .indexOf("@tui.") + "@tui.".length

        myFixture.editor.caretModel.moveToOffset(caretOffset)
        project.service<IconCompletionService>().loadNow(sourcePath)

        val item =
            requireNotNull(myFixture.completeBasic())
                .first { element -> element.lookupString == "@tui.a-arrow-down" }
        val targets =
            runReadAction {
                IdeDocumentationTargetProvider
                    .getInstance(project)
                    .documentationTargets(myFixture.editor, myFixture.file, item)
            }

        assertEmpty(targets)
    }

    fun testCompletesOnlyInstalledProprietaryIconsWhenProprietaryPackageIsPresent() {
        createPackage("proprietary")
        createIcon("icons/src/a-arrow-down.svg")
        createIcon("icons/src/flags/ab.svg")
        createIcon("tds-icons/src/fancy/medium/info-circle.svg")
        createIcon("tds-icons/src/fancy/medium/alert.svg")
        createIcon("tds-icons/src/fancy/medium/check-circle.svg")

        val suggestions = complete("@tui.")

        assertContainsElements(
            suggestions,
            "@tui.fancy.medium.info-circle",
            "@tui.fancy.medium.alert",
            "@tui.fancy.medium.check-circle",
        )
        assertFalse("@tui.a-arrow-down" in suggestions)
        assertFalse("@tui.flags.ab" in suggestions)
    }

    private fun complete(prefix: String): List<String> {
        val sourcePath = workspaceRoot.resolve("src/icons.html")
        val sourceFile =
            createFile(
                sourcePath,
                "<button iconStart=\"$prefix\"></button>",
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
