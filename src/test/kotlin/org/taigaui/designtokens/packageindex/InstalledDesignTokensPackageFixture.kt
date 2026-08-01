package org.taigaui.designtokens.packageindex

import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import java.nio.file.Files
import java.nio.file.Path

internal class InstalledDesignTokensPackageFixture(val projectRoot: Path = Path.of("").toAbsolutePath().normalize()) {
    val packageJson: Path = projectRoot.resolve(
        "node_modules/@taiga-ui/design-tokens/package.json",
    )

    fun resolve(): DesignTokensPackage {
        assumeTrue(
            "Run `npm ci` to execute the real-package integration tests.",
            Files.isRegularFile(packageJson),
        )

        val result = DesignTokensPackageResolver().resolve(
            projectRoot.resolve("build.gradle.kts"),
        )

        assertNotNull(result)

        return requireNotNull(result)
    }
}
