package org.taigaui.designtokens.icons

import com.intellij.openapi.util.IconLoader
import com.intellij.util.IconUtil
import java.net.URI
import javax.swing.Icon

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
            val icon = IconLoader.findIcon(source.uri.toURL()) ?: return@runCatching null

            if (icon.iconWidth <= 0 || icon.iconHeight <= 0) {
                return@runCatching null
            }

            IconUtil.resizeSquared(icon, logicalSize)
        }.getOrNull()
}

private data class IconPreviewRenderKey(
    val uri: URI,
    val logicalSize: Int,
)

internal const val ICON_PREVIEW_LOGICAL_SIZE = 64
