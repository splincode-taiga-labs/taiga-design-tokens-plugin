package org.taigaui.designtokens.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemInlayHintCollectorTest {
    @Test
    fun `shows pixel value after declaration semicolon`() {
        val text = ".button { gap: 1rem; }"
        val hint = RemInlayHintCollector.collect(text).single()

        assertEquals(" 16px", hint.text)
        assertEquals("1rem = 16px", hint.tooltip)
        assertTrue(text.substring(0, hint.offset).endsWith("1rem;"))
    }

    @Test
    fun `groups multiple rem values on the same declaration line`() {
        val text = ".button { padding: 1rem 2.5rem; }"
        val hint = RemInlayHintCollector.collect(text).single()

        assertEquals(" 16px · 40px", hint.text)
        assertEquals("1rem = 16px · 2.5rem = 40px", hint.tooltip)
        assertTrue(text.substring(0, hint.offset).endsWith("2.5rem;"))
    }

    @Test
    fun `creates independent hints for separate lines`() {
        val text =
            """
            .button {
                gap: 1rem;
                min-width: 21rem;
            }
            """.trimIndent()

        assertEquals(
            listOf(" 16px", " 336px"),
            RemInlayHintCollector.collect(text).map(RemInlayHint::text),
        )
    }

    @Test
    fun `ignores rem values inside strings and comments`() {
        val text =
            """
            .button::before {
                content: "1rem";
                // gap: 2rem;
                /* width: 3rem; */
                margin: 4rem;
            }
            """.trimIndent()

        assertEquals(
            listOf(" 64px"),
            RemInlayHintCollector.collect(text).map(RemInlayHint::text),
        )
    }
}
