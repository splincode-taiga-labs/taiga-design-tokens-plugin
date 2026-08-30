package org.taigaui.designtokens.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AngularRemStyleBindingHintCollectorTest {
    @Test
    fun `shows pixel values for numeric rem style bindings`() {
        val text =
            """
            <tui-icon
                [style.font-size.rem]="1"
                [style.border-width.rem]="0.25"
            />
            """.trimIndent()

        val hints = AngularRemStyleBindingHintCollector.collect(text)

        assertEquals(listOf(" 16px", " 4px"), hints.map(RemInlayHint::text))
        assertEquals(listOf("1rem = 16px", "0.25rem = 4px"), hints.map(RemInlayHint::tooltip))
        assertTrue(text.substring(0, hints[0].offset).endsWith("[style.font-size.rem]=\"1\""))
        assertTrue(text.substring(0, hints[1].offset).endsWith("[style.border-width.rem]=\"0.25\""))
    }

    @Test
    fun `supports signed decimal exponent and single quoted literals`() {
        val text =
            """
            <div
                [style.margin-left.rem]='-0.5'
                [style.width.rem]=".125"
                [style.height.rem]="1e2"
            ></div>
            """.trimIndent()

        assertEquals(
            listOf(" -8px", " 2px", " 1600px"),
            AngularRemStyleBindingHintCollector.collect(text).map(RemInlayHint::text),
        )
    }

    @Test
    fun `ignores dynamic expressions and non rem style bindings`() {
        val text =
            """
            <div
                [style.width.rem]="size"
                [style.height.rem]="size + 1"
                [style.padding.px]="16"
                [class.rem]="1"
            ></div>
            """.trimIndent()

        assertEquals(emptyList<RemInlayHint>(), AngularRemStyleBindingHintCollector.collect(text))
    }
}
