package org.taigaui.designtokens.events

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext

class TypeScriptHostEventPluginCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            TypeScriptHostEventPluginCompletionProvider(),
        )
    }
}

private class TypeScriptHostEventPluginCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val request = parameters.toHostEventPluginCompletionRequest() ?: return
        val matchingResult = result.withPrefixMatcher(request.prefix)

        EVENT_PLUGIN_COMPLETIONS
            .asSequence()
            .filterNot { completion -> completion.identity in request.usedModifierIdentities }
            .forEach { completion -> matchingResult.addElement(completion.toLookupElement()) }
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

        val offset = editor.caretModel.offset
        val hostBinding = AngularHostBindingSupport.findAt(file, (offset - 1).coerceAtLeast(0))
        val sourceBeforeCaret = hostBinding?.sourceBeforeCaret(editor.document.immutableCharSequence, offset)

        if (sourceBeforeCaret?.let(HostEventPluginCompletionContext::parse) != null) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }

        return Result.CONTINUE
    }
}

private fun CompletionParameters.toHostEventPluginCompletionRequest(): HostEventPluginCompletionContext? {
    val offset = editor.caretModel.offset
    val hostBinding = AngularHostBindingSupport.findAt(originalFile, (offset - 1).coerceAtLeast(0)) ?: return null
    val sourceBeforeCaret = hostBinding.sourceBeforeCaret(editor.document.immutableCharSequence, offset) ?: return null

    return HostEventPluginCompletionContext.parse(sourceBeforeCaret)
}

private fun AngularHostEventBinding.sourceBeforeCaret(
    text: CharSequence,
    caretOffset: Int,
): String? {
    if (caretOffset !in (startOffset + 1)..endOffset || startOffset !in text.indices) {
        return null
    }

    val safeEnd = caretOffset.coerceAtMost(text.length)

    return text.subSequence(startOffset, safeEnd).toString()
}

internal data class HostEventPluginCompletionContext(
    val prefix: String,
    val usedModifierIdentities: Set<String>,
) {
    companion object {
        fun parse(sourceBeforeCaret: String): HostEventPluginCompletionContext? {
            if (!sourceBeforeCaret.startsWith('(')) {
                return null
            }

            val eventChain = sourceBeforeCaret.drop(1)
            val separatorIndex = eventChain.lastIndexOf('.')

            if (separatorIndex <= 0) {
                return null
            }

            val completedChain = eventChain.substring(0, separatorIndex)
            val prefix = eventChain.substring(separatorIndex + 1)
            val usedModifiers = parseCompletedChain(completedChain) ?: return null

            return HostEventPluginCompletionContext(
                prefix = prefix,
                usedModifierIdentities = usedModifiers,
            )
        }

        private fun parseCompletedChain(chain: String): Set<String>? {
            val parts = chain.split('.')
            val firstModifierIndex = parts.indexOfFirst { part -> EventPluginModifier.parse(part) != null }
            val event: String
            val modifierSources: List<String>

            if (firstModifierIndex > 0) {
                event = parts.take(firstModifierIndex).joinToString(".")
                modifierSources = parts.drop(firstModifierIndex)
            } else {
                event = chain
                modifierSources = emptyList()
            }

            if (!isCompleteEvent(event)) {
                return null
            }

            val modifiers = modifierSources.map { source -> EventPluginModifier.parse(source) ?: return null }
            val identities = modifiers.map(EventPluginModifier::completionIdentity)

            return identities
                .takeIf { values -> values.distinct().size == values.size }
                ?.toSet()
        }

        private fun isCompleteEvent(event: String): Boolean {
            val firstPart = event.substringBefore('.').lowercase()

            return if (firstPart == "keydown" || firstPart == "keyup") {
                AngularExtendedKeyEventSupport.isValid(event)
            } else {
                event.isNotBlank() && !event.contains('.')
            }
        }
    }
}

private data class EventPluginCompletion(
    val source: String,
    val presentableText: String = source,
    val identity: String = source,
)

private fun EventPluginCompletion.toLookupElement(): LookupElementBuilder {
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
