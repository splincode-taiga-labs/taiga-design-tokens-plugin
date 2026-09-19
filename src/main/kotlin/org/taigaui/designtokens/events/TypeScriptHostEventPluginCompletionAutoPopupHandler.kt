package org.taigaui.designtokens.events

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.util.Alarm

class TypeScriptHostEventPluginCompletionAutoPopupHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        editor.hostCompletionAlarm(project).cancelAllRequests()
        editor.putUserData(HOST_COMPLETION_CARET_OFFSET, null)

        if (!charTyped.isCompletionTrigger() || LookupManager.getActiveLookup(editor) != null) {
            return Result.CONTINUE
        }

        val caretOffset = editor.caretModel.offset
        val isInsideHost =
            AngularHostBindingSupport.isInsideHostProperty(
                file,
                (caretOffset - 1).coerceAtLeast(0),
            )

        if (!isInsideHost) {
            return Result.CONTINUE
        }

        editor.putUserData(HOST_COMPLETION_CARET_OFFSET, caretOffset)

        return Result.STOP
    }

    override fun charTyped(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        val caretOffsetBeforeTyping = editor.getUserData(HOST_COMPLETION_CARET_OFFSET)

        editor.putUserData(HOST_COMPLETION_CARET_OFFSET, null)

        if (
            caretOffsetBeforeTyping == null ||
            !charTyped.isCompletionTrigger() ||
            editor.caretModel.offset != caretOffsetBeforeTyping + 1
        ) {
            return Result.CONTINUE
        }

        val expectedCaretOffset = editor.caretModel.offset
        val alarm = editor.hostCompletionAlarm(project)

        alarm.cancelAllRequests()
        alarm.addRequest(
            {
                if (
                    project.isDisposed ||
                    editor.caretModel.offset != expectedCaretOffset ||
                    LookupManager.getActiveLookup(editor) != null
                ) {
                    return@addRequest
                }

                PsiDocumentManager.getInstance(project).commitDocument(editor.document)

                if (
                    !AngularHostBindingSupport.isInsideHostProperty(
                        file,
                        (expectedCaretOffset - 1).coerceAtLeast(0),
                    )
                ) {
                    return@addRequest
                }

                CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true)
                    .invokeCompletion(project, editor)
            },
            HOST_COMPLETION_DEBOUNCE_MS,
        )

        return Result.CONTINUE
    }

    private fun Editor.hostCompletionAlarm(project: Project): Alarm =
        getUserData(HOST_COMPLETION_ALARM)
            ?: Alarm(Alarm.ThreadToUse.SWING_THREAD, project).also { alarm ->
                putUserData(HOST_COMPLETION_ALARM, alarm)
            }

    private fun Char.isCompletionTrigger(): Boolean =
        isLetterOrDigit() || this == '.' || this == '>' || this == '-' || this == '_'
}

private const val HOST_COMPLETION_DEBOUNCE_MS = 150

private val HOST_COMPLETION_CARET_OFFSET =
    Key.create<Int>("taiga.ui.event.plugins.host.completion.caret.offset")

private val HOST_COMPLETION_ALARM =
    Key.create<Alarm>("taiga.ui.event.plugins.host.completion.alarm")
