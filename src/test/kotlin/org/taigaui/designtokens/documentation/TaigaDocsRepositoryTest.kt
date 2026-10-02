package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.atomic.AtomicInteger

class TaigaDocsRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val source = requireNotNull(TaigaDocsSources.forMajor(5))

    @Test
    fun usesLastSuccessfulDiskSnapshotWhenOffline() {
        val cache = TaigaDocsCache(temporaryFolder.newFolder("cache").toPath())
        val fetchCalls = AtomicInteger()
        val online =
            TaigaDocsRepository(
                fetcher =
                    TaigaDocsFetcher {
                        fetchCalls.incrementAndGet()
                        docs()
                    },
                cache = cache,
            )

        val first = requireNotNull(online.load(source))
        assertEquals(TaigaDocsLoadOrigin.REMOTE, first.origin)
        assertEquals(1, fetchCalls.get())

        val offline =
            TaigaDocsRepository(
                fetcher = TaigaDocsFetcher { null },
                cache = cache,
            )
        val cached = requireNotNull(offline.load(source))

        assertEquals(TaigaDocsLoadOrigin.DISK_CACHE, cached.origin)
        assertEquals("components/button", cached.index.findByPublicSymbol("TuiButton").single().sectionId)
    }

    @Test
    fun malformedRefreshKeepsLastSuccessfulDiskSnapshot() {
        val cache = TaigaDocsCache(temporaryFolder.newFolder("fallback").toPath())

        requireNotNull(
            TaigaDocsRepository(
                fetcher = TaigaDocsFetcher { docs() },
                cache = cache,
            ).load(source),
        )

        val result =
            requireNotNull(
                TaigaDocsRepository(
                    fetcher = TaigaDocsFetcher { "invalid remote response" },
                    cache = cache,
                ).refresh(source),
            )

        assertEquals(TaigaDocsLoadOrigin.DISK_CACHE, result.origin)
        assertEquals(1, result.index.findByPublicSymbol("TuiButton").size)
    }

    @Test
    fun emptyOrMalformedSourceWithoutCacheReturnsNull() {
        assertNull(
            TaigaDocsRepository(
                fetcher = TaigaDocsFetcher { "" },
                cache = TaigaDocsCache(temporaryFolder.newFolder("empty").toPath()),
            ).load(source),
        )
        assertNull(
            TaigaDocsRepository(
                fetcher = TaigaDocsFetcher { "not docs" },
                cache = TaigaDocsCache(temporaryFolder.newFolder("malformed").toPath()),
            ).load(source),
        )
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
            "",
            "### Example",
            FENCE + "html",
            "<button tuiButton>Button</button>",
            FENCE,
        ).joinToString("\n")

    private companion object {
        const val BACKTICK = "\u0060"
        const val FENCE = "\u0060\u0060\u0060"
    }
}
