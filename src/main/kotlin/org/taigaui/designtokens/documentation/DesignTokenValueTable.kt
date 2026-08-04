package org.taigaui.designtokens.documentation

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.text.StyleConstants

internal fun createDesignTokenValueTable(
    rows: List<DesignTokenHoverValueRow>,
    valueWidth: Int,
): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(createTableHeader(valueWidth))
        add(Box.createVerticalStrut(JBUI.scale(6)))
        rows.forEachIndexed { index, row ->
            add(createValueRow(row, valueWidth))

            if (index != rows.lastIndex) {
                add(Box.createVerticalStrut(JBUI.scale(6)))
            }
        }
    }

private fun createTableHeader(valueWidth: Int): JComponent =
    JPanel(GridBagLayout()).apply {
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(0, ROW_HORIZONTAL_PADDING)

        add(
            JBLabel("Platform").apply {
                font = font.deriveFont(java.awt.Font.BOLD)
            },
            GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            },
        )
        add(
            JBLabel("Value", SwingConstants.RIGHT).apply {
                font = font.deriveFont(java.awt.Font.BOLD)
                preferredSize = Dimension(JBUI.scale(valueWidth), preferredSize.height)
            },
            GridBagConstraints().apply {
                gridx = 1
                weightx = 0.0
                fill = GridBagConstraints.NONE
                anchor = GridBagConstraints.EAST
            },
        )
    }

private fun createValueRow(
    row: DesignTokenHoverValueRow,
    valueWidth: Int,
): JComponent =
    RoundedRowPanel().apply {
        layout = GridBagLayout()
        alignmentX = JComponent.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(10, ROW_HORIZONTAL_PADDING)

        add(
            JBLabel(row.platform),
            GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
                insets = Insets(0, 0, 0, JBUI.scale(12))
            },
        )
        add(
            createValueCell(row, valueWidth),
            GridBagConstraints().apply {
                gridx = 1
                weightx = 0.0
                fill = GridBagConstraints.NONE
                anchor = GridBagConstraints.EAST
            },
        )

        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

private fun createValueCell(
    row: DesignTokenHoverValueRow,
    valueWidth: Int,
): JComponent {
    val color = row.color?.let { value -> ColorSwatch(value, DESIGN_TOKEN_POPUP_SWATCH_SIZE) }
    val copyButton = CopyValueButton(row.resolvedValue)
    val fixedWidth =
        DESIGN_TOKEN_POPUP_COPY_BUTTON_SIZE + VALUE_ITEM_GAP +
            if (color == null) {
                0
            } else {
                DESIGN_TOKEN_POPUP_SWATCH_SIZE + VALUE_ITEM_GAP
            }
    val maxTextWidth = (valueWidth - fixedWidth).coerceAtLeast(MIN_VALUE_TEXT_WIDTH)
    val textWidth =
        calculateNaturalTextWidth(row.resolvedValue)
            .coerceIn(MIN_SINGLE_LINE_TEXT_WIDTH, maxTextWidth)
    val text =
        WrappedTextPane(
            text = row.resolvedValue,
            width = textWidth,
            textFont = DESIGN_TOKEN_POPUP_CODE_FONT,
            textColor = UIUtil.getLabelForeground(),
            alignment = StyleConstants.ALIGN_RIGHT,
        )

    return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false

        color?.let { component ->
            component.alignmentY = JComponent.CENTER_ALIGNMENT
            add(component)
            add(Box.createHorizontalStrut(JBUI.scale(VALUE_ITEM_GAP)))
        }

        text.alignmentY = JComponent.CENTER_ALIGNMENT
        copyButton.alignmentY = JComponent.CENTER_ALIGNMENT
        add(text)
        add(Box.createHorizontalStrut(JBUI.scale(VALUE_ITEM_GAP)))
        add(copyButton)

        val contentWidth =
            text.preferredSize.width + copyButton.preferredSize.width + JBUI.scale(VALUE_ITEM_GAP) +
                if (color == null) {
                    0
                } else {
                    color.preferredSize.width + JBUI.scale(VALUE_ITEM_GAP)
                }
        val contentHeight =
            maxOf(
                text.preferredSize.height,
                copyButton.preferredSize.height,
                color?.preferredSize?.height ?: 0,
            )

        preferredSize = Dimension(contentWidth, contentHeight)
        minimumSize = preferredSize
        maximumSize = preferredSize
    }
}

private const val ROW_HORIZONTAL_PADDING = 12
private const val MIN_VALUE_TEXT_WIDTH = 150
private const val MIN_SINGLE_LINE_TEXT_WIDTH = 24
private const val VALUE_ITEM_GAP = 8
