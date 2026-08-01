package org.taigaui.designtokens.tokenindex

import java.nio.file.Files
import java.nio.file.Path

class DesignTokenDeclarationParser : DesignTokenSourceExtractor {
    override fun extract(sourceFile: Path): List<DesignTokenDeclaration> {
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()
        val content = runCatching { Files.readString(normalizedSourceFile) }
            .getOrNull()
            ?: return emptyList()
        val searchableContent = maskCommentsAndStrings(content)
        val lineStarts = findLineStarts(content)

        return DECLARATION.findAll(searchableContent)
            .mapNotNull { match ->
                val valueStart = match.range.last + 1
                val valueEnd = findValueEnd(searchableContent, valueStart)
                val value = content.substring(valueStart, valueEnd).trim()

                value.takeIf(String::isNotEmpty)?.let {
                    DesignTokenDeclaration(
                        name = match.groupValues[1],
                        value = it,
                        sourceFile = normalizedSourceFile,
                        line = lineNumber(lineStarts, match.range.first),
                    )
                }
            }
            .toList()
    }

    private fun maskCommentsAndStrings(content: String): String {
        val masked = content.toCharArray()
        var index = 0
        var parenthesesDepth = 0

        while (index < content.length) {
            when {
                content.startsWith("/*", index) ->
                    index = maskUntil(masked, content, index, "*/")

                content.startsWith("//", index) && parenthesesDepth == 0 ->
                    index = maskLineComment(masked, content, index)

                content[index] == '\'' || content[index] == '"' ->
                    index = maskString(masked, content, index)

                content[index] == '(' -> {
                    parenthesesDepth++
                    index++
                }

                content[index] == ')' -> {
                    parenthesesDepth = (parenthesesDepth - 1).coerceAtLeast(0)
                    index++
                }

                else -> index++
            }
        }

        return String(masked)
    }

    private fun maskUntil(
        masked: CharArray,
        content: String,
        startIndex: Int,
        terminator: String,
    ): Int {
        val endIndex = content.indexOf(terminator, startIndex + 2)
            .let { if (it == -1) content.length else it + terminator.length }

        maskRange(masked, startIndex, endIndex)

        return endIndex
    }

    private fun maskLineComment(
        masked: CharArray,
        content: String,
        startIndex: Int,
    ): Int {
        val endIndex = content.indexOf('\n', startIndex + 2)
            .let { if (it == -1) content.length else it }

        maskRange(masked, startIndex, endIndex)

        return endIndex
    }

    private fun maskString(
        masked: CharArray,
        content: String,
        startIndex: Int,
    ): Int {
        val quote = content[startIndex]
        var index = startIndex + 1

        while (index < content.length) {
            when {
                content[index] == '\\' -> index = (index + 2).coerceAtMost(content.length)
                content[index] == quote -> {
                    index++
                    break
                }
                else -> index++
            }
        }

        maskRange(masked, startIndex, index)

        return index
    }

    private fun maskRange(masked: CharArray, startIndex: Int, endIndex: Int) {
        for (index in startIndex until endIndex) {
            if (masked[index] != '\n' && masked[index] != '\r') {
                masked[index] = ' '
            }
        }
    }

    private fun findValueEnd(content: String, startIndex: Int): Int {
        var parenthesesDepth = 0
        var bracketsDepth = 0

        for (index in startIndex until content.length) {
            when (content[index]) {
                '(' -> parenthesesDepth++
                ')' -> parenthesesDepth = (parenthesesDepth - 1).coerceAtLeast(0)
                '[' -> bracketsDepth++
                ']' -> bracketsDepth = (bracketsDepth - 1).coerceAtLeast(0)
                ';', '}' -> if (parenthesesDepth == 0 && bracketsDepth == 0) {
                    return index
                }
            }
        }

        return content.length
    }

    private fun findLineStarts(content: String): List<Int> = buildList {
        add(0)

        content.forEachIndexed { index, character ->
            if (character == '\n') {
                add(index + 1)
            }
        }
    }

    private fun lineNumber(lineStarts: List<Int>, offset: Int): Int {
        val index = lineStarts.binarySearch(offset)

        return if (index >= 0) index + 1 else -index - 1
    }

    private companion object {
        val DECLARATION = Regex("""(--tui-[A-Za-z0-9_-]+)\s*:""")
    }
}
