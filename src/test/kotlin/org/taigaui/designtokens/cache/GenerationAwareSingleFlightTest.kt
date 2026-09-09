package org.taigaui.designtokens.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GenerationAwareSingleFlightTest {
    @Test
    fun `coalesces concurrent builds for the same key`() {
        val builds = AtomicInteger()
        val buildStarted = CountDownLatch(1)
        val secondJoined = CountDownLatch(1)
        val releaseBuild = CountDownLatch(1)
        val singleFlight = GenerationAwareSingleFlight<String, Int, Unit> { Unit }
        val executor = Executors.newFixedThreadPool(2)

        try {
            val first =
                executor.submit<Int> {
                    singleFlight.getOrBuild(
                        key = "token",
                        build = {
                            builds.incrementAndGet()
                            buildStarted.countDown()
                            releaseBuild.await(10, TimeUnit.SECONDS)
                            42
                        },
                    )
                }

            assertTrue(buildStarted.await(10, TimeUnit.SECONDS))

            val second =
                executor.submit<Int> {
                    singleFlight.getOrBuild(
                        key = "token",
                        updateMetadata = { secondJoined.countDown() },
                        build = { error("duplicate build") },
                    )
                }

            assertTrue(secondJoined.await(10, TimeUnit.SECONDS))
            releaseBuild.countDown()

            assertEquals(42, first.get(10, TimeUnit.SECONDS))
            assertEquals(42, second.get(10, TimeUnit.SECONDS))
            assertEquals(1, builds.get())
        } finally {
            releaseBuild.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `retries build when pending generation is invalidated`() {
        val builds = AtomicInteger()
        val buildStarted = CountDownLatch(1)
        val releaseBuild = CountDownLatch(1)
        val singleFlight = GenerationAwareSingleFlight<String, Int, Unit> { Unit }
        val executor = Executors.newSingleThreadExecutor()

        try {
            val result =
                executor.submit<Int> {
                    singleFlight.getOrBuild(
                        key = "token",
                        build = {
                            val currentBuild = builds.incrementAndGet()

                            buildStarted.countDown()
                            releaseBuild.await(10, TimeUnit.SECONDS)
                            currentBuild
                        },
                    )
                }

            assertTrue(buildStarted.await(10, TimeUnit.SECONDS))
            singleFlight.updatePending { _, _ -> PendingBuildAction.INVALIDATE }
            releaseBuild.countDown()

            assertEquals(2, result.get(10, TimeUnit.SECONDS))
            assertEquals(2, builds.get())
        } finally {
            releaseBuild.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `retries build when completed value is stale for pending metadata`() {
        val builds = AtomicInteger()
        val buildStarted = CountDownLatch(1)
        val releaseBuild = CountDownLatch(1)
        val singleFlight = GenerationAwareSingleFlight<String, Int, MutableSet<String>> { linkedSetOf() }
        val executor = Executors.newSingleThreadExecutor()

        try {
            val result =
                executor.submit<Int> {
                    singleFlight.getOrBuild(
                        key = "token",
                        build = {
                            val currentBuild = builds.incrementAndGet()

                            buildStarted.countDown()
                            releaseBuild.await(10, TimeUnit.SECONDS)
                            currentBuild
                        },
                        isCurrent = { metadata, _ -> "changed" !in metadata },
                    )
                }

            assertTrue(buildStarted.await(10, TimeUnit.SECONDS))
            singleFlight.updatePending { _, metadata ->
                metadata += "changed"
                PendingBuildAction.KEEP
            }
            releaseBuild.countDown()

            assertEquals(2, result.get(10, TimeUnit.SECONDS))
            assertEquals(2, builds.get())
        } finally {
            releaseBuild.countDown()
            executor.shutdownNow()
        }
    }
}
