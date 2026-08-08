package org.taigaui.designtokens.documentation

import java.math.BigDecimal

internal fun String.withRemPixels(): String {
    val pixels =
        REM_VALUE
            .matchEntire(trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toBigDecimalOrNull()
            ?.multiply(REM_BASE_PX)
            ?.stripTrailingZeros()
            ?.toPlainString()
            ?.let { value -> if (value == "-0") "0" else value }

    return pixels?.let { value -> "$this, ${value}px" } ?: this
}

private val REM_VALUE = Regex("""([+-]?(?:\d+(?:\.\d+)?|\.\d+))rem""", RegexOption.IGNORE_CASE)
private val REM_BASE_PX = BigDecimal("16")
