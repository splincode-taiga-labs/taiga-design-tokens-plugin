package org.taigaui.designtokens.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconReferenceAtOffsetFinderTest {
    @Test
    fun `finds icon inside typescript string`() {
        assertEquals(
            IconReferenceAtOffset(
                name = "@tui.fancy.small.clock-circle",
                startOffset = 14,
                endOffset = 43,
            ),
            reference("const icon = '@tui.fancy.small.clock-circle';", "clock"),
        )
    }

    @Test
    fun `finds icon inside static html attribute`() {
        val text = "<button iconStart=\"@tui.fancy.small.clock-circle\"></button>"
        val start = text.indexOf("@tui.")
        val name = "@tui.fancy.small.clock-circle"

        assertEquals(
            IconReferenceAtOffset(
                name = name,
                startOffset = start,
                endOffset = start + name.length,
            ),
            reference(text, "small"),
        )
    }

    @Test
    fun `finds icon inside angular bound string`() {
        val text = "<button [iconStart]=\"'@tui.fancy.small.clock-circle'\"></button>"

        assertEquals(
            "@tui.fancy.small.clock-circle",
            reference(text, "clock")?.name,
        )
    }

    @Test
    fun `ignores tui reference outside string`() {
        assertNull(reference("const icon = @tui.fancy.small.clock-circle;", "clock"))
    }

    private fun reference(
        text: String,
        marker: String,
    ): IconReferenceAtOffset? =
        IconReferenceAtOffsetFinder.find(
            text = text,
            offset = text.indexOf(marker),
        )
}
