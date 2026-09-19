package org.taigaui.designtokens.units

import org.junit.Assert.assertEquals
import org.junit.Test

class AngularHostRemStyleBindingHintCollectorTest {
    @Test
    fun `shows pixel values for numeric rem host bindings`() {
        val text =
            """
            @Component({
                host: {
                    '[style.--tui-radius.rem]': '10',
                    "[style.margin-left.rem]": "-0.5",
                },
            })
            export class ExampleComponent {}
            """.trimIndent()

        val hints = AngularHostRemStyleBindingHintCollector.collect(text)

        assertEquals(listOf(" 160px", " -8px"), hints.map(RemInlayHint::text))
        assertEquals(
            listOf("10rem = 160px", "-0.5rem = -8px"),
            hints.map(RemInlayHint::tooltip),
        )
    }

    @Test
    fun `ignores non numeric and non rem host bindings`() {
        val text =
            """
            @Component({
                host: {
                    '[style.width.rem]': 'size',
                    '[style.width.px]': '10',
                    '[class.rem]': '10',
                },
            })
            export class ExampleComponent {}
            """.trimIndent()

        assertEquals(
            emptyList<RemInlayHint>(),
            AngularHostRemStyleBindingHintCollector.collect(text),
        )
    }
}
