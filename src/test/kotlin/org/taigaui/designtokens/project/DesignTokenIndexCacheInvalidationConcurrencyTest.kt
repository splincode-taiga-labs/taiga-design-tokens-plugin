package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.packageinfo.DesignTokensPackage
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DesignTokenIndexCacheInvalidationConcurrencyTest {
    @Test
    fun `invalidation does not wait for an in flight package index build`() {
        val packageRoot = path("workspace/node_modules/@taiga-ui/design-tokens")
        val designTokensPackage =
            DesignTokensPackage(
                root = packageRoot,
                realRoot = packageRoot,
                version = "0.310.0",
            )
        val buildStarted = CountDownLatch(1)
        val releaseBuild = CountDownLatch(1)
        val builds = AtomicInteger()
        val cache =
            DesignTokenIndexCache { packageInfo ->
                builds.incrementAndGet()
                buildStarted.countDown()
                releaseBuild.await(10, TimeUnit.SECONDS)
                DesignTokenIndex.build(packageInfo.realRoot, emptyList())
            }
        val executor = Executors.newFixedThreadPool(2)

        try {
            val buildFuture =
                executor.submit<DesignTokenIndex> {
                    cache.getOrBuild(designTokensPackage)
                }

            assertTrue(buildStarted.await(10, TimeUnit.SECONDS))

            val invalidationFuture =
                executor.submit<Int> {
                    cache.invalidate(listOf(packageRoot.resolve("palette/light.css")))
                }

            assertEquals(0, invalidationFuture.get(5, TimeUnit.SECONDS))

            releaseBuild.countDown()
            buildFuture.get(10, TimeUnit.SECONDS)

            assertFalse(cache.contains(designTokensPackage))

            cache.getOrBuild(designTokensPackage)

            assertEquals(2, builds.get())
            assertTrue(cache.contains(designTokensPackage))
        } finally {
            releaseBuild.countDown()
            executor.shutdownNow()
        }
    }

    private fun path(value: String): Path =
        Path
            .of("build", "cache-invalidation-concurrency", value)
            .toAbsolutePath()
            .normalize()
}
