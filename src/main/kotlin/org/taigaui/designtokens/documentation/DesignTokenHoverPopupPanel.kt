package org.taigaui.designtokens.documentation

import com.intellij.icons.AllIcons
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator

internal class DesignTokenHoverPopupPanel(
    model: DesignTokenHoverPopupModel,
    onCopy: () -> Unit,
    onNavigate: () -> Unit,
    onReportBug: () -> Unit,
) : JPanel(BorderLayout()) {
    init {
        background = PANEL_BACKGROUND
        border = JBUI.Borders.empty()

        val content =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = PANEL_BACKGROUND
                border = JBUI.Borders.empty(18)
                add(createHeader(model, onCopy, onNavigate))
                add(Box.createVerticalStrut(JBUI.scale(18)))
                add(createValueTable(model.rows))

                if (model.chains.isNotEmpty()) {
                    add(Box.createVerticalStrut(JBUI.scale(14)))
                    add(JSeparator())
                    add(Box.createVerticalStrut(JBUI.scale(14)))
                    add(createReferenceSection(model.chains))
                }

                add(Box.createVerticalStrut(JBUI.scale(12)))
                add(createFooter(onReportBug))
            }
        val scrollPane =
            JBScrollPane(
                content,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
            ).apply {
                border = JBUI.Borders.empty()
                viewport.background = PANEL_BACKGROUND
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBar.unitIncrement = JBUI.scale(16)
            }

        add(scrollPane, BorderLayout.CENTER)
        preferredSize = JBUI.size(POPUP_WIDTH, calculatePopupHeight(model))
        minimumSize = JBUI.size(POPUP_WIDTH, MIN_POPUP_HEIGHT)
    }
}

private fun createHeader(
    model: DesignTokenHoverPopupModel,
    onCopy: () -> Unit,
    onNavigate: () -> Unit,
): JComponent =
    JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        add(TokenBadge(), BorderLayout.WEST)
        add(
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(
                    JBLabel(model.tokenName).apply {
                        font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(19f))
                    },
                )
                add(Box.createVerticalStrut(JBUI.scale(3)))
                add(
                    JBLabel("Design token  ·  @taiga-ui/design-tokens").apply {
                        foreground = UIUtil.getContextHelpForeground()
                    },
                )
            },
            BorderLayout.CENTER,
        )
        add(
            JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(10), 0)).apply {
                isOpaque = false
                add(
                    ActionLink("Copy value") { onCopy() }.apply {
                        icon = AllIcons.Actions.Copy
                    },
                )
                add(
                    ActionLink("Go to definition") { onNavigate() }.apply {
                        isEnabled = model.navigationTarget != null
                    },
                )
            },
            BorderLayout.EAST,
        )
    }

private fun createValueTable(rows: List<DesignTokenHoverValueRow>): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(createTableHeader())
        add(Box.createVerticalStrut(JBUI.scale(6)))
        rows.forEachIndexed { index, row ->
            add(createValueRow(row))

            if (index != rows.lastIndex) {
                add(Box.createVerticalStrut(JBUI.scale(6)))
            }
        }
    }

private fun createTableHeader(): JComponent =
    JPanel(GridBagLayout()).apply {
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(0, 12)

        add(
            JBLabel("Platform").apply { font = font.deriveFont(Font.BOLD) },
            GridBagConstraints().apply {
                gridx = 0
                weightx = 0.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
                preferredWidth(PLATFORM_COLUMN_WIDTH)
            },
        )
        add(
            JBLabel("Value").apply { font = font.deriveFont(Font.BOLD) },
            GridBagConstraints().apply {
                gridx = 1
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            },
        )
    }

private fun createValueRow(row: DesignTokenHoverValueRow): JComponent =
    RoundedRowPanel().apply {
        layout = GridBagLayout()
        alignmentX = JComponent.LEFT_ALIGNMENT
        border = JBUI.Borders.empty(10, 12)

        add(
            JBLabel(row.platform),
            GridBagConstraints().apply {
                gridx = 0
                weightx = 0.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
                preferredWidth(PLATFORM_COLUMN_WIDTH)
            },
        )
        add(
            createValueCell(row),
            GridBagConstraints().apply {
                gridx = 1
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.WEST
            },
        )
    }

private fun createValueCell(row: DesignTokenHoverValueRow): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false

        if (row.showsResolution) {
            add(CodeChip(row.rawValues.joinToString(separator = " | ")))
            add(Box.createHorizontalStrut(JBUI.scale(10)))
            add(JBLabel("→"))
            add(Box.createHorizontalStrut(JBUI.scale(10)))
        }

        row.color?.let { color ->
            add(ColorSwatch(color, SWATCH_SIZE))
            add(Box.createHorizontalStrut(JBUI.scale(8)))
        }

        add(CodeValueLabel(row.resolvedValue))
        add(Box.createHorizontalGlue())
    }

