package org.taigaui.designtokens.completion

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.model.Pointer
import com.intellij.polySymbols.search.PsiSourcedPolySymbol
import com.intellij.polySymbols.utils.PolySymbolDeclaredInPsi
import com.intellij.polySymbols.utils.PolySymbolDelegate
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.taigaui.designtokens.documentation.DesignTokenHoverPackageSection
import org.taigaui.designtokens.documentation.DesignTokenHoverPopupModel
import org.taigaui.designtokens.documentation.DesignTokenHoverValueRow
import org.taigaui.designtokens.documentation.DesignTokenNavigationTarget
import java.nio.file.Path

internal fun LookupElement.toCustomPropertyPreviewModel(tokenName: String): DesignTokenHoverPopupModel? =
    linkedPsiElement()?.toCustomPropertyPreviewModel(tokenName)

private fun LookupElement.linkedPsiElement(): PsiElement? {
    val lookupObject = getObject()
    val dereferenced =
        (lookupObject as? Pointer<*>)
            ?.dereference()

    return psiElement
        ?: lookupObject.polySymbolPsiElement()
        ?: dereferenced.polySymbolPsiElement()
}

private fun Any?.polySymbolPsiElement(): PsiElement? =
    when (this) {
        is PsiSourcedPolySymbol -> source
        is PolySymbolDeclaredInPsi -> sourceElement
        is PolySymbolDelegate<*> -> delegate.polySymbolPsiElement()
        else -> null
    }

private fun PsiElement.toCustomPropertyPreviewModel(tokenName: String): DesignTokenHoverPopupModel? {
    val declaration = findCustomPropertyDeclaration(tokenName) ?: return null
    val sourceFile =
        containingFile
            ?.virtualFile
            ?.path
            ?.let { path -> runCatching { Path.of(path) }.getOrNull() }
    val line =
        containingFile
            ?.let { file -> PsiDocumentManager.getInstance(project).getDocument(file) }
            ?.getLineNumber(declaration.offset)
            ?.plus(1)
    val sourceLabel =
        buildString {
            append(sourceFile?.fileName ?: "Project styles")
            line?.let { value ->
                append(':')
                append(value)
            }
        }

    return DesignTokenHoverPopupModel(
        tokenName = tokenName,
        description = null,
        sections =
            listOf(
                DesignTokenHoverPackageSection(
                    packageName = "Project custom property",
                    rows =
                        listOf(
                            DesignTokenHoverValueRow(
                                platform = sourceLabel,
                                resolvedValue = declaration.value,
                                color = null,
                                navigationTarget =
                                    if (sourceFile != null && line != null) {
                                        DesignTokenNavigationTarget(sourceFile, line)
                                    } else {
                                        null
                                    },
                            ),
                        ),
                    chains = emptyList(),
                ),
            ),
    )
}

private fun PsiElement.findCustomPropertyDeclaration(tokenName: String): CustomPropertyDeclaration? {
    val localDeclaration =
        generateSequence(this) { element -> element.parent }
            .take(MAX_PSI_ANCESTORS)
            .map { element -> element to element.text }
            .filter { (_, text) -> text.length <= MAX_DECLARATION_TEXT_LENGTH }
            .mapNotNull { (element, text) ->
                extractCustomPropertyValue(text, tokenName)?.let { value ->
                    CustomPropertyDeclaration(
                        value = value,
                        offset = element.textRange.startOffset,
                    )
                }
            }.firstOrNull()

    if (localDeclaration != null) {
        return localDeclaration
    }

    val file = containingFile ?: return null
    val document = PsiDocumentManager.getInstance(project).getDocument(file)
    val text = document?.immutableCharSequence ?: file.text
    val sourceOffset = textOffset

    return customPropertyPattern(tokenName)
        .findAll(text)
        .mapNotNull { match ->
            match
                .groups[1]
                ?.value
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { value ->
                    CustomPropertyDeclaration(
                        value = value,
                        offset = match.range.first,
                    )
                }
        }.minByOrNull { declaration ->
            kotlin.math.abs(declaration.offset - sourceOffset)
        }
}

internal fun extractCustomPropertyValue(
    text: String,
    tokenName: String,
): String? {
    val match = customPropertyPattern(tokenName).find(text)

    return match
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private data class CustomPropertyDeclaration(
    val value: String,
    val offset: Int,
)

private fun customPropertyPattern(tokenName: String): Regex =
    Regex(
        pattern = """(?s)(?:^|[;{])\s*${Regex.escape(tokenName)}\s*:\s*([^;{}]+)""",
    )

private const val MAX_PSI_ANCESTORS = 8
private const val MAX_DECLARATION_TEXT_LENGTH = 8_192
