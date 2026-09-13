package org.taigaui.designtokens.events

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

class TypeScriptHostEventPluginCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        parameters.toHostEventPluginCompletionRequest()?.let { request ->
            result
                .withPrefixMatcher(request.prefix)
                .addAllElements(request.toLookupElements())
        }
    }
}

class TypeScriptHostEventPluginCompletionAutoPopupHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (charTyped != '.') {
            return Result.CONTINUE
        }

        val caretOffset = editor.caretModel.offset
        val completionContext =
            HostEventPluginCompletionContext.findBeforeCaret(
                text = editor.document.immutableCharSequence,
                caretOffset = caretOffset,
            ) ?: return Result.CONTINUE
        val hostOffset = (caretOffset - 2).coerceAtLeast(0)

        if (AngularHostBindingSupport.findAt(file, hostOffset) == null) {
            return Result.CONTINUE
        }

        showLookupLater(
            project = project,
            editor = editor,
            file = file,
            expectedCaretOffset = caretOffset,
            expectedContext = completionContext,
        )

        return Result.STOP
    }

    private fun showLookupLater(
        project: Project,
        editor: Editor,
        file: PsiFile,
        expectedCaretOffset: Int,
        expectedContext: HostEventPluginCompletionContext,
    ) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || !file.isValid || editor.caretModel.offset != expectedCaretOffset) {
                return@invokeLater
            }

            PsiDocumentManager.getInstance(project).commitDocument(editor.document)

            val context =
                HostEventPluginCompletionContext.findBeforeCaret(
                    text = editor.document.immutableCharSequence,
                    caretOffset = expectedCaretOffset,
                )?.takeIf { current -> current == expectedContext }
                    ?: return@invokeLater
            val hostBinding =
                AngularHostBindingSupport.findAt(
                    file,
                    (expectedCaretOffset - 1).coerceAtLeast(0),
                ) ?: return@invokeLater

            if (expectedCaretOffset !in hostBinding.startOffset..hostBinding.endOffset) {
                return@invokeLater
            }

            val lookupElements = context.toLookupElements().toTypedArray()

            if (lookupElements.isNotEmpty()) {
                LookupManager
                    .getInstance(project)
                    .showLookup(editor, lookupElements, context.prefix)
            }
        }
    }
}

private fun CompletionParameters.toHostEventPluginCompletionRequest(): HostEventPluginCompletionContext? {
    val caretOffset = editor.caretModel.offset
    val completionContext =
        HostEventPluginCompletionContext.findBeforeCaret(
            text = editor.document.immutableCharSequence,
            caretOffset = caretOffset,
        )
    val isHostProperty =
        sequenceOf(position, originalPosition)
            .filterNotNull()
            .any(AngularHostBindingSupport::isInsideHostProperty) ||
            AngularHostBindingSupport.findAt(
                originalFile,
                (caretOffset - 1).coerceAtLeast(0),
            ) != null

    return completionContext.takeIf { isHostProperty }
}

internal data class HostEventPluginCompletionContext(
    val prefix: String,
    val usedModifierIdentities: Set<String>,
) {
    companion object {
        fun parse(sourceBeforeCaret: String): HostEventPluginCompletionContext? =
            sourceBeforeCaret
                .takeIf { source -> source.startsWith('(') }
                ?.drop(1)
                ?.let { eventChain ->
                    eventChain
                        .lastIndexOf('.')
                        .takeIf { separatorIndex -> separatorIndex > 0 }
                        ?.let { separatorIndex ->
                            parseCompletedChain(eventChain.substring(0, separatorIndex))?.let { usedModifiers ->
                                HostEventPluginCompletionContext(
                                    prefix = eventChain.substring(separatorIndex + 1),
                                    usedModifierIdentities = usedModifiers,
                                )
                            }
                        }
                }

        fun findBeforeCaret(
            text: CharSequence,
            caretOffset: Int,
        ): HostEventPluginCompletionContext? {
            val safeOffset = caretOffset.coerceIn(0, text.length)
            val searchStart = maxOf(0, safeOffset - MAX_BINDING_LENGTH)
            val openingParenthesis =
                (safeOffset - 1 downTo searchStart)
                    .firstOrNull { index -> text[index] == '(' || text[index].isBindingBoundary() }
                    ?.takeIf { index -> text[index] == '(' }

            return openingParenthesis?.let { start ->
                parse(text.subSequence(start, safeOffset).toString())
            }
        }

        private fun parseCompletedChain(chain: String): Set<String>? {
            val parts = chain.split('.')
            val firstModifierIndex = parts.indexOfFirst { part -> EventPluginModifier.parse(part) != null }
            val event =
                if (firstModifierIndex > 0) {
                    parts.take(firstModifierIndex).joinToString(".")
                } else {
                    chain
                }
            val modifierSources =
                if (firstModifierIndex > 0) {
                    parts.drop(firstModifierIndex)
                } else {
                    emptyList()
                }
            val modifiers = modifierSources.mapNotNull(EventPluginModifier::parse)
            val identities = modifiers.map(EventPluginModifier::completionIdentity)

            return identities
                .takeIf {
                    isCompleteEvent(event) &&
                        modifiers.size == modifierSources.size &&
                        identities.distinct().size == identities.size
                }?.toSet()
        }

        private fun isCompleteEvent(event: String): Boolean {
            val firstPart = event.substringBefore('.').lowercase()

            return if (firstPart == "keydown" || firstPart == "keyup") {
                AngularExtendedKeyEventSupport.isValid(event)
            } else {
                event.isNotBlank() && !event.contains('.')
            }
        }

        private fun Char.isBindingBoundary(): Boolean =
            isWhitespace() || this == '\'' || this == '"' || this == '{' || this == '}' || this == ',' || this == ':'

        private const val MAX_BINDING_LENGTH = 160
    }
}

private data class EventPluginCompletion(
    val source: String,
    val presentableText: String = source,
    val identity: String = source,
)

private fun HostEventPluginCompletionContext.toLookupElements(): List<LookupElement> =
    EVENT_PLUGIN_COMPLETIONS
        .asSequence()
        .filterNot { completion -> completion.identity in usedModifierIdentities }
        .map(EventPluginCompletion::toLookupElement)
        .toList()

private fun EventPluginCompletion.toLookupElement(): LookupElement {
    val modifier = EventPluginModifier.parse(source)

    return LookupElementBuilder
        .create(source)
        .withPresentableText(presentableText)
        .withTypeText("Taiga UI event modifier", true)
        .let { element ->
            modifier?.description?.let { description -> element.withTailText(" — $description", true) } ?: element
        }
}

private fun EventPluginModifier.completionIdentity(): String =
    when {
        source == "silent" -> "zoneless"
        source.startsWith("debounce~") -> "debounce"
        source.startsWith("throttle~") -> "throttle"
        else -> source
    }

private val EVENT_PLUGIN_COMPLETIONS =
    listOf(
        EventPluginCompletion("capture"),
        EventPluginCompletion("once"),
        EventPluginCompletion("passive"),
        EventPluginCompletion("prevent"),
        EventPluginCompletion("self"),
        EventPluginCompletion("silent", identity = "zoneless"),
        EventPluginCompletion("zoneless"),
        EventPluginCompletion("stop"),
        EventPluginCompletion("debounce~300ms", "debounce~<delay>ms", "debounce"),
        EventPluginCompletion("debounce~2s", "debounce~<delay>s", "debounce"),
        EventPluginCompletion("throttle~300ms", "throttle~<delay>ms", "throttle"),
        EventPluginCompletion("throttle~2s", "throttle~<delay>s", "throttle"),
    )
