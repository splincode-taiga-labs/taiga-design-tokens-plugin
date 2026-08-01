package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenUnresolvedReason
import org.taigaui.designtokens.resolution.DesignTokenValueResolution

internal object DesignTokenDocumentationHtmlRenderer {
    fun renderHint(
        tokenName: String,
        groups: List<DesignTokenResolutionGroup>,
    ): String {
        val firstGroup = groups.firstOrNull()
        val summary = firstGroup?.representative?.summary().orEmpty()
        val swatch = firstGroup?.representative?.swatch().orEmpty()

        return buildString {
            append("<b>")
            append(tokenName.escapeHtml())
            append("</b>")

            if (summary.isNotEmpty()) {
                append("<br>")
                append(swatch)
                append("<code>")
                append(summary.escapeHtml())
                append("</code>")
            }
        }
    }

    fun render(
        tokenName: String,
        groups: List<DesignTokenResolutionGroup>,
    ): String =
        buildString {
            append("<div class='definition'><pre>")
            append(tokenName.escapeHtml())
            append("</pre></div>")
            append("<div class='content'>")

            if (groups.isEmpty()) {
                append("<p>No declarations were found in the installed @taiga-ui/design-tokens package.</p>")
            } else {
                groups.forEachIndexed { index, group ->
                    if (index > 0) {
                        append("<hr>")
                    }

                    appendGroup(group)
                }
            }

            append("</div>")
        }

    private fun StringBuilder.appendGroup(group: DesignTokenResolutionGroup) {
        val contexts =
            group.resolutions
                .map { resolution -> resolution.variant.context }
                .distinct()
                .joinToString(separator = ", ", transform = DesignTokenContext::label)
        val result = group.representative

        append("<p><b>")
        append(contexts.escapeHtml())
        append("</b></p>")
        append("<p>")
        append(result.swatch())
        append("<b>Resolved:</b> <code>")
        append(result.summary().escapeHtml())
        append("</code></p>")

        val rawValues =
            group.resolutions
                .map { resolution -> resolution.variant.rawValue }
                .distinct()

        append("<p><b>Raw:</b> ")
        append(
            rawValues.joinToString(separator = "<br>") { rawValue ->
                "<code>${rawValue.escapeHtml()}</code>"
            },
        )
        append("</p>")
        appendReferences(result)
        appendOrigins(group.allOrigins)
    }

    private fun StringBuilder.appendReferences(result: DesignTokenValueResolution) {
        if (result.references.isEmpty()) {
            return
        }

        append("<p><b>References:</b></p>")
        appendReferenceList(result.references)
    }

    private fun StringBuilder.appendReferenceList(references: List<DesignTokenReferenceResolution>) {
        append("<ul>")

        references.forEach { reference ->
            append("<li><code>")
            append(reference.name.escapeHtml())
            append("</code> → <code>")
            append(reference.effectiveResult.summary().escapeHtml())
            append("</code>")

            if (reference.fallbackUsed) {
                append(" <i>(fallback)</i>")
            }

            if (reference.effectiveResult.references.isNotEmpty()) {
                appendReferenceList(reference.effectiveResult.references)
            }

            append("</li>")
        }

        append("</ul>")
    }

    private fun StringBuilder.appendOrigins(origins: List<DesignTokenOrigin>) {
        if (origins.isEmpty()) {
            return
        }

        append("<p><b>Sources:</b></p><ul>")

        origins.forEach { origin ->
            append("<li><code>")
            append(origin.sourceFile.toString().escapeHtml())
            append(':')
            append(origin.line)
            append("</code>")

            if (origin.selectorChain.isNotEmpty()) {
                append("<br><small>")
                append(origin.selectorChain.joinToString(" → ").escapeHtml())
                append("</small>")
            }

            append("</li>")
        }

        append("</ul>")
    }

    private fun DesignTokenValueResolution.summary(): String =
        when (this) {
            is DesignTokenValueResolution.Resolved -> value
            is DesignTokenValueResolution.Unresolved -> reason.summary()
        }

    private fun DesignTokenValueResolution.swatch(): String {
        val color = (this as? DesignTokenValueResolution.Resolved)?.color ?: return ""
        val cssColor = color.canonicalValue.escapeHtmlAttribute()

        return """
            <span style='display:inline-block;width:12px;height:12px;margin-right:6px;vertical-align:-1px;border:1px solid #808080;border-radius:2px;background-color:$cssColor'></span>
        """.trimIndent()
    }

    private fun DesignTokenUnresolvedReason.summary(): String =
        when (this) {
            is DesignTokenUnresolvedReason.MissingReference -> "Missing reference: $name"
            is DesignTokenUnresolvedReason.AmbiguousReference ->
                "Ambiguous reference: $name (${candidates.size} candidates)"

            is DesignTokenUnresolvedReason.CircularReference ->
                "Circular reference: ${chain.joinToString(" → ") { node -> node.name }}"

            is DesignTokenUnresolvedReason.InvalidExpression ->
                "Invalid expression at offset $offset: $message"
        }

    private fun DesignTokenContext.label(): String {
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

    private fun String.escapeHtml(): String =
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
}
