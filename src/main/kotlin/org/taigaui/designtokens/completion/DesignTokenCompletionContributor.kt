package org.taigaui.designtokens.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import java.nio.file.Path

class DesignTokenCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            DesignTokenCompletionProvider(),
        )
    }
}

private class DesignTokenCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val editor = parameters.editor
        val virtualFile = parameters.originalFile.virtualFile ?: return

        if (virtualFile.extension?.lowercase() !in SUPPORTED_EXTENSIONS) {
            return
        }

        val document = editor.document
        val completionContext =
            DesignTokenCompletionContextFinder.find(
                text = document.immutableCharSequence,
                offset = editor.caretModel.offset,
            ) ?: return
        val sourceFile = runCatching { Path.of(virtualFile.path) }.getOrNull() ?: return
        val project = parameters.originalFile.project
        val modificationStamp = document.modificationStamp
        val names =
            project
                .service<DesignTokenCompletionService>()
                .namesFor(sourceFile) {
                    if (!editor.isDisposed && document.modificationStamp == modificationStamp) {
                        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
                    }
                } ?: return
        val matchingResult = result.withPrefixMatcher(completionContext.prefix)

        names.forEach { name ->
            matchingResult.addElement(
                LookupElementBuilder
                    .create(name)
                    .withTypeText(COMPLETION_TYPE_TEXT, true)
                    .withInsertHandler(::removeExistingTokenSuffix),
            )
        }
    }
}

private fun removeExistingTokenSuffix(
    context: InsertionContext,
    element: LookupElement,
) {
    val document = context.document
    val text = document.charsSequence
    var suffixEnd = context.tailOffset

    while (suffixEnd < text.length && text[suffixEnd].isTokenNameCharacter()) {
        suffixEnd++
    }

    if (suffixEnd > context.tailOffset) {
        document.deleteString(context.tailOffset, suffixEnd)
    }
}

private fun Char.isTokenNameCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_'

private val SUPPORTED_EXTENSIONS = setOf("css", "less", "scss")
private const val COMPLETION_TYPE_TEXT = "Taiga UI design token"
