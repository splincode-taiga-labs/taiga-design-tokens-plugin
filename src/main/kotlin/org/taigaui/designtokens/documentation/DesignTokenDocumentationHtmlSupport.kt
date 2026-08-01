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

    return """
        <span style='display:inline-block;width:12px;height:12px;margin-right:6px;vertical-align:-1px;border:1px solid #808080;border-radius:2px;background-color:$cssColor'></span>
    """.trimIndent()
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
