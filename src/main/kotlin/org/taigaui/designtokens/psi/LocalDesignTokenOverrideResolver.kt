package org.taigaui.designtokens.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.css.CssRuleset
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import java.nio.file.Path

internal class LocalDesignTokenOverrideResolver(
    private val extractor: PsiDesignTokenSourceExtractor,
) {
    constructor(project: Project) : this(PsiDesignTokenSourceExtractor(project))

    fun resolve(
        psiFile: PsiFile,
        sourceFile: Path,
        referenceOffset: Int,
    ): List<DesignTokenDeclaration> {
        val usageSelectorChain = psiFile.selectorChainAt(referenceOffset)

        if (usageSelectorChain.isEmpty()) {
            return emptyList()
        }

        return extractor
            .extract(psiFile, sourceFile)
            .asSequence()
            .filterNot { declaration -> GlobalDesignTokenContext.isGlobal(declaration.selectorChain) }
            .filter { declaration -> declaration.selectorChain.appliesTo(usageSelectorChain) }
            .groupBy(DesignTokenDeclaration::name)
            .values
            .mapNotNull(::effectiveDeclaration)
            .map { declaration ->
                declaration.copy(
                    packageName = PROJECT_STYLES_PACKAGE,
                    packageRoot = sourceFile.parent,
                    cascadeOrder = declaration.line,
                    localOverride = true,
                )
            }
    }

    private fun effectiveDeclaration(
        declarations: List<DesignTokenDeclaration>,
    ): DesignTokenDeclaration? =
        declarations.maxWithOrNull(
            compareBy<DesignTokenDeclaration>(
                { declaration -> declaration.selectorChain.size },
                DesignTokenDeclaration::line,
            ),
        )
}

private fun PsiFile.selectorChainAt(offset: Int): List<String> =
    findElementAt(offset.coerceIn(0, (textLength - 1).coerceAtLeast(0)))
        ?.cssSelectorChain()
        .orEmpty()

private fun PsiElement.cssSelectorChain(): List<String> =
    generateSequence(this, PsiElement::getParent)
        .mapNotNull { element ->
            (element as? CssRuleset)
                ?.selectorList
                ?.text
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }.toList()
        .asReversed()

private fun List<String>.appliesTo(usageSelectorChain: List<String>): Boolean {
    if (isEmpty() || size > usageSelectorChain.size) {
        return false
    }

    return indices.all { index ->
        selectorScopesOverlap(
            declarationScope = this[index],
            usageScope = usageSelectorChain[index],
        )
    }
}

private fun selectorScopesOverlap(
    declarationScope: String,
    usageScope: String,
): Boolean {
    val declarationSelectors = declarationScope.selectorList()
    val usageSelectors = usageScope.selectorList()

    return declarationSelectors.any(usageSelectors::contains)
}

private fun String.selectorList(): Set<String> =
    split(',')
        .map(String::normalizeSelector)
        .filter(String::isNotEmpty)
        .toSet()

private fun String.normalizeSelector(): String = replace(WHITESPACE, " ").trim()

private val WHITESPACE = Regex("""\s+""")
