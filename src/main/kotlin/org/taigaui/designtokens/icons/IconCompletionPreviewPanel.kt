package org.taigaui.designtokens.icons

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
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
            background = PREVIEW_BACKGROUND
            border = JBUI.Borders.empty(PREVIEW_PADDING)
        }

    init {
        background = PREVIEW_BACKGROUND
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
            JBLabel(ImageIcon(image)).apply {
                alignmentX = JComponent.CENTER_ALIGNMENT
                background = PREVIEW_BACKGROUND
                isOpaque = true
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
        foreground = Color.BLACK
        alignmentX = JComponent.CENTER_ALIGNMENT
        horizontalAlignment = SwingConstants.CENTER
    }

private val PREVIEW_BACKGROUND = Color.WHITE
private const val PREVIEW_WIDTH = 260
private const val PREVIEW_HEIGHT = 220
private const val PREVIEW_PADDING = 12
