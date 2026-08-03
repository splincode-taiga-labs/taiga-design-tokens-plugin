package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme

class DesignTokenHoverPopupSupportTest {
    @Test
    fun `labels ios and android separately`() {
        assertEquals(
            "📱 iOS · Any theme",
            listOf(DesignTokenContext(DesignTokenPlatform.IOS, DesignTokenTheme.UNSPECIFIED))
                .hoverPlatformLabel(),
        )
        assertEquals(
            "🤖 Android · Any theme",
            listOf(DesignTokenContext(DesignTokenPlatform.ANDROID, DesignTokenTheme.UNSPECIFIED))
                .hoverPlatformLabel(),
        )
    }

    @Test
    fun `collapses equivalent ios and android contexts`() {
        assertEquals(
            "📱 iOS and Android · Light ☀️ and dark 🌚",
            listOf(
                DesignTokenContext(DesignTokenPlatform.IOS, DesignTokenTheme.LIGHT),
                DesignTokenContext(DesignTokenPlatform.IOS, DesignTokenTheme.DARK),
                DesignTokenContext(DesignTokenPlatform.ANDROID, DesignTokenTheme.LIGHT),
                DesignTokenContext(DesignTokenPlatform.ANDROID, DesignTokenTheme.DARK),
            ).hoverPlatformLabel(),
        )
    }

    @Test
    fun `collapses desktop ios and android into all platforms`() {
        assertEquals(
            "All platforms · Light ☀️",
            listOf(
                DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.LIGHT),
                DesignTokenContext(DesignTokenPlatform.IOS, DesignTokenTheme.LIGHT),
                DesignTokenContext(DesignTokenPlatform.ANDROID, DesignTokenTheme.LIGHT),
            ).hoverPlatformLabel(),
        )
    }
}
