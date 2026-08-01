package org.taigaui.designtokens.project

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokenPlatform
import java.nio.file.Files
import java.nio.file.Path

class DesignTokenIndexServiceTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var service: DesignTokenIndexService
    private lateinit var fixture: PackageFixture

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("design-token-index-service")
        service = project.getService(DesignTokenIndexService::class.java)
        service.clear()
        fixture = createPackage("workspace", "#fff")
    }

    override fun tearDown() {
        try {
            service.clear()
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testCachesRepeatedRequestsUntilTokenFileChanges() {
        val first = index(fixture)
        val second = index(fixture)

        assertSame(first, second)
        assertEquals(1, service.cachedPackageCount)

        writeToken(fixture.tokenFile, "#000")

        assertEquals(0, service.cachedPackageCount)

        val rebuilt = index(fixture)

        assertNotSame(first, rebuilt)
        assertEquals("#000", tokenValue(rebuilt))
        assertSame(rebuilt, index(fixture))
        assertEquals(1, service.cachedPackageCount)
    }

    fun testKeepsCachedIndexForUnrelatedProjectAndPackageFiles() {
        val first = index(fixture)

        writeFile(fixture.sourceFile, "Application source updated")
        val readme = createVfsFile(fixture.packageRoot.resolve("README.md"), "Docs")
        writeFile(readme, "Updated docs")

        assertSame(first, index(fixture))
        assertEquals(1, service.cachedPackageCount)
    }

    fun testInvalidatesWhenNewStylesheetIsCreated() {
        val first = index(fixture)

        createVfsFile(
            fixture.packageRoot.resolve("palette/dark.scss"),
            "[tuiTheme='dark'] { --tui-background-base: #000; }",
        )

        assertEquals(0, service.cachedPackageCount)

        val rebuilt = index(fixture)

        assertNotSame(first, rebuilt)
        assertEquals(2, rebuilt.find(TOKEN_NAME).size)
    }

    fun testInvalidatesWhenLessFileChanges() {
        val lessTokenName = "--tui-less-test"
        val lessFile =
            createVfsFile(
                fixture.packageRoot.resolve("angular/desktop.less"),
                ":root { $lessTokenName: #111; }",
            )
        val first = index(fixture)

        assertEquals("#111", first.find(lessTokenName).single().rawValue)

        writeFile(lessFile, ":root { $lessTokenName: #222; }")

        assertEquals(0, service.cachedPackageCount)

        val rebuilt = index(fixture)

        assertNotSame(first, rebuilt)
        assertEquals("#222", rebuilt.find(lessTokenName).single().rawValue)
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

    fun testInvalidatesWhenStylesheetMovesToAnotherContext() {
        val mobileDirectory = createDirectory(fixture.packageRoot.resolve("mobile"))
        val first = index(fixture)

        move(fixture.tokenFile, mobileDirectory)

        assertEquals(0, service.cachedPackageCount)

        val rebuilt = index(fixture)
        val variant = rebuilt.find(TOKEN_NAME).single()

        assertNotSame(first, rebuilt)
        assertEquals(DesignTokenPlatform.MOBILE, variant.context.platform)
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

        assertEquals(1, service.cachedPackageCount)

        val rebuiltFirst = index(firstPackage)
        val cachedSecond = index(secondPackage)

        assertNotSame(firstIndex, rebuiltFirst)
        assertEquals("#333", tokenValue(rebuiltFirst))
        assertSame(secondIndex, cachedSecond)
        assertEquals("#222", tokenValue(cachedSecond))
        assertEquals(2, service.cachedPackageCount)
    }

    fun testServiceReturnsNullWhenNoInstalledPackageCanBeResolved() {
        val sourcePath = tempRoot.resolve("standalone/app.txt")
        createFile(sourcePath, "Application source")

        assertNull(service.getIndex(sourcePath))
        assertEquals(0, service.cachedPackageCount)
    }

    private fun createPackage(
        workspace: String,
        tokenValue: String,
    ): PackageFixture {
        val workspaceRoot = tempRoot.resolve(workspace)
        val packageRoot = workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens")
        val sourcePath = workspaceRoot.resolve("src/app.txt")

        return PackageFixture(
            packageRoot = packageRoot,
            sourcePath = sourcePath,
            sourceFile = createFile(sourcePath, "Application source"),
            packageJson =
                createFile(
                    packageRoot.resolve("package.json"),
                    """{"name":"@taiga-ui/design-tokens","version":"0.310.0"}""",
                ),
            tokenFile =
                createFile(
                    packageRoot.resolve("palette/light.css"),
                    ":root { --tui-background-base: $tokenValue; }",
                ),
        )
    }

    private fun index(packageFixture: PackageFixture): DesignTokenIndex =
        requireNotNull(
            service.getIndexOrThrow(packageFixture.sourcePath),
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

    private fun move(
        file: VirtualFile,
        directory: VirtualFile,
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            file.move(this, directory)
        }
    }

    private fun delete(file: VirtualFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            file.delete(this)
        }
    }

    private fun createVfsFile(
        path: Path,
        content: String,
    ): VirtualFile {
        Files.createDirectories(path.parent)

        val parent = requireNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path.parent))
        val file =
            WriteCommandAction.writeCommandAction(project).compute<VirtualFile, RuntimeException> {
                parent.findChild(path.fileName.toString())
                    ?: parent.createChildData(this, path.fileName.toString())
            }

        writeFile(file, content)

        return file
    }

    private fun createDirectory(path: Path): VirtualFile {
        Files.createDirectories(path)

        return requireNotNull(
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path),
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

    private data class PackageFixture(
        val packageRoot: Path,
        val sourcePath: Path,
        val sourceFile: VirtualFile,
        val packageJson: VirtualFile,
        val tokenFile: VirtualFile,
    )

    private companion object {
        const val TOKEN_NAME = "--tui-background-base"
    }
}
