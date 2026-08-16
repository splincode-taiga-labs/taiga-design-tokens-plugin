package org.taigaui.designtokens.completion

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.taigaui.designtokens.documentation.DesignTokenNameMatcher
import org.taigaui.designtokens.documentation.DesignTokenReferenceAtOffset
import org.taigaui.designtokens.documentation.DesignTokenReferenceAtOffsetFinder
import java.nio.file.Path

internal data class DesignTokenCompletionContext(
    val prefix: String,
)

internal object DesignTokenCompletionContextFinder {
    fun find(
        text: CharSequence,
        offset: Int,
    ): DesignTokenCompletionContext? =
        offset
            .takeIf { it in 0..text.length }
            ?.let { validOffset -> DesignTokenCompletionContextScanner(text).find(validOffset) }
}

class UnknownDesignTokenInspection :
    LocalInspectionTool(),
    DumbAware {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                inspectUnknownDesignTokens(file, holder)
            }
        }
}

private fun inspectUnknownDesignTokens(
    file: PsiFile,
    holder: ProblemsHolder,
) {
    val sourceFile = file.inspectionSourceFile() ?: return
    val project = file.project
    val knownTokens =
        project
            .service<DesignTokenCompletionService>()
            .namesForInspection(sourceFile) {
                if (file.isValid) {
                    DaemonCodeAnalyzer
                        .getInstance(project)
                        .restart(file, INSPECTION_RESTART_REASON)
                }
            }?.toSet()
            ?: return

    DesignTokenReferenceAtOffsetFinder
        .findAll(file.text)
        .filterNot { reference -> reference.name in knownTokens }
        .forEach { reference ->
            holder.registerUnknownTokenProblem(
                file,
                reference,
                DesignTokenNameMatcher.closest(reference.name, knownTokens),
            )
        }
}

private fun PsiFile.inspectionSourceFile(): Path? =
    virtualFile
        ?.takeIf { candidate -> candidate.extension?.lowercase() in SUPPORTED_EXTENSIONS }
        ?.path
        ?.let(::pathOrNull)

private fun ProblemsHolder.registerUnknownTokenProblem(
    file: PsiFile,
    reference: DesignTokenReferenceAtOffset,
    replacement: String?,
) {
    val range = TextRange(reference.startOffset, reference.endOffset)

    if (replacement == null) {
        registerProblem(
            file,
            UNKNOWN_TOKEN_MESSAGE,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            range,
        )
    } else {
        registerProblem(
            file,
            UNKNOWN_TOKEN_MESSAGE,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            range,
            ReplaceUnknownDesignTokenQuickFix(replacement),
        )
    }
}

private class ReplaceUnknownDesignTokenQuickFix(
    private val replacement: String,
) : LocalQuickFix,
        DumbAware {
    override fun getFamilyName(): String = "Replace with $replacement"

    override fun applyFix(
        project: Project,
        descriptor: ProblemDescriptor,
    ) {
        val element = descriptor.psiElement
        val file = element.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val range = descriptor.textRangeInElement.shiftRight(element.textRange.startOffset)

        if (range.endOffset <= document.textLength) {
            document.replaceString(range.startOffset, range.endOffset, replacement)
        }
    }
}

private class DesignTokenCompletionContextScanner(
    private val text: CharSequence,
) {
    fun find(offset: Int): DesignTokenCompletionContext? {
        var index = 0
        var result: DesignTokenCompletionContext? = null

        while (index < offset) {
            index =
                when {
                    text[index].isQuote() -> findQuotedEnd(index) + 1
                    text.startsComment(index) -> findCommentEnd(index)
                    isVarFunctionAt(index) -> {
                        completionContext(index, offset)?.let { context -> result = context }
                        index + 1
                    }

                    else -> index + 1
                }
        }

        return result
    }

    private fun completionContext(
        functionStart: Int,
        offset: Int,
    ): DesignTokenCompletionContext? {
        val argumentStart = functionStart + VAR_FUNCTION_NAME.length + 1
        val argumentEnd = findFirstArgumentEnd(argumentStart) ?: text.length

        if (offset !in argumentStart..argumentEnd) {
            return null
        }

        val prefixStart = skipWhitespace(argumentStart, offset)
        val prefix = text.subSequence(prefixStart, offset).toString()

        return prefix
            .takeIf(TAIGA_TOKEN_PREFIX::matches)
            ?.let(::DesignTokenCompletionContext)
    }

    private fun findFirstArgumentEnd(start: Int): Int? {
        var depth = 0
        var end: Int? = null
        var index = start

        while (index < text.length && end == null) {
            val character = text[index]

            index =
                when {
                    character.isQuote() -> findQuotedEnd(index) + 1
                    text.startsComment(index) -> findCommentEnd(index)
                    character == '(' -> {
                        depth++
                        index + 1
                    }

                    character == ')' && depth == 0 -> {
                        end = index
                        index
                    }

                    character == ')' -> {
                        depth--
                        index + 1
                    }

                    character == ',' && depth == 0 -> {
                        end = index
                        index
                    }

                    else -> index + 1
                }
        }

        return end
    }

    private fun skipWhitespace(
        start: Int,
        end: Int,
    ): Int {
        var index = start

        while (index < end && text[index].isWhitespace()) {
            index++
        }

        return index
    }

    private fun isVarFunctionAt(index: Int): Boolean {
        val functionEnd = index + VAR_FUNCTION_NAME.length
        val hasFunctionName =
            functionEnd < text.length &&
                text.matchesAtIgnoreCase(index, VAR_FUNCTION_NAME) &&
                text[functionEnd] == '('
        val hasBoundary = index == 0 || !text[index - 1].isIdentifierCharacter()

        return hasFunctionName && hasBoundary
    }

    private fun findQuotedEnd(quoteStart: Int): Int {
        val quote = text[quoteStart]
        var escaped = false
        var index = quoteStart + 1

        while (index < text.length) {
            val character = text[index]

            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> return index
            }

            index++
        }

        return text.lastIndex
    }

    private fun findCommentEnd(commentStart: Int): Int {
        var index = commentStart + 2

        while (index + 1 < text.length) {
            if (text[index] == '*' && text[index + 1] == '/') {
                return index + 2
            }

            index++
        }

        return text.length
    }

    private companion object {
        const val VAR_FUNCTION_NAME = "var"
        val TAIGA_TOKEN_PREFIX = Regex("--tui-[A-Za-z0-9_-]*")
    }
}

private fun CharSequence.matchesAtIgnoreCase(
    offset: Int,
    value: String,
): Boolean =
    offset + value.length <= length &&
        value.indices.all { valueIndex ->
            this[offset + valueIndex].equals(value[valueIndex], ignoreCase = true)
        }

private fun CharSequence.startsComment(index: Int): Boolean =
    index + 1 < length && this[index] == '/' && this[index + 1] == '*'

private fun Char.isQuote(): Boolean = this == '\'' || this == '"'

private fun Char.isIdentifierCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_'

private const val UNKNOWN_TOKEN_MESSAGE = "Unknown Taiga UI design token"
private const val INSPECTION_RESTART_REASON = "Taiga UI design token index updated"
