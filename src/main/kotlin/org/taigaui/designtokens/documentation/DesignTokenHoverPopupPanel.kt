package org.taigaui.designtokens.documentation

import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.SwingUtilities
import javax.swing.text.StyleConstants

internal class DesignTokenHoverPopupPanel(
    private val popupWidth: Int,
    private val onNavigate: (DesignTokenNavigationTarget) -> Unit,
    private val onReportBug: () -> Unit,
    private val onPreferredSizeChanged: (Dimension) -> Unit,
) : JPanel(BorderLayout()) {
    private val contentPanel =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = DESIGN_TOKEN_POPUP_BACKGROUND
            border = JBUI.Borders.empty(CONTENT_PADDING)
        }

    init {
        background = DESIGN_TOKEN_POPUP_BACKGROUND
        border = JBUI.Borders.empty()
        add(createScrollPane(contentPanel), BorderLayout.CENTER)
        minimumSize = Dimension(minOf(popupWidth, JBUI.scale(MIN_POPUP_WIDTH)), JBUI.scale(MIN_POPUP_HEIGHT))
    }

    fun showLoading(tokenName: String) {
        contentPanel.removeAll()
        contentPanel.add(
            createHeader(
                tokenName = tokenName,
                description = null,
                descriptionWidth = calculateDescriptionWidth(popupWidth),
            ),
        )
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(18)))
        contentPanel.add(createLoadingRow())
        updatePreferredSize(notify = true)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    fun showModel(model: DesignTokenHoverPopupModel) {
        val valueWidth = calculateValueWidth(popupWidth)
        val descriptionWidth = calculateDescriptionWidth(popupWidth)
        val referenceWidth = calculateReferenceWidth(popupWidth)

        contentPanel.removeAll()
        contentPanel.add(createHeader(model.tokenName, model.description, descriptionWidth))
        contentPanel.add(Box.createVerticalStrut(JBUI.scale(14)))
        contentPanel.add(
            createDesignTokenValueSections(
                sections = model.sections,
                valueWidth = valueWidth,
                onNavigate = onNavigate,
            ),
        )

        if (model.referenceChainCount > 0) {
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(12)))
            contentPanel.add(JSeparator())
            contentPanel.add(Box.createVerticalStrut(JBUI.scale(8)))
            contentPanel.add(
                createDesignTokenReferenceAccordion(
                    sections = model.sections,
                    referenceWidth = referenceWidth,
                    onExpandedChanged = ::setReferenceExpanded,
                ),
            )
        }

        contentPanel.add(Box.createVerticalStrut(JBUI.scale(10)))
        contentPanel.add(createFooter(onReportBug))
        updatePreferredSize(notify = true)
        contentPanel.revalidate()
        contentPanel.repaint()
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

private fun createScrollPane(content: JComponent): JComponent =
    JBScrollPane(
        content,
        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
    ).apply {
        border = JBUI.Borders.empty()
        viewport.background = DESIGN_TOKEN_POPUP_BACKGROUND
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBar.unitIncrement = JBUI.scale(16)
    }

private fun createHeader(
    tokenName: String,
    description: String?,
    descriptionWidth: Int,
): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(createTitleRow(tokenName))

        description?.let { text ->
            add(Box.createVerticalStrut(JBUI.scale(3)))
            add(
                WrappedTextPane(
                    text = text,
                    width = descriptionWidth,
                    textFont = UIUtil.getLabelFont(),
                    textColor = UIUtil.getContextHelpForeground(),
                    alignment = StyleConstants.ALIGN_LEFT,
                ),
            )
        }
    }

private fun createTitleRow(tokenName: String): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        add(
            TokenBadge().apply {
                alignmentY = JComponent.CENTER_ALIGNMENT
            },
        )
        add(Box.createHorizontalStrut(JBUI.scale(TITLE_GAP)))
        add(
            JBLabel(tokenName).apply {
                font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(18f).toFloat())
                alignmentY = JComponent.CENTER_ALIGNMENT
            },
        )
    }

private fun createLoadingRow(): JComponent =
    JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT
        add(AsyncProcessIcon("Loading design token graph"))
        add(Box.createHorizontalStrut(JBUI.scale(8)))
        add(
            JBLabel("Loading design token graph…").apply {
                foreground = UIUtil.getContextHelpForeground()
            },
        )
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

private fun calculateValueWidth(popupWidth: Int): Int =
    (JBUI.unscale(popupWidth) - VALUE_COLUMN_RESERVED_WIDTH)
        .coerceIn(MIN_VALUE_COLUMN_WIDTH, MAX_VALUE_COLUMN_WIDTH)

private fun calculateDescriptionWidth(popupWidth: Int): Int =
    (JBUI.unscale(popupWidth) - DESCRIPTION_RESERVED_WIDTH)
        .coerceAtLeast(MIN_DESCRIPTION_WIDTH)

private fun calculateReferenceWidth(popupWidth: Int): Int =
    (JBUI.unscale(popupWidth) - REFERENCE_RESERVED_WIDTH)
        .coerceAtLeast(MIN_REFERENCE_WIDTH)

private const val CONTENT_PADDING = 14
private const val TITLE_GAP = 10
private const val MIN_POPUP_WIDTH = 460
private const val MIN_POPUP_HEIGHT = 210
private const val MAX_POPUP_HEIGHT = 640
private const val VALUE_COLUMN_RESERVED_WIDTH = 270
private const val MIN_VALUE_COLUMN_WIDTH = 210
private const val MAX_VALUE_COLUMN_WIDTH = 300
private const val DESCRIPTION_RESERVED_WIDTH = 185
private const val MIN_DESCRIPTION_WIDTH = 240
private const val REFERENCE_RESERVED_WIDTH = 58
private const val MIN_REFERENCE_WIDTH = 340
