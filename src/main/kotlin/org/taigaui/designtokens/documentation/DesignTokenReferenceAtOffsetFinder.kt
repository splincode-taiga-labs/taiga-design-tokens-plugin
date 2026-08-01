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
        if (offset !in 0..text.length) {
            return null
        }

        var index = 0

        while (index < text.length) {
            when {
                text[index].isQuote() -> index = text.findQuotedEnd(index) + 1
                text.startsComment(index) -> index = text.findCommentEnd(index)
                text.isVarFunctionAt(index) -> {
                    val reference = text.parseReference(index)

                    if (reference != null && offset in reference.startOffset..reference.endOffset) {
                        return reference
                    }

                    index++
                }

                else -> index++
            }
        }

        return null
    }

    private fun CharSequence.parseReference(functionStart: Int): DesignTokenReferenceAtOffset? {
        val openParenthesis = functionStart + VAR_FUNCTION_NAME.length
        var depth = 0
        var end: Int? = null
        var index = openParenthesis + 1

        while (index < length && end == null) {
            when {
                this[index].isQuote() -> index = findQuotedEnd(index) + 1
                startsComment(index) -> index = findCommentEnd(index)
                this[index] == '(' -> {
                    depth++
                    index++
                }

                this[index] == ')' && depth == 0 -> end = index
                this[index] == ')' -> {
                    depth--
                    index++
                }

                this[index] == ',' && depth == 0 -> end = index
                else -> index++
            }
        }

        val referenceEnd = end ?: return null
        var nameStart = openParenthesis + 1
        var nameEnd = referenceEnd

        while (nameStart < nameEnd && this[nameStart].isWhitespace()) {
            nameStart++
        }

        while (nameEnd > nameStart && this[nameEnd - 1].isWhitespace()) {
            nameEnd--
        }

        val name = subSequence(nameStart, nameEnd).toString()

        return name
            .takeIf(TAIGA_TOKEN_NAME::matches)
            ?.let {
                DesignTokenReferenceAtOffset(
                    name = it,
                    startOffset = nameStart,
                    endOffset = nameEnd,
                )
            }
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
    ): Boolean {
        if (thisOffset + other.length > length) {
            return false
        }

        return other.indices.all { otherIndex ->
            this[thisOffset + otherIndex].equals(other[otherIndex], ignoreCase)
        }
    }

    private fun CharSequence.findQuotedEnd(quoteStart: Int): Int {
        val quote = this[quoteStart]
        var escaped = false
        var index = quoteStart + 1

        while (index < length) {
            val character = this[index]

            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == quote -> return index
            }

            index++
        }

        return lastIndex
    }

    private fun CharSequence.findCommentEnd(commentStart: Int): Int {
        var index = commentStart + 2

        while (index + 1 < length) {
            if (this[index] == '*' && this[index + 1] == '/') {
                return index + 2
            }

            index++
        }

        return length
    }

    private fun CharSequence.startsComment(index: Int): Boolean =
        index + 1 < length && this[index] == '/' && this[index + 1] == '*'

    private fun Char.isQuote(): Boolean = this == '\'' || this == '"'

    private fun Char.isIdentifierCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_'

    private const val VAR_FUNCTION_NAME = "var"
    private val TAIGA_TOKEN_NAME = Regex("--tui-[A-Za-z0-9_-]+")
}
