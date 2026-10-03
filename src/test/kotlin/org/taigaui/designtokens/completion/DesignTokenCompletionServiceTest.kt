package org.taigaui.designtokens.completion

import com.intellij.openapi.components.service
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.taigaui.designtokens.cache.RefreshCallback
import org.taigaui.designtokens.project.DesignTokenCatalogEntry
import org.taigaui.designtokens.project.DesignTokenIndexService
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class DesignTokenCompletionServiceTest : BasePlatformTestCase() {
    private lateinit var tempRoot: Path
    private lateinit var workspaceRoot: Path
    private lateinit var sourcePath: Path
    private lateinit var indexService: DesignTokenIndexService
    private lateinit var completionService: DesignTokenCompletionService

    override fun setUp() {
        super.setUp()
        tempRoot = Files.createTempDirectory("design-token-completion-service")
        workspaceRoot = tempRoot.resolve("workspace")
        sourcePath = workspaceRoot.resolve("src/component.less")
        indexService = project.service()
        completionService = project.service()
        indexService.clear()

        write(workspaceRoot.resolve("package.json"), "{}")
        write(
            workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens/package.json"),
            """{"name":"@taiga-ui/design-tokens","version":"1.0.0"}""",
        )
        write(
            workspaceRoot.resolve("node_modules/@taiga-ui/design-tokens/tokens.css"),
            """
            :root {
                --tui-alpha: red;
                --tui-beta: blue;
            }
            """.trimIndent(),
        )
        write(sourcePath, ".demo { color: var(--tui-alpha); }")
    }

    override fun tearDown() {
        try {
            indexService.clear()
            tempRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun testColdRequestWarmsCatalogInvokesCallbackAndThenReturnsImmediateSnapshot() {
        val calls = AtomicInteger()
        val callback = callback(calls)

        assertNull(completionService.entriesForInspection(sourcePath, callback))
        assertNull(completionService.namesForInspection(sourcePath, callback))

        waitForCalls(calls, expected = 1)

        val entries =
            requireNotNull(
                completionService.entriesForInspection(
                    sourcePath,
                    callback(calls),
                ),
            )

        assertContainsElements(
            entries.map(DesignTokenCatalogEntry::name),
            "--tui-alpha",
            "--tui-beta",
        )
        assertContainsElements(
            requireNotNull(
                completionService.namesFor(
                    sourcePath,
                    callback(calls),
                ),
            ),
            "--tui-alpha",
            "--tui-beta",
        )
    }

    fun testStaleSnapshotIsReturnedToCompletionButNotInspectionWhileRewarming() {
        val firstCalls = AtomicInteger()

        completionService.entriesFor(sourcePath, callback(firstCalls))
        waitForCalls(firstCalls, expected = 1)

        val warm =
            requireNotNull(
                completionService.entriesFor(
                    sourcePath,
                    callback(AtomicInteger()),
                ),
            )

        assertTrue(warm.isNotEmpty())

        indexService.clear()

        val stale =
            completionService.entriesFor(
                sourcePath,
                callback(AtomicInteger()),
            )
        val strict =
            completionService.entriesForInspection(
                sourcePath,
                callback(AtomicInteger()),
            )

        assertEquals(warm, stale)
        assertNull(strict)
    }

    fun testConcurrentColdRequestsCoalesceWarmupAndCallbacks() {
        val first = AtomicInteger()
        val second = AtomicInteger()

        assertNull(completionService.entriesForInspection(sourcePath, callback(first)))
        assertNull(completionService.entriesForInspection(sourcePath, callback(second)))

        waitForCalls(first, expected = 1)
        waitForCalls(second, expected = 1)

        assertTrue(indexService.isIndexCached(sourcePath))
    }

    fun testEmptyCatalogDoesNotInvokeRefreshCallback() {
        val standalone = tempRoot.resolve("standalone/app.txt")
        val calls = AtomicInteger()

        write(standalone, "plain text")

        completionService.entriesForInspection(standalone, callback(calls))

        waitUntil {
            indexService.isIndexCached(standalone) ||
                readPendingWarmupCount() == 0
        }

        assertEquals(0, calls.get())
        assertEquals(
            emptyList<DesignTokenCatalogEntry>(),
            completionService.entriesForInspection(
                standalone,
                callback(calls),
            ),
        )
    }

    private fun callback(calls: AtomicInteger): RefreshCallback<AtomicInteger> =
        RefreshCallback(
            owner = calls,
            kind = "test",
            notifyWhenUnchanged = true,
        ) { counter ->
            counter.incrementAndGet()
        }

    private fun waitForCalls(
        calls: AtomicInteger,
        expected: Int,
    ) {
        waitUntil { calls.get() >= expected }
        assertEquals(expected, calls.get())
    }

    private fun waitUntil(condition: () -> Boolean) {
        repeat(500) {
            if (condition()) {
                return
            }

            Thread.sleep(10)
        }

        assertTrue("Timed out waiting for async completion service work", condition())
    }

    private fun readPendingWarmupCount(): Int {
        val field =
            DesignTokenCompletionService::class.java
                .getDeclaredField("pendingWarmups")
                .apply { isAccessible = true }
        val value = field.get(completionService) as Set<*>

        return value.size
    }

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    }
}
