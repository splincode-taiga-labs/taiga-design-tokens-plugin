package org.taigaui.designtokens.tokenindex

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.css.CssDeclaration
import com.intellij.psi.css.CssRuleset
import java.nio.file.Path

class PsiDesignTokenSourceExtractor(
    private val project: Project,
) : DesignTokenSourceExtractor {
    override fun extract(sourceFile: Path): List<DesignTokenDeclaration> {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val virtualFile = LocalFileSystem.getInstance()
            .refreshAndFindFileByNioFile(normalizedSourceFile)
            ?: return emptyList()

        return ApplicationManager.getApplication().runReadAction(
            Computable {
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
                    ?: return@Computable emptyList()

                extract(psiFile, normalizedSourceFile)
            },
        )
    }

    internal fun extract(
        psiFile: PsiFile,
        sourceFile: Path,
    ): List<DesignTokenDeclaration> {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val declarations = mutableListOf<DesignTokenDeclaration>()
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)

        psiFile.accept(
            object : PsiRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    if (element is CssDeclaration) {
                        element.toDesignTokenDeclaration(
                            sourceFile = normalizedSourceFile,
                            line = document
                                ?.getLineNumber(element.textOffset)
                                ?.plus(1)
                                ?: lineNumber(psiFile.text, element.textOffset),
                        )?.let(declarations::add)
                    }

                    super.visitElement(element)
                }
            },
        )

        return declarations
    }

    private fun CssDeclaration.toDesignTokenDeclaration(
        sourceFile: Path,
        line: Int,
    ): DesignTokenDeclaration? {
        val tokenName = propertyName
        val rawValue = rawValue()

        return if (tokenName.startsWith(TOKEN_PREFIX) && rawValue.isNotEmpty()) {
            DesignTokenDeclaration(
                name = tokenName,
                value = rawValue,
                sourceFile = sourceFile,
                line = line,
                selectorChain = selectorChain(),
            )
        } else {
            null
        }
    }

    private fun CssDeclaration.rawValue(): String =
        generateSequence(firstChild, PsiElement::getNextSibling)
            .dropWhile { it.text != COLON }
            .drop(1)
            .joinToString(separator = "", transform = PsiElement::getText)
            .trim()

    private fun CssDeclaration.selectorChain(): List<String> =
        generateSequence(parent, PsiElement::getParent)
            .filterIsInstance<CssRuleset>()
            .mapNotNull { ruleset ->
                ruleset.selectorList
                    ?.text
                    ?.trim()
                    ?.takeIf(String::isNotEmpty)
            }
            .toList()
            .asReversed()

    private fun lineNumber(content: String, offset: Int): Int =
        content.take(offset).count { it == '\n' } + 1

    private companion object {
        const val TOKEN_PREFIX = "--tui-"
        const val COLON = ":"
    }
}
