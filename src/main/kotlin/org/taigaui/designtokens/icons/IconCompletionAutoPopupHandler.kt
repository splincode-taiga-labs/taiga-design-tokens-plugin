package org.taigaui.designtokens.icons

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManager
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

private fun requestIconCompletion(
    project: Project,
    editor: Editor,
    sourceFile: Path,
) {
    val service = project.service<IconCompletionService>()
    val names =
        service.namesFor(sourceFile) {
            restartIconCompletionIfNeeded(project, editor, force = true)
        }

    if (!names.isNullOrEmpty()) {
        restartIconCompletionIfNeeded(project, editor)
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

        val activeLookup = LookupManager.getActiveLookup(editor)

        if (!force && activeLookup?.containsIconSuggestions() == true) {
            return@invokeLater
        }

        activeLookup?.hideLookup(true)

        CodeCompletionHandlerBase
            .createHandler(
                CompletionType.BASIC,
                true,
                false,
                false,
            ).invokeCompletion(project, editor, 1)
    }
}

private fun PsiFile.toSupportedSourceFile(): Path? =
    virtualFile
        ?.takeIf { file -> file.extension?.lowercase() in ICON_SUPPORTED_EXTENSIONS }
        ?.path
        ?.let { path -> runCatching { Path.of(path) }.getOrNull() }

private fun Lookup.containsIconSuggestions(): Boolean = items.any { item -> item.lookupString.startsWith(ICON_PREFIX) }
