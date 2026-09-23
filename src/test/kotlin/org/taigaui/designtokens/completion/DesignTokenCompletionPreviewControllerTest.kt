package org.taigaui.designtokens.completion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesignTokenCompletionPreviewControllerTest {
    @Test
    fun `keeps full Taiga token lookup string`() {
        assertEquals(
            "--tui-text-primary",
            normalizeDesignTokenLookupString("--tui-text-primary"),
        )
    }

    @Test
    fun `normalizes native CSS lookup string without custom property prefix`() {
        assertEquals(
            "--tui-text-primary",
            normalizeDesignTokenLookupString("tui-text-primary"),
        )
    }

    @Test
    fun `extracts numeric project custom property values`() {
        assertEquals(
            "40px",
            extractCustomPropertyValue(
                ".field { --tui-height: 40px; }",
                "--tui-height",
            ),
        )
        assertEquals(
            "300ms",
            extractCustomPropertyValue(
                ":root { --tui-duration: 300ms; }",
                "--tui-duration",
            ),
        )
    }

    @Test
    fun `extracts functional project custom property values`() {
        assertEquals(
            "calc(1rem + 2px)",
            extractCustomPropertyValue(
                """
                .field {
                    --tui-radius: calc(1rem + 2px);
                }
                """.trimIndent(),
                "--tui-radius",
            ),
        )
    }

    @Test
    fun `ignores unrelated lookup string`() {
        assertNull(normalizeDesignTokenLookupString("background-color"))
    }
}
