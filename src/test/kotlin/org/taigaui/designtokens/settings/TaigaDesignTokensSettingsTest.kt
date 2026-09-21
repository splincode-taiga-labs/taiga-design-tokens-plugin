package org.taigaui.designtokens.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class TaigaDesignTokensSettingsTest {
    @Test
    fun `enables presentation details by default`() {
        val settings = TaigaDesignTokensSettings()

        assertTrue(settings.showCompletionSourceDetails)
        assertTrue(settings.showHoverSourceDetails)
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
