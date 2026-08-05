package org.taigaui.designtokens.psi

internal object LessThemeMixinContextFinder {
    fun find(
        content: String,
        offset: Int,
    ): String? =
        LESS_THEME_MIXIN
            .findAll(content)
            .takeWhile { match -> match.range.first < offset }
            .mapNotNull { match -> match.enclosingTheme(content, offset) }
            .lastOrNull()

    private fun MatchResult.enclosingTheme(
        content: String,
        offset: Int,
    ): String? {
        val openingBrace = range.last
        val closingBrace = content.findClosingBrace(openingBrace) ?: return null

        return groupValues[1]
            .lowercase()
            .takeIf { offset in (openingBrace + 1)..<closingBrace }
            ?.let { theme -> ".$theme()" }
    }

    private fun String.findClosingBrace(openingBrace: Int): Int? {
        var depth = 0
        var state = ScannerState.CODE
        var escaped = false
        var index = openingBrace

        while (index < length) {
            val character = this[index]
            val next = getOrNull(index + 1)

            when (state) {
                ScannerState.CODE ->
                    when {
                        character == '/' && next == '*' -> {
                            state = ScannerState.BLOCK_COMMENT
                            index++
                        }

                        character == '/' && next == '/' -> {
                            state = ScannerState.LINE_COMMENT
                            index++
                        }

                        character == '\'' -> state = ScannerState.SINGLE_QUOTE
                        character == '"' -> state = ScannerState.DOUBLE_QUOTE
                        character == '{' -> depth++
                        character == '}' -> {
                            depth--

                            if (depth == 0) {
                                return index
                            }
                        }
                    }

                ScannerState.BLOCK_COMMENT ->
                    if (character == '*' && next == '/') {
                        state = ScannerState.CODE
                        index++
                    }

                ScannerState.LINE_COMMENT ->
                    if (character == '\n') {
                        state = ScannerState.CODE
                    }

                ScannerState.SINGLE_QUOTE,
                ScannerState.DOUBLE_QUOTE,
                -> {
                    val quote = if (state == ScannerState.SINGLE_QUOTE) '\'' else '"'

                    when {
                        escaped -> escaped = false
                        character == '\\' -> escaped = true
                        character == quote -> state = ScannerState.CODE
                    }
                }
            }

            index++
        }

        return null
    }

    private enum class ScannerState {
        CODE,
        BLOCK_COMMENT,
        LINE_COMMENT,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
    }

    private val LESS_THEME_MIXIN =
        Regex(
            pattern = """\.(light|dark)\s*\(\s*\)\s*\{""",
            option = RegexOption.IGNORE_CASE,
        )
}
