package org.taigaui.designtokens.documentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TaigaDocsIndexStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun coalescesConcurrentLoadsForTheSameSource() =
        runBlocking {
            val fetchCalls = AtomicInteger()
            val fetchStarted = CountDownLatch(1)
            val releaseFetch = CountDownLatch(1)
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val source = requireNotNull(TaigaDocsSources.forMajor(5))
            val repository =
                TaigaDocsRepository(
                    fetcher =
                        TaigaDocsFetcher {
                            fetchCalls.incrementAndGet()
                            fetchStarted.countDown()
                            releaseFetch.await(5, TimeUnit.SECONDS)
                            docs()
                        },
                    cache = TaigaDocsCache(temporaryFolder.newFolder("cache").toPath()),
                )
            val store = TaigaDocsIndexStore(scope, repository)

            try {
                val requests = List(4) { async { store.indexFor(source) } }

                fetchStarted.await(5, TimeUnit.SECONDS)
                releaseFetch.countDown()
                val results = requests.awaitAll()

                assertEquals(1, fetchCalls.get())
                assertEquals(4, results.count { result -> result != null })
            } finally {
                scope.cancel()
            }
        }

    private fun docs(): String =
        listOf(
            "# Import Map - Package Exports Reference",
            "",
            "## @taiga-ui/core",
            "**Components:**",
            "### button",
            FENCE + "text",
            "TuiButton",
            FENCE,
            "",
            "# components/Button",
            "- **Package**: " + BACKTICK + "CORE" + BACKTICK,
            "- **Type**: components",
            "- **Version**: 5.0.0",
            "",
            "Button documentation.",
        ).joinToString("\n")

    private companion object {
        const val BACKTICK = "\u0060"
        const val FENCE = "\u0060\u0060\u0060"
    }
}
