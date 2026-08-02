package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.resolution.DesignTokenValueResolution

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

private fun String.escapeHtmlAttribute(): String = escapeHtml().replace("`", "&#96;")
