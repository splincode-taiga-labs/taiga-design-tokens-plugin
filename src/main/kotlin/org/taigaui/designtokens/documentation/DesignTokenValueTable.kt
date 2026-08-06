package org.taigaui.designtokens.documentation

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.text.StyleConstants

internal fun createDesignTokenValueSections(
    sections: List<DesignTokenHoverPackageSection>,
    valueWidth: Int,
    onNavigate: (DesignTokenNavigationTarget) -> Unit,
): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        sections.forEachIndexed { index, section ->
            add(createPackageValueSection(section, valueWidth, onNavigate))

            if (index != sections.lastIndex) {
                add(Box.createVerticalStrut(JBUI.scale(12)))
                add(JSeparator())
                add(Box.createVerticalStrut(JBUI.scale(12)))
            }
        }
    }

private fun createPackageValueSection(
    section: DesignTokenHoverPackageSection,
    valueWidth: Int,
    onNavigate: (DesignTokenNavigationTarget) -> Unit,
): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(createTableHeader(section.packageName, valueWidth))
        add(Box.createVerticalStrut(JBUI.scale(6)))

        section.rows.forEachIndexed { index, row ->
            add(createValueRow(row, valueWidth, onNavigate))

            if (index != section.rows.lastIndex) {
                add(Box.createVerticalStrut(JBUI.scale(6)))
            }
        }
    }

private fun createTableHeader(
    packageName: String,
    valueWidth: Int,
): JComponent =
    JPanel(GridBagLayout()).apply {
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(0, ROW_HORIZONTAL_PADDING)

        add(
            JBLabel(packageName).apply {
                font = font.deriveFont(Font.BOLD)
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
                font = font.deriveFont(Font.BOLD)
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
    onNavigate: (DesignTokenNavigationTarget) -> Unit,
): JComponent =
    RoundedRowPanel().apply {
        layout = GridBagLayout()
        alignmentX = JComponent.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(10, ROW_HORIZONTAL_PADDING)
        row.overrideMessage?.let { message ->
            toolTipText = "This declaration is not applied. $message."
        }

        add(
            createContextCell(row),
            GridBagConstraints().apply {
                gridx = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
                insets = Insets(0, 0, 0, JBUI.scale(12))
            },
        )
        add(
            createValueCell(row, valueWidth, onNavigate),
            GridBagConstraints().apply {
                gridx = 1
                weightx = 0.0
                fill = GridBagConstraints.NONE
                anchor = GridBagConstraints.EAST
            },
        )

        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

private fun createContextCell(row: DesignTokenHoverValueRow): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        add(
            JBLabel(row.platform).apply {
                foreground = row.textColor()
                alignmentX = JComponent.LEFT_ALIGNMENT
            },
        )

        row.overrideMessage?.let { message ->
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(
                JBLabel("Not applied · $message").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                    alignmentX = JComponent.LEFT_ALIGNMENT
                },
            )
        }
    }

private fun createValueCell(
    row: DesignTokenHoverValueRow,
    valueWidth: Int,
    onNavigate: (DesignTokenNavigationTarget) -> Unit,
): JComponent {
    val color = row.color?.let { value -> ColorSwatch(value, DESIGN_TOKEN_POPUP_SWATCH_SIZE) }
    val copyButton = CopyValueButton(row.resolvedValue)
    val navigationButton =
        row.navigationTarget?.let { target ->
            NavigateToDefinitionButton { onNavigate(target) }
        }
    val fixedComponents = listOfNotNull(color, copyButton, navigationButton)
    val fixedWidth =
        fixedComponents.sumOf { component -> JBUI.unscale(component.preferredSize.width) } +
            VALUE_ITEM_GAP * (fixedComponents.size - 1).coerceAtLeast(0)
    val maxTextWidth = (valueWidth - fixedWidth).coerceAtLeast(MIN_VALUE_TEXT_WIDTH)
    val textWidth =
        calculateNaturalTextWidth(row.resolvedValue)
            .coerceIn(MIN_SINGLE_LINE_TEXT_WIDTH, maxTextWidth)
    val text =
        WrappedTextPane(
            text = row.resolvedValue,
            width = textWidth,
            textFont = DESIGN_TOKEN_POPUP_CODE_FONT,
            textColor = row.textColor(),
            alignment = StyleConstants.ALIGN_RIGHT,
        )
    val components = listOfNotNull(color, text, copyButton, navigationButton)

    return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false

        components.forEachIndexed { index, component ->
            component.alignmentY = JComponent.CENTER_ALIGNMENT
            add(component)

            if (index != components.lastIndex) {
                add(Box.createHorizontalStrut(JBUI.scale(VALUE_ITEM_GAP)))
            }
        }

        val contentWidth =
            components.sumOf { component -> component.preferredSize.width } +
                JBUI.scale(VALUE_ITEM_GAP) * (components.size - 1).coerceAtLeast(0)
        val contentHeight = components.maxOf { component -> component.preferredSize.height }

        preferredSize = Dimension(contentWidth, contentHeight)
        minimumSize = preferredSize
        maximumSize = preferredSize
    }
}

private fun DesignTokenHoverValueRow.textColor() =
    if (overrideMessage == null) {
        UIUtil.getLabelForeground()
    } else {
        UIUtil.getContextHelpForeground()
    }

private const val ROW_HORIZONTAL_PADDING = 12
private const val MIN_VALUE_TEXT_WIDTH = 120
private const val MIN_SINGLE_LINE_TEXT_WIDTH = 24
private const val VALUE_ITEM_GAP = 8
