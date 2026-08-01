package org.taigaui.designtokens.documentation

import com.intellij.model.Pointer
import com.intellij.openapi.components.service
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import org.taigaui.designtokens.project.DesignTokenIndexService
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import java.nio.file.Path

class DesignTokenDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(
        file: PsiFile,
        offset: Int,
    ): List<DocumentationTarget> {
        if (!file.isSupportedStylesheet()) {
            return emptyList()
        }

        val reference = DesignTokenReferenceAtOffsetFinder.find(file.text, offset) ?: return emptyList()
        val sourceFile = file.virtualFile?.path?.toPathOrNull() ?: return emptyList()
        val groups =
            file.project
                .service<DesignTokenIndexService>()
                .resolveToken(sourceFile, reference.name)

        return groups
            .takeIf(List<*>::isNotEmpty)
            ?.let { listOf(DesignTokenDocumentationTarget(reference.name, it)) }
            .orEmpty()
    }

    private fun PsiFile.isSupportedStylesheet(): Boolean =
        virtualFile
            ?.extension
            ?.lowercase() in SUPPORTED_EXTENSIONS

    private fun String.toPathOrNull(): Path? = runCatching { Path.of(this) }.getOrNull()

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("css", "less", "scss")
    }
}

internal class DesignTokenDocumentationTarget(
    private val tokenName: String,
    private val groups: List<DesignTokenResolutionGroup>,
) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)

    override fun computePresentation(): TargetPresentation =
        TargetPresentation
            .builder(tokenName)
            .presentableText(tokenName)
            .presentation()

    override fun computeDocumentationHint(): @Nls String =
        DesignTokenDocumentationHtmlRenderer.renderHint(tokenName, groups)

    override fun computeDocumentation(): DocumentationResult =
        DocumentationResult.documentation(
            DesignTokenDocumentationHtmlRenderer.render(tokenName, groups),
        )
}
