package org.taigaui.designtokens.icons

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
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

        // Once the caret is inside a confirmed @tui.* reference, this contributor
        // owns manual BASIC completion. Automatic completion uses a dedicated native
        // lookup so it does not depend on the Angular/HTML completion lifecycle.
        result.stopHere()

        request.project.service<IconCompletionPreviewController>().ensureAttached()

        val names =
            request.project
                .service<IconCompletionService>()
                .namesFor(request.sourceFile, request::scheduleRefresh)
                ?: return
        val matchingResult = result.withPrefixMatcher(request.prefix)

        names.forEach { name ->
            matchingResult.addElement(createIconLookupElement(name))
        }
    }
}

internal fun createIconLookupElement(
    name: String,
    removeSuffixOnInsert: Boolean = true,
): LookupElement {
    var builder =
        LookupElementBuilder
            .create(IconCompletionLookupItem(name), name)
            .withTypeText(COMPLETION_TYPE_TEXT, true)

    if (removeSuffixOnInsert) {
        builder =
            builder.withInsertHandler { insertionContext, _ ->
                removeExistingIconSuffix(insertionContext)
            }
    }

    return IconCompletionLookupElement(builder)
}

private class IconCompletionLookupElement(
    delegate: LookupElement,
) : LookupElementDecorator<LookupElement>(delegate) {
    override fun <T> getUserData(key: Key<T>): T? {
        if (key.toString() in NATIVE_DOCUMENTATION_SUPPRESSION_KEYS) {
            @Suppress("UNCHECKED_CAST")
            return true as T
        }

        return super.getUserData(key)
    }
}

private class IconCompletionLookupItem(
    val name: String,
)

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
            requestIconCompletion(project, editor, sourceFile)
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

private val NATIVE_DOCUMENTATION_SUPPRESSION_KEYS =
    setOf(
        "LookupManagerImpl.suppressAutopopupJavadoc",
        "lookup.suppress.quick.documentation",
    )

private const val COMPLETION_TYPE_TEXT = "Taiga UI icon"
