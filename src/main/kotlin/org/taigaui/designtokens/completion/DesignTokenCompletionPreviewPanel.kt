package org.taigaui.designtokens.completion

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.taigaui.designtokens.documentation.DesignTokenHoverPopupModel
import org.taigaui.designtokens.documentation.DesignTokenHoverValueRow
import org.taigaui.designtokens.documentation.designTokenValuePresentation
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

internal class DesignTokenCompletionPreviewPanel : JPanel(BorderLayout()) {
    private val content =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = UIUtil.getPanelBackground()
            border = JBUI.Borders.empty(PREVIEW_PADDING)
        }

    init {
        isFocusable = false
        AccessibleContextUtil.setName(this, "Taiga UI design token completion preview")
        AccessibleContextUtil.setDescription(
            this,
            "Shows resolved values for the selected design token completion.",
        )
        background = UIUtil.getPanelBackground()
        border = JBUI.Borders.customLine(JBColor.border(), 1)
        add(
            JBScrollPane(
                content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
            ).apply {
                border = JBUI.Borders.empty()
                viewport.background = UIUtil.getPanelBackground()
            },
            BorderLayout.CENTER,
        )
        preferredSize = Dimension(JBUI.scale(PREVIEW_WIDTH), JBUI.scale(MIN_PREVIEW_HEIGHT))
    }

    fun showLoading(tokenName: String) {
        AccessibleContextUtil.setDescription(this, "Loading resolved values for $tokenName.")
        content.removeAll()
        content.add(createTitle(tokenName))
        content.add(Box.createVerticalStrut(JBUI.scale(12)))
        content.add(
            JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
                add(AsyncProcessIcon("Loading design token completion preview"))
                add(Box.createHorizontalStrut(JBUI.scale(8)))
                add(
                    JBLabel("Loading values…").apply {
                        foreground = UIUtil.getContextHelpForeground()
                    },
                )
            },
        )
        updateSize()
    }

    fun showModel(model: DesignTokenHoverPopupModel) {
        AccessibleContextUtil.setDescription(this, "Resolved values for ${model.tokenName}.")
        content.removeAll()
        content.add(createTitle(model.tokenName))

        model.description?.let { description ->
            content.add(Box.createVerticalStrut(JBUI.scale(4)))
            content.add(
                JBLabel(description).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    alignmentX = JComponent.LEFT_ALIGNMENT
                },
            )
        }

        model.sections.forEach { section ->
            val activeRows = section.rows.filter { row -> row.overrideMessage == null }

            if (activeRows.isNotEmpty()) {
                content.add(Box.createVerticalStrut(JBUI.scale(12)))
                content.add(
                    JBLabel(section.packageName).apply {
                        font = font.deriveFont(Font.BOLD)
                        alignmentX = JComponent.LEFT_ALIGNMENT
                    },
                )
                content.add(Box.createVerticalStrut(JBUI.scale(6)))
                activeRows.forEach { row -> content.add(createValueRow(row)) }
            }
        }

        updateSize()
    }

    private fun updateSize() {
        val height =
            (content.preferredSize.height + JBUI.scale(PREVIEW_PADDING * 2))
                .coerceIn(JBUI.scale(MIN_PREVIEW_HEIGHT), JBUI.scale(MAX_PREVIEW_HEIGHT))

        preferredSize = Dimension(JBUI.scale(PREVIEW_WIDTH), height)
        revalidate()
        repaint()
    }
}

private fun createTitle(tokenName: String): JComponent =
    JBLabel(tokenName).apply {
        font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(16f).toFloat())
        alignmentX = JComponent.LEFT_ALIGNMENT
    }

private fun createValueRow(row: DesignTokenHoverValueRow): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(5, 0)

        add(
            JBLabel(row.platform).apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
            },
        )
        add(Box.createVerticalStrut(JBUI.scale(4)))
        add(createResolvedValue(row))
    }

private fun createResolvedValue(row: DesignTokenHoverValueRow): JComponent {
    val value = designTokenValuePresentation(row.resolvedValue)

    return JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        row.color?.let { color ->
            add(
                JBLabel(ColorPreviewIcon(color)).apply {
                    verticalAlignment = JBLabel.TOP
                    AccessibleContextUtil.setName(this, "Color preview")
                    AccessibleContextUtil.setDescription(this, "Color preview for $value.")
                },
                BorderLayout.WEST,
            )
        }
        add(createWrappingValue(value), BorderLayout.CENTER)
    }
}

private fun createWrappingValue(value: String): JTextArea =
    JTextArea(value).apply {
        isEditable = false
        isFocusable = false
        isOpaque = false
        lineWrap = true
        wrapStyleWord = true
        font = UIUtil.getLabelFont()
        foreground = UIUtil.getLabelForeground()
        border = JBUI.Borders.empty()
        columns = VALUE_COLUMNS
    }

private class ColorPreviewIcon(
    private val color: Color,
) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(COLOR_PREVIEW_SIZE)

    override fun getIconHeight(): Int = JBUI.scale(COLOR_PREVIEW_SIZE)

    override fun paintIcon(
        component: Component?,
        graphics: Graphics,
        x: Int,
        y: Int,
    ) {
        val graphics2D = graphics.create() as Graphics2D
        val size = iconWidth
        val tile = (size / 4).coerceAtLeast(1)

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.clip(
            Ellipse2D.Double(
                x.toDouble(),
                y.toDouble(),
                size.toDouble(),
                size.toDouble(),
            ),
        )

        for (row in 0 until 4) {
            for (column in 0 until 4) {
                graphics2D.color = if ((row + column) % 2 == 0) Color.WHITE else Color(0xD0, 0xD0, 0xD0)
                graphics2D.fillRect(x + column * tile, y + row * tile, tile + 1, tile + 1)
            }
        }

        graphics2D.color = color
        graphics2D.fillOval(x, y, size, size)
        graphics2D.clip = null
        graphics2D.color = JBColor.border()
        graphics2D.drawOval(x, y, size - 1, size - 1)
        graphics2D.dispose()
    }
}

private const val PREVIEW_WIDTH = 430
private const val PREVIEW_PADDING = 12
private const val MIN_PREVIEW_HEIGHT = 72
internal const val MAX_PREVIEW_HEIGHT = 420
private const val COLOR_PREVIEW_SIZE = 18
private const val VALUE_COLUMNS = 34
