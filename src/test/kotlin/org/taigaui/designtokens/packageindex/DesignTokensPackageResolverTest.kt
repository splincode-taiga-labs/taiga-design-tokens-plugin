package org.taigaui.designtokens.packageindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path

class DesignTokensPackageResolverTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val resolver = DesignTokensPackageResolver()

    @Test
    fun `resolves package from a nested directory`() {
        val projectRoot = temporaryFolder.newFolder("project").toPath()
        val packageRoot = createPackage(projectRoot, version = "4.2.0")
        val sourceDirectory = Files.createDirectories(projectRoot.resolve("src/components/button"))

        val result = resolver.resolve(sourceDirectory)

        assertNotNull(result)
        assertEquals(packageRoot.toAbsolutePath().normalize(), result!!.root)
        assertEquals(packageRoot.toRealPath(), result.realRoot)
        assertEquals("4.2.0", result.version)
    }

    @Test
    fun `resolves package relative to a source file`() {
        val projectRoot = temporaryFolder.newFolder("file-project").toPath()
        val packageRoot = createPackage(projectRoot, version = "4.3.0")
        val sourceFile =
            Files
                .createDirectories(projectRoot.resolve("src/styles"))
                .resolve("theme.css")
        Files.writeString(sourceFile, ":root {}")

        val result = resolver.resolve(sourceFile)

        assertNotNull(result)
        assertEquals(packageRoot.toAbsolutePath().normalize(), result!!.root)
        assertEquals("4.3.0", result.version)
    }

    @Test
    fun `prefers the nearest package in a monorepo`() {
        val workspaceRoot = temporaryFolder.newFolder("workspace").toPath()
        createPackage(workspaceRoot, version = "3.0.0")

        val applicationRoot = Files.createDirectories(workspaceRoot.resolve("apps/admin"))
        val nearestPackage = createPackage(applicationRoot, version = "5.0.0")
        val sourceDirectory = Files.createDirectories(applicationRoot.resolve("src/app"))

        val result = resolver.resolve(sourceDirectory)

        assertNotNull(result)
        assertEquals(nearestPackage.toAbsolutePath().normalize(), result!!.root)
        assertEquals("5.0.0", result.version)
    }

    @Test
    fun `returns null when package is not installed`() {
        val projectRoot = temporaryFolder.newFolder("missing-package").toPath()
        val sourceDirectory = Files.createDirectories(projectRoot.resolve("src"))

        assertNull(resolver.resolve(sourceDirectory))
    }

    @Test
    fun `returns null when nearest package has no version`() {
        val workspaceRoot = temporaryFolder.newFolder("invalid-package").toPath()
        createPackage(workspaceRoot, version = "3.0.0")

        val applicationRoot = Files.createDirectories(workspaceRoot.resolve("apps/admin"))
        val packageRoot = designTokensPackageRoot(applicationRoot)
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("package.json"),
            """
            {
                "name": "@taiga-ui/design-tokens"
            }
            """.trimIndent(),
        )

        assertNull(resolver.resolve(applicationRoot))
    }

    private fun createPackage(
        projectRoot: Path,
        version: String,
    ): Path {
        val packageRoot = designTokensPackageRoot(projectRoot)
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("package.json"),
            """
            {
                "name": "@taiga-ui/design-tokens",
                "version": "$version"
            }
            """.trimIndent(),
        )

        return packageRoot
    }

    private fun designTokensPackageRoot(projectRoot: Path): Path =
        projectRoot.resolve(
            Path.of("node_modules", "@taiga-ui", "design-tokens"),
        )
}
