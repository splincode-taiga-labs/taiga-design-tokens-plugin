package org.taigaui.designtokens.icons

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.Image
import javax.swing.Box
import javax.swing.BoxLayout
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
    }

    fun showLoading(iconName: String) {
        content.removeAll()
        content.add(createTitle(iconName))
        content.add(Box.createVerticalStrut(JBUI.scale(PREVIEW_GAP)))
        content.add(
            AsyncProcessIcon("Loading Taiga UI icon preview").apply {
                alignmentX = JComponent.CENTER_ALIGNMENT
            },
        )
        refresh()
    }

    fun showIcon(
        iconName: String,
        image: Image,
    ) {
        content.removeAll()
        content.add(createTitle(iconName))
        content.add(Box.createVerticalStrut(JBUI.scale(PREVIEW_GAP)))
        content.add(
            JBLabel(JBImageIcon(image)).apply {
                alignmentX = JComponent.CENTER_ALIGNMENT
                background = PREVIEW_BACKGROUND
                isOpaque = true
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
            },
        )
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
private const val PREVIEW_PADDING = 8
private const val PREVIEW_GAP = 8
