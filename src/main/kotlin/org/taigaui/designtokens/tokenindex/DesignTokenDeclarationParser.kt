package org.taigaui.designtokens.tokenindex

import java.nio.file.Files
import java.nio.file.Path

class DesignTokenDeclarationParser {
    fun parse(sourceFile: Path): List<DesignTokenDeclaration> {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val content = runCatching { Files.readString(normalizedSourceFile) }
            .getOrNull()
            ?: return emptyList()

        return parseContent(content, normalizedSourceFile)
    }

    private fun parseContent(
        content: String,
        sourceFile: Path,
    ): List<DesignTokenDeclaration> {
        val declarations = mutableListOf<DesignTokenDeclaration>()
        var index = 0
        var line = 1

        while (index < content.length) {
            when {
                content.startsWith("/*", index) -> {
                    val result = skipBlockComment(content, index, line)
                    index = result.index
                    line = result.line
                }

                content.startsWith("//", index) -> {
                    val result = skipLineComment(content, index, line)
                    index = result.index
                    line = result.line
                }

                content[index] == '\'' || content[index] == '"' -> {
                    val result = skipString(content, index, line)
                    index = result.index
                    line = result.line
                }

                content.startsWith(TOKEN_PREFIX, index) && isIdentifierBoundary(content, index) -> {
                    val parsed = parseDeclaration(content, index, line, sourceFile)

                    if (parsed == null) {
                        index++
                    } else {
                        declarations += parsed.declaration
                        index = parsed.index
                        line = parsed.line
                    }
                }

                else -> {
                    if (content[index] == '\n') {
                        line++
                    }

                    index++
                }
            }
        }

        return declarations
    }

    private fun parseDeclaration(
        content: String,
        startIndex: Int,
        startLine: Int,
        sourceFile: Path,
    ): ParsedDeclaration? {
        var index = startIndex + TOKEN_PREFIX.length
        var line = startLine

        while (index < content.length && isIdentifierCharacter(content[index])) {
            index++
        }

        val name = content.substring(startIndex, index)
        val trivia = skipTrivia(content, index, line)
        index = trivia.index
        line = trivia.line

        if (content.getOrNull(index) != ':') {
            return null
        }

        index++
        val valueStart = index
        var parenthesesDepth = 0
        var bracketsDepth = 0

        while (index < content.length) {
            when {
                content.startsWith("/*", index) -> {
                    val result = skipBlockComment(content, index, line)
                    index = result.index
                    line = result.line
                }

                content.startsWith("//", index) && parenthesesDepth == 0 && bracketsDepth == 0 -> {
                    val result = skipLineComment(content, index, line)
                    index = result.index
                    line = result.line
                }

                content[index] == '\'' || content[index] == '"' -> {
                    val result = skipString(content, index, line)
                    index = result.index
                    line = result.line
                }

                content[index] == '(' -> {
                    parenthesesDepth++
                    index++
                }

                content[index] == ')' -> {
                    parenthesesDepth = (parenthesesDepth - 1).coerceAtLeast(0)
                    index++
                }

                content[index] == '[' -> {
                    bracketsDepth++
                    index++
                }

                content[index] == ']' -> {
                    bracketsDepth = (bracketsDepth - 1).coerceAtLeast(0)
                    index++
                }

                content[index] == ';' && parenthesesDepth == 0 && bracketsDepth == 0 -> {
                    return createParsedDeclaration(
                        name = name,
                        rawValue = content.substring(valueStart, index),
                        sourceFile = sourceFile,
                        line = startLine,
                        nextIndex = index + 1,
                        nextLine = line,
                    )
                }

                content[index] == '}' && parenthesesDepth == 0 && bracketsDepth == 0 -> {
                    return createParsedDeclaration(
                        name = name,
                        rawValue = content.substring(valueStart, index),
                        sourceFile = sourceFile,
                        line = startLine,
                        nextIndex = index,
                        nextLine = line,
                    )
                }

                else -> {
                    if (content[index] == '\n') {
                        line++
                    }

                    index++
                }
            }
        }

        return createParsedDeclaration(
            name = name,
            rawValue = content.substring(valueStart),
            sourceFile = sourceFile,
            line = startLine,
            nextIndex = content.length,
            nextLine = line,
        )
    }

    private fun createParsedDeclaration(
        name: String,
        rawValue: String,
        sourceFile: Path,
        line: Int,
        nextIndex: Int,
        nextLine: Int,
    ): ParsedDeclaration? {
        val value = rawValue.trim()

        if (value.isEmpty()) {
            return null
        }

        return ParsedDeclaration(
            declaration = DesignTokenDeclaration(
                name = name,
                value = value,
                sourceFile = sourceFile,
                line = line,
            ),
            index = nextIndex,
            line = nextLine,
        )
    }

    private fun skipTrivia(
        content: String,
        startIndex: Int,
        startLine: Int,
    ): Cursor {
        var index = startIndex
        var line = startLine

        while (index < content.length) {
            when {
                content[index].isWhitespace() -> {
                    if (content[index] == '\n') {
                        line++
                    }

                    index++
                }

                content.startsWith("/*", index) -> {
                    val result = skipBlockComment(content, index, line)
                    index = result.index
                    line = result.line
                }

                else -> return Cursor(index, line)
            }
        }

        return Cursor(index, line)
    }

    private fun skipBlockComment(
        content: String,
        startIndex: Int,
        startLine: Int,
    ): Cursor {
        var index = startIndex + 2
        var line = startLine

        while (index < content.length && !content.startsWith("*/", index)) {
            if (content[index] == '\n') {
                line++
            }

            index++
        }

        return Cursor(
            index = if (index < content.length) index + 2 else content.length,
            line = line,
        )
    }

    private fun skipLineComment(
        content: String,
        startIndex: Int,
        startLine: Int,
    ): Cursor {
        var index = startIndex + 2

        while (index < content.length && content[index] != '\n') {
            index++
        }

        return Cursor(index, startLine)
    }

    private fun skipString(
        content: String,
        startIndex: Int,
        startLine: Int,
    ): Cursor {
        val quote = content[startIndex]
        var index = startIndex + 1
        var line = startLine

        while (index < content.length) {
            when {
                content[index] == '\\' -> index = (index + 2).coerceAtMost(content.length)
                content[index] == quote -> return Cursor(index + 1, line)
                else -> {
                    if (content[index] == '\n') {
                        line++
                    }

                    index++
                }
            }
        }

        return Cursor(content.length, line)
    }

    private fun isIdentifierBoundary(content: String, index: Int): Boolean =
        index == 0 || !isIdentifierCharacter(content[index - 1])

    private fun isIdentifierCharacter(character: Char): Boolean =
        character.isLetterOrDigit() || character == '-' || character == '_'

    private data class Cursor(
        val index: Int,
        val line: Int,
    )

    private data class ParsedDeclaration(
        val declaration: DesignTokenDeclaration,
        val index: Int,
        val line: Int,
    )

    private companion object {
        const val TOKEN_PREFIX = "--tui-"
    }
}
