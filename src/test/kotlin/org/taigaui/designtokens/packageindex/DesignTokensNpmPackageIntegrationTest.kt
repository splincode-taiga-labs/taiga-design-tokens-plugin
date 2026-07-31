package org.taigaui.designtokens.packageindex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class DesignTokensNpmPackageIntegrationTest {
    @Test
    fun `resolves package installed from npm`() {
        val projectRoot = Path.of("").toAbsolutePath().normalize()
        val packageJson = projectRoot.resolve(
            "node_modules/@taiga-ui/design-tokens/package.json",
        )

        assertTrue(
            "The real npm fixture is missing. Run `npm ci` before `./gradlew integrationTest`.",
            Files.isRegularFile(packageJson),
        )

        val result = DesignTokensPackageResolver().resolve(
            projectRoot.resolve("build.gradle.kts"),
        )
        val expectedRoot = packageJson.parent.toAbsolutePath().normalize()

        assertNotNull(result)
        assertEquals(expectedRoot, result!!.root)
        assertEquals(expectedRoot.toRealPath(), result.realRoot)
        assertEquals(DESIGN_TOKENS_VERSION, result.version)
    }

    private companion object {
        const val DESIGN_TOKENS_VERSION = "0.310.0"
    }
}
