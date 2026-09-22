package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.units.RemUnitConverter

internal fun designTokenValuePresentation(value: String): String {
    val remValue =
        EXACT_REM_VALUE
            .matchEntire(value.trim())
            ?.groupValues
            ?.getOrNull(1)
            ?.toBigDecimalOrNull()

    return remValue
        ?.let { current -> "$value · ${RemUnitConverter.pxPresentation(current)}" }
        ?: value
}

private val EXACT_REM_VALUE =
    Regex(
        pattern = """([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)rem""",
        option = RegexOption.IGNORE_CASE,
    )
