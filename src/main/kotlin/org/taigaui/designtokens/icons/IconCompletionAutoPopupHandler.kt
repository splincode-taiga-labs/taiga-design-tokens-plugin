package org.taigaui.designtokens.icons

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
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
                IconCompletionContextFinder.find(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                )
            } else {
                null
            }

        if (completionContext != null && charTyped.isIconNameCharacter()) {
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
    val activeLookup = LookupManager.getActiveLookup(editor)
    val needsRestart = force || activeLookup?.containsIconSuggestions() != true

    if (needsRestart) {
        activeLookup?.hideLookup(true)
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
    }
}

private fun Lookup.containsIconSuggestions(): Boolean =
    items.any { item -> item.lookupString.startsWith(ICON_PREFIX) }
