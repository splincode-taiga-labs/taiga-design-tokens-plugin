package org.taigaui.designtokens.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaigaDesignTokensSettingsTest {
    @Test
    fun `keeps current presentation as the default`() {
        val settings = TaigaDesignTokensSettings()

        assertFalse(settings.showCompletionSourceDetails)
        assertFalse(settings.showHoverSourceDetails)
    }

    @Test
    fun `loads persisted presentation settings`() {
        val settings = TaigaDesignTokensSettings()
        val state =
            TaigaDesignTokensSettings.SettingsState().apply {
                showCompletionSourceDetails = true
                showHoverSourceDetails = true
            }

        settings.loadState(state)

        assertTrue(settings.showCompletionSourceDetails)
        assertTrue(settings.showHoverSourceDetails)
    }
}
