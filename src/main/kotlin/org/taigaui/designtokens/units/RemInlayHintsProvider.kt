package org.taigaui.designtokens.units

import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFontSize
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLanguageInjectionHost

internal class RemInlayHintsProvider : InlayHintsProvider {
    override fun createCollector(
        file: PsiFile,
        editor: Editor,
    ): InlayHintsCollector =
        when {
            file.language.isStylesheetLanguage() -> StylesheetCollector(file)
            file.language.isHtmlLanguage() -> HtmlTemplateCollector(file)
            else -> InjectedContentCollector(file)
        }

    private class StylesheetCollector(
        private val file: PsiFile,
    ) : SharedBypassCollector {
        override fun collectFromElement(
            element: PsiElement,
            sink: InlayTreeSink,
        ) {
            if (element === file) {
                addStylesheetHints(
                    file = file,
                    content = file.text,
                    sink = sink,
                    mapOffset = { it },
                )
            }
        }
    }

    private class HtmlTemplateCollector(
        private val file: PsiFile,
    ) : SharedBypassCollector {
        override fun collectFromElement(
            element: PsiElement,
            sink: InlayTreeSink,
        ) {
            if (element === file) {
                addAngularTemplateHints(
                    file = file,
                    content = file.text,
                    sink = sink,
                    mapOffset = { it },
                )
            }
        }
    }

    private class InjectedContentCollector(
        private val file: PsiFile,
    ) : SharedBypassCollector {
        private val injectionManager = InjectedLanguageManager.getInstance(file.project)

        override fun collectFromElement(
            element: PsiElement,
            sink: InlayTreeSink,
        ) {
            if (element === file) {
                addAngularHostHints(
                    file = file,
                    sink = sink,
                )
            }

            val host = element as? PsiLanguageInjectionHost ?: return

            injectionManager.enumerate(host) { injectedFile, _ ->
                when {
                    injectedFile.language.isStylesheetLanguage() ->
                        addStylesheetHints(
                            file = injectedFile,
                            content = injectedFile.text,
                            sink = sink,
                            mapOffset = { offset -> injectionManager.injectedToHost(injectedFile, offset) },
                        )

                    injectedFile.language.isHtmlLanguage() ->
                        addAngularTemplateHints(
                            file = injectedFile,
                            content = injectedFile.text,
                            sink = sink,
                            mapOffset = { offset -> injectionManager.injectedToHost(injectedFile, offset) },
                        )
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

private fun addStylesheetHints(
    file: PsiFile,
    content: CharSequence,
    sink: InlayTreeSink,
    mapOffset: (Int) -> Int,
) {
    val hints =
        file.project
            .service<RemInlayHintCache>()
            .hints(file, RemInlayHintKind.STYLESHEET) {
                RemInlayHintCollector.collect(content)
            }

    addHints(hints, sink, mapOffset)
}

private fun addAngularTemplateHints(
    file: PsiFile,
    content: CharSequence,
    sink: InlayTreeSink,
    mapOffset: (Int) -> Int,
) {
    val hints =
        file.project
            .service<RemInlayHintCache>()
            .hints(file, RemInlayHintKind.ANGULAR_TEMPLATE) {
                AngularRemStyleBindingHintCollector.collect(content)
            }

    addHints(hints, sink, mapOffset)
}

private fun addAngularHostHints(
    file: PsiFile,
    sink: InlayTreeSink,
) {
    val hints =
        file.project
            .service<RemInlayHintCache>()
            .hints(file, RemInlayHintKind.ANGULAR_HOST) {
                AngularHostRemStyleBindingHintCollector.collect(file)
            }

    addHints(
        hints = hints,
        sink = sink,
        mapOffset = { it },
    )
}

private fun addHints(
    hints: List<RemInlayHint>,
    sink: InlayTreeSink,
    mapOffset: (Int) -> Int,
) {
    hints.forEach { hint ->
        sink.addPresentation(
            position = InlineInlayPosition(mapOffset(hint.offset), true),
            hintFormat = REM_HINT_FORMAT,
            tooltip = hint.tooltip,
        ) {
            text(hint.text)
        }
    }
}

private fun Language.isStylesheetLanguage(): Boolean =
    generateSequence(this) { language -> language.baseLanguage }
        .any { language -> language.id in STYLESHEET_LANGUAGE_IDS }

private fun Language.isHtmlLanguage(): Boolean =
    generateSequence(this) { language -> language.baseLanguage }
        .any { language -> language.id in HTML_LANGUAGE_IDS }

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

private val STYLESHEET_LANGUAGE_IDS = setOf("CSS", "LESS", "SCSS")
private val HTML_LANGUAGE_IDS = setOf("HTML", "Angular2HTML")
private val REM_HINT_FORMAT =
    HintFormat.default
        .withColorKind(HintColorKind.TextWithoutBackground)
        .withFontSize(HintFontSize.ABitSmallerThanInEditor)
