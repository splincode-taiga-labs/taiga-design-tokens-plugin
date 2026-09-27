package org.taigaui.designtokens.icons

import com.intellij.openapi.util.IconLoader
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
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
            val icon = IconLoader.findIcon(source.uri.toURL()) ?: return@runCatching null
            val maxSourceSize = maxOf(icon.iconWidth, icon.iconHeight)

            if (maxSourceSize <= 0) {
                return@runCatching null
            }

            ScaledPreviewIcon(
                icon = icon,
                scale = logicalSize.toDouble() / maxSourceSize,
            )
        }.getOrNull()
}

private class ScaledPreviewIcon(
    private val icon: Icon,
    private val scale: Double,
) : Icon {
    override fun getIconWidth(): Int = maxOf(1, (icon.iconWidth * scale).roundToInt())

    override fun getIconHeight(): Int = maxOf(1, (icon.iconHeight * scale).roundToInt())

    override fun paintIcon(
        component: Component?,
        graphics: Graphics,
        x: Int,
        y: Int,
    ) {
        val scaledGraphics = graphics.create() as Graphics2D

        try {
            scaledGraphics.translate(x, y)
            scaledGraphics.scale(scale, scale)
            icon.paintIcon(component, scaledGraphics, 0, 0)
        } finally {
            scaledGraphics.dispose()
        }
    }
}

private data class IconPreviewRenderKey(
    val uri: URI,
    val logicalSize: Int,
)

internal const val ICON_PREVIEW_LOGICAL_SIZE = 64
