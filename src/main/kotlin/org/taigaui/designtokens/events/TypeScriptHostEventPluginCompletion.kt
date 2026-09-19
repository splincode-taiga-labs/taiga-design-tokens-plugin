package org.taigaui.designtokens.events

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.html.HtmlDescriptorUtils
import com.intellij.polySymbols.js.JS_PROPERTIES
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile

class TypeScriptHostEventPluginCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(
        parameters: CompletionParameters,
        result: CompletionResultSet,
    ) {
        val request = parameters.toAngularHostCompletionRequest() ?: return
        val eventContext = HostEventPluginCompletionContext.parse(request.nameBeforeCaret)

        when {
            request.nameBeforeCaret.startsWith('[') ->
                addAngularBindingCompletions(parameters, result, request)

            eventContext != null ->
                addEventCompletions(parameters, result, request, eventContext)

            else ->
                addHtmlAttributeCompletions(parameters, result, request)
        }
    }
}

private fun addAngularBindingCompletions(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    request: AngularHostCompletionRequest,
) {
    val queryExecutor = PolySymbolQueryExecutorFactory.create(request.context.hostObject)
    val patchedResult =
        result.withPrefixMatcher(
            result.prefixMatcher.cloneWithPrefix(request.nameBeforeCaret),
        )
    val nativeItems =
        queryExecutor
            .codeCompletionQuery(
                JS_PROPERTIES,
                request.nameBeforeCaret,
                request.nameBeforeCaret.length,
            ).run()
    val existingClosingDelimiter = request.existingClosingDelimiter(parameters)

    nativeItems.forEach { item ->
        item
            .withHostClosingDelimiterDeduplication(existingClosingDelimiter)
            .addToResult(parameters, patchedResult)
    }
}

private fun addEventCompletions(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    request: AngularHostCompletionRequest,
    context: HostEventPluginCompletionContext,
) {
    val existingClosingDelimiter = request.existingClosingDelimiter(parameters)
    val standardEvents =
        if (context.kind == HostEventPluginCompletionContext.Kind.EVENT) {
            parameters.originalFile.project
                .standardHtmlHostCompletions()
                .events
                .asSequence()
                .filter { event -> event.startsWith(context.prefix, ignoreCase = true) }
                .map { event ->
                    LookupElementBuilder
                        .create(event)
                        .withTypeText("DOM event", true)
                        .let { element ->
                            existingClosingDelimiter
                                ?.let { delimiter ->
                                    element.withInsertHandler(
                                        hostClosingDelimiterDeduplicationHandler(delimiter),
                                    )
                                } ?: element
                        }
                }
                .toList()
        } else {
            emptyList()
        }
    val standardNames = standardEvents.map(LookupElement::getLookupString).toSet()
    val taigaItems =
        context
            .toLookupElements(existingClosingDelimiter)
            .filterNot { item -> item.lookupString in standardNames }

    result
        .withPrefixMatcher(context.prefix)
        .addAllElements(standardEvents + taigaItems)
}

private fun addHtmlAttributeCompletions(
    parameters: CompletionParameters,
    result: CompletionResultSet,
    request: AngularHostCompletionRequest,
) {
    val prefix = request.nameBeforeCaret
    val items =
        parameters.originalFile.project
            .standardHtmlHostCompletions()
            .attributes
            .asSequence()
            .filter { attribute -> attribute.startsWith(prefix, ignoreCase = true) }
            .map { attribute ->
                LookupElementBuilder
                    .create(attribute)
                    .withTypeText("HTML attribute", true)
            }
            .toList()

    result
        .withPrefixMatcher(prefix)
        .addAllElements(items)
}

private data class StandardHtmlHostCompletions(
    val attributes: List<String>,
    val events: List<String>,
)

private fun Project.standardHtmlHostCompletions(): StandardHtmlHostCompletions =
    getUserData(STANDARD_HTML_HOST_COMPLETIONS_KEY)
        ?: buildStandardHtmlHostCompletions().also { completions ->
            putUserData(STANDARD_HTML_HOST_COMPLETIONS_KEY, completions)
        }

private fun Project.buildStandardHtmlHostCompletions(): StandardHtmlHostCompletions {
    val attributes =
        HtmlDescriptorUtils
            .getHtmlNSDescriptor(this)
            ?.getAllElementsDescriptors(null)
            ?.asSequence()
            ?.flatMap { descriptor ->
                descriptor
                    .getDefaultAttributeDescriptors(null)
                    .asSequence()
            }?.map { descriptor -> descriptor.name }
            ?.filterNot { name -> ':' in name }
            ?.distinct()
            ?.sorted()
            ?.toList()
            .orEmpty()
    val events =
        attributes
            .asSequence()
            .filter { name -> name.length > 2 && name.startsWith("on", ignoreCase = true) }
            .map { name -> name.drop(2) }
            .distinct()
            .sorted()
            .toList()

    return StandardHtmlHostCompletions(
        attributes = attributes,
        events = events,
    )
}

