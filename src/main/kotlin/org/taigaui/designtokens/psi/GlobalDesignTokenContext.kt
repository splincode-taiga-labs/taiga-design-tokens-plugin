package org.taigaui.designtokens.psi

internal object GlobalDesignTokenContext {
    fun isGlobal(selectorChain: List<String>): Boolean =
        selectorChain.isNotEmpty() &&
            selectorChain.all { selectorList ->
                selectorList
                    .split(',')
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .all(::isGlobalSelector)
            }

    private fun isGlobalSelector(selector: String): Boolean =
        ROOT_SELECTOR.matches(selector) ||
            PLATFORM_SELECTOR.matches(selector) ||
            THEME_SELECTOR.matches(selector) ||
            THEME_MIXIN.matches(selector)

    private val ROOT_SELECTOR = Regex("""(?:&?:root|:host|html|body)""", RegexOption.IGNORE_CASE)
    private val PLATFORM_SELECTOR =
        Regex(
            """\[(?:tuiPlatform|data-platform)\s*=\s*(?:['"](?:ios|android)['"]|(?:ios|android))\s*]""",
            RegexOption.IGNORE_CASE,
        )
    private val THEME_SELECTOR =
        Regex(
            """\[tuiTheme\s*=\s*(?:['"](?:light|dark)['"]|(?:light|dark))\s*]""",
            RegexOption.IGNORE_CASE,
        )
    private val THEME_MIXIN =
        Regex(
            """\.(?:tui-theme-)?(?:light|dark)\s*\(\s*\)""",
            RegexOption.IGNORE_CASE,
        )
}
