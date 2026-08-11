package org.taigaui.designtokens.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProjectStylesheetIndexCacheTest {
    @Test
    fun `invalidates project entries for stylesheet changes but ignores node modules`() {
        val workspaceRoot = Path.of("build/fixtures/project-cache").toAbsolutePath().normalize()
        val request =
            ProjectStylesheetIndexRequest(
                sourceFile = workspaceRoot.resolve("src/component.scss"),
                workspaceRoot = workspaceRoot,
            )
        val cache =
            ProjectStylesheetIndexCache { cacheRequest ->
                DesignTokenIndex.build(cacheRequest.workspaceRoot, emptyList())
            }

        assertFalse(cache.contains(request))

        cache.getOrBuild(request)

        assertTrue(cache.contains(request))
        assertEquals(
            0,
            cache.invalidate(
                listOf(workspaceRoot.resolve("node_modules/@taiga-ui/core/styles/variables.less")),
            ),
        )
        assertTrue(cache.contains(request))
        assertEquals(1, cache.invalidate(listOf(workspaceRoot.resolve("src/theme.scss"))))
        assertFalse(cache.contains(request))
    }

    @Test
    fun `invalidation does not wait for an in flight project index build`() {
        val workspaceRoot = Path.of("build/fixtures/project-cache-concurrency").toAbsolutePath().normalize()
        val request =
            ProjectStylesheetIndexRequest(
                sourceFile = workspaceRoot.resolve("src/component.scss"),
                workspaceRoot = workspaceRoot,
            )
        val buildStarted = CountDownLatch(1)
        val releaseBuild = CountDownLatch(1)
        val builds = AtomicInteger()
        val cache =
            ProjectStylesheetIndexCache { cacheRequest ->
                builds.incrementAndGet()
                buildStarted.countDown()
                releaseBuild.await(10, TimeUnit.SECONDS)
                DesignTokenIndex.build(cacheRequest.workspaceRoot, emptyList())
            }
        val executor = Executors.newFixedThreadPool(2)

        try {
            val buildFuture =
                executor.submit<DesignTokenIndex> {
                    cache.getOrBuild(request)
                }

            assertTrue(buildStarted.await(10, TimeUnit.SECONDS))

            val invalidationFuture =
                executor.submit<Int> {
                    cache.invalidate(listOf(workspaceRoot.resolve("src/theme.scss")))
                }

            assertEquals(0, invalidationFuture.get(5, TimeUnit.SECONDS))

            releaseBuild.countDown()
            buildFuture.get(10, TimeUnit.SECONDS)

            assertFalse(cache.contains(request))

            cache.getOrBuild(request)

            assertEquals(2, builds.get())
            assertTrue(cache.contains(request))
        } finally {
            releaseBuild.countDown()
            executor.shutdownNow()
        }
    }
}