class TypeScriptHostEventPluginCompletionAutoPopupHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        editor.putUserData(HOST_COMPLETION_CARET_OFFSET, null)

        val caretOffset = editor.caretModel.offset
        val shouldOpenCompletion =
            if (charTyped.isCompletionTrigger()) {
                PsiDocumentManager.getInstance(project).commitDocument(editor.document)
                AngularHostBindingSupport.isInsideHostProperty(
                    file,
                    (caretOffset - 1).coerceAtLeast(0),
                )
            } else {
                false
            }

        if (shouldOpenCompletion) {
            editor.putUserData(HOST_COMPLETION_CARET_OFFSET, caretOffset)
        }

        // The standard auto-popup runs before the character is inserted and does not reliably
        // traverse Angular's HostBindingsScope. Invoke BASIC completion from charTyped instead,
        // after the document contains the actual host binding prefix.
        return if (shouldOpenCompletion) Result.STOP else Result.CONTINUE
    }

    override fun charTyped(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        val caretOffsetBeforeTyping = editor.getUserData(HOST_COMPLETION_CARET_OFFSET)
        editor.putUserData(HOST_COMPLETION_CARET_OFFSET, null)

        if (
            caretOffsetBeforeTyping == null ||
            !charTyped.isCompletionTrigger() ||
            editor.caretModel.offset != caretOffsetBeforeTyping + 1
        ) {
            return Result.CONTINUE
        }

        val expectedCaretOffset = editor.caretModel.offset

        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed || editor.caretModel.offset != expectedCaretOffset) {
                return@invokeLater
            }

            PsiDocumentManager.getInstance(project).commitDocument(editor.document)

            CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
                .invokeCompletion(project, editor)
        }

        return Result.CONTINUE
    }
}

private data class AngularHostCompletionRequest(
    val context: AngularHostPropertyContext,
    val nameBeforeCaret: String,
)

private fun AngularHostCompletionRequest.existingClosingDelimiter(parameters: CompletionParameters): Char? {
    val delimiter =
        when (nameBeforeCaret.firstOrNull()) {
            '(' -> ')'
            '[' -> ']'
            else -> null
        }
    val caretOffset = parameters.editor.caretModel.offset

    return delimiter?.takeIf { candidate ->
        parameters.editor.document.charsSequence
            .getOrNull(caretOffset) == candidate
    }
}

private fun PolySymbolCodeCompletionItem.withHostClosingDelimiterDeduplication(
    closingDelimiter: Char?,
): PolySymbolCodeCompletionItem =
    closingDelimiter
        ?.let { delimiter ->
            withInsertHandlerAdded(
                hostClosingDelimiterDeduplicationHandler(delimiter),
                PolySymbol.Priority.LOWEST,
            )
        } ?: this

private fun hostClosingDelimiterDeduplicationHandler(closingDelimiter: Char): InsertHandler<LookupElement> =
    InsertHandler { context, _ ->
        val text = context.document.charsSequence
        val startOffset = context.startOffset.coerceIn(0, text.length)
        val endOffset = minOf(text.length, startOffset + HOST_BINDING_COMPLETION_SCAN_LENGTH)
        val duplicateOffset =
            (startOffset until (endOffset - 1).coerceAtLeast(startOffset))
                .takeWhile { index -> text[index] != '\'' && text[index] != '"' }
                .firstOrNull { index ->
                    text[index] == closingDelimiter && text[index + 1] == closingDelimiter
                }

        duplicateOffset?.let { offset ->
            context.document.deleteString(offset + 1, offset + 2)
        }
    }

private fun CompletionParameters.toAngularHostCompletionRequest(): AngularHostCompletionRequest? {
    val caretOffset = editor.caretModel.offset
    val context =
        AngularHostBindingSupport.findPropertyContext(
            originalFile,
            (caretOffset - 1).coerceAtLeast(0),
        ) ?: return null
    val endOffset = caretOffset.coerceIn(context.nameStartOffset, context.nameEndOffset)
    val nameBeforeCaret =
        editor.document.getText(
            TextRange(context.nameStartOffset, endOffset),
        )

    return AngularHostCompletionRequest(context, nameBeforeCaret)
}

