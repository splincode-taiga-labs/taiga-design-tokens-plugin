package org.taigaui.designtokens.icons

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext
import java.nio.file.Path

class IconCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            IconCompletionProvider(),
        )
    }
}

private class IconCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val text = parameters.editor.document.immutableCharSequence
        val offset = parameters.editor.caretModel.offset
        val partialPrefix = IconCompletionContextFinder.findPartialPrefix(text, offset)

        if (partialPrefix != null) {
            result
                .withPrefixMatcher(partialPrefix)
                .restartCompletionOnPrefixChange(ICON_PREFIX)
        }

        val request = parameters.toIconCompletionRequest() ?: return

        request.project.service<IconCompletionPreviewController>().ensureAttached()

        val names =
            request.project
                .service<IconCompletionService>()
                .namesFor(request.sourceFile, request::scheduleRefresh)
                ?: return
        val matchingResult = result.withPrefixMatcher(request.prefix)

        names.forEach { name ->
            matchingResult.addElement(
                LookupElementBuilder
                    .create(name)
                    .withTypeText(COMPLETION_TYPE_TEXT, true)
                    .withInsertHandler { insertionContext, _ ->
                        removeExistingIconSuffix(insertionContext)
                    },
            )
        }

        // Inside a confirmed @tui.* string the icon catalog is the complete source
        // of suggestions. Do not continue into Angular/HTML contributors: in real
        // WebStorm projects they can keep the lookup in the "calculating" state
        // even after all Taiga UI icon items have already been produced.
        if (names.isNotEmpty()) {
            result.stopHere()
        }
    }
}

private fun CompletionParameters.toIconCompletionRequest(): IconCompletionRequest? =
    originalFile.virtualFile
        ?.takeIf { file -> file.extension?.lowercase() in ICON_SUPPORTED_EXTENSIONS }
        ?.path
        ?.let(::pathOrNull)
        ?.let { sourceFile ->
            IconCompletionContextFinder
                .find(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                )?.let { completionContext ->
                    IconCompletionRequest(
                        project = originalFile.project,
                        editor = editor,
                        sourceFile = sourceFile,
                        prefix = completionContext.prefix,
                    )
                }
        }

private data class IconCompletionRequest(
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
                IconCompletionContextFinder.find(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                )
            }

        if (currentContext != null) {
            restartIconCompletionIfNeeded(project, editor, force = true)
        }
    }
}

private fun removeExistingIconSuffix(context: InsertionContext) {
    val document = context.document
    val text = document.charsSequence
    var suffixEnd = context.tailOffset

    while (suffixEnd < text.length && text[suffixEnd].isIconNameCharacter()) {
        suffixEnd++
    }

    if (suffixEnd > context.tailOffset) {
        document.deleteString(context.tailOffset, suffixEnd)
    }
}

private fun pathOrNull(value: String): Path? = runCatching { Path.of(value) }.getOrNull()

private const val COMPLETION_TYPE_TEXT = "Taiga UI icon"
