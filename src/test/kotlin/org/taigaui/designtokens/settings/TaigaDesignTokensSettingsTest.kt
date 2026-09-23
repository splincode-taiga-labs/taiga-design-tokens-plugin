package org.taigaui.designtokens.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaigaDesignTokensSettingsTest {
    @Test
    fun `enables token preview and hover by default`() {
        val settings = TaigaDesignTokensSettings()

        assertTrue(settings.showCompletionPreview)
        assertTrue(settings.showHoverPopup)
    }

    @Test
    fun `loads persisted presentation settings`() {
        val settings = TaigaDesignTokensSettings()
        val state =
            TaigaDesignTokensSettings.SettingsState().apply {
                showCompletionPreview = false
                showHoverPopup = false
            }

        settings.loadState(state)

        assertFalse(settings.showCompletionPreview)
        assertFalse(settings.showHoverPopup)
    }
}
