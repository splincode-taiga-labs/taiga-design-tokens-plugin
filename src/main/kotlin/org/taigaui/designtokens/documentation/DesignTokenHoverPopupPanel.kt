package org.taigaui.designtokens.documentation

import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
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
    private val model: DesignTokenHoverPopupModel,
    private val popupWidth: Int,
    onNavigate: () -> Unit,
    onReportBug: () -> Unit,
    private val onPreferredSizeChanged: (Dimension) -> Unit,
) : JPanel(BorderLayout()) {
    init {
        background = PANEL_BACKGROUND
        border = JBUI.Borders.empty()

        val content =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = PANEL_BACKGROUND
                border = JBUI.Borders.empty(16)
                add(createHeader(model, onNavigate))
                add(Box.createVerticalStrut(JBUI.scale(16)))
                add(createValueTable(model.rows))

                if (model.chains.isNotEmpty()) {
                    add(Box.createVerticalStrut(JBUI.scale(14)))
                    add(JSeparator())
                    add(Box.createVerticalStrut(JBUI.scale(10)))
                    add(createReferenceAccordion(model.chains, ::setReferenceExpanded))
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
        updatePreferredSize(referenceExpanded = false, notify = false)
        minimumSize = Dimension(minOf(popupWidth, JBUI.scale(MIN_POPUP_WIDTH)), JBUI.scale(MIN_POPUP_HEIGHT))
    }

    private fun setReferenceExpanded(expanded: Boolean) {
        updatePreferredSize(referenceExpanded = expanded, notify = true)
    }

    private fun updatePreferredSize(
        referenceExpanded: Boolean,
        notify: Boolean,
    ) {
        preferredSize = Dimension(popupWidth, JBUI.scale(calculatePopupHeight(model, referenceExpanded)))
        revalidate()

        if (notify) {
            onPreferredSizeChanged(preferredSize)
        }
    }
}

private fun createHeader(
    model: DesignTokenHoverPopupModel,
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
                        font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(18f).toFloat())
                    },
                )
                add(Box.createVerticalStrut(JBUI.scale(3)))
                add(createSubtitle(model.description))
            },
            BorderLayout.CENTER,
        )
        add(
            JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
                isOpaque = false
                add(
                    ActionLink("Go to definition") { onNavigate() }.apply {
                        isEnabled = model.navigationTarget != null
                    },
                )
            },
            BorderLayout.EAST,
        )
    }

private fun createSubtitle(description: String?): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false

        if (description == null) {
            add(
                JBLabel("Design token  ·  @taiga-ui/design-tokens").apply {
                    foreground = UIUtil.getContextHelpForeground()
                },
            )
        } else {
            add(WrappedLabel(description, DESCRIPTION_WIDTH))
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(
                JBLabel("@taiga-ui/design-tokens").apply {
                    foreground = UIUtil.getContextHelpForeground()
                },
            )
        }
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
                anchor = GridBagConstraints.EAST
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
                anchor = GridBagConstraints.EAST
            },
        )
    }

private fun createValueCell(row: DesignTokenHoverValueRow): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        add(Box.createHorizontalGlue())

        row.color?.let { color ->
            add(ColorSwatch(color, SWATCH_SIZE))
            add(Box.createHorizontalStrut(JBUI.scale(8)))
        }

        add(TruncatedCodeValueLabel(row.resolvedValue, VALUE_TEXT_MAX_WIDTH))
    }

private fun createReferenceAccordion(
    chains: List<DesignTokenHoverReferenceChain>,
    onExpandedChanged: (Boolean) -> Unit,
): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        val body =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                alignmentX = JComponent.LEFT_ALIGNMENT
                isVisible = false

                chains.forEachIndexed { index, chain ->
                    add(createReferenceChain(chain))

                    if (index != chains.lastIndex) {
                        add(Box.createVerticalStrut(JBUI.scale(8)))
                        add(JSeparator())
                        add(Box.createVerticalStrut(JBUI.scale(8)))
                    }
                }
            }
        lateinit var toggle: ActionLink
        toggle =
            ActionLink(collapsedReferenceTitle(chains.size)) {
                body.isVisible = !body.isVisible
                toggle.text =
                    if (body.isVisible) {
                        expandedReferenceTitle(chains.size)
                    } else {
                        collapsedReferenceTitle(chains.size)
                    }
                revalidate()
                repaint()
                onExpandedChanged(body.isVisible)
            }.apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(15f).toFloat())
                alignmentX = JComponent.LEFT_ALIGNMENT
            }

        add(toggle)
        add(Box.createVerticalStrut(JBUI.scale(8)))
        add(body)
    }

private fun createReferenceChain(chain: DesignTokenHoverReferenceChain): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(
            JBLabel(chain.platform).apply {
                font = font.deriveFont(Font.BOLD)
                alignmentX = JComponent.LEFT_ALIGNMENT
            },
        )
        add(Box.createVerticalStrut(JBUI.scale(7)))
        chain.lines.forEachIndexed { index, line ->
            add(createReferenceLine(line))

            if (index != chain.lines.lastIndex) {
                add(Box.createVerticalStrut(JBUI.scale(4)))
            }
        }
    }

