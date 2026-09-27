package org.taigaui.designtokens.icons

import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ImageLoader
import com.intellij.util.SVGLoader
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import java.net.URI
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
            val url = source.uri.toURL()
            val sourceImage = ImageLoader.loadFromUrl(url) ?: return@runCatching null
            val sourceWidth = sourceImage.getWidth(null)
            val sourceHeight = sourceImage.getHeight(null)
            val maxSourceSize = maxOf(sourceWidth, sourceHeight)

            if (maxSourceSize <= 0) {
                return@runCatching null
            }

            val scale = logicalSize.toDouble() / maxSourceSize
            val targetWidth = maxOf(1, (sourceWidth * scale).roundToInt())
            val targetHeight = maxOf(1, (sourceHeight * scale).roundToInt())
            val scaleContext = ScaleContext.create()

            val rendered =
                url.openStream().use { stream ->
                    SVGLoader.load(
                        url,
                        stream,
                        scaleContext,
                        targetWidth.toDouble(),
                        targetHeight.toDouble(),
                    )
                }
            val image =
                ImageUtil.ensureHiDPI(
                    rendered,
                    scaleContext,
                    targetWidth.toDouble(),
                    targetHeight.toDouble(),
                )

            JBImageIcon(image)
        }.getOrNull()
}

private data class IconPreviewRenderKey(
    val uri: URI,
    val logicalSize: Int,
)

internal const val ICON_PREVIEW_LOGICAL_SIZE = 64
