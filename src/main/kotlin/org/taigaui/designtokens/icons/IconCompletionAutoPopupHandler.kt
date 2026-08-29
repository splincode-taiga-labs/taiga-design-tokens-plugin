package org.taigaui.designtokens.icons

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import java.nio.file.Path

class IconCompletionAutoPopupHandler : TypedHandlerDelegate() {
    override fun charTyped(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        val sourceFile = file.toSupportedSourceFile() ?: return Result.CONTINUE
        val completionContext =
            if (charTyped.isIconNameCharacter()) {
                IconCompletionContextFinder.find(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                )
            } else {
                null
            }

        if (completionContext != null) {
            requestIconCompletion(project, editor, sourceFile)
        }

        return Result.CONTINUE
    }
}

internal fun requestIconCompletion(
    project: Project,
    editor: Editor,
    sourceFile: Path,
) {
    if (LookupManager.getActiveLookup(editor)?.containsIconSuggestions() == true) {
        return
    }

    val service = project.service<IconCompletionService>()
    val names =
        service.namesFor(sourceFile) {
            requestIconCompletion(project, editor, sourceFile)
        }

    if (names.isNullOrEmpty()) {
        return
    }

    showIconLookup(project, editor, names)
}

private fun showIconLookup(
    project: Project,
    editor: Editor,
    names: List<String>,
) {
    ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed || editor.isDisposed) {
            return@invokeLater
        }

        val completionContext =
            IconCompletionContextFinder.find(
                text = editor.document.immutableCharSequence,
                offset = editor.caretModel.offset,
            ) ?: return@invokeLater
        val activeLookup = LookupManager.getActiveLookup(editor)

        if (activeLookup?.containsIconSuggestions() == true) {
            return@invokeLater
        }

        activeLookup?.hideLookup(true)

        val items =
            names
                .asSequence()
                .filter { name -> name.startsWith(completionContext.prefix) }
                .map { name ->
                    LookupElementBuilder
                        .create(name)
                        .withTypeText(COMPLETION_TYPE_TEXT, true)
                }.toList()

        if (items.isEmpty()) {
            return@invokeLater
        }

        val arranger =
            object : LookupArranger.DefaultArranger() {
                override fun isCompletion(): Boolean = true
            }
        val lookup =
            LookupManager
                .getInstance(project)
                .showLookup(
                    editor,
                    items.toTypedArray(),
                    completionContext.prefix,
                    arranger,
                ) ?: return@invokeLater

        lookup.addLookupListener(
            object : LookupListener {
                override fun itemSelected(event: LookupEvent) {
                    if (event.item?.lookupString?.startsWith(ICON_PREFIX) == true) {
                        removeExistingIconSuffix(editor)
                    }
                }
            },
        )

        project.service<IconCompletionPreviewController>().ensureAttached()
    }
}

private fun removeExistingIconSuffix(editor: Editor) {
    if (editor.isDisposed) {
        return
    }

    ApplicationManager.getApplication().runWriteAction {
        val document = editor.document
        val text = document.charsSequence
        val start = editor.caretModel.offset
        var end = start

        while (end < text.length && text[end].isIconNameCharacter()) {
            end++
        }

        if (end > start) {
            document.deleteString(start, end)
        }
    }
}

private fun PsiFile.toSupportedSourceFile(): Path? =
    virtualFile
        ?.takeIf { file -> file.extension?.lowercase() in ICON_SUPPORTED_EXTENSIONS }
        ?.path
        ?.let { path -> runCatching { Path.of(path) }.getOrNull() }

private fun Lookup.containsIconSuggestions(): Boolean = items.any { item -> item.lookupString.startsWith(ICON_PREFIX) }

private const val COMPLETION_TYPE_TEXT = "Taiga UI icon"
