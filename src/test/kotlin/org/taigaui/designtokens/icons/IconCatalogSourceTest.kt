package org.taigaui.designtokens.icons

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Path

class IconCatalogSourceTest {
    @Test
    fun `uses first supporting source and does not inspect later sources`() {
        val calls = mutableListOf<String>()
        val loader =
            IconCatalogLoader(
                listOf(
                    RecordingSource("first", supports = false, calls),
                    RecordingSource("second", supports = true, calls),
                    RecordingSource("third", supports = true, calls),
                ),
            )
        val scopeRoot =
            Path
                .of("build/fixtures/icon-source-order/node_modules/@taiga-ui")
                .toAbsolutePath()
                .normalize()

        assertEquals(listOf("@tui.second"), loader.load(scopeRoot))
        assertEquals(
            listOf(
                "first:supports",
                "second:supports",
                "second:load",
            ),
            calls,
        )
    }

    private class RecordingSource(
        private val name: String,
        private val supports: Boolean,
        private val calls: MutableList<String>,
    ) : IconCatalogSource {
        override fun supports(context: IconCatalogContext): Boolean {
            calls += "$name:supports"

            return supports
        }

        override fun load(context: IconCatalogContext): IconCatalogLoadResult {
            calls += "$name:load"

            return IconCatalogLoadResult(
                catalog =
                    IconCatalog(
                        listOf(
                            IconCatalogEntry(
                                name = "@tui.$name",
                                svgSource = IconSvgSource.Local(context.scopeRoot.resolve("$name.svg")),
                            ),
                        ),
                    ),
                cachePolicy = IconCatalogCachePolicy.LOCAL,
            )
        }
    }
}
