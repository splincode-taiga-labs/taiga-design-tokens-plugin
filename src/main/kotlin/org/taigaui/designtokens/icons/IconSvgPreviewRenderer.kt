package org.taigaui.designtokens.icons

import com.intellij.openapi.util.IconLoader
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.math.roundToInt

internal class IconSvgPreviewRenderer {
    private val cache = mutableMapOf<IconPreviewRenderKey, Icon>()

    fun render(
        source: IconSvgSource,
        logicalSize: Int,
    ): Icon? {
        val key = IconPreviewRenderKey(source.uri, logicalSize)
        val cached = synchronized(cache) { cache[key] }

        return cached
            ?: renderSvg(source, logicalSize)
                ?.also { icon ->
                    synchronized(cache) {
                        cache[key] = icon
                    }
                }
    }

    private fun renderSvg(
        source: IconSvgSource,
        logicalSize: Int,
    ): Icon? =
        runCatching {
            val svg = readSvg(source.uri)
            val sourceSize = readSvgSize(svg) ?: return@runCatching null
            val maxSourceSize = maxOf(sourceSize.width, sourceSize.height)
            val scale = logicalSize / maxSourceSize
            val targetWidth = maxOf(1, (sourceSize.width * scale).roundToInt())
            val targetHeight = maxOf(1, (sourceSize.height * scale).roundToInt())
            val previewSvg = resizeSvgRoot(svg, targetWidth, targetHeight)
            val previewFile = writePreviewSvg(previewSvg)

            IconLoader
                .findIcon(previewFile.toUri().toURL(), false)
                ?.also { previewFile.toFile().deleteOnExit() }
                ?: Files.deleteIfExists(previewFile).let { null }
        }.getOrNull()
}

private fun readSvg(uri: URI): String =
    uri
        .toURL()
        .openStream()
        .bufferedReader()
        .use { reader -> reader.readText() }

private fun readSvgSize(svg: String): SvgSize? =
    SVG_ROOT
        .find(svg)
        ?.value
        ?.let(::explicitSvgSize)
        ?: SVG_ROOT
            .find(svg)
            ?.value
            ?.let(::viewBoxSvgSize)

private fun explicitSvgSize(root: String): SvgSize? {
    val width =
        SVG_WIDTH
            .find(root)
            ?.groupValues
            ?.get(1)
            ?.toSvgLength()
    val height =
        SVG_HEIGHT
            .find(root)
            ?.groupValues
            ?.get(1)
            ?.toSvgLength()

    return svgSize(width, height)
}

private fun viewBoxSvgSize(root: String): SvgSize? =
    SVG_VIEW_BOX
        .find(root)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.split(VIEW_BOX_SEPARATOR)
        ?.mapNotNull(String::toFloatOrNull)
        ?.takeIf { values -> values.size == VIEW_BOX_VALUE_COUNT }
        ?.let { values -> svgSize(values[2], values[3]) }

private fun resizeSvgRoot(
    svg: String,
    width: Int,
    height: Int,
): String {
    val match = SVG_ROOT.find(svg) ?: return svg
    val resizedRoot =
        match.value
            .withSvgAttribute(SVG_WIDTH_ATTRIBUTE, width)
            .withSvgAttribute(SVG_HEIGHT_ATTRIBUTE, height)

    return svg.replaceRange(match.range, resizedRoot)
}

private fun String.withSvgAttribute(
    attribute: Regex,
    value: Int,
): String =
    if (attribute.containsMatchIn(this)) {
        replace(attribute, """ $1"$value"""")
    } else {
        val suffixLength = if (endsWith("/>")) 2 else 1
        val insertionPoint = length - suffixLength

        substring(0, insertionPoint) +
            """ $value""".let { """ ${attribute.attributeName()}="$value"""" } +
            substring(insertionPoint)
    }

private fun Regex.attributeName(): String =
    when (this) {
        SVG_WIDTH_ATTRIBUTE -> "width"
        SVG_HEIGHT_ATTRIBUTE -> "height"
        else -> error("Unknown SVG attribute")
    }

private fun writePreviewSvg(svg: String): Path =
    Files.createTempFile(PREVIEW_FILE_PREFIX, SVG_FILE_SUFFIX).also { path ->
        Files.writeString(path, svg)
    }

private fun svgSize(
    width: Float?,
    height: Float?,
): SvgSize? =
    if (width != null && height != null) {
        SvgSize(width, height).takeIf(SvgSize::isValid)
    } else {
        null
    }

private fun SvgSize.isValid(): Boolean = width > 0F && height > 0F

private fun String.toSvgLength(): Float? =
    SVG_LENGTH
        .matchEntire(trim())
        ?.groupValues
        ?.get(1)
        ?.toFloatOrNull()

private data class SvgSize(
    val width: Float,
    val height: Float,
)

private data class IconPreviewRenderKey(
    val uri: URI,
    val logicalSize: Int,
)

private val SVG_ROOT = Regex("""<svg\\b[^>]*>""", RegexOption.IGNORE_CASE)
private val SVG_WIDTH = Regex("""\\swidth\\s*=\\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SVG_HEIGHT = Regex("""\\sheight\\s*=\\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SVG_VIEW_BOX = Regex("""\\sviewBox\\s*=\\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SVG_WIDTH_ATTRIBUTE = Regex("""(\\swidth\\s*=\\s*)["'][^"']*["']""", RegexOption.IGNORE_CASE)
private val SVG_HEIGHT_ATTRIBUTE = Regex("""(\\sheight\\s*=\\s*)["'][^"']*["']""", RegexOption.IGNORE_CASE)
private val SVG_LENGTH =
    Regex(
        """^([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)(?:px)?$""",
        RegexOption.IGNORE_CASE,
    )
private val VIEW_BOX_SEPARATOR = Regex("""[,\\s]+""")
private const val VIEW_BOX_VALUE_COUNT = 4
private const val PREVIEW_FILE_PREFIX = "taiga-ui-icon-preview-"
private const val SVG_FILE_SUFFIX = ".svg"

internal const val ICON_PREVIEW_LOGICAL_SIZE = 64
