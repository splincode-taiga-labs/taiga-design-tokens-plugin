package org.taigaui.designtokens.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.IconUtil
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
            val icon = IconLoader.findIcon(source.uri.toURL()) ?: return@runCatching null
            val maxIconSize = maxOf(icon.iconWidth, icon.iconHeight)

            if (maxIconSize <= 0) {
                return@runCatching null
            }

            val scale = logicalSize.toFloat() / maxIconSize
            val scaledIcon = IconUtil.scale(icon, null, scale)

            IconLoader.toImage(scaledIcon, ScaleContext.create())
        }.getOrNull()
}

private data class IconPreviewRenderKey(
    val uri: URI,
    val logicalSize: Int,
)

internal const val ICON_PREVIEW_LOGICAL_SIZE = 64
