package org.taigaui.designtokens.index

internal fun String.isValidDesignTokenName(): Boolean = DESIGN_TOKEN_NAME.matches(this)

private val DESIGN_TOKEN_NAME = Regex("""--tui-[A-Za-z0-9_-]+""")
