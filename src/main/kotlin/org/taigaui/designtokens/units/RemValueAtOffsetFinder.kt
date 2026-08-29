package org.taigaui.designtokens.units

import java.math.BigDecimal

internal data class RemValueAtOffset(
    val remValue: BigDecimal,
    val startOffset: Int,
    val endOffset: Int,
) {
    val pxValue: BigDecimal = remValue.multiply(ROOT_FONT_SIZE_PX)
    val pxPresentation: String = "${pxValue.format()}px"

    fun presentation(): String = "${remValue.format()}rem = $pxPresentation"
}

internal object RemValueAtOffsetFinder {
    fun find(
        content: CharSequence,
        offset: Int,
    ): RemValueAtOffset? {
        if (content.isEmpty() || offset !in 0..content.length) {
            return null
        }

        val candidateOffsets = sequenceOf(offset, offset - 1).filter(content.indices::contains)

        return findAll(content).firstOrNull { value ->
            candidateOffsets.any { current -> current in value.startOffset until value.endOffset }
        }
    }

    fun findAll(content: CharSequence): List<RemValueAtOffset> =
        REM_VALUE
            .findAll(content)
            .filterNot { match -> content.isInsideCommentOrString(match.range.first) }
            .map { match ->
                RemValueAtOffset(
                    remValue = match.groupValues[1].toBigDecimal(),
                    startOffset = match.range.first,
                    endOffset = match.range.last + 1,
                )
            }
            .toList()
}

private fun CharSequence.isInsideCommentOrString(offset: Int): Boolean {
    var index = 0
    var quote: Char? = null
    var inBlockComment = false
    var inLineComment = false

    while (index < offset.coerceAtMost(length)) {
        val current = this[index]
        val next = if (index + 1 < length) this[index + 1] else null

        when {
            inLineComment && current == '\n' -> {
                inLineComment = false
                index++
            }

            inLineComment -> index++
            inBlockComment && current == '*' && next == '/' -> {
                inBlockComment = false
                index += 2
            }

            inBlockComment -> index++
            quote != null && current == '\\' -> index += 2
            quote != null && current == quote -> {
                quote = null
                index++
            }

            quote != null -> index++
            current == '/' && next == '*' -> {
                inBlockComment = true
                index += 2
            }

            current == '/' && next == '/' -> {
                inLineComment = true
                index += 2
            }

            current == '\'' || current == '"' -> {
                quote = current
                index++
            }

            else -> index++
        }
    }

    return inBlockComment || inLineComment || quote != null
}

private fun BigDecimal.format(): String = stripTrailingZeros().toPlainString()

private val ROOT_FONT_SIZE_PX = BigDecimal("16")
private val REM_VALUE =
    Regex(
        pattern = "(?<![A-Za-z0-9_-])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)rem(?![A-Za-z0-9_-])",
        option = RegexOption.IGNORE_CASE,
    )
