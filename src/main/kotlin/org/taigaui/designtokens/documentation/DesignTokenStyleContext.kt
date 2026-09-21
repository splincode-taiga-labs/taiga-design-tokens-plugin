package org.taigaui.designtokens.documentation

import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import java.nio.file.Path

internal data class DesignTokenStyleTextContext(
    val text: CharSequence,
    val offset: Int,
)

internal fun Editor.designTokenStyleTextContext(offset: Int): DesignTokenStyleTextContext? {
    val physicalFile = FileDocumentManager.getInstance().getFile(document)

    if (physicalFile?.extension?.lowercase() in DESIGN_TOKEN_STYLE_EXTENSIONS) {
        return DesignTokenStyleTextContext(
            text = document.immutableCharSequence,
            offset = offset,
        )
    }

    val currentProject = project ?: return null
    val psiDocumentManager = PsiDocumentManager.getInstance(currentProject)
    val hostFile = psiDocumentManager.getPsiFile(document) ?: return null
    val injectionManager = InjectedLanguageManager.getInstance(currentProject)
    val injectedFile =
        injectionManager
            .findInjectedElementAt(hostFile, offset)
            ?.containingFile
            ?.takeIf { file -> file.language.isKindOf(CSSLanguage.INSTANCE) }
            ?: offset
                .takeIf { currentOffset -> currentOffset > 0 }
                ?.let { currentOffset -> injectionManager.findInjectedElementAt(hostFile, currentOffset - 1) }
                ?.containingFile
                ?.takeIf { file -> file.language.isKindOf(CSSLanguage.INSTANCE) }
            ?: return null
    val injectedDocument = psiDocumentManager.getDocument(injectedFile) as? DocumentWindow ?: return null

    return DesignTokenStyleTextContext(
        text = injectedDocument.immutableCharSequence,
        offset = injectedDocument.hostToInjected(offset),
    )
}

internal fun Editor.isDesignTokenStyleContext(offset: Int): Boolean = designTokenStyleTextContext(offset) != null

internal fun Editor.designTokenSourceFile(): Path? =
    FileDocumentManager
        .getInstance()
        .getFile(document)
        ?.path
        ?.let { path -> runCatching { Path.of(path) }.getOrNull() }

internal fun CharSequence.hasDesignTokenNear(offset: Int): Boolean {
    val start = (offset - DESIGN_TOKEN_CONTEXT_RADIUS).coerceAtLeast(0)
    val end = (offset + DESIGN_TOKEN_CONTEXT_RADIUS).coerceAtMost(length)

    return subSequence(start, end).contains(DESIGN_TOKEN_MARKER)
}

private val DESIGN_TOKEN_STYLE_EXTENSIONS = setOf("css", "less", "scss")
private const val DESIGN_TOKEN_CONTEXT_RADIUS = 64
private const val DESIGN_TOKEN_MARKER = "--tui-"
