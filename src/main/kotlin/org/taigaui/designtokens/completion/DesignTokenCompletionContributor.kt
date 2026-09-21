package org.taigaui.designtokens.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.css.CSSLanguage
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import org.taigaui.designtokens.project.DesignTokenCatalogEntry
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
        val request = parameters.toDesignTokenCompletionRequest() ?: return

        request.project.service<DesignTokenCompletionPreviewController>().ensureAttached()

        val entries =
            request.project
                .service<DesignTokenCompletionService>()
                .entriesFor(request.sourceFile, request::scheduleRefresh)
                .orEmpty()
        val matchingResult = result.withPrefixMatcher(request.prefix)

        entries.forEach { entry ->
            matchingResult.addElement(entry.toLookupElement())
        }
    }
}

private fun DesignTokenCatalogEntry.toLookupElement(): LookupElementBuilder {
    val base =
        LookupElementBuilder
            .create(name)
            .withTypeText(COMPLETION_TYPE_TEXT, true)
            .withInsertHandler { insertionContext, _ ->
                removeExistingTokenSuffix(insertionContext)
            }

    return if (deprecation == null) {
        base
    } else {
        base
            .withStrikeoutness(true)
            .withTailText(" (deprecated)", true)
    }
}

private fun CompletionParameters.toDesignTokenCompletionRequest(): DesignTokenCompletionRequest? =
    position
        .language
        .takeIf { language -> language.isKindOf(CSSLanguage.INSTANCE) }
        ?.let {
            editor.designTokenSourceFilePath()?.let { sourceFile ->
                DesignTokenCompletionContextFinder
                    .find(
                        text = editor.document.immutableCharSequence,
                        offset = editor.caretModel.offset,
                    )?.let { completionContext ->
                        DesignTokenCompletionRequest(
                            project = originalFile.project,
                            editor = editor,
                            sourceFile = sourceFile,
                            prefix = completionContext.prefix,
                        )
                    }
            }
        }

private fun Editor.designTokenSourceFilePath(): Path? {
    val sourceDocument = (document as? DocumentWindow)?.delegate ?: document

    return FileDocumentManager
        .getInstance()
        .getFile(sourceDocument)
        ?.path
        ?.let(::pathOrNull)
}

private data class DesignTokenCompletionRequest(
    val project: Project,
    val editor: Editor,
    val sourceFile: Path,
    val prefix: String,
) {
    fun scheduleRefresh() {
        val currentContext =
            if (editor.isDisposed) {
                null
            } else {
                DesignTokenCompletionContextFinder.find(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                )
            }

        if (currentContext != null) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
    }
}

private fun removeExistingTokenSuffix(context: InsertionContext) {
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

internal fun pathOrNull(value: String): Path? = runCatching { Path.of(value) }.getOrNull()

private const val COMPLETION_TYPE_TEXT = "Taiga UI design token"
