package org.taigaui.designtokens.icons

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class IconCompletionAutoPopupHandler : TypedHandlerDelegate() {
    override fun charTyped(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        val supportedFile = file.virtualFile?.extension?.lowercase() in ICON_SUPPORTED_EXTENSIONS
        val completionContext =
            if (supportedFile && charTyped.isIconNameCharacter()) {
                IconCompletionContextFinder.find(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                )
            } else {
                null
            }

        if (completionContext != null) {
            restartIconCompletionIfNeeded(project, editor)
        }

        return Result.CONTINUE
    }
}

internal fun restartIconCompletionIfNeeded(
    project: Project,
    editor: Editor,
    force: Boolean = false,
) {
    if (!force && LookupManager.getActiveLookup(editor)?.containsIconSuggestions() == true) {
        return
    }

    ApplicationManager.getApplication().invokeLater {
        if (project.isDisposed || editor.isDisposed) {
            return@invokeLater
        }

        val completionContext =
            IconCompletionContextFinder.find(
                text = editor.document.immutableCharSequence,
                offset = editor.caretModel.offset,
            )

        if (completionContext == null) {
            return@invokeLater
        }

        if (!force && LookupManager.getActiveLookup(editor)?.containsIconSuggestions() == true) {
            return@invokeLater
        }

        CodeCompletionHandlerBase
            .createHandler(
                CompletionType.BASIC,
                false,
                true,
                false,
            ).invokeCompletion(project, editor, 0)
    }
}

private fun Lookup.containsIconSuggestions(): Boolean = items.any { item -> item.lookupString.startsWith(ICON_PREFIX) }
