package org.taigaui.designtokens.icons

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagLayout
import java.awt.Image
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class IconCompletionPreviewPanel : JPanel(BorderLayout()) {
    private val content =
        JPanel(GridBagLayout()).apply {
            background = PREVIEW_BACKGROUND
            border = JBUI.Borders.empty(PREVIEW_PADDING)
        }

    init {
        background = PREVIEW_BACKGROUND
        border = JBUI.Borders.customLine(JBColor.border(), 1)
        add(content, BorderLayout.CENTER)

        val size = JBUI.scale(ICON_PREVIEW_LOGICAL_SIZE + PREVIEW_PADDING * 2 + BORDER_WIDTH * 2)

        preferredSize = Dimension(size, size)
        minimumSize = Dimension(size, size)
    }

    fun showLoading() {
        content.removeAll()
        content.add(AsyncProcessIcon("Loading Taiga UI icon preview"))
        refresh()
    }

    fun showIcon(image: Image) {
        content.removeAll()
        content.add(
            JBLabel(JBImageIcon(image)).apply {
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

private val PREVIEW_BACKGROUND = Color.WHITE
private const val PREVIEW_PADDING = 8
private const val BORDER_WIDTH = 1
