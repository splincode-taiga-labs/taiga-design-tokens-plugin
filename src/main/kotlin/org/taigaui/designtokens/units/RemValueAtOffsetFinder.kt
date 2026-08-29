package org.taigaui.designtokens.units

import java.math.BigDecimal

internal data class RemValueAtOffset(
    val remValue: BigDecimal,
    val startOffset: Int,
    val endOffset: Int,
) {
    val pxValue: BigDecimal = remValue.multiply(ROOT_FONT_SIZE_PX)

    fun presentation(): String = "${remValue.format()}rem = ${pxValue.format()}px"
}

internal object RemValueAtOffsetFinder {
    fun find(
        content: CharSequence,
        offset: Int,
    ): RemValueAtOffset? {
        if (content.isEmpty() || offset !in 0..content.length) {
            return null
        }

        val lineStart = content.findLineStart(offset)
        val lineEnd = content.findLineEnd(offset)
        val line = content.subSequence(lineStart, lineEnd).toString()
        val localOffset = offset - lineStart

        return REM_VALUE
            .findAll(line)
            .firstOrNull { match -> localOffset in match.range.first..(match.range.last + 1) }
            ?.takeUnless { match -> content.isInsideCommentOrString(lineStart + match.range.first) }
            ?.let { match ->
                RemValueAtOffset(
                    remValue = match.groupValues[1].toBigDecimal(),
                    startOffset = lineStart + match.range.first,
                    endOffset = lineStart + match.range.last + 1,
                )
            }
    }
}

private fun CharSequence.findLineStart(offset: Int): Int {
    val start = (offset - 1).coerceAtMost(lastIndex)

    for (index in start downTo 0) {
        if (this[index] == '\n') {
            return index + 1
        }
    }

    return 0
}

private fun CharSequence.findLineEnd(offset: Int): Int {
    for (index in offset.coerceAtLeast(0).coerceAtMost(length) until length) {
        if (this[index] == '\n') {
            return index
        }
    }

    return length
}

private fun CharSequence.isInsideCommentOrString(offset: Int): Boolean {
    var index = 0
    var quote: Char? = null
    var inComment = false

    while (index < offset.coerceAtMost(length)) {
        val current = this[index]
        val next = this.getOrNull(index + 1)

        when {
            inComment && current == '*' && next == '/' -> {
                inComment = false
                index += 2
            }

            inComment -> index++
            quote != null && current == '\\' -> index += 2
            quote != null && current == quote -> {
                quote = null
                index++
            }

            quote != null -> index++
            current == '/' && next == '*' -> {
                inComment = true
                index += 2
            }

            current == '\'' || current == '"' -> {
                quote = current
                index++
            }

            else -> index++
        }
    }

    return inComment || quote != null
}

private fun BigDecimal.format(): String = stripTrailingZeros().toPlainString()

private val ROOT_FONT_SIZE_PX = BigDecimal("16")
private val REM_VALUE =
    Regex(
        pattern = "(?<![A-Za-z0-9_-])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)rem(?![A-Za-z0-9_-])",
        option = RegexOption.IGNORE_CASE,
    )
