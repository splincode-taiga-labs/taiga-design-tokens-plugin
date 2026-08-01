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
    ): DesignTokenReferenceAtOffset? {
        var result: DesignTokenReferenceAtOffset? = null

        if (offset in 0..text.length) {
            var index = 0

            while (index < text.length && result == null) {
                index =
                    when {
                        text[index].isQuote() -> text.findQuotedEnd(index) + 1
                        text.startsComment(index) -> text.findCommentEnd(index)
                        text.isVarFunctionAt(index) -> {
                            val reference = text.parseReference(index)

                            if (reference != null && offset in reference.startOffset..reference.endOffset) {
                                result = reference
                            }

                            index + 1
                        }

                        else -> index + 1
                    }
            }
        }

        return result
    }

    private fun CharSequence.parseReference(functionStart: Int): DesignTokenReferenceAtOffset? {
        val openParenthesis = functionStart + VAR_FUNCTION_NAME.length

        return findFirstArgumentEnd(openParenthesis + 1)
            ?.let { referenceEnd -> trimmedRange(openParenthesis + 1, referenceEnd) }
            ?.let { range ->
                subSequence(range.start, range.end)
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

    private fun CharSequence.findFirstArgumentEnd(start: Int): Int? {
        var depth = 0
        var end: Int? = null
        var index = start

        while (index < length && end == null) {
            val character = this[index]

            index =
                when {
                    character.isQuote() -> findQuotedEnd(index) + 1
                    startsComment(index) -> findCommentEnd(index)
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

    private fun CharSequence.trimmedRange(
        initialStart: Int,
        initialEnd: Int,
    ): OffsetRange {
        var start = initialStart
        var end = initialEnd

        while (start < end && this[start].isWhitespace()) {
            start++
        }

        while (end > start && this[end - 1].isWhitespace()) {
            end--
        }

        return OffsetRange(start, end)
    }

    private fun CharSequence.isVarFunctionAt(index: Int): Boolean {
        val functionEnd = index + VAR_FUNCTION_NAME.length
        val hasFunctionName =
            functionEnd < length &&
                regionMatches(
                    thisOffset = index,
                    other = VAR_FUNCTION_NAME,
                    ignoreCase = true,
                ) &&
                this[functionEnd] == '('
        val hasBoundary = index == 0 || !this[index - 1].isIdentifierCharacter()

        return hasFunctionName && hasBoundary
    }

    private fun CharSequence.regionMatches(
        thisOffset: Int,
        other: String,
        ignoreCase: Boolean,
    ): Boolean =
        thisOffset + other.length <= length &&
            other.indices.all { otherIndex ->
                this[thisOffset + otherIndex].equals(other[otherIndex], ignoreCase)
            }

    private fun CharSequence.findQuotedEnd(quoteStart: Int): Int {
        val quote = this[quoteStart]
        var escaped = false
        var quotedEnd: Int? = null
        var index = quoteStart + 1

        while (index < length && quotedEnd == null) {
            val character = this[index]

            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> quotedEnd = index
            }

            index++
        }

        return quotedEnd ?: lastIndex
    }

    private fun CharSequence.findCommentEnd(commentStart: Int): Int {
        var commentEnd: Int? = null
        var index = commentStart + 2

        while (index + 1 < length && commentEnd == null) {
            if (this[index] == '*' && this[index + 1] == '/') {
                commentEnd = index + 2
            }

            index++
        }

        return commentEnd ?: length
    }

    private fun CharSequence.startsComment(index: Int): Boolean =
        index + 1 < length && this[index] == '/' && this[index + 1] == '*'

    private fun Char.isQuote(): Boolean = this == '\'' || this == '"'

    private fun Char.isIdentifierCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_'

    private data class OffsetRange(
        val start: Int,
        val end: Int,
    )

    private const val VAR_FUNCTION_NAME = "var"
    private val TAIGA_TOKEN_NAME = Regex("--tui-[A-Za-z0-9_-]+")
}
