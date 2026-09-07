package org.taigaui.designtokens.icons

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class IconCatalogCacheTest {
    @Test
    fun `local catalog does not expire`() {
        var now = 1_000L
        val scopeRoot = scopeRoot("local")
        val cache = IconCatalogCache(IconCatalogClock { now })

        assertTrue(cache.publish(scopeRoot, generation = 0L, result(IconCatalogCachePolicy.LOCAL)))

        now += 24L * 60L * 60L * 1_000L

        assertTrue(requireNotNull(cache.lookup(scopeRoot)).isFresh)
    }

    @Test
    fun `successful remote catalog expires and can be refreshed`() {
        var now = 1_000L
        val scopeRoot = scopeRoot("remote-success")
        val cache = IconCatalogCache(IconCatalogClock { now })
        val maxAge = requireNotNull(IconCatalogCachePolicy.REMOTE_SUCCESS.maxAge).toMillis()

        assertTrue(cache.publish(scopeRoot, generation = 0L, result(IconCatalogCachePolicy.REMOTE_SUCCESS)))

        now += maxAge - 1L
        assertTrue(requireNotNull(cache.lookup(scopeRoot)).isFresh)

        now += 1L
        assertFalse(requireNotNull(cache.lookup(scopeRoot)).isFresh)

        assertTrue(cache.publish(scopeRoot, generation = 0L, result(IconCatalogCachePolicy.REMOTE_SUCCESS)))
        assertTrue(requireNotNull(cache.lookup(scopeRoot)).isFresh)
    }

    @Test
    fun `failed remote catalog uses short retry lifetime`() {
        var now = 1_000L
        val scopeRoot = scopeRoot("remote-retry")
        val cache = IconCatalogCache(IconCatalogClock { now })
        val maxAge = requireNotNull(IconCatalogCachePolicy.REMOTE_RETRY.maxAge).toMillis()

        assertTrue(cache.publish(scopeRoot, generation = 0L, result(IconCatalogCachePolicy.REMOTE_RETRY)))

        now += maxAge

        assertFalse(requireNotNull(cache.lookup(scopeRoot)).isFresh)
    }

    @Test
    fun `invalidation rejects stale publication from previous generation`() {
        val scopeRoot = scopeRoot("generation")
        val cache = IconCatalogCache()
        val staleGeneration = cache.generation(scopeRoot)

        cache.invalidate(scopeRoot)

        assertFalse(cache.publish(scopeRoot, staleGeneration, result(IconCatalogCachePolicy.LOCAL)))
        assertNull(cache.lookup(scopeRoot))
        assertTrue(
            cache.publish(
                scopeRoot,
                cache.generation(scopeRoot),
                result(IconCatalogCachePolicy.LOCAL),
            ),
        )
    }

    @Test
    fun `invalidates only paths that can change effective icon source`() {
        val workspace = Path.of("build/fixtures/icon-invalidation").toAbsolutePath().normalize()
        val scopeRoot = workspace.resolve("node_modules/@taiga-ui")

        assertTrue(IconCatalogInvalidation.isAffected(scopeRoot, workspace.resolve("package.json")))
        assertTrue(IconCatalogInvalidation.isAffected(scopeRoot, scopeRoot.resolve("icons/package.json")))
        assertTrue(IconCatalogInvalidation.isAffected(scopeRoot, scopeRoot.resolve("icons/src/new.svg")))
        assertTrue(IconCatalogInvalidation.isAffected(scopeRoot, scopeRoot.resolve("icons/src/fancy")))
        assertTrue(IconCatalogInvalidation.isAffected(scopeRoot, scopeRoot.resolve("tds-icons/src/new.svg")))
        assertTrue(IconCatalogInvalidation.isAffected(scopeRoot, scopeRoot.resolve("proprietary")))
        assertTrue(IconCatalogInvalidation.isAffected(scopeRoot, scopeRoot.resolve("proprietary/package.json")))

        assertFalse(IconCatalogInvalidation.isAffected(scopeRoot, scopeRoot.resolve("core/styles/variables.less")))
        assertFalse(IconCatalogInvalidation.isAffected(scopeRoot, scopeRoot.resolve("icons/fesm2022/index.mjs")))
        assertFalse(IconCatalogInvalidation.isAffected(scopeRoot, workspace.resolve("src/app.ts")))
    }

    private fun result(cachePolicy: IconCatalogCachePolicy): IconCatalogLoadResult =
        IconCatalogLoadResult(
            catalog = IconCatalog(emptyList()),
            cachePolicy = cachePolicy,
        )

    private fun scopeRoot(name: String): Path =
        Path
            .of("build/fixtures/icon-cache/$name/node_modules/@taiga-ui")
            .toAbsolutePath()
            .normalize()
}
