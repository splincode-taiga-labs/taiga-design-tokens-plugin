package org.taigaui.designtokens.icons

import com.github.weisj.jsvg.parser.SVGLoader
import com.github.weisj.jsvg.view.ViewBox
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
            val document = SVGLoader().load(source.uri.toURL()) ?: return@runCatching null
            val sourceSize = document.size()
            val maxSourceSize = maxOf(sourceSize.width, sourceSize.height)

            if (maxSourceSize <= 0F) {
                return@runCatching null
            }

            val scale = logicalSize / maxSourceSize
            val targetWidth = maxOf(1, (sourceSize.width * scale).roundToInt())
            val targetHeight = maxOf(1, (sourceSize.height * scale).roundToInt())
            val image =
                BufferedImage(
                    targetWidth,
                    targetHeight,
                    BufferedImage.TYPE_INT_ARGB,
                )
            val graphics = image.createGraphics()

            try {
                document.render(
                    null,
                    graphics,
                    ViewBox(targetWidth.toFloat(), targetHeight.toFloat()),
                )
            } finally {
                graphics.dispose()
            }

            ImageIcon(image)
        }.getOrNull()
}

private data class IconPreviewRenderKey(
    val uri: URI,
    val logicalSize: Int,
)

internal const val ICON_PREVIEW_LOGICAL_SIZE = 64
