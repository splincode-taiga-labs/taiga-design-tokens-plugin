package org.taigaui.designtokens.completion

import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.taigaui.designtokens.documentation.DesignTokenSourceDetail
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal fun createCompactSourceDetails(details: List<DesignTokenSourceDetail>): JComponent =
    JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        isOpaque = false
        alignmentX = JComponent.LEFT_ALIGNMENT

        details.take(MAX_COMPLETION_SOURCE_DETAILS).forEach { detail ->
            add(
                JBLabel(detail.compactLabel()).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                    toolTipText = detail.sourceFile.toString()
                    alignmentX = JComponent.LEFT_ALIGNMENT
                },
            )
        }

        val hiddenCount = details.size - MAX_COMPLETION_SOURCE_DETAILS

        if (hiddenCount > 0) {
            add(
                JBLabel("+$hiddenCount more sources").apply {
                    foreground = UIUtil.getContextHelpForeground()
                    font = font.deriveFont(JBUI.scaleFontSize(11f).toFloat())
                    alignmentX = JComponent.LEFT_ALIGNMENT
                },
            )
        }
    }

private fun DesignTokenSourceDetail.compactLabel(): String =
    buildString {
        append(sourceFile.fileName)
        append(':')
        append(line)

        if (selectorChain.isNotEmpty()) {
            append(" · ")
            append(selectorChain.joinToString(" → "))
        }
    }

private const val MAX_COMPLETION_SOURCE_DETAILS = 2
