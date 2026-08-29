package org.taigaui.designtokens.units

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

internal class RemInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(
        file: PsiFile,
        editor: Editor,
    ): InlayHintsCollector = Collector(file)

    private class Collector(
        private val file: PsiFile,
    ) : SharedBypassCollector {
        override fun collectFromElement(
            element: PsiElement,
            sink: InlayTreeSink,
        ) {
            if (element !== file) {
                return
            }

            RemInlayHintCollector.collect(file.text).forEach { hint ->
                sink.addPresentation(
                    position = InlineInlayPosition(hint.offset, true),
                    hintFormat = HintFormat.default,
                    tooltip = hint.tooltip,
                ) {
                    text(hint.text)
                }
            }
        }
    }
}

internal data class RemInlayHint(
    val offset: Int,
    val text: String,
    val tooltip: String,
)

internal object RemInlayHintCollector {
    fun collect(content: CharSequence): List<RemInlayHint> =
        RemValueAtOffsetFinder
            .findAll(content)
            .groupBy { value -> content.lineStart(value.startOffset) }
            .values
            .map { values -> values.sortedBy(RemValueAtOffset::startOffset) }
            .map { values ->
                RemInlayHint(
                    offset = content.hintOffset(values),
                    text = values.joinToString(separator = " · ", prefix = " ") { it.pxPresentation },
                    tooltip = values.joinToString(separator = " · ") { it.presentation() },
                )
            }
}

private fun CharSequence.lineStart(offset: Int): Int {
    for (index in (offset - 1).coerceAtMost(lastIndex) downTo 0) {
        if (this[index] == '\n') {
            return index + 1
        }
    }

    return 0
}

private fun CharSequence.hintOffset(values: List<RemValueAtOffset>): Int {
    val afterLastValue = values.maxOf(RemValueAtOffset::endOffset)
    val lineEnd =
        (afterLastValue until length)
            .firstOrNull { index -> this[index] == '\n' }
            ?: length
    val semicolon =
        (afterLastValue until lineEnd)
            .firstOrNull { index -> this[index] == ';' }

    return semicolon?.plus(1) ?: afterLastValue
}
