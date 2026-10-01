package org.taigaui.designtokens.navigation

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.ResolveResult
import com.intellij.util.ProcessingContext
import org.taigaui.designtokens.documentation.DesignTokenReferenceAtOffsetFinder
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.project.DesignTokenIndexService
import java.nio.file.Path

class DesignTokenReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            DesignTokenReferenceProvider(),
        )
    }
}

internal class DesignTokenReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(
        element: PsiElement,
        context: ProcessingContext,
    ): Array<PsiReference> =
        element
            .takeIf { candidate ->
                candidate.firstChild == null &&
                    DESIGN_TOKEN_NAME.containsMatchIn(candidate.text)
            }?.referencesForDesignTokens()
            ?: PsiReference.EMPTY_ARRAY
}

private class DesignTokenPsiReference(
    element: PsiElement,
    range: TextRange,
    private val tokenName: String,
    private val origins: List<DesignTokenOrigin>,
) : PsiPolyVariantReferenceBase<PsiElement>(element, range, false) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        origins
            .mapNotNull { origin ->
                origin
                    .toPsiTarget(element.project, tokenName)
                    ?.let(::PsiElementResolveResult)
            }.distinctBy { result ->
                val target = result.element

                target.containingFile?.virtualFile?.path to target.textRange
            }.toTypedArray()

    override fun handleElementRename(newElementName: String): PsiElement = element
}

private fun PsiElement.referencesForDesignTokens(): Array<PsiReference> {
    val sourceFile = topLevelSourceFile() ?: return PsiReference.EMPTY_ARRAY
    val indexService = project.service<DesignTokenIndexService>()

    return DESIGN_TOKEN_NAME
        .findAll(text)
        .mapNotNull { match ->
            match
                .takeIf(::isVarReference)
                ?.let { validMatch ->
                    indexService
                        .navigationOriginsIfCached(sourceFile, validMatch.value)
                        ?.takeIf(List<DesignTokenOrigin>::isNotEmpty)
                        ?.let { origins ->
                            DesignTokenPsiReference(
                                element = this,
                                range =
                                    TextRange(
                                        validMatch.range.first,
                                        validMatch.range.last + 1,
                                    ),
                                tokenName = validMatch.value,
                                origins = origins,
                            )
                        }
                }
        }.toList()
        .toTypedArray()
}

private fun PsiElement.topLevelSourceFile(): Path? =
    InjectedLanguageManager
        .getInstance(project)
        .getTopLevelFile(this)
        .virtualFile
        ?.path
        ?.let(::pathOrNull)

private fun PsiElement.isVarReference(match: MatchResult): Boolean {
    val absoluteStart = textRange.startOffset + match.range.first
    val absoluteEnd = textRange.startOffset + match.range.last + 1

    return generateSequence(this) { current -> current.parent }
        .take(MAX_CONTEXT_DEPTH)
        .takeWhile { current -> current !is PsiFile }
        .any { current ->
            val localOffset = absoluteStart - current.textRange.startOffset
            val reference =
                localOffset
                    .takeIf { offset -> offset in 0 until current.textLength }
                    ?.let { offset ->
                        DesignTokenReferenceAtOffsetFinder.find(
                            current.text,
                            offset,
                        )
                    }

            reference != null &&
                reference.name == match.value &&
                current.textRange.startOffset + reference.startOffset == absoluteStart &&
                current.textRange.startOffset + reference.endOffset == absoluteEnd
        }
}

private fun DesignTokenOrigin.toPsiTarget(
    project: Project,
    tokenName: String,
): PsiElement? =
    LocalFileSystem
        .getInstance()
        .findFileByNioFile(sourceFile)
        ?.let { virtualFile -> PsiManager.getInstance(project).findFile(virtualFile) }
        ?.let { file ->
            PsiDocumentManager
                .getInstance(project)
                .getDocument(file)
                ?.takeIf { document -> document.lineCount > 0 }
                ?.let { document ->
                    val lineIndex = (line - 1).coerceIn(0, document.lineCount - 1)
                    val lineStart = document.getLineStartOffset(lineIndex)
                    val lineEnd = document.getLineEndOffset(lineIndex)

                    document.charsSequence
                        .indexOf(tokenName, startIndex = lineStart)
                        .takeIf { offset -> offset in lineStart until lineEnd }
                        ?.let(file::findElementAt)
                }
        }?.let { leaf ->
            generateSequence(leaf) { current -> current.parent }
                .filterIsInstance<PsiNamedElement>()
                .firstOrNull { candidate -> candidate.name == tokenName }
                ?: leaf
        }

private fun pathOrNull(value: String): Path? = runCatching { Path.of(value) }.getOrNull()

private val DESIGN_TOKEN_NAME = Regex("--tui-[A-Za-z0-9_-]+")
private const val MAX_CONTEXT_DEPTH = 12
