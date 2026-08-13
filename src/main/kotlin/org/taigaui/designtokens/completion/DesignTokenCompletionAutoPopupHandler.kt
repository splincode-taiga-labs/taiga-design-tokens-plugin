package org.taigaui.designtokens.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

class DesignTokenCompletionAutoPopupHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        val completionContext =
            DesignTokenCompletionContextFinder.find(
                text = editor.document.immutableCharSequence,
                offset = editor.caretModel.offset,
            )
        val supportedContext =
            file.virtualFile?.extension?.lowercase() in SUPPORTED_EXTENSIONS && completionContext != null

        if (supportedContext) {
            project.service<DesignTokenCompletionPreviewController>()

            if (charTyped.isTokenNameCharacter() && LookupManager.getInstance(project).activeLookup == null) {
                AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
            }
        }

        return Result.CONTINUE
    }
}

internal fun Char.isTokenNameCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_'

internal val SUPPORTED_EXTENSIONS = setOf("css", "less", "scss")
