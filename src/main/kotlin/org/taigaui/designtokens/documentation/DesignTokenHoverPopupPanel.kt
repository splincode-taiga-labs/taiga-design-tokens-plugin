package org.taigaui.designtokens.documentation

import com.intellij.icons.AllIcons
import com.intellij.openapi.ide.CopyPasteManager
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
import java.awt.Insets
import java.awt.RenderingHints
import java.awt.datatransfer.StringSelection
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JTextPane
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

internal class DesignTokenHoverPopupPanel(
    private val model: DesignTokenHoverPopupModel,
    private val popupWidth: Int,
    onNavigate: () -> Unit,
    onReportBug: () -> Unit,
    private val onPreferredSizeChanged: (Dimension) -> Unit,
) : JPanel(BorderLayout()) {
    private val contentPanel =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = PANEL_BACKGROUND
            border = JBUI.Borders.empty(CONTENT_PADDING)
        }

    init {
        background = PANEL_BACKGROUND
        border = JBUI.Borders.empty()

        val valueWidth = calculateValueWidth(popupWidth)
        val descriptionWidth = calculateDescriptionWidth(popupWidth)
        val referenceWidth = calculateReferenceWidth(popupWidth)

        contentPanel.add(createHeader(model, descriptionWidth, onNavigate))
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(14)))
        contentPanel.add(createValueTable(model.rows, valueWidth))

        if (model.chains.isNotEmpty()) {
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(12)))
            contentPanel.add(JSeparator())
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
            contentPanel.add(
                createReferenceAccordion(
                    chains = model.chains,
                    referenceWidth = referenceWidth,
                    onExpandedChanged = ::setReferenceExpanded,
                ),
            )
        }

        contentPanel.add(Box.createVerticalStrut(JBUI.scale(10)))
        contentPanel.add(createFooter(onReportBug))

        val scrollPane =
            JBScrollPane(
                contentPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
            ).apply {
                border = JBUI.Borders.empty()
                viewport.background = PANEL_BACKGROUND
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                verticalScrollBar.unitIncrement = JBUI.scale(16)
            }

        add(scrollPane, BorderLayout.CENTER)
        updatePreferredSize(notify = false)
        minimumSize = Dimension(minOf(popupWidth, JBUI.scale(MIN_POPUP_WIDTH)), JBUI.scale(MIN_POPUP_HEIGHT))
    }

    private fun setReferenceExpanded(
        @Suppress("UNUSED_PARAMETER") expanded: Boolean,
    ) {
        contentPanel.revalidate()
        contentPanel.repaint()

        SwingUtilities.invokeLater {
            updatePreferredSize(notify = true)
        }
    }

    private fun updatePreferredSize(notify: Boolean) {
        val contentHeight =
            (contentPanel.preferredSize.height + JBUI.scale(2))
                .coerceIn(JBUI.scale(MIN_POPUP_HEIGHT), JBUI.scale(MAX_POPUP_HEIGHT))

        preferredSize = Dimension(popupWidth, contentHeight)
        revalidate()

        if (notify) {
            onPreferredSizeChanged(preferredSize)
        }
    }
}

private fun createHeader(
    model: DesignTokenHoverPopupModel,
    descriptionWidth: Int,
    onNavigate: () -> Unit,
): JComponent =
    JPanel(BorderLayout(JBUI.scale(10), 0)).apply {
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
                add(createSubtitle(model.description, descriptionWidth))
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

private fun createSubtitle(
    description: String?,
    descriptionWidth: Int,
): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        if (description == null) {
            add(
                JBLabel("Design token  ·  @taiga-ui/design-tokens").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    alignmentX = JComponent.LEFT_ALIGNMENT
                },
            )
        } else {
            add(
                WrappedTextPane(
                    text = description,
                    width = descriptionWidth,
                    textFont = UIUtil.getLabelFont(),
                    textColor = UIUtil.getContextHelpForeground(),
                    alignment = StyleConstants.ALIGN_LEFT,
                ),
            )
            add(Box.createVerticalStrut(JBUI.scale(2)))
            add(
                JBLabel("@taiga-ui/design-tokens").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    alignmentX = JComponent.LEFT_ALIGNMENT
                },
            )
        }
    }

