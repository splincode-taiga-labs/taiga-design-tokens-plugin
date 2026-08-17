package org.taigaui.designtokens.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconCompletionContextFinderTest {
    @Test
    fun `finds icon prefix inside single quoted string`() {
        assertEquals(
            IconCompletionContext("@tui.fancy.medium.inf"),
            context("const icon = '@tui.fancy.medium.inf|';"),
        )
    }

    @Test
    fun `finds proprietary icon prefix inside status object`() {
        assertEquals(
            IconCompletionContext("@tui.fancy.medium.info"),
            context(
                """
                const icons = {
                    info: '@tui.fancy.medium.info|',
                    warning: '@tui.fancy.medium.alert',
                    neutral: '@tui.fancy.medium.info-circle',
                    error: '@tui.fancy.medium.alert',
                    success: '@tui.fancy.medium.check-circle',
                };
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `finds icon prefix inside html attribute`() {
        assertEquals(
            IconCompletionContext("@tui.flags.a"),
            context("<tui-icon icon='@tui.flags.a|'></tui-icon>"),
        )
    }

    @Test
    fun `ignores tui prefix outside a string`() {
        assertNull(context("const icon = @tui.flags.a|;"))
    }

    @Test
    fun `ignores unrelated string`() {
        assertNull(context("const icon = 'tui.flags.a|';"))
    }

    private fun context(value: String): IconCompletionContext? {
        val offset = value.indexOf('|')
        val text = value.replace("|", "")

        return IconCompletionContextFinder.find(text, offset)
    }
}