private fun createReferenceSection(chains: List<DesignTokenHoverReferenceChain>): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(
            JBLabel("Reference chain").apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(15f))
            },
        )
        add(Box.createVerticalStrut(JBUI.scale(10)))

        val chainRows = chains.chunked(MAX_CHAIN_COLUMNS)

        chainRows.forEachIndexed { index, row ->
            add(
                JPanel(GridBagLayout()).apply {
                    isOpaque = false
                    alignmentX = JComponent.LEFT_ALIGNMENT

                    row.forEachIndexed { column, chain ->
                        add(
                            createReferenceChain(chain),
                            GridBagConstraints().apply {
                                gridx = column
                                weightx = 1.0
                                fill = GridBagConstraints.HORIZONTAL
                                anchor = GridBagConstraints.NORTHWEST
                                insets = JBUI.insetsRight(if (column == row.lastIndex) 0 else 16)
                            },
                        )
                    }
                },
            )

            if (index != chainRows.lastIndex) {
                add(Box.createVerticalStrut(JBUI.scale(16)))
            }
        }
    }

private fun createReferenceChain(chain: DesignTokenHoverReferenceChain): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        border = JBUI.Borders.emptyRight(8)
        add(
            JBLabel(chain.platform).apply {
                font = font.deriveFont(Font.BOLD)
            },
        )
        add(Box.createVerticalStrut(JBUI.scale(7)))
        chain.lines.forEach { line ->
            add(createReferenceLine(line))
            add(Box.createVerticalStrut(JBUI.scale(4)))
        }
    }

private fun createReferenceLine(line: DesignTokenHoverReferenceLine): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        border = JBUI.Borders.emptyLeft(line.depth * 14)

        if (line.root) {
            add(ReferenceDot())
        } else {
            add(JBLabel("↓"))
        }

        add(Box.createHorizontalStrut(JBUI.scale(8)))
        line.color?.let { color ->
            add(ColorSwatch(color, SMALL_SWATCH_SIZE))
            add(Box.createHorizontalStrut(JBUI.scale(7)))
        }
        add(
            CodeValueLabel(line.text).apply {
                if (line.root) {
                    foreground = LINK_COLOR
                }
            },
        )
        add(Box.createHorizontalGlue())
    }

private fun createFooter(onReportBug: () -> Unit): JComponent =
    JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(
            ActionLink("Report a bug") { onReportBug() }.apply {
                setExternalLinkIcon()
            },
        )
    }

private class RoundedRowPanel : JPanel() {
    init {
        isOpaque = false
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.color = ROW_BACKGROUND
        graphics2D.fill(
            RoundRectangle2D.Float(
                0f,
                0f,
                (width - 1).toFloat(),
                (height - 1).toFloat(),
                JBUI.scale(12).toFloat(),
                JBUI.scale(12).toFloat(),
            ),
        )
        graphics2D.color = ROW_BORDER
        graphics2D.stroke = BasicStroke(JBUI.scale(1f))
        graphics2D.draw(
            RoundRectangle2D.Float(
                0.5f,
                0.5f,
                (width - 2).toFloat(),
                (height - 2).toFloat(),
                JBUI.scale(12).toFloat(),
                JBUI.scale(12).toFloat(),
            ),
        )
        graphics2D.dispose()
        super.paintComponent(graphics)
    }
}

private class CodeChip(
    text: String,
) : JBLabel(text) {
    init {
        font = CODE_FONT
        isOpaque = true
        background = CHIP_BACKGROUND
        border = JBUI.Borders.empty(4, 7)
    }
}

private class CodeValueLabel(
    text: String,
) : JBLabel(text) {
    init {
        font = CODE_FONT
    }
}

private class TokenBadge : JComponent() {
    init {
        preferredSize = JBUI.size(38, 38)
        minimumSize = preferredSize
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.color = TOKEN_BADGE_BACKGROUND
        graphics2D.fillRoundRect(0, 0, width, height, JBUI.scale(9), JBUI.scale(9))
        graphics2D.color = Color.WHITE
        graphics2D.font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(20f))

        val metrics = graphics2D.fontMetrics
        val text = "T"
        val x = (width - metrics.stringWidth(text)) / 2
        val y = (height - metrics.height) / 2 + metrics.ascent

        graphics2D.drawString(text, x, y)
        graphics2D.dispose()
    }
}

