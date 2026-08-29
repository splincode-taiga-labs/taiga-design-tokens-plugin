package org.taigaui.designtokens.units

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile

internal class RemDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(
        psiFile: PsiFile,
        offset: Int,
    ): List<DocumentationTarget> {
        if (psiFile.virtualFile.extension?.lowercase() !in SUPPORTED_EXTENSIONS) {
            return emptyList()
        }

        val value = RemValueAtOffsetFinder.find(psiFile.text, offset) ?: return emptyList()

        return listOf(RemDocumentationTarget(value.presentation()))
    }
}

private class RemDocumentationTarget(
    private val presentation: String,
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation =
        TargetPresentation
            .builder(presentation)
            .presentation()

    override fun computeDocumentationHint(): String = presentation

    override fun computeDocumentation(): DocumentationResult =
        DocumentationResult.documentation("<html><body><code>$presentation</code></body></html>")
}

private val SUPPORTED_EXTENSIONS = setOf("css", "less", "scss")
