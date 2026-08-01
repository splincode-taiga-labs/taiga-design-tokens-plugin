package org.taigaui.designtokens.tokenindex

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.taigaui.designtokens.packageindex.DesignTokensPackage
import java.nio.file.Files

class DesignTokensPackageScannerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scanner = DesignTokensPackageScanner()

    @Test
    fun `collects declarations from all supported package files`() {
        val packageRoot = temporaryFolder.newFolder("design-tokens").toPath()
        Files.createDirectories(packageRoot.resolve("themes"))
        Files.writeString(
            packageRoot.resolve("a.css"),
            ":root { --tui-text-primary: #000; }",
        )
        Files.writeString(
            packageRoot.resolve("themes/b.less"),
            ":root { --tui-text-primary: #fff; --tui-radius: 0.75rem; }",
        )
        Files.writeString(
            packageRoot.resolve("index.js"),
            "export const ignored = true;",
        )
        val designTokensPackage =
            DesignTokensPackage(
                root = packageRoot,
                realRoot = packageRoot,
                version = "1.0.0",
            )

        val result = scanner.scan(designTokensPackage)

        assertEquals(
            listOf(
                "--tui-text-primary",
                "--tui-text-primary",
                "--tui-radius",
            ),
            result.map(DesignTokenDeclaration::name),
        )
        assertEquals(listOf("#000", "#fff", "0.75rem"), result.map(DesignTokenDeclaration::value))
    }
}
