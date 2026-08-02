package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup

internal object DesignTokenDocumentationHtmlRenderer {
    fun renderHint(
        tokenName: String,
        groups: List<DesignTokenResolutionGroup>,
    ): String = renderDocumentation(tokenName, groups, definition = false)

    fun render(
        tokenName: String,
        groups: List<DesignTokenResolutionGroup>,
    ): String = renderDocumentation(tokenName, groups, definition = true)

    private fun renderDocumentation(
        tokenName: String,
        groups: List<DesignTokenResolutionGroup>,
        definition: Boolean,
    ): String =
        buildString {
            if (definition) {
                append("<div class='definition'><pre>")
                append(tokenName.escapeHtml())
                append("</pre></div>")
            } else {
                append("<b>")
                append(tokenName.escapeHtml())
                append("</b>")
            }

            append("<div class='content'>")
            append("<p><i>Design token · @taiga-ui/design-tokens</i></p>")

            if (groups.isEmpty()) {
                append("<p>No declarations were found in the installed @taiga-ui/design-tokens package.</p>")
            } else {
                appendSummaryTable(groups)
                appendReferenceSections(tokenName, groups)
            }

            append("</div>")
        }

    private fun StringBuilder.appendSummaryTable(groups: List<DesignTokenResolutionGroup>) {
        append("<table cellspacing='0' cellpadding='5' style='border-collapse:collapse;'>")
        append("<thead><tr>")
        append("<th align='left'>Platform</th>")
        append("<th align='left'>Value</th>")
        append("</tr></thead><tbody>")
        groups.forEach { group -> appendSummaryRow(group) }
        append("</tbody></table>")
    }

    private fun StringBuilder.appendSummaryRow(group: DesignTokenResolutionGroup) {
        val platforms =
            group.resolutions
                .map { resolution -> resolution.variant.context }
                .documentationPlatformLabel()
        val rawValues =
            group.resolutions
                .map { resolution -> resolution.variant.rawValue }
                .distinct()
        val result = group.representative
        val finalValue = result.documentationSummary()
        val shouldShowResolution = rawValues.any { rawValue -> rawValue != finalValue }

        append("<tr>")
        append("<td valign='top'>")
        append(platforms.escapeHtml())
        append("</td>")
        append("<td valign='top'>")

        if (shouldShowResolution) {
            append(
                rawValues.joinToString(separator = "<br>") { rawValue ->
                    "<code>${rawValue.escapeHtml()}</code>"
                },
            )
            append(" &rarr; ")
        }

        append(result.documentationSwatch())
        append("<code>")
        append(finalValue.escapeHtml())
        append("</code></td>")
        append("</tr>")
    }

    private fun StringBuilder.appendReferenceSections(
        tokenName: String,
        groups: List<DesignTokenResolutionGroup>,
    ) {
        val referenceGroups = groups.filter { group -> group.representative.references.isNotEmpty() }

        if (referenceGroups.isEmpty()) {
            return
        }

        append("<hr>")
        append("<p><b>Reference chain</b></p>")
        append("<table cellspacing='0' cellpadding='0'>")

        referenceGroups.chunked(MAX_REFERENCE_COLUMNS).forEach { row ->
            append("<tr>")

            row.forEach { group ->
                val platforms =
                    group.resolutions
                        .map { resolution -> resolution.variant.context }
                        .documentationPlatformLabel()

                append("<td valign='top' style='padding-right:24px;padding-bottom:12px;'>")
                append("<b>")
                append(platforms.escapeHtml())
                append("</b><br>")
                append("<code><b>")
                append(tokenName.escapeHtml())
                append("</b></code><br>")

                group.representative.references.forEach { reference ->
                    appendReferenceBranch(reference, depth = 0)
                }

                append("</td>")
            }

            append("</tr>")
        }

        append("</table>")
    }

    private fun StringBuilder.appendReferenceBranch(
        reference: DesignTokenReferenceResolution,
        depth: Int,
    ) {
        appendIndent(depth)
        append("&darr; <code>")
        append(reference.name.escapeHtml())
        append("</code>")

        if (reference.fallbackUsed) {
            append(" <i>(fallback)</i>")
        }

        append("<br>")

        val result = reference.effectiveResult
        val nestedReferences = result.references

        if (nestedReferences.isEmpty()) {
            appendIndent(depth)
            append("&darr; ")
            append(result.documentationSwatch())
            append("<code>")
            append(result.documentationSummary().escapeHtml())
            append("</code><br>")
        } else {
            nestedReferences.forEach { nested ->
                appendReferenceBranch(nested, depth + 1)
            }
        }
    }

    private fun StringBuilder.appendIndent(depth: Int) {
        repeat(depth) {
            append("&nbsp;&nbsp;&nbsp;&nbsp;")
        }
    }

    private const val MAX_REFERENCE_COLUMNS = 3
}
