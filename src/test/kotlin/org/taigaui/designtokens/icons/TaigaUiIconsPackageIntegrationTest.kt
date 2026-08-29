package org.taigaui.designtokens.icons

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class TaigaUiIconsPackageIntegrationTest {
    @Test
    fun `loads public icon names from installed Taiga UI v4 package`() {
        val workspace = REPOSITORY_ROOT.resolve("test-fixtures/taiga-ui-v4")
        val iconsRoot = workspace.resolve("node_modules/@taiga-ui/icons/src")

        assumeTrue(
            "Run `npm run install:fixtures --prefix test-fixtures` to execute icon package integration tests.",
            Files.isDirectory(iconsRoot),
        )

        val loader = IconCatalogLoader(IconCatalogFetcher { null })
        val scopeRoot = requireNotNull(loader.resolveScopeRoot(workspace.resolve("fixture.less")))
        val names = loader.load(scopeRoot)

        assertTrue("@tui.a-arrow-down" in names)
        assertTrue("@tui.flags.ab" in names)
    }

    private companion object {
        val REPOSITORY_ROOT: Path = Path.of("").toAbsolutePath().normalize()
    }
}
