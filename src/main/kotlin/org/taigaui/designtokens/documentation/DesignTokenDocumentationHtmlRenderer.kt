package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenValueResolution

internal object DesignTokenDocumentationHtmlRenderer {
    fun renderHint(
        tokenName: String,
        groups: List<DesignTokenResolutionGroup>,
    ): String {
        val firstGroup = groups.firstOrNull()
        val summary = firstGroup?.representative?.documentationSummary().orEmpty()
        val swatch = firstGroup?.representative?.documentationSwatch().orEmpty()

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
                .joinToString(separator = ", ", transform = DesignTokenContext::documentationLabel)
        val result = group.representative

        append("<p><b>")
        append(contexts.escapeHtml())
        append("</b></p>")
        append("<p>")
        append(result.documentationSwatch())
        append("<b>Resolved:</b> <code>")
        append(result.documentationSummary().escapeHtml())
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
        if (result.references.isNotEmpty()) {
            append("<p><b>References:</b></p>")
            appendReferenceList(result.references)
        }
    }

    private fun StringBuilder.appendReferenceList(references: List<DesignTokenReferenceResolution>) {
        append("<ul>")

        references.forEach { reference ->
            append("<li><code>")
            append(reference.name.escapeHtml())
            append("</code> → <code>")
            append(reference.effectiveResult.documentationSummary().escapeHtml())
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
        if (origins.isNotEmpty()) {
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
    }
}