private fun createValueTable(
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
    val scaledValueWidth = JBUI.scale(valueWidth)
    val swatchWidth = if (row.color == null) 0 else JBUI.scale(SWATCH_SIZE + 8)
    val textWidth = (scaledValueWidth - swatchWidth).coerceAtLeast(JBUI.scale(MIN_VALUE_TEXT_WIDTH))
    val text =
        WrappedTextPane(
            text = row.resolvedValue,
            width = JBUI.unscale(textWidth),
            textFont = CODE_FONT,
            textColor = UIUtil.getLabelForeground(),
            alignment = StyleConstants.ALIGN_RIGHT,
            copyValue = row.resolvedValue,
        )

    return JPanel(GridBagLayout()).apply {
        isOpaque = false
        preferredSize = Dimension(scaledValueWidth, maxOf(text.preferredSize.height, JBUI.scale(SWATCH_SIZE)))
        minimumSize = preferredSize
        maximumSize = Dimension(scaledValueWidth, preferredSize.height)

        row.color?.let { color ->
            add(
                ColorSwatch(color, SWATCH_SIZE),
                GridBagConstraints().apply {
                    gridx = 0
                    weightx = 0.0
                    anchor = GridBagConstraints.NORTHEAST
                    insets = Insets(0, 0, 0, JBUI.scale(8))
                },
            )
        }

        add(
            text,
            GridBagConstraints().apply {
                gridx = 1
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                anchor = GridBagConstraints.EAST
            },
        )
    }
}

private fun createReferenceAccordion(
    chains: List<DesignTokenHoverReferenceChain>,
    referenceWidth: Int,
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
                    add(createReferenceChain(chain, referenceWidth))

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

private fun createReferenceChain(
    chain: DesignTokenHoverReferenceChain,
    referenceWidth: Int,
): JComponent =
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
            add(createReferenceLine(line, referenceWidth))

            if (index != chain.lines.lastIndex) {
                add(Box.createVerticalStrut(JBUI.scale(4)))
            }
        }
    }

private fun createReferenceLine(
    line: DesignTokenHoverReferenceLine,
    referenceWidth: Int,
): JComponent {
    val indent = line.depth * REFERENCE_DEPTH_INDENT
    val iconWidth = if (line.color == null) REFERENCE_MARKER_WIDTH else REFERENCE_MARKER_WIDTH + SMALL_SWATCH_SIZE + 7
    val textWidth = (referenceWidth - indent - iconWidth).coerceAtLeast(MIN_REFERENCE_TEXT_WIDTH)

    return JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        border = JBUI.Borders.emptyLeft(indent)

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
            WrappedTextPane(
                text = line.text,
                width = textWidth,
                textFont = CODE_FONT,
                textColor = if (line.root) LINK_COLOR else UIUtil.getLabelForeground(),
                alignment = StyleConstants.ALIGN_LEFT,
            ),
        )
    }
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
        graphics2D.stroke = BasicStroke(JBUI.scale(1).toFloat())
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

private class WrappedTextPane(
    text: String,
    width: Int,
    textFont: Font,
    textColor: Color,
    alignment: Int,
    copyValue: String? = null,
) : JTextPane() {
    init {
        font = textFont
        foreground = textColor
        isEditable = false
        isOpaque = false
        isFocusable = false
        border = JBUI.Borders.empty()
        margin = Insets(0, 0, 0, 0)
        highlighter = null

        styledDocument.insertString(0, text, null)
        copyValue?.let { value ->
            val componentAttributes = SimpleAttributeSet()

            StyleConstants.setComponent(componentAttributes, createCopyButton(value))
            styledDocument.insertString(styledDocument.length, " ", componentAttributes)
        }

        val paragraphAttributes = SimpleAttributeSet()

        StyleConstants.setAlignment(paragraphAttributes, alignment)
        styledDocument.setParagraphAttributes(0, styledDocument.length, paragraphAttributes, false)

        val scaledWidth = JBUI.scale(width)

        setSize(Dimension(scaledWidth, Short.MAX_VALUE.toInt()))
        val calculatedHeight = super.getPreferredSize().height

        preferredSize = Dimension(scaledWidth, calculatedHeight)
        minimumSize = preferredSize
        maximumSize = Dimension(scaledWidth, calculatedHeight)
        alignmentX = JComponent.LEFT_ALIGNMENT
    }
}

