package org.taigaui.designtokens.documentation

import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.text.StyleConstants

internal fun createDesignTokenReferenceAccordion(
    sections: List<DesignTokenHoverPackageSection>,
    referenceWidth: Int,
    onExpandedChanged: (Boolean) -> Unit,
): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        val sectionsWithChains = sections.filter { section -> section.chains.isNotEmpty() }
        val showPackageHeaders = sectionsWithChains.size > 1
        val chainCount = sectionsWithChains.sumOf { section -> section.chains.size }
        val body =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                alignmentX = JComponent.LEFT_ALIGNMENT
                isVisible = false

                sectionsWithChains.forEachIndexed { index, section ->
                    add(
                        createPackageReferenceSection(
                            section = section,
                            referenceWidth = referenceWidth,
                            showPackageHeader = showPackageHeaders,
                        ),
                    )

                    if (index != sectionsWithChains.lastIndex) {
                        add(Box.createVerticalStrut(JBUI.scale(10)))
                        add(JSeparator())
                        add(Box.createVerticalStrut(JBUI.scale(10)))
                    }
                }
            }
        lateinit var toggle: ActionLink
        toggle =
            ActionLink(collapsedReferenceTitle(chainCount)) {
                body.isVisible = !body.isVisible
                toggle.text =
                    if (body.isVisible) {
                        expandedReferenceTitle(chainCount)
                    } else {
                        collapsedReferenceTitle(chainCount)
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

private fun createPackageReferenceSection(
    section: DesignTokenHoverPackageSection,
    referenceWidth: Int,
    showPackageHeader: Boolean,
): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        if (showPackageHeader) {
            add(
                JBLabel(section.packageName).apply {
                    font = font.deriveFont(Font.BOLD, JBUI.scaleFontSize(14f).toFloat())
                    alignmentX = JComponent.LEFT_ALIGNMENT
                },
            )
            add(Box.createVerticalStrut(JBUI.scale(8)))
        }

        section.chains.forEachIndexed { index, chain ->
            add(createReferenceChain(chain, referenceWidth))

            if (index != section.chains.lastIndex) {
                add(Box.createVerticalStrut(JBUI.scale(8)))
                add(JSeparator())
                add(Box.createVerticalStrut(JBUI.scale(8)))
            }
        }
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
    val iconWidth =
        if (line.color == null) {
            REFERENCE_MARKER_WIDTH
        } else {
            REFERENCE_MARKER_WIDTH + DESIGN_TOKEN_POPUP_SMALL_SWATCH_SIZE + SWATCH_GAP
        }
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
            add(ColorSwatch(color, DESIGN_TOKEN_POPUP_SMALL_SWATCH_SIZE))
            add(Box.createHorizontalStrut(JBUI.scale(SWATCH_GAP)))
        }
        add(
            WrappedTextPane(
                text = line.text,
                width = textWidth,
                textFont = DESIGN_TOKEN_POPUP_CODE_FONT,
                textColor = if (line.root) DESIGN_TOKEN_POPUP_LINK_COLOR else UIUtil.getLabelForeground(),
                alignment = StyleConstants.ALIGN_LEFT,
            ),
        )
    }
}

private fun collapsedReferenceTitle(count: Int): String = "▸ Reference chain ($count)"

private fun expandedReferenceTitle(count: Int): String = "▾ Reference chain ($count)"

private const val MIN_REFERENCE_TEXT_WIDTH = 240
private const val REFERENCE_DEPTH_INDENT = 14
private const val REFERENCE_MARKER_WIDTH = 18
private const val SWATCH_GAP = 7
