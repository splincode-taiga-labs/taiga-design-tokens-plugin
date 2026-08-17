package org.taigaui.designtokens.icons

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
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

        if (
            completionContext != null &&
            charTyped.isIconNameCharacter() &&
            LookupManager.getInstance(project).activeLookup == null
        ) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }

        return Result.CONTINUE
    }
}