private class ReferenceDot : JComponent() {
    init {
        preferredSize = JBUI.size(10, 16)
        minimumSize = preferredSize
        maximumSize = preferredSize
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D
        val size = JBUI.scale(8)
        val x = (width - size) / 2
        val y = (height - size) / 2

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.color = LINK_COLOR
        graphics2D.fillOval(x, y, size, size)
        graphics2D.dispose()
    }
}

private class ColorSwatch(
    private val swatchColor: Color,
    size: Int,
) : JComponent() {
    init {
        preferredSize = JBUI.size(size, size)
        minimumSize = preferredSize
        maximumSize = preferredSize

        val alpha = formatAlpha(swatchColor.alpha)

        toolTipText = "rgba(${swatchColor.red}, ${swatchColor.green}, ${swatchColor.blue}, $alpha)"
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D
        val inset = JBUI.scale(1)
        val diameter = minOf(width, height) - inset * 2
        val circle = Ellipse2D.Float(inset.toFloat(), inset.toFloat(), diameter.toFloat(), diameter.toFloat())

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.clip = circle
        paintCheckerboard(graphics2D, diameter, inset)
        graphics2D.color = swatchColor
        graphics2D.fill(circle)
        graphics2D.clip = null
        graphics2D.color = SWATCH_BORDER
        graphics2D.stroke = BasicStroke(JBUI.scale(1f))
        graphics2D.draw(circle)
        graphics2D.dispose()
    }

    private fun paintCheckerboard(
        graphics: Graphics2D,
        diameter: Int,
        inset: Int,
    ) {
        val tile = maxOf(JBUI.scale(4), 1)
        var row = 0
        var y = inset

        while (y < inset + diameter) {
            var column = 0
            var x = inset

            while (x < inset + diameter) {
                graphics.color = if ((row + column) % 2 == 0) CHECKER_LIGHT else CHECKER_DARK
                graphics.fillRect(x, y, tile, tile)
                column++
                x += tile
            }

            row++
            y += tile
        }
    }
}

private fun GridBagConstraints.preferredWidth(width: Int) {
    ipadx = JBUI.scale(width)
}

private fun calculatePopupHeight(model: DesignTokenHoverPopupModel): Int {
    val rowsHeight = model.rows.size * VALUE_ROW_HEIGHT
    val chainRows = (model.chains.size + MAX_CHAIN_COLUMNS - 1) / MAX_CHAIN_COLUMNS
    val chainsHeight = if (model.chains.isEmpty()) 0 else REFERENCE_HEADER_HEIGHT + chainRows * CHAIN_ROW_HEIGHT

    return (BASE_POPUP_HEIGHT + rowsHeight + chainsHeight)
        .coerceIn(MIN_POPUP_HEIGHT, MAX_POPUP_HEIGHT)
}

private fun formatAlpha(alpha: Int): String =
    if (alpha == OPAQUE_ALPHA) {
        "1"
    } else {
        "%.2f".format(alpha.toDouble() / OPAQUE_ALPHA).trimEnd('0').trimEnd('.')
    }

private val PANEL_BACKGROUND = JBColor(Color(247, 248, 250), Color(35, 37, 42))
private val ROW_BACKGROUND = JBColor(Color(255, 255, 255), Color(43, 46, 52))
private val ROW_BORDER = JBColor(Color(220, 223, 229), Color(65, 69, 77))
private val CHIP_BACKGROUND = JBColor(Color(237, 239, 243), Color(56, 59, 66))
private val LINK_COLOR = JBColor(Color(45, 108, 223), Color(88, 157, 246))
private val TOKEN_BADGE_BACKGROUND = JBColor(Color(93, 63, 211), Color(94, 64, 220))
private val CHECKER_LIGHT = Color(235, 235, 235)
private val CHECKER_DARK = Color(185, 185, 185)
private val SWATCH_BORDER = JBColor(Color(110, 110, 110), Color(170, 170, 170))
private val CODE_FONT = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(13))

private const val POPUP_WIDTH = 780
private const val MIN_POPUP_HEIGHT = 300
private const val MAX_POPUP_HEIGHT = 620
private const val BASE_POPUP_HEIGHT = 120
private const val VALUE_ROW_HEIGHT = 58
private const val REFERENCE_HEADER_HEIGHT = 56
private const val CHAIN_ROW_HEIGHT = 150
private const val PLATFORM_COLUMN_WIDTH = 230
private const val MAX_CHAIN_COLUMNS = 3
private const val SWATCH_SIZE = 28
private const val SMALL_SWATCH_SIZE = 22
private const val OPAQUE_ALPHA = 255
