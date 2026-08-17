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
    fun `ignores unrelated lookup string`() {
        assertNull(normalizeDesignTokenLookupString("background-color"))
    }
}
