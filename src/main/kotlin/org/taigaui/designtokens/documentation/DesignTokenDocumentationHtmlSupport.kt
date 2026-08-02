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

private fun Set<DesignTokenPlatform>.platformsDocumentationLabel(): String? =
    when (this) {
        setOf(DesignTokenPlatform.DESKTOP) -> "Desktop"
        setOf(DesignTokenPlatform.MOBILE) -> "Mobile"
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