private fun createCopyButton(value: String): JButton =
    JButton(AllIcons.Actions.Copy).apply {
        toolTipText = "Copy value"
        isOpaque = false
        isContentAreaFilled = false
        isBorderPainted = false
        isFocusable = true
        margin = Insets(0, 0, 0, 0)
        border = JBUI.Borders.emptyLeft(COPY_ICON_GAP)
        preferredSize = JBUI.size(COPY_BUTTON_WIDTH, COPY_BUTTON_HEIGHT)
        minimumSize = preferredSize
        maximumSize = preferredSize
        addActionListener {
            CopyPasteManager.getInstance().setContents(StringSelection(value))
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
        graphics2D.fillRoundRect(0, 0, width, height, JBUI.scale(6), JBUI.scale(6))
        graphics2D.color = Color.WHITE
        graphics2D.font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(13f).toFloat())

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
        graphics2D.stroke = BasicStroke(JBUI.scale(1).toFloat())
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

private fun calculateValueWidth(popupWidth: Int): Int =
    (JBUI.unscale(popupWidth) - VALUE_COLUMN_RESERVED_WIDTH)
        .coerceIn(MIN_VALUE_COLUMN_WIDTH, MAX_VALUE_COLUMN_WIDTH)

private fun calculateDescriptionWidth(popupWidth: Int): Int =
    (JBUI.unscale(popupWidth) - DESCRIPTION_RESERVED_WIDTH)
        .coerceAtLeast(MIN_DESCRIPTION_WIDTH)

private fun calculateReferenceWidth(popupWidth: Int): Int =
    (JBUI.unscale(popupWidth) - REFERENCE_RESERVED_WIDTH)
        .coerceAtLeast(MIN_REFERENCE_WIDTH)

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
private val TOKEN_BADGE_BACKGROUND = Color(255, 112, 67)
private val CHECKER_LIGHT = Color(235, 235, 235)
private val CHECKER_DARK = Color(185, 185, 185)
private val SWATCH_BORDER = JBColor(Color(110, 110, 110), Color(170, 170, 170))
private val CODE_FONT = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(13))

private const val CONTENT_PADDING = 14
private const val ROW_HORIZONTAL_PADDING = 12
private const val MIN_POPUP_WIDTH = 460
private const val MIN_POPUP_HEIGHT = 210
private const val MAX_POPUP_HEIGHT = 640
private const val VALUE_COLUMN_RESERVED_WIDTH = 270
private const val MIN_VALUE_COLUMN_WIDTH = 210
private const val MAX_VALUE_COLUMN_WIDTH = 300
private const val MIN_VALUE_TEXT_WIDTH = 150
private const val DESCRIPTION_RESERVED_WIDTH = 185
private const val MIN_DESCRIPTION_WIDTH = 240
private const val REFERENCE_RESERVED_WIDTH = 58
private const val MIN_REFERENCE_WIDTH = 340
private const val MIN_REFERENCE_TEXT_WIDTH = 240
private const val REFERENCE_DEPTH_INDENT = 14
private const val REFERENCE_MARKER_WIDTH = 18
private const val TOKEN_BADGE_SIZE = 24
private const val SWATCH_SIZE = 26
private const val SMALL_SWATCH_SIZE = 20
private const val COPY_ICON_GAP = 6
private const val COPY_BUTTON_WIDTH = 24
private const val COPY_BUTTON_HEIGHT = 20
private const val OPAQUE_ALPHA = 255
