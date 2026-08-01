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
        append("border-radius:2px;")
        append("background-color:")
        append(cssColor)
        append("'></span>")
    }
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

private fun String.escapeHtmlAttribute(): String = escapeHtml().replace("`", "&#96;")
