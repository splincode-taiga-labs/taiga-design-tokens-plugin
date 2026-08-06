package org.taigaui.designtokens.documentation

internal data class DesignTokenReferenceAtOffset(
    val name: String,
    val startOffset: Int,
    val endOffset: Int,
)

internal object DesignTokenReferenceAtOffsetFinder {
    fun find(
        text: CharSequence,
        offset: Int,
    ): DesignTokenReferenceAtOffset? =
        offset
            .takeIf { it in 0..text.length }
            ?.let { validOffset -> DesignTokenReferenceScanner(text).find(validOffset) }
}

private class DesignTokenReferenceScanner(
    private val text: CharSequence,
) {
    fun find(offset: Int): DesignTokenReferenceAtOffset? {
        var index = 0
        var result: DesignTokenReferenceAtOffset? = null

        while (index < text.length && result == null) {
            val step = scanAt(index)

            result =
                step.reference
                    ?.takeIf { reference -> offset in reference.startOffset until reference.endOffset }
            index = step.nextOffset
        }

        return result
    }

    private fun scanAt(index: Int): ScanStep =
        when {
            text[index].isQuote() -> ScanStep(nextOffset = findQuotedEnd(index) + 1)
            text.startsComment(index) -> ScanStep(nextOffset = findCommentEnd(index))
            isVarFunctionAt(index) ->
                ScanStep(
                    nextOffset = index + 1,
                    reference = parseReference(index),
                )

            else -> ScanStep(nextOffset = index + 1)
        }

    private fun parseReference(functionStart: Int): DesignTokenReferenceAtOffset? {
        val openParenthesis = functionStart + VAR_FUNCTION_NAME.length

        return findFirstArgumentEnd(openParenthesis + 1)
            ?.let { referenceEnd -> trimmedRange(openParenthesis + 1, referenceEnd) }
            ?.let { range ->
                text
                    .subSequence(range.start, range.end)
                    .toString()
                    .takeIf(TAIGA_TOKEN_NAME::matches)
                    ?.let { name ->
                        DesignTokenReferenceAtOffset(
                            name = name,
                            startOffset = range.start,
                            endOffset = range.end,
                        )
                    }
            }
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

    private fun trimmedRange(
        initialStart: Int,
        initialEnd: Int,
    ): OffsetRange {
        var start = initialStart
        var end = initialEnd

        while (start < end && text[start].isWhitespace()) {
            start++
        }

        while (end > start && text[end - 1].isWhitespace()) {
            end--
        }

        return OffsetRange(start, end)
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
        var quotedEnd: Int? = null
        var index = quoteStart + 1

        while (index < text.length && quotedEnd == null) {
            val character = text[index]

            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> quotedEnd = index
            }

            index++
        }

        return quotedEnd ?: text.lastIndex
    }

    private fun findCommentEnd(commentStart: Int): Int {
        var commentEnd: Int? = null
        var index = commentStart + 2

        while (index + 1 < text.length && commentEnd == null) {
            if (text[index] == '*' && text[index + 1] == '/') {
                commentEnd = index + 2
            }

            index++
        }

        return commentEnd ?: text.length
    }

    private data class ScanStep(
        val nextOffset: Int,
        val reference: DesignTokenReferenceAtOffset? = null,
    )

    private data class OffsetRange(
        val start: Int,
        val end: Int,
    )

    private companion object {
        const val VAR_FUNCTION_NAME = "var"
        val TAIGA_TOKEN_NAME = Regex("--tui-[A-Za-z0-9_-]+")
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
