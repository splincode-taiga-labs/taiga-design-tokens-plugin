package org.taigaui.designtokens.icons

import com.intellij.ui.scale.ScaleContext
import com.intellij.util.SVGLoader
import java.awt.Image
import java.net.URI

internal class IconSvgPreviewRenderer {
    private val cache = mutableMapOf<IconPreviewRenderKey, Image>()

    fun render(
        source: IconSvgSource,
        logicalSize: Int,
    ): Image? {
        val key = IconPreviewRenderKey(source.uri, logicalSize)
        val cached = synchronized(cache) { cache[key] }

        return cached
            ?: renderSvg(source, logicalSize)
                ?.also { image ->
                    synchronized(cache) {
                        cache[key] = image
                    }
                }
    }

    private fun renderSvg(
        source: IconSvgSource,
        logicalSize: Int,
    ): Image? =
        runCatching {
            val url = source.uri.toURL()

            url.openStream().use { stream ->
                SVGLoader.load(
                    url,
                    stream,
                    ScaleContext.create(),
                    logicalSize.toDouble(),
                    logicalSize.toDouble(),
                )
            }
        }.getOrNull()
}

private data class IconPreviewRenderKey(
    val uri: URI,
    val logicalSize: Int,
)

internal const val ICON_PREVIEW_LOGICAL_SIZE = 144
