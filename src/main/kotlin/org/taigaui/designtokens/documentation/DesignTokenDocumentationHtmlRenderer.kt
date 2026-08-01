package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup

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
                appendSummaryTable(groups)
                appendReferenceSections(groups)
            }

            append("</div>")
        }

    private fun StringBuilder.appendSummaryTable(groups: List<DesignTokenResolutionGroup>) {
        append("<table cellspacing='0' cellpadding='4'>")
        append("<thead><tr>")
        append("<th align='left'>Context</th>")
        append("<th align='left'>Token value</th>")
        append("<th align='left'>Final value</th>")
        append("</tr></thead><tbody>")
        groups.forEach { group -> appendSummaryRow(group) }
        append("</tbody></table>")
    }

    private fun StringBuilder.appendSummaryRow(group: DesignTokenResolutionGroup) {
        val contexts =
            group.resolutions
                .map { resolution -> resolution.variant.context }
                .documentationContextsLabel()
        val rawValues =
            group.resolutions
                .map { resolution -> resolution.variant.rawValue }
                .distinct()
        val result = group.representative

        append("<tr>")
        append("<td valign='top'>")
        append(contexts.escapeHtml())
        append("</td>")
        append("<td valign='top'>")
        append(
            rawValues.joinToString(separator = "<br>") { rawValue ->
                "<code>${rawValue.escapeHtml()}</code>"
            },
        )
        append("</td>")
        append("<td valign='top'>")
        append(result.documentationSwatch())
        append("<code>")
        append(result.documentationSummary().escapeHtml())
        append("</code></td>")
        append("</tr>")
    }

    private fun StringBuilder.appendReferenceSections(groups: List<DesignTokenResolutionGroup>) {
        groups.forEach { group ->
            val references = group.representative.references

            if (references.isNotEmpty()) {
                val contexts =
                    group.resolutions
                        .map { resolution -> resolution.variant.context }
                        .documentationContextsLabel()

                append("<p><b>Reference chain")

                if (groups.size > 1) {
                    append(" · ")
                    append(contexts.escapeHtml())
                }

                append(":</b></p>")
                appendReferenceList(references)
            }
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
}
