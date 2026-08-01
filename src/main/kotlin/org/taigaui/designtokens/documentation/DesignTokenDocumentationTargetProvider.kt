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
        val request = file.documentationRequest(offset)
        val groups =
            request?.let { documentationRequest ->
                file.project
                    .service<DesignTokenIndexService>()
                    .resolveToken(documentationRequest.sourceFile, documentationRequest.tokenName)
            }

        return if (request != null && !groups.isNullOrEmpty()) {
            listOf(DesignTokenDocumentationTarget(request.tokenName, groups))
        } else {
            emptyList()
        }
    }

    private fun PsiFile.documentationRequest(offset: Int): DocumentationRequest? =
        takeIf(PsiFile::isSupportedStylesheet)
            ?.let { psiFile ->
                DesignTokenReferenceAtOffsetFinder
                    .find(psiFile.text, offset)
                    ?.let { reference ->
                        psiFile.virtualFile
                            ?.path
                            ?.toPathOrNull()
                            ?.let { sourceFile ->
                                DocumentationRequest(reference.name, sourceFile)
                            }
                    }
            }

    private fun PsiFile.isSupportedStylesheet(): Boolean =
        virtualFile
            ?.extension
            ?.lowercase()
            ?.let(SUPPORTED_EXTENSIONS::contains) == true

    private fun String.toPathOrNull(): Path? = runCatching { Path.of(this) }.getOrNull()

    private data class DocumentationRequest(
        val tokenName: String,
        val sourceFile: Path,
    )

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
