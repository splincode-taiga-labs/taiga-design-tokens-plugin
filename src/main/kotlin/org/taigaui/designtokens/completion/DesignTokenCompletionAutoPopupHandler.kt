package org.taigaui.designtokens.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupManager
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
        val shouldSchedule =
            charTyped.isTokenNameCharacter() &&
                file.virtualFile?.extension?.lowercase() in SUPPORTED_EXTENSIONS &&
                LookupManager.getInstance(project).activeLookup == null &&
                DesignTokenCompletionContextFinder.find(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                ) != null

        if (shouldSchedule) {
            AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }

        return Result.CONTINUE
    }
}

internal fun Char.isTokenNameCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_'

internal val SUPPORTED_EXTENSIONS = setOf("css", "less", "scss")
