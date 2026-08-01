package org.taigaui.designtokens.resolution

internal data class ParsedDesignTokenValue(
    val rawValue: String,
    val parts: List<DesignTokenValuePart>,
)

internal sealed interface DesignTokenValuePart {
    data class Text(
        val value: String,
    ) : DesignTokenValuePart

    data class Reference(
        val name: String,
        val fallback: ParsedDesignTokenValue?,
    ) : DesignTokenValuePart
}

internal sealed interface DesignTokenValueParseResult {
    data class Parsed(
        val value: ParsedDesignTokenValue,
    ) : DesignTokenValueParseResult

    data class Invalid(
        val offset: Int,
        val message: String,
    ) : DesignTokenValueParseResult
}

internal object DesignTokenValueParser {
    fun parse(value: String): DesignTokenValueParseResult = Parser(value).parse()

    private class Parser(
        private val input: String,
    ) {
        fun parse(): DesignTokenValueParseResult = parseRange(0, input.length, trim = false)

        private fun parseRange(
            initialStart: Int,
            initialEnd: Int,
            trim: Boolean,
        ): DesignTokenValueParseResult {
            val (start, end) =
                if (trim) {
                    trimmedRange(initialStart, initialEnd)
                } else {
                    initialStart to initialEnd
                }
            val parts = mutableListOf<DesignTokenValuePart>()
            var textStart = start
            var index = start

            while (index < end) {
                when {
                    input[index].isQuote() -> {
                        val quotedEnd = findQuotedEnd(index, end)

                        if (quotedEnd == null) {
                            return invalid(index, "Unterminated quoted string in token value.")
                        }

                        index = quotedEnd + 1
                    }

                    input.startsComment(index, end) -> {
                        val commentEnd = input.indexOf("*/", startIndex = index + 2)

                        if (commentEnd < 0 || commentEnd + 2 > end) {
                            return invalid(index, "Unterminated comment in token value.")
                        }

                        index = commentEnd + 2
                    }

                    isVarFunctionAt(index, end) -> {
                        addText(parts, textStart, index)

                        val openParenthesis = index + VAR_FUNCTION_NAME.length
                        val closeParenthesis = findClosingParenthesis(openParenthesis, end)

                        if (closeParenthesis == null) {
                            return invalid(index, "Unterminated var() expression.")
                        }

                        when (
                            val reference =
                                parseReference(
                                    contentStart = openParenthesis + 1,
                                    contentEnd = closeParenthesis,
                                )
                        ) {
                            is ReferenceParseResult.Parsed -> parts.add(reference.reference)
                            is ReferenceParseResult.Invalid -> return reference.result
                        }

                        index = closeParenthesis + 1
                        textStart = index
                    }

                    else -> index++
                }
            }

            addText(parts, textStart, end)

            return DesignTokenValueParseResult.Parsed(
                ParsedDesignTokenValue(
                    rawValue = input.substring(start, end),
                    parts = parts.toList(),
                ),
            )
        }

        private fun parseReference(
            contentStart: Int,
            contentEnd: Int,
        ): ReferenceParseResult {
            val comma = findTopLevelComma(contentStart, contentEnd)
            val nameEnd = comma ?: contentEnd
            val name = input.substring(contentStart, nameEnd).trim()

            if (!name.isCustomPropertyName()) {
                return ReferenceParseResult.Invalid(
                    invalid(
                        contentStart,
                        "var() must reference a CSS custom property name.",
                    ),
                )
            }

            val fallback =
                if (comma == null) {
                    null
                } else {
                    when (val result = parseRange(comma + 1, contentEnd, trim = true)) {
                        is DesignTokenValueParseResult.Parsed -> result.value
                        is DesignTokenValueParseResult.Invalid -> {
                            return ReferenceParseResult.Invalid(result)
                        }
                    }
                }

            return ReferenceParseResult.Parsed(
                DesignTokenValuePart.Reference(
                    name = name,
                    fallback = fallback,
                ),
            )
        }

        private fun findTopLevelComma(
            start: Int,
            end: Int,
        ): Int? {
            var depth = 0
            var index = start

            while (index < end) {
                when {
                    input[index].isQuote() -> {
                        index = findQuotedEnd(index, end)?.plus(1) ?: end
                    }

                    input.startsComment(index, end) -> {
                        val commentEnd = input.indexOf("*/", startIndex = index + 2)
                        index = if (commentEnd < 0) end else commentEnd + 2
                    }

                    input[index] == '(' -> {
                        depth++
                        index++
                    }

                    input[index] == ')' -> {
                        depth--
                        index++
                    }

                    input[index] == ',' && depth == 0 -> return index
                    else -> index++
                }
            }

            return null
        }

        private fun findClosingParenthesis(
            openParenthesis: Int,
            end: Int,
        ): Int? {
            var depth = 1
            var index = openParenthesis + 1

            while (index < end) {
                when {
                    input[index].isQuote() -> {
                        val quotedEnd = findQuotedEnd(index, end) ?: return null
                        index = quotedEnd + 1
                    }

                    input.startsComment(index, end) -> {
                        val commentEnd = input.indexOf("*/", startIndex = index + 2)

                        if (commentEnd < 0 || commentEnd + 2 > end) {
                            return null
                        }

                        index = commentEnd + 2
                    }

                    input[index] == '(' -> {
                        depth++
                        index++
                    }

                    input[index] == ')' -> {
                        depth--

                        if (depth == 0) {
                            return index
                        }

                        index++
                    }

                    else -> index++
                }
            }

            return null
        }

        private fun findQuotedEnd(
            quoteStart: Int,
            end: Int,
        ): Int? {
            val quote = input[quoteStart]
            var escaped = false
            var index = quoteStart + 1

            while (index < end) {
                val character = input[index]

                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == quote -> return index
                }

                index++
            }

            return null
        }