private fun createReferenceLine(line: DesignTokenHoverReferenceLine): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
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
            TruncatedCodeValueLabel(line.text, REFERENCE_TEXT_MAX_WIDTH).apply {
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

private class TruncatedCodeValueLabel(
    private val fullText: String,
    maxWidth: Int,
) : JBLabel() {
    init {
        font = CODE_FONT
        val scaledMaxWidth = JBUI.scale(maxWidth)
        text = truncateToWidth(fullText, getFontMetrics(font), scaledMaxWidth)
        toolTipText = fullText.takeIf { value -> value != text }
        maximumSize = Dimension(scaledMaxWidth, preferredSize.height)
    }
}

private class WrappedLabel(
    text: String,
    width: Int,
) : JBLabel(
        "<html><div style='width:${JBUI.scale(width)}px'>${text.escapeHtml()}</div></html>",
    ) {
    init {
        foreground = UIUtil.getContextHelpForeground()
        toolTipText = text
    }
}

private class TokenBadge : JComponent() {
    init {
        preferredSize = JBUI.size(TOKEN_BADGE_SIZE, TOKEN_BADGE_SIZE)
        minimumSize = preferredSize
        maximumSize = preferredSize
    }

    override fun paintComponent(graphics: Graphics) {
        val graphics2D = graphics.create() as Graphics2D

        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics2D.color = TOKEN_BADGE_BACKGROUND
        graphics2D.fillRoundRect(0, 0, width, height, JBUI.scale(7), JBUI.scale(7))
        graphics2D.color = Color.WHITE
        graphics2D.font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(16f).toFloat())

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

private fun calculatePopupHeight(
    model: DesignTokenHoverPopupModel,
    referenceExpanded: Boolean,
): Int {
    val rowsHeight = model.rows.size * VALUE_ROW_HEIGHT
    val descriptionHeight = if (model.description == null) 0 else DESCRIPTION_EXTRA_HEIGHT
    val referenceHeight =
        when {
            model.chains.isEmpty() -> 0
            !referenceExpanded -> REFERENCE_COLLAPSED_HEIGHT
            else ->
                REFERENCE_COLLAPSED_HEIGHT +
                    model.chains.sumOf { chain ->
                        CHAIN_HEADER_HEIGHT + chain.lines.size * CHAIN_LINE_HEIGHT
                    }
        }

    return (BASE_POPUP_HEIGHT + descriptionHeight + rowsHeight + referenceHeight)
        .coerceIn(MIN_POPUP_HEIGHT, MAX_POPUP_HEIGHT)
}

private fun truncateToWidth(
    value: String,
    metrics: java.awt.FontMetrics,
    maxWidth: Int,
): String {
    if (metrics.stringWidth(value) <= maxWidth) {
        return value
    }

    var low = 0
    var high = value.length

    while (low < high) {
        val middle = (low + high + 1) / 2
        val candidate = value.take(middle) + ELLIPSIS

        if (metrics.stringWidth(candidate) <= maxWidth) {
            low = middle
        } else {
            high = middle - 1
        }
    }

    return value.take(low) + ELLIPSIS
}

private fun String.escapeHtml(): String =
    buildString(length) {
        this@escapeHtml.forEach { character ->
            append(
                when (character) {
                    '&' -> "&amp;"
                    '<' -> "&lt;"
                    '>' -> "&gt;"
                    '"' -> "&quot;"
                    '\'' -> "&#39;"
                    else -> character
                },
            )
        }
    }

private fun collapsedReferenceTitle(count: Int): String = "▸ Reference chain ($count)"

private fun expandedReferenceTitle(count: Int): String = "▾ Reference chain ($count)"

private fun formatAlpha(alpha: Int): String =
    if (alpha == OPAQUE_ALPHA) {
        "1"
    } else {
        "%.2f".format(alpha.toDouble() / OPAQUE_ALPHA).trimEnd('0').trimEnd('.')
    }

private val PANEL_BACKGROUND = JBColor(Color(247, 248, 250), Color(35, 37, 42))
private val ROW_BACKGROUND = JBColor(Color(255, 255, 255), Color(43, 46, 52))
private val ROW_BORDER = JBColor(Color(220, 223, 229), Color(65, 69, 77))
private val LINK_COLOR = JBColor(Color(45, 108, 223), Color(88, 157, 246))
private val TOKEN_BADGE_BACKGROUND = JBColor(Color(93, 63, 211), Color(94, 64, 220))
private val CHECKER_LIGHT = Color(235, 235, 235)
private val CHECKER_DARK = Color(185, 185, 185)
private val SWATCH_BORDER = JBColor(Color(110, 110, 110), Color(170, 170, 170))
private val CODE_FONT = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(13))

private const val MIN_POPUP_WIDTH = 520
private const val MIN_POPUP_HEIGHT = 230
private const val MAX_POPUP_HEIGHT = 600
private const val BASE_POPUP_HEIGHT = 128
private const val DESCRIPTION_EXTRA_HEIGHT = 28
private const val VALUE_ROW_HEIGHT = 56
private const val REFERENCE_COLLAPSED_HEIGHT = 44
private const val CHAIN_HEADER_HEIGHT = 34
private const val CHAIN_LINE_HEIGHT = 27
private const val PLATFORM_COLUMN_WIDTH = 250
private const val DESCRIPTION_WIDTH = 430
private const val VALUE_TEXT_MAX_WIDTH = 280
private const val REFERENCE_TEXT_MAX_WIDTH = 500
private const val TOKEN_BADGE_SIZE = 30
private const val SWATCH_SIZE = 28
private const val SMALL_SWATCH_SIZE = 22
private const val OPAQUE_ALPHA = 255
private const val ELLIPSIS = "…"
