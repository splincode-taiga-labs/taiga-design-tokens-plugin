package org.taigaui.designtokens.packageindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
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
    fun `diagnostic samples from real npm package`() {
        val designTokensPackage = resolveInstalledPackage()
        val declarations = DesignTokensPackageScanner().scan(designTokensPackage)

        val samples = declarations
            .groupBy { it.sourceFile.fileName.toString().substringAfterLast('.', missingDelimiterValue = "") }
            .mapValues { (_, values) ->
                values.take(12).joinToString("\n") { declaration ->
                    val relativePath = designTokensPackage.realRoot.relativize(declaration.sourceFile)

                    "$relativePath:${declaration.line} ${declaration.name} = ${declaration.value}"
                }
            }
            .entries
            .sortedBy { it.key }
            .joinToString("\n\n") { (extension, values) -> "$extension:\n$values" }

        println("REAL_PACKAGE_DECLARATION_SAMPLES\n$samples")
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
