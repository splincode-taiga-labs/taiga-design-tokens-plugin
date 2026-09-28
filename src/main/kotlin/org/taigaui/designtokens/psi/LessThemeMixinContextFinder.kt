package org.taigaui.designtokens.psi

internal object LessThemeMixinContextFinder {
    fun find(
        content: String,
        offset: Int,
    ): String? =
        THEME_MIXIN
            .findAll(content)
            .takeWhile { match -> match.range.first < offset }
            .mapNotNull { match -> match.enclosingTheme(content, offset) }
            .lastOrNull()

    private fun MatchResult.enclosingTheme(
        content: String,
        offset: Int,
    ): String? {
        val openingBrace = range.last
        val closingBrace = BraceScanner(content).findClosingBrace(openingBrace) ?: return null

        return groupValues[1]
            .lowercase()
            .takeIf { offset in (openingBrace + 1)..<closingBrace }
            ?.let(::normalizeMixin)
    }

    private class BraceScanner(
        private val content: String,
    ) {
        fun findClosingBrace(openingBrace: Int): Int? {
            val cursor = ScanCursor(index = openingBrace)

            while (cursor.index < content.length) {
                val closingBraceFound =
                    when (cursor.state) {
                        ScannerState.CODE -> scanCode(cursor)
                        ScannerState.BLOCK_COMMENT -> scanBlockComment(cursor)
                        ScannerState.LINE_COMMENT -> scanLineComment(cursor)
                        ScannerState.SINGLE_QUOTE -> scanQuoted(cursor, '\'')
                        ScannerState.DOUBLE_QUOTE -> scanQuoted(cursor, '"')
                    }

                if (closingBraceFound) {
                    return cursor.index
                }

                cursor.index++
            }

            return null
        }

        private fun scanCode(cursor: ScanCursor): Boolean {
            val character = content[cursor.index]
            val next = content.getOrNull(cursor.index + 1)

            when {
                character == '/' && next == '*' -> cursor.enterComment(ScannerState.BLOCK_COMMENT)
                character == '/' && next == '/' -> cursor.enterComment(ScannerState.LINE_COMMENT)
                character == '\'' -> cursor.state = ScannerState.SINGLE_QUOTE
                character == '"' -> cursor.state = ScannerState.DOUBLE_QUOTE
                character == '{' -> cursor.depth++
                character == '}' -> cursor.depth--
            }

            return character == '}' && cursor.depth == 0
        }

        private fun scanBlockComment(cursor: ScanCursor): Boolean {
            if (content[cursor.index] == '*' && content.getOrNull(cursor.index + 1) == '/') {
                cursor.state = ScannerState.CODE
                cursor.index++
            }

            return false
        }

        private fun scanLineComment(cursor: ScanCursor): Boolean {
            if (content[cursor.index] == '\n') {
                cursor.state = ScannerState.CODE
            }

            return false
        }

        private fun scanQuoted(
            cursor: ScanCursor,
            quote: Char,
        ): Boolean {
            val character = content[cursor.index]

            when {
                cursor.escaped -> cursor.escaped = false
                character == '\\' -> cursor.escaped = true
                character == quote -> cursor.state = ScannerState.CODE
            }

            return false
        }
    }

    private data class ScanCursor(
        var index: Int,
        var depth: Int = 0,
        var state: ScannerState = ScannerState.CODE,
        var escaped: Boolean = false,
    ) {
        fun enterComment(commentState: ScannerState) {
            state = commentState
            index++
        }
    }

    private enum class ScannerState {
        CODE,
        BLOCK_COMMENT,
        LINE_COMMENT,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
    }

    private fun normalizeMixin(mixin: String): String =
        when (mixin) {
            "tui-theme-variables" -> ".tui-theme-variables()"
            else -> "." + mixin.removePrefix("tui-theme-") + "()"
        }

    private val THEME_MIXIN =
        Regex(
            pattern =
                """(?:\.|@mixin\s+)((?:tui-theme-)?(?:light|dark)|tui-theme-variables)\s*(?:\(\s*\))?\s*\{""",
            option = RegexOption.IGNORE_CASE,
        )
}