internal data class HostEventPluginCompletionContext(
    val prefix: String,
    val usedModifierIdentities: Set<String>,
    val kind: Kind,
) {
    enum class Kind {
        EVENT,
        MODIFIER,
    }

    companion object {
        fun parse(sourceBeforeCaret: String): HostEventPluginCompletionContext? =
            sourceBeforeCaret
                .takeIf { source -> source.startsWith('(') }
                ?.drop(1)
                ?.let(::parseEventChain)

        fun findBeforeCaret(
            text: CharSequence,
            caretOffset: Int,
        ): HostEventPluginCompletionContext? = sourceBeforeCaret(text, caretOffset)?.let(::parse)

        fun findAfterTyping(
            text: CharSequence,
            caretOffset: Int,
            charTyped: Char,
        ): HostEventPluginCompletionContext? =
            sourceBeforeCaret(text, caretOffset)
                ?.plus(charTyped)
                ?.let(::parse)

        private fun parseEventChain(eventChain: String): HostEventPluginCompletionContext? {
            if (!eventChain.contains('.')) {
                val prefix = eventChain.substringAfterLast('>')
                val targetIsValid =
                    '>' !in eventChain ||
                        eventChain.substringBeforeLast('>').all { character ->
                            character.isEventNameCharacter() && character != '>'
                        }

                return prefix
                    .takeIf { value ->
                        targetIsValid &&
                            value.all { character -> character.isEventNameCharacter() }
                    }?.let { value ->
                        HostEventPluginCompletionContext(
                            prefix = value,
                            usedModifierIdentities = emptySet(),
                            kind = Kind.EVENT,
                        )
                    }
            }

            val separatorIndex = eventChain.lastIndexOf('.')

            return separatorIndex
                .takeIf { index -> index > 0 }
                ?.let { index ->
                    parseCompletedChain(eventChain.substring(0, index))?.let { usedModifiers ->
                        HostEventPluginCompletionContext(
                            prefix = eventChain.substring(index + 1),
                            usedModifierIdentities = usedModifiers,
                            kind = Kind.MODIFIER,
                        )
                    }
                }
        }

        private fun sourceBeforeCaret(
            text: CharSequence,
            caretOffset: Int,
        ): String? {
            val safeOffset = caretOffset.coerceIn(0, text.length)
            val searchStart = maxOf(0, safeOffset - MAX_BINDING_LENGTH)
            val openingParenthesis =
                (safeOffset - 1 downTo searchStart)
                    .firstOrNull { index -> text[index] == '(' || text[index].isBindingBoundary() }
                    ?.takeIf { index -> text[index] == '(' }

            return openingParenthesis?.let { start ->
                text.subSequence(start, safeOffset).toString()
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

        private fun Char.isEventNameCharacter(): Boolean =
            isLetterOrDigit() || this == '-' || this == '_' || this == '>'

        private fun Char.isBindingBoundary(): Boolean =
            isWhitespace() || this == '\'' || this == '"' || this == '{' || this == '}' || this == ',' || this == ':'

        private const val MAX_BINDING_LENGTH = 160
    }
}

private data class EventPluginCompletion(
    val source: String,
    val presentableText: String = source,
    val identity: String = source,
    val typeText: String = "Taiga UI event modifier",
    val description: String? = null,
)

private fun HostEventPluginCompletionContext.toLookupElements(closingDelimiter: Char?): List<LookupElement> =
    when (kind) {
        HostEventPluginCompletionContext.Kind.EVENT -> EVENT_PLUGIN_EVENT_COMPLETIONS
        HostEventPluginCompletionContext.Kind.MODIFIER ->
            EVENT_PLUGIN_MODIFIER_COMPLETIONS.filterNot { completion ->
                completion.identity in usedModifierIdentities
            }
    }.asSequence()
        .filter { completion -> completion.source.startsWith(prefix, ignoreCase = true) }
        .map { completion -> completion.toLookupElement(closingDelimiter) }
        .toList()

private fun EventPluginCompletion.toLookupElement(closingDelimiter: Char?): LookupElement {
    val modifierDescription = EventPluginModifier.parse(source)?.description

    return LookupElementBuilder
        .create(source)
        .withPresentableText(presentableText)
        .withTypeText(typeText, true)
        .let { element ->
            closingDelimiter
                ?.let { delimiter -> element.withInsertHandler(hostClosingDelimiterDeduplicationHandler(delimiter)) }
                ?: element
        }.let { element ->
            (description ?: modifierDescription)
                ?.let { text -> element.withTailText(" — $text", true) }
                ?: element
        }
}

private fun EventPluginModifier.completionIdentity(): String =
    when {
        source == "silent" -> "zoneless"
        source.startsWith("debounce~") -> "debounce"
        source.startsWith("throttle~") -> "throttle"
        else -> source
    }

private fun Char.isCompletionTrigger(): Boolean =
    this == '.' || this == '>'

private val EVENT_PLUGIN_EVENT_COMPLETIONS =
    listOf(
        EventPluginCompletion(
            source = "longtap",
            typeText = "Taiga UI event",
            description = "Fires for a long press or context-menu gesture.",
        ),
        EventPluginCompletion(
            source = "resize",
            typeText = "Taiga UI event",
            description = "Fires when the element dimensions change using ResizeObserver.",
        ),
        EventPluginCompletion(
            source = "visualViewport",
            typeText = "Taiga UI global event target",
            description = "Targets the browser VisualViewport when followed by '>'.",
        ),
    )

private val EVENT_PLUGIN_MODIFIER_COMPLETIONS =
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

private const val HOST_BINDING_COMPLETION_SCAN_LENGTH = 200

private val STANDARD_HTML_HOST_COMPLETIONS_KEY =
    Key.create<StandardHtmlHostCompletions>("taiga.ui.standard.html.host.completions")

private val HOST_COMPLETION_CARET_OFFSET =
    Key.create<Int>("taiga.ui.event.plugins.host.completion.caret.offset")
