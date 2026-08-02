package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme

internal fun Collection<DesignTokenContext>.documentationPlatformLabel(): String {
    val contexts = distinct()
    val platforms = contexts.map(DesignTokenContext::platform).toSet()
    val themes = contexts.map(DesignTokenContext::theme).toSet()
    val expectedContexts =
        platforms
            .flatMap { platform ->
                themes.map { theme -> DesignTokenContext(platform, theme) }
            }.toSet()
    val platformLabel = platforms.platformsDocumentationLabelWithIcon()
    val themeLabel = themes.themesDocumentationLabelWithEmoji()
    val canCompact =
        contexts.toSet() == expectedContexts &&
            platformLabel != null &&
            themeLabel != null

    return if (canCompact) {
        "$platformLabel · $themeLabel"
    } else {
        contexts.joinToString(separator = ", ") { context ->
            context.documentationPlatformLabel()
        }
    }
}

private fun DesignTokenContext.documentationPlatformLabel(): String {
    val platformLabel =
        when (platform) {
            DesignTokenPlatform.DESKTOP -> "🖥️ Desktop"
            DesignTokenPlatform.MOBILE -> "📱 Mobile"
        }
    val themeLabel =
        when (theme) {
            DesignTokenTheme.LIGHT -> "Light ☀️"
            DesignTokenTheme.DARK -> "Dark 🌚"
            DesignTokenTheme.UNSPECIFIED -> "Any theme"
        }

    return "$platformLabel · $themeLabel"
}

private fun Set<DesignTokenPlatform>.platformsDocumentationLabelWithIcon(): String? =
    when (this) {
        setOf(DesignTokenPlatform.DESKTOP) -> "🖥️ Desktop"
        setOf(DesignTokenPlatform.MOBILE) -> "📱 Mobile"
        DesignTokenPlatform.entries.toSet() -> "All platforms"
        else -> null
    }

private fun Set<DesignTokenTheme>.themesDocumentationLabelWithEmoji(): String? =
    when (this) {
        setOf(DesignTokenTheme.LIGHT) -> "Light ☀️"
        setOf(DesignTokenTheme.DARK) -> "Dark 🌚"
        setOf(DesignTokenTheme.UNSPECIFIED) -> "Any theme"
        setOf(DesignTokenTheme.LIGHT, DesignTokenTheme.DARK) -> "Light ☀️ and dark 🌚"
        else -> null
    }
