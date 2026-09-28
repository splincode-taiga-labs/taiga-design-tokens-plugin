package org.taigaui.designtokens.icons

import com.github.weisj.jsvg.parser.SVGLoader
import java.awt.image.BufferedImage
import java.net.URI
import javax.swing.Icon
import javax.swing.ImageIcon
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
            val sourceSize = readSvgSize(source.uri) ?: return@runCatching null
            val maxSourceSize = maxOf(sourceSize.width, sourceSize.height)
            val scale = logicalSize / maxSourceSize
            val targetWidth = maxOf(1, (sourceSize.width * scale).roundToInt())
            val targetHeight = maxOf(1, (sourceSize.height * scale).roundToInt())
            val document = SVGLoader().load(source.uri.toURL()) ?: return@runCatching null
            val image =
                BufferedImage(
                    targetWidth,
                    targetHeight,
                    BufferedImage.TYPE_INT_ARGB,
                )
            val graphics = image.createGraphics()

            try {
                graphics.scale(scale.toDouble(), scale.toDouble())
                document.render(null, graphics)
            } finally {
                graphics.dispose()
            }

            ImageIcon(image)
        }.getOrNull()
}

private fun readSvgSize(uri: URI): SvgSize? =
    runCatching {
        val root =
            uri
                .toURL()
                .openStream()
                .bufferedReader()
                .use { reader -> SVG_ROOT.find(reader.readText())?.value }
                ?: return@runCatching null

        explicitSvgSize(root)
            ?: viewBoxSvgSize(root)
    }.getOrNull()

private fun explicitSvgSize(root: String): SvgSize? {
    val width = SVG_WIDTH.find(root)?.groupValues?.get(1)?.toSvgLength()
    val height = SVG_HEIGHT.find(root)?.groupValues?.get(1)?.toSvgLength()

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

private val SVG_ROOT = Regex("""<svg\b[^>]*>""", RegexOption.IGNORE_CASE)
private val SVG_WIDTH = Regex("""\swidth\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SVG_HEIGHT = Regex("""\sheight\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SVG_VIEW_BOX = Regex("""\sviewBox\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
private val SVG_LENGTH =
    Regex(
        """^([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)(?:px)?$""",
        RegexOption.IGNORE_CASE,
    )
private val VIEW_BOX_SEPARATOR = Regex("""[,\s]+""")
private const val VIEW_BOX_VALUE_COUNT = 4

internal const val ICON_PREVIEW_LOGICAL_SIZE = 64
