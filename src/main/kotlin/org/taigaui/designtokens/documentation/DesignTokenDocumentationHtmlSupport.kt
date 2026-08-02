package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.resolution.DesignTokenUnresolvedReason
import org.taigaui.designtokens.resolution.DesignTokenValueResolution

internal fun DesignTokenValueResolution.documentationSummary(): String =
    when (this) {
        is DesignTokenValueResolution.Resolved -> value
        is DesignTokenValueResolution.Unresolved -> reason.documentationSummary()
    }

internal fun DesignTokenValueResolution.documentationSwatch(): String {
    val color = (this as? DesignTokenValueResolution.Resolved)?.color ?: return ""
    val cssColor = color.canonicalValue.escapeHtmlAttribute()

    return buildString {
        append("<span style='")
        append("display:inline-block;")
        append("width:12px;")
        append("height:12px;")
        append("margin-right:6px;")
        append("vertical-align:-1px;")
        append("border:1px solid #808080;")
        append("border-radius:50%;")
        append("background-color:")
        append(cssColor)
        append("'></span>")
    }
}

internal fun DesignTokenValueResolution.documentationColorDetails(): String? {
    val color = (this as? DesignTokenValueResolution.Resolved)?.color ?: return null

    return color.canonicalValue.toRgbFunction()
}

internal fun DesignTokenUnresolvedReason.documentationSummary(): String =
    when (this) {
        is DesignTokenUnresolvedReason.MissingReference -> "Missing reference: $name"
        is DesignTokenUnresolvedReason.AmbiguousReference ->
            "Ambiguous reference: $name (${candidates.size} candidates)"

        is DesignTokenUnresolvedReason.CircularReference ->
            "Circular reference: ${chain.joinToString(" → ") { node -> node.name }}"

        is DesignTokenUnresolvedReason.InvalidExpression ->
            "Invalid expression at offset $offset: $message"
    }

internal fun Collection<DesignTokenContext>.documentationContextsLabel(): String {
    val contexts = distinct()
    val platforms = contexts.map(DesignTokenContext::platform).toSet()
    val themes = contexts.map(DesignTokenContext::theme).toSet()
    val expectedContexts =
        platforms
            .flatMap { platform ->
                themes.map { theme -> DesignTokenContext(platform, theme) }
            }.toSet()
    val platformLabel = platforms.platformsDocumentationLabel()
    val themeLabel = themes.themesDocumentationLabel()
    val canCompact =
        contexts.toSet() == expectedContexts &&
            platformLabel != null &&
            themeLabel != null

    return if (canCompact) {
        "$platformLabel · $themeLabel"
    } else {
        contexts.joinToString(separator = ", ", transform = DesignTokenContext::documentationLabel)
    }
}

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

internal fun DesignTokenContext.documentationLabel(): String {
    val platformLabel =
        when (platform) {
            DesignTokenPlatform.DESKTOP -> "Desktop"
            DesignTokenPlatform.MOBILE -> "Mobile"
        }
    val themeLabel =
        when (theme) {
            DesignTokenTheme.LIGHT -> "Light"
            DesignTokenTheme.DARK -> "Dark"
            DesignTokenTheme.UNSPECIFIED -> "Any theme"
        }

    return "$platformLabel · $themeLabel"
}

internal fun String.escapeHtml(): String =
    buildString(length) {
        this@escapeHtml.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character
                },
            )
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

private fun Set<DesignTokenPlatform>.platformsDocumentationLabel(): String? =
    when (this) {
        setOf(DesignTokenPlatform.DESKTOP) -> "Desktop"
        setOf(DesignTokenPlatform.MOBILE) -> "Mobile"
        DesignTokenPlatform.entries.toSet() -> "All platforms"
        else -> null
    }

private fun Set<DesignTokenPlatform>.platformsDocumentationLabelWithIcon(): String? =
    when (this) {
        setOf(DesignTokenPlatform.DESKTOP) -> "🖥️ Desktop"
        setOf(DesignTokenPlatform.MOBILE) -> "📱 Mobile"
        DesignTokenPlatform.entries.toSet() -> "All platforms"
        else -> null
    }

private fun Set<DesignTokenTheme>.themesDocumentationLabel(): String? =
    when (this) {
        setOf(DesignTokenTheme.LIGHT) -> "Light"
        setOf(DesignTokenTheme.DARK) -> "Dark"
        setOf(DesignTokenTheme.UNSPECIFIED) -> "Any theme"
        setOf(DesignTokenTheme.LIGHT, DesignTokenTheme.DARK) -> "Light and dark"
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

private fun String.toRgbFunction(): String? {
    val hex = removePrefix("#")
    val expanded =
        when (hex.length) {
            3 -> hex.flatMapCharacters() + "ff"
            4 -> hex.flatMapCharacters()
            6 -> hex + "ff"
            8 -> hex
            else -> return null
        }
    val red = expanded.substring(0, 2).toIntOrNull(16) ?: return null
    val green = expanded.substring(2, 4).toIntOrNull(16) ?: return null
    val blue = expanded.substring(4, 6).toIntOrNull(16) ?: return null
    val alpha = expanded.substring(6, 8).toIntOrNull(16) ?: return null

    return if (alpha == OPAQUE_ALPHA) {
        "rgb($red, $green, $blue)"
    } else {
        "rgba($red, $green, $blue, ${alpha.toCssAlpha()})"
    }
}

private fun String.flatMapCharacters(): String = buildString(length * 2) {
    this@flatMapCharacters.forEach { character ->
        append(character)
        append(character)
    }
}

private fun Int.toCssAlpha(): String {
    val hundredths = (this * 100 + OPAQUE_ALPHA / 2) / OPAQUE_ALPHA
    val whole = hundredths / 100
    val fraction = hundredths % 100

    return when {
        fraction == 0 -> whole.toString()
        fraction % 10 == 0 -> "$whole.${fraction / 10}"
        else -> "$whole.${fraction.toString().padStart(2, '0')}"
    }
}

private fun String.escapeHtmlAttribute(): String = escapeHtml().replace("`", "&#96;")

private const val OPAQUE_ALPHA = 255
