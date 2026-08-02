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

internal fun DesignTokenValueResolution.documentationColorDetails(): String? {
    val color = (this as? DesignTokenValueResolution.Resolved)?.color ?: return null

    return color.canonicalValue.toRgbFunction()
}

private fun String.toRgbFunction(): String? = expandedHexColor()?.toRgbFunctionOrNull()

private fun String.expandedHexColor(): String? {
    val hex = removePrefix("#")

    return when (hex.length) {
        3 -> hex.flatMapCharacters() + "ff"
        4 -> hex.flatMapCharacters()
        6 -> hex + "ff"
        8 -> hex
        else -> null
    }
}

private fun String.toRgbFunctionOrNull(): String? {
    val components = chunked(2).map { component -> component.toIntOrNull(16) }
    val red = components.getOrNull(0)
    val green = components.getOrNull(1)
    val blue = components.getOrNull(2)
    val alpha = components.getOrNull(3)

    return if (red == null || green == null || blue == null || alpha == null) {
        null
    } else if (alpha == OPAQUE_ALPHA) {
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