        private fun isVarFunctionAt(
            index: Int,
            end: Int,
        ): Boolean {
            val functionEnd = index + VAR_FUNCTION_NAME.length
            val hasFunctionName =
                functionEnd < end &&
                    input.regionMatches(
                        thisOffset = index,
                        other = VAR_FUNCTION_NAME,
                        otherOffset = 0,
                        length = VAR_FUNCTION_NAME.length,
                        ignoreCase = true,
                    ) &&
                    input[functionEnd] == '('
            val hasBoundary = index == 0 || !input[index - 1].isIdentifierCharacter()

            return hasFunctionName && hasBoundary
        }

        private fun addText(
            parts: MutableList<DesignTokenValuePart>,
            start: Int,
            end: Int,
        ) {
            if (start < end) {
                parts.add(DesignTokenValuePart.Text(input.substring(start, end)))
            }
        }

        private fun trimmedRange(
            initialStart: Int,
            initialEnd: Int,
        ): Pair<Int, Int> {
            var start = initialStart
            var end = initialEnd

            while (start < end && input[start].isWhitespace()) {
                start++
            }

            while (end > start && input[end - 1].isWhitespace()) {
                end--
            }

            return start to end
        }

        private fun invalid(
            offset: Int,
            message: String,
        ): DesignTokenValueParseResult.Invalid =
            DesignTokenValueParseResult.Invalid(
                offset = offset,
                message = message,
            )
    }

    private sealed interface ReferenceParseResult {
        data class Parsed(
            val reference: DesignTokenValuePart.Reference,
        ) : ReferenceParseResult

        data class Invalid(
            val result: DesignTokenValueParseResult.Invalid,
        ) : ReferenceParseResult
    }

    private const val VAR_FUNCTION_NAME = "var"

    private fun Char.isQuote(): Boolean = this == '\'' || this == '"'

    private fun Char.isIdentifierCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_'

    private fun String.startsComment(
        index: Int,
        end: Int,
    ): Boolean = index + 1 < end && this[index] == '/' && this[index + 1] == '*'

    private fun String.isCustomPropertyName(): Boolean =
        length > 2 &&
            startsWith("--") &&
            none { character -> character.isWhitespace() || character == ',' || character == '(' || character == ')' }
}
