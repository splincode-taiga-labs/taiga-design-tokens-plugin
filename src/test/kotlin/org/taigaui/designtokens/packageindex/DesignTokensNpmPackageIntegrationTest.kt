package org.taigaui.designtokens.packageindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.taigaui.designtokens.tokenindex.DesignTokenDeclaration
import org.taigaui.designtokens.tokenindex.DesignTokensPackageScanner
import java.nio.file.Files
import java.nio.file.Path

class DesignTokensNpmPackageIntegrationTest {
    private val projectRoot = Path.of("").toAbsolutePath().normalize()
    private val packageJson = projectRoot.resolve(
        "node_modules/@taiga-ui/design-tokens/package.json",
    )

    @Test
    fun `resolves package installed from npm`() {
        val result = resolveInstalledPackage()
        val expectedRoot = packageJson.parent.toAbsolutePath().normalize()

        assertEquals(expectedRoot, result.root)
        assertEquals(expectedRoot.toRealPath(), result.realRoot)
        assertEquals(DESIGN_TOKENS_VERSION, result.version)
    }

    @Test
    fun `scans declarations from package installed from npm`() {
        val designTokensPackage = resolveInstalledPackage()

        val declarations = DesignTokensPackageScanner().scan(designTokensPackage)

        assertTrue(
            "Expected the real npm package to contain Taiga UI custom-property declarations.",
            declarations.isNotEmpty(),
        )
        assertTrue(declarations.all { it.name.startsWith("--tui-") })
        assertTrue(declarations.all { it.sourceFile.startsWith(designTokensPackage.realRoot) })
    }

    @Test
    fun `turns real CSS Less and SCSS declarations into models`() {
        val designTokensPackage = resolveInstalledPackage()

        val declarations = DesignTokensPackageScanner().scan(designTokensPackage)
        val expectedDeclarations = listOf(
            DesignTokenDeclaration(
                name = "--tui-font-offset",
                value = "0rem",
                sourceFile = designTokensPackage.realRoot.resolve("fonts/desktop.css"),
                line = 3,
            ),
            DesignTokenDeclaration(
                name = "--tui-background-base",
                value = "var(--tui-const-white)",
                sourceFile = designTokensPackage.realRoot.resolve("angular/desktop.less"),
                line = 5,
            ),
            DesignTokenDeclaration(
                name = "--tui-background-base",
                value = "var(--tui-const-black-lighter-13)",
                sourceFile = designTokensPackage.realRoot.resolve("palette/scss/dark.scss"),
                line = 2,
            ),
        )

        expectedDeclarations.forEach { expected ->
            assertTrue(
                "Expected to parse the real declaration $expected",
                expected in declarations,
            )
        }
    }

    private fun resolveInstalledPackage(): DesignTokensPackage {
        assumeTrue(
            "Run `npm ci` to execute the real-package integration tests.",
            Files.isRegularFile(packageJson),
        )

        val result = DesignTokensPackageResolver().resolve(
            projectRoot.resolve("build.gradle.kts"),
        )

        assertNotNull(result)

        return result!!
    }

    private companion object {
        const val DESIGN_TOKENS_VERSION = "0.310.0"
    }
}
