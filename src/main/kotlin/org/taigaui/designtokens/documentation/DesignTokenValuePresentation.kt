package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.units.RemUnitConverter

internal fun designTokenValuePresentation(value: String): String {
    val match = EXACT_REM_VALUE.matchEntire(value.trim()) ?: return value
    val remValue = match.groupValues[1].toBigDecimalOrNull() ?: return value

    return "$value · ${RemUnitConverter.pxPresentation(remValue)}"
}

private val EXACT_REM_VALUE =
    Regex(
        pattern = """([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)rem""",
        option = RegexOption.IGNORE_CASE,
    )
