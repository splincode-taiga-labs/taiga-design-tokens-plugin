package org.taigaui.designtokens.icons

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class IconCompletionPreviewPanel : JPanel(BorderLayout()) {
    private val content =
        JPanel(GridBagLayout()).apply {
            background = PREVIEW_BACKGROUND
            border = JBUI.Borders.empty(PREVIEW_PADDING)
        }

    init {
        isFocusable = false
        AccessibleContextUtil.setName(this, "Taiga UI icon completion preview")
        AccessibleContextUtil.setDescription(
            this,
            "Shows a visual preview for the selected Taiga UI icon completion.",
        )
        background = PREVIEW_BACKGROUND
        border = JBUI.Borders.customLine(JBColor.border(), 1)
        add(content, BorderLayout.CENTER)

        val size = JBUI.scale(ICON_PREVIEW_LOGICAL_SIZE + PREVIEW_PADDING * 2 + BORDER_WIDTH * 2)

        preferredSize = Dimension(size, size)
        minimumSize = Dimension(size, size)
    }

    fun showLoading(iconName: String) {
        AccessibleContextUtil.setDescription(this, "Loading icon preview for $iconName.")
        content.removeAll()
        content.add(AsyncProcessIcon("Loading Taiga UI icon preview"))
        refresh()
    }

    fun showIcon(
        iconName: String,
        icon: Icon,
    ) {
        AccessibleContextUtil.setDescription(this, "Visual preview of $iconName.")
        content.removeAll()
        content.add(
            JBLabel(icon).apply {
                background = PREVIEW_BACKGROUND
                isOpaque = true
                horizontalAlignment = SwingConstants.CENTER
                verticalAlignment = SwingConstants.CENTER
                AccessibleContextUtil.setName(this, "$iconName icon preview")
                AccessibleContextUtil.setDescription(this, "Visual preview of $iconName.")
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
