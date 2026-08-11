package org.taigaui.designtokens.completion

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
        var index = start

        while (index < text.length) {
            val character = text[index]

            when {
                character.isQuote() -> index = findQuotedEnd(index) + 1
                text.startsComment(index) -> index = findCommentEnd(index)
                character == '(' -> {
                    depth++
                    index++
                }

                character == ')' && depth == 0 -> return index
                character == ')' -> {
                    depth--
                    index++
                }

                character == ',' && depth == 0 -> return index
                else -> index++
            }
        }

        return null
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
