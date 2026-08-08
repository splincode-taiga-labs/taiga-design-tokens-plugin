package org.taigaui.designtokens.project

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.packageinfo.DesignTokensPackage
import java.nio.file.Path

class DesignTokenIndexCacheStateTest {
    @Test
    fun `reports whether a package index is already cached`() {
        val packageRoot =
            Path
                .of("build", "fixtures", "cache-state", "node_modules", "@taiga-ui", "design-tokens")
                .toAbsolutePath()
                .normalize()
        val designTokensPackage =
            DesignTokensPackage(
                root = packageRoot,
                realRoot = packageRoot,
                version = "1.0.0",
            )
        val cache =
            DesignTokenIndexCache { packageInfo ->
                DesignTokenIndex.build(packageInfo.realRoot, emptyList())
            }

        assertFalse(cache.contains(designTokensPackage))

        cache.getOrBuild(designTokensPackage)

        assertTrue(cache.contains(designTokensPackage))
        assertFalse(cache.contains(designTokensPackage.copy(version = "2.0.0", cacheVersion = "2.0.0")))
    }
}
