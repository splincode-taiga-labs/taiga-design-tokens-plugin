package org.taigaui.designtokens.units

internal object AngularRemStyleBindingHintCollector {
    fun collect(content: CharSequence): List<RemInlayHint> {
        val matches = ANGULAR_REM_STYLE_BINDING.findAll(content)

        return matches.mapNotNull { match ->
            val value = match.groups[2]?.value?.toBigDecimalOrNull() ?: return@mapNotNull null

            RemInlayHint(
                offset = match.range.last + 1,
                text = " ${RemUnitConverter.pxPresentation(value)}",
                tooltip = RemUnitConverter.presentation(value),
            )
        }.toList()
    }
}

private const val NUMBER_PATTERN =
    "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"
private val ANGULAR_REM_STYLE_BINDING =
    Regex(
        pattern =
            """\[style\.[A-Za-z_-][A-Za-z0-9_-]*\.rem]\s*=\s*([\"'])\s*($NUMBER_PATTERN)\s*\1""",
    )
