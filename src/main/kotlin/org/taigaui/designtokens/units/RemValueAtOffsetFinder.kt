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

    fun findAll(content: CharSequence): List<RemValueAtOffset> {
        val excludedRanges = EXCLUDED_TEXT.findAll(content).map(MatchResult::range).toList()

        return REM_VALUE
            .findAll(content)
            .filterNot { match -> excludedRanges.any { range -> match.range.first in range } }
            .map { match ->
                RemValueAtOffset(
                    remValue = match.groupValues[1].toBigDecimal(),
                    startOffset = match.range.first,
                    endOffset = match.range.last + 1,
                )
            }
            .toList()
    }
}

private fun BigDecimal.format(): String = stripTrailingZeros().toPlainString()

private val ROOT_FONT_SIZE_PX = BigDecimal("16")
private val REM_VALUE =
    Regex(
        pattern = "(?<![A-Za-z0-9_-])([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)rem(?![A-Za-z0-9_-])",
        option = RegexOption.IGNORE_CASE,
    )
private val EXCLUDED_TEXT =
    Regex(
        pattern = "\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|/\\*[\\s\\S]*?\\*/|//[^\\r\\n]*",
    )
