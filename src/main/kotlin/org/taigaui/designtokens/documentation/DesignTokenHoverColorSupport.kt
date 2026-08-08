package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.resolution.DesignTokenColorValue
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.awt.Color
import java.util.Locale
import kotlin.math.roundToInt

internal fun DesignTokenValueResolution.toHoverColorOrNull(): Color? =
    (this as? DesignTokenValueResolution.Resolved)
        ?.color
        ?.toAwtColorOrNull()

internal fun DesignTokenValueResolution.hoverReferenceValueText(color: Color?): String {
    val value = hoverValueText()
    val rgba = color?.toRgbaText()

    return when {
        rgba == null -> value
        value.startsWith("rgba(", ignoreCase = true) -> value
        else -> "$value, $rgba"
    }
}

private fun DesignTokenColorValue.toAwtColorOrNull(): Color? =
    canonicalValue.toHexColorOrNull()
        ?: cssText.toRgbColorOrNull()
        ?: canonicalValue.toRgbColorOrNull()

private fun String.toHexColorOrNull(): Color? {
    val digits = removePrefix("#")
    val expanded =
        when (digits.length) {
            3 -> digits.duplicateCharacters() + "ff"
            4 -> digits.duplicateCharacters()
            6 -> digits + "ff"
            8 -> digits
            else -> null
        }
    val components = expanded?.chunked(2)?.mapNotNull { component -> component.toIntOrNull(16) }

    return components
        ?.takeIf { values -> values.size == RGBA_COMPONENTS_COUNT }
        ?.let { values -> Color(values[0], values[1], values[2], values[3]) }
}

private fun String.toRgbColorOrNull(): Color? =
    RGB_FUNCTION
        .matchEntire(trim())
        ?.let(RgbFunction::from)
        ?.toColorOrNull()

private fun RgbFunction.toColorOrNull(): Color? {
    val components =
        channels
            .map(String::toRgbChannelOrNull)
            .takeIf { values -> values.size == RGB_CHANNEL_COUNT }
            ?.takeIf { values -> values.all { value -> value != null } }
            ?.map { value -> requireNotNull(value) }
    val alphaValue = alpha?.toAlphaChannelOrNull() ?: OPAQUE_ALPHA

    return components?.let { values ->
        Color(values[0], values[1], values[2], alphaValue)
    }
}

private fun String.toRgbChannelOrNull(): Int? =
    if (endsWith('%')) {
        removeSuffix("%")
            .toDoubleOrNull()
            ?.coerceIn(0.0, 100.0)
            ?.let { value -> (value * OPAQUE_ALPHA / 100.0).roundToInt() }
    } else {
        toDoubleOrNull()
            ?.coerceIn(0.0, OPAQUE_ALPHA.toDouble())
            ?.roundToInt()
    }

private fun String.toAlphaChannelOrNull(): Int? =
    if (endsWith('%')) {
        removeSuffix("%")
            .toDoubleOrNull()
            ?.coerceIn(0.0, 100.0)
            ?.let { value -> (value * OPAQUE_ALPHA / 100.0).roundToInt() }
    } else {
        toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)
            ?.let { value -> (value * OPAQUE_ALPHA).roundToInt() }
    }

private fun Color.toRgbaText(): String {
    val alphaValue =
        if (alpha == OPAQUE_ALPHA) {
            "1"
        } else {
            String
                .format(Locale.ROOT, "%.2f", alpha.toDouble() / OPAQUE_ALPHA)
                .trimEnd('0')
                .trimEnd('.')
        }

    return "rgba($red, $green, $blue, $alphaValue)"
}

private fun String.duplicateCharacters(): String =
    buildString(length * 2) {
        this@duplicateCharacters.forEach { character ->
            append(character)
            append(character)
        }
    }

private data class RgbFunction(
    val channels: List<String>,
    val alpha: String?,
) {
    companion object {
        fun from(match: MatchResult): RgbFunction {
            val functionName = match.groupValues[1].lowercase()
            val body = match.groupValues[2].trim()
            val parts =
                if (body.contains(',')) {
                    body.split(',').map(String::trim)
                } else {
                    body
                        .replace("/", " / ")
                        .split(WHITESPACE)
                        .filter(String::isNotEmpty)
                }
            val slashIndex = parts.indexOf("/")
            val channels = if (slashIndex >= 0) parts.take(slashIndex) else parts.take(RGB_CHANNEL_COUNT)
            val alpha =
                when {
                    slashIndex >= 0 -> parts.getOrNull(slashIndex + 1)
                    functionName == "rgba" -> parts.getOrNull(RGB_CHANNEL_COUNT)
                    else -> null
                }

            return RgbFunction(channels, alpha)
        }
    }
}

private val RGB_FUNCTION = Regex("""(?i)(rgb|rgba)\((.*)\)""")
private val WHITESPACE = Regex("\\s+")
private const val OPAQUE_ALPHA = 255
private const val RGB_CHANNEL_COUNT = 3
private const val RGBA_COMPONENTS_COUNT = 4
