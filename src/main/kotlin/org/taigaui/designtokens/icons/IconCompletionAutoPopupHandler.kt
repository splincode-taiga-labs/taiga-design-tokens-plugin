package org.taigaui.designtokens.icons

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class IconCompletionAutoPopupHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        val supportedFile = file.virtualFile?.extension?.lowercase() in ICON_SUPPORTED_EXTENSIONS
        val completionContext =
            if (supportedFile) {
                IconCompletionContextFinder.findAfterTyping(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                    charTyped = charTyped,
                )
            } else {
                null
            }

        return if (completionContext != null) {
            restartIconCompletionIfNeeded(project, editor)
            Result.STOP
        } else {
            Result.CONTINUE
        }
    }
}

internal fun restartIconCompletionIfNeeded(
    project: Project,
    editor: Editor,
    force: Boolean = false,
) {
    val activeLookup = LookupManager.getActiveLookup(editor)

    if (!force && activeLookup?.containsIconSuggestions() == true) {
        return
    }

    if (activeLookup == null) {
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return
    }

    activeLookup.hideLookup(true)

    ApplicationManager.getApplication().invokeLater {
        if (
            !project.isDisposed &&
            !editor.isDisposed &&
            IconCompletionContextFinder.find(
                text = editor.document.immutableCharSequence,
                offset = editor.caretModel.offset,
            ) != null
        ) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
    }
}

private fun Lookup.containsIconSuggestions(): Boolean = items.any { item -> item.lookupString.startsWith(ICON_PREFIX) }
