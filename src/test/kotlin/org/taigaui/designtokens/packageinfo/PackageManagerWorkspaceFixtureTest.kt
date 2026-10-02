package org.taigaui.designtokens.packageinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.taigaui.designtokens.index.DesignTokensPackageScanner
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class PackageManagerWorkspaceFixtureTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val resolver = DesignTokensPackageResolver()
    private val scanner = DesignTokensPackageScanner()

    @Test
    fun `resolves a hoisted package from an npm workspace`() {
        val workspace = copyFixture("npm-workspaces")
        val packageRoot = workspace.resolve(DESIGN_TOKENS_PACKAGE_PATH)

        installPackage(
            packageRoot = packageRoot,
            marker = NPM_MARKER,
        )

        assertResolvedPackage(
            sourceFile = workspace.resolve(SOURCE_FILE),
            expectedLogicalRoot = packageRoot,
            expectedRealRoot = packageRoot,
            expectedWorkspaceRoot = workspace,
            marker = NPM_MARKER,
        )
    }

    @Test
    fun `resolves a pnpm workspace package through the virtual-store symlink`() {
        val workspace = copyFixture("pnpm-workspaces")
        val appRoot = workspace.resolve("apps/demo")
        val realPackageRoot =
            workspace.resolve(
                "node_modules/.pnpm/@taiga-ui+design-tokens@$DESIGN_TOKENS_VERSION/" +
                    "node_modules/@taiga-ui/design-tokens",
            )
        val logicalPackageRoot = appRoot.resolve(DESIGN_TOKENS_PACKAGE_PATH)

        installPackage(
            packageRoot = realPackageRoot,
            marker = PNPM_MARKER,
        )
        createPackageSymlink(
            link = logicalPackageRoot,
            target = realPackageRoot,
        )

        assertTrue(Files.isSymbolicLink(logicalPackageRoot))
        assertResolvedPackage(
            sourceFile = workspace.resolve(SOURCE_FILE),
            expectedLogicalRoot = logicalPackageRoot,
            expectedRealRoot = realPackageRoot,
            expectedWorkspaceRoot = appRoot,
            marker = PNPM_MARKER,
        )
    }

    private fun assertResolvedPackage(
        sourceFile: Path,
        expectedLogicalRoot: Path,
        expectedRealRoot: Path,
        expectedWorkspaceRoot: Path,
        marker: String,
    ) {
        val result = requireNotNull(resolver.resolve(sourceFile))

        assertEquals(expectedLogicalRoot.normalized(), result.root)
        assertEquals(expectedRealRoot.toRealPath(), result.realRoot)
        assertEquals(DESIGN_TOKENS_VERSION, result.version)
        assertEquals(expectedWorkspaceRoot.normalized(), result.workspaceRoot)
        assertTrue(
            scanner
                .scan(result)
                .any { declaration ->
                    declaration.name == PACKAGE_MANAGER_TOKEN &&
                        declaration.value == marker
                },
        )
    }

    private fun installPackage(
        packageRoot: Path,
        marker: String,
    ) {
        Files.createDirectories(packageRoot)
        Files.writeString(
            packageRoot.resolve("package.json"),
            """
            {
                "name": "@taiga-ui/design-tokens",
                "version": "$DESIGN_TOKENS_VERSION"
            }
            """.trimIndent(),
        )
        Files.writeString(
            packageRoot.resolve("tokens.css"),
            """
            :root {
                $PACKAGE_MANAGER_TOKEN: $marker;
            }
            """.trimIndent(),
        )
    }

    private fun createPackageSymlink(
        link: Path,
        target: Path,
    ) {
        Files.createDirectories(link.parent)
        val relativeTarget = link.parent.relativize(target)

        try {
            Files.createSymbolicLink(link, relativeTarget)
        } catch (_: UnsupportedOperationException) {
            assumeTrue("Symbolic links are required to model the pnpm layout.", false)
        } catch (_: IOException) {
            assumeTrue("Symbolic links are required to model the pnpm layout.", false)
        }
    }

    private fun copyFixture(name: String): Path {
        val source = REPOSITORY_ROOT.resolve("test-fixtures/package-manager-workspaces/$name")
        val target = temporaryFolder.newFolder(name).toPath()

        assertTrue("Missing checked-in fixture: $source", Files.isDirectory(source))

        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val relative = source.relativize(path)

                if (relative.toString().isEmpty()) {
                    return@forEach
                }

                val destination = target.resolve(relative.toString())

                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        return target
    }

    private fun Path.normalized(): Path = toAbsolutePath().normalize()

    private companion object {
        const val DESIGN_TOKENS_VERSION = "0.322.0"
        const val PACKAGE_MANAGER_TOKEN = "--tui-workspace-package-manager"
        const val NPM_MARKER = "#111111"
        const val PNPM_MARKER = "#222222"
        val REPOSITORY_ROOT: Path = Path.of("").toAbsolutePath().normalize()
        val DESIGN_TOKENS_PACKAGE_PATH: Path = Path.of("node_modules/@taiga-ui/design-tokens")
        val SOURCE_FILE: Path = Path.of("apps/demo/src/theme.less")
    }
}
