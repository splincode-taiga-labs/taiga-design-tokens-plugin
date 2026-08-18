package org.taigaui.designtokens.icons

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Image
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class IconCompletionPreviewPanel : JPanel(BorderLayout()) {
    private val content =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UIUtil.getPanelBackground()
            border = JBUI.Borders.empty(PREVIEW_PADDING)
        }

    init {
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.customLine(JBColor.border(), 1)
        add(content, BorderLayout.CENTER)
        preferredSize = Dimension(JBUI.scale(PREVIEW_WIDTH), JBUI.scale(PREVIEW_HEIGHT))
    }

    fun showLoading(iconName: String) {
        content.removeAll()
        content.add(createTitle(iconName))
        content.add(Box.createVerticalGlue())
        content.add(
            AsyncProcessIcon("Loading Taiga UI icon preview").apply {
                alignmentX = JComponent.CENTER_ALIGNMENT
            },
        )
        content.add(Box.createVerticalGlue())
        refresh()
    }

    fun showIcon(
        iconName: String,
        image: Image,
    ) {
        content.removeAll()
        content.add(createTitle(iconName))
        content.add(Box.createVerticalStrut(JBUI.scale(12)))
        content.add(Box.createVerticalGlue())
        content.add(
            JBLabel(ImageIcon(image.fitToPreview())).apply {
                alignmentX = JComponent.CENTER_ALIGNMENT
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
            },
        )
        content.add(Box.createVerticalGlue())
        refresh()
    }

    private fun refresh() {
        revalidate()
        repaint()
    }
}

private fun createTitle(iconName: String): JComponent =
    JBLabel(iconName).apply {
        font = font.deriveFont(Font.BOLD)
        alignmentX = JComponent.CENTER_ALIGNMENT
        horizontalAlignment = SwingConstants.CENTER
    }

private fun Image.fitToPreview(): Image {
    val width = getWidth(null).coerceAtLeast(1)
    val height = getHeight(null).coerceAtLeast(1)
    val maxSize = JBUI.scale(ICON_SIZE)
    val scale = minOf(maxSize.toDouble() / width, maxSize.toDouble() / height)
    val targetWidth = (width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (height * scale).toInt().coerceAtLeast(1)

    return getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH)
}

private const val PREVIEW_WIDTH = 220
private const val PREVIEW_HEIGHT = 180
private const val PREVIEW_PADDING = 12
private const val ICON_SIZE = 112
