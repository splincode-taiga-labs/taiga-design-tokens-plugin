package org.taigaui.designtokens.icons

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupArranger
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

class IconCompletionAutoPopupHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        if (file.toSupportedSourceFile() == null || !charTyped.isIconNameCharacter()) {
            return Result.CONTINUE
        }

        val completionContext =
            IconCompletionContextFinder.findAfterTyping(
                text = editor.document.immutableCharSequence,
                offset = editor.caretModel.offset,
                charTyped = charTyped,
            )

        // Prevent the generic HTML/Angular completion from being scheduled for
        // @tui.*. The dedicated icon lookup is opened from charTyped after the
        // character is actually present in the document.
        return if (completionContext == null) Result.CONTINUE else Result.STOP
    }

    override fun charTyped(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile,
    ): Result {
        val sourceFile = file.toSupportedSourceFile()
        val completionContext =
            if (sourceFile != null && charTyped.isIconNameCharacter()) {
                IconCompletionContextFinder.find(
                    text = editor.document.immutableCharSequence,
                    offset = editor.caretModel.offset,
                )
            } else {
                null
            }

        if (sourceFile == null || completionContext == null) {
            return Result.CONTINUE
        }

        project.service<IconCompletionAutoPopupScheduler>().schedule(editor, sourceFile)

        // The character is already in the document. Stop the remaining post-typing
        // delegates only for a confirmed Taiga UI icon reference.
        return Result.STOP
    }
}

@Service(Service.Level.PROJECT)
internal class IconCompletionAutoPopupScheduler(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    private val pending = mutableMapOf<Editor, Job>()

    fun schedule(
        editor: Editor,
        sourceFile: Path,
    ) {
        pending.remove(editor)?.cancel()

        if (editor.isDisposed || LookupManager.getActiveLookup(editor)?.containsIconSuggestions() == true) {
            return
        }

        pending[editor] =
            coroutineScope.launch(Dispatchers.EDT + CoroutineName("Taiga UI icon completion debounce")) {
                delay(ICON_COMPLETION_DEBOUNCE)
                pending.remove(editor)

                if (
                    !project.isDisposed &&
                    !editor.isDisposed &&
                    IconCompletionContextFinder.find(
                        text = editor.document.immutableCharSequence,
                        offset = editor.caretModel.offset,
                    ) != null
                ) {
                    requestIconCompletion(project, editor, sourceFile)
                }
            }
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
                    createIconLookupElement(
                        name = name,
                        removeSuffixOnInsert = false,
                    )
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

private val ICON_COMPLETION_DEBOUNCE = 120.milliseconds
