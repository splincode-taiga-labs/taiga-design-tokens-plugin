package org.taigaui.designtokens.documentation

import com.intellij.lang.css.CSSLanguage
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import java.nio.file.Path

internal fun Editor.isDesignTokenStyleContext(offset: Int): Boolean {
    val physicalFile = FileDocumentManager.getInstance().getFile(document)

    return physicalFile?.extension?.lowercase() in DESIGN_TOKEN_STYLE_EXTENSIONS ||
        project
            ?.let { currentProject ->
                PsiDocumentManager
                    .getInstance(currentProject)
                    .getPsiFile(document)
                    ?.let { hostFile ->
                        InjectedLanguageManager
                            .getInstance(currentProject)
                            .findInjectedElementAt(hostFile, offset)
                    }?.language
                    ?.isKindOf(CSSLanguage.INSTANCE)
            } == true
}

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
