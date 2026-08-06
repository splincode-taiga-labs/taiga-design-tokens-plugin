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

    private fun isGlobalSelector(selector: String): Boolean {
        val compact = selector.replace(WHITESPACE, "")

        if (THEME_MIXIN.matches(compact)) {
            return true
        }

        val withoutGlobalParts =
            GLOBAL_SELECTOR_PART
                .replace(compact, "")
                .replace("&", "")

        return withoutGlobalParts.isEmpty() && GLOBAL_SELECTOR_PART.containsMatchIn(compact)
    }

    private val GLOBAL_SELECTOR_PART =
        Regex(
            pattern =
                """(?:\:root|\:host|\bhtml\b|\bbody\b|\[(?:tuiPlatform|data-platform)=(?:['"]?(?:ios|android)['"]?)\]|\[tuiTheme=(?:['"]?(?:light|dark)['"]?)\])""",
            option = RegexOption.IGNORE_CASE,
        )
    private val THEME_MIXIN =
        Regex(
            """\.(?:tui-theme-)?(?:light|dark)\(\)""",
            RegexOption.IGNORE_CASE,
        )
    private val WHITESPACE = Regex("""\s+""")
}
