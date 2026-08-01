package org.taigaui.designtokens.project

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path

class DesignTokenIndexServiceTest : BasePlatformTestCase() {
    private lateinit var service: DesignTokenIndexService
    private lateinit var fixture: PackageFixture

    override fun setUp() {
        super.setUp()
        fixture = createPackage("workspace", "#fff")
        service = project.getService(DesignTokenIndexService::class.java)
    }

    fun testCachesRepeatedRequestsUntilTokenFileChanges() {
        val first = index(fixture)
        val second = index(fixture)

        assertSame(first, second)
        assertEquals(1, service.cachedPackageCount)

        writeToken(fixture.tokenFile, "#000")

        val rebuilt = index(fixture)

        assertNotSame(first, rebuilt)
        assertEquals("#000", tokenValue(rebuilt))
        assertSame(rebuilt, index(fixture))
        assertEquals(1, service.cachedPackageCount)
    }

    fun testKeepsCachedIndexForUnrelatedProjectAndPackageFiles() {
        val first = index(fixture)

        writeFile(fixture.sourceFile, "Application source")
        val readme = createFile("workspace/node_modules/@taiga-ui/design-tokens/README.md", "Docs")
        writeFile(readme, "Updated docs")

        assertSame(first, index(fixture))
        assertEquals(1, service.cachedPackageCount)
    }

    fun testInvalidatesWhenNewStylesheetIsCreated() {
        val first = index(fixture)

        createFile(
            "workspace/node_modules/@taiga-ui/design-tokens/palette/dark.scss",
            "[tuiTheme='dark'] { --tui-background-base: #000; }",
        )

        val rebuilt = index(fixture)

        assertNotSame(first, rebuilt)
        assertEquals(2, rebuilt.find(TOKEN_NAME).size)
    }

    fun testInvalidatesWhenStylesheetIsRenamedOrDeleted() {
        val first = index(fixture)

        rename(fixture.tokenFile, "dark.css")
        val afterRename = index(fixture)

        assertNotSame(first, afterRename)

        delete(fixture.tokenFile)
        val afterDelete = index(fixture)

        assertNotSame(afterRename, afterDelete)
        assertTrue(afterDelete.find(TOKEN_NAME).isEmpty())
    }

    fun testInvalidatesWhenPackageVersionChanges() {
        val first = index(fixture)

        writeFile(
            fixture.packageJson,
            """{"name":"@taiga-ui/design-tokens","version":"0.311.0"}""",
        )

        val rebuilt = index(fixture)

        assertNotSame(first, rebuilt)
        assertEquals("#fff", tokenValue(rebuilt))
        assertEquals(1, service.cachedPackageCount)
    }

    fun testInvalidatesOnlyChangedPackageInMonorepo() {
        val firstPackage = createPackage("apps/first", "#111")
        val secondPackage = createPackage("apps/second", "#222")
        val firstIndex = index(firstPackage)
        val secondIndex = index(secondPackage)

        assertEquals(2, service.cachedPackageCount)

        writeToken(firstPackage.tokenFile, "#333")

        val rebuiltFirst = index(firstPackage)
        val cachedSecond = index(secondPackage)

        assertNotSame(firstIndex, rebuiltFirst)
        assertEquals("#333", tokenValue(rebuiltFirst))
        assertSame(secondIndex, cachedSecond)
        assertEquals("#222", tokenValue(cachedSecond))
        assertEquals(2, service.cachedPackageCount)
    }

    fun testServiceReturnsNullWhenNoInstalledPackageCanBeResolved() {
        val sourceFile = createFile("standalone/app.txt", "Application source")

        assertNull(service.getIndex(Path.of(sourceFile.path)))
        assertEquals(0, service.cachedPackageCount)
    }

    private fun createPackage(
        workspace: String,
        tokenValue: String,
    ): PackageFixture {
        val packagePath = "$workspace/node_modules/@taiga-ui/design-tokens"

        return PackageFixture(
            sourceFile = createFile("$workspace/src/app.txt", "Application source"),
            packageJson =
                createFile(
                    "$packagePath/package.json",
                    """{"name":"@taiga-ui/design-tokens","version":"0.310.0"}""",
                ),
            tokenFile =
                createFile(
                    "$packagePath/palette/light.css",
                    ":root { --tui-background-base: $tokenValue; }",
                ),
        )
    }

    private fun index(packageFixture: PackageFixture): DesignTokenIndex =
        requireNotNull(
            service.getIndexOrThrow(Path.of(packageFixture.sourceFile.path)),
        )

    private fun tokenValue(index: DesignTokenIndex): String =
        index
            .find(TOKEN_NAME)
            .single()
            .rawValue

    private fun writeToken(
        file: VirtualFile,
        value: String,
    ) {
        writeFile(file, ":root { --tui-background-base: $value; }")
    }

    private fun writeFile(
        file: VirtualFile,
        content: String,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(file, content)
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun rename(
        file: VirtualFile,
        newName: String,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            file.rename(this, newName)
        }
    }

    private fun delete(file: VirtualFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            file.delete(this)
        }
    }

    private fun createFile(
        relativePath: String,
        content: String,
    ): VirtualFile = myFixture.tempDirFixture.createFile(relativePath, content)

    private data class PackageFixture(
        val sourceFile: VirtualFile,
        val packageJson: VirtualFile,
        val tokenFile: VirtualFile,
    )

    private companion object {
        const val TOKEN_NAME = "--tui-background-base"
    }
}
