package org.taigaui.designtokens.icons

internal data class IconCompletionContext(
    val prefix: String,
)

internal object IconCompletionContextFinder {
    fun find(
        text: CharSequence,
        offset: Int,
    ): IconCompletionContext? {
        if (offset !in 0..text.length) {
            return null
        }

        val prefixStart = findPrefixStart(text, offset) ?: return null
        val quoteOffset = prefixStart - 1

        if (quoteOffset < 0 || text[quoteOffset] !in QUOTES) {
            return null
        }

        val prefix = text.subSequence(prefixStart, offset).toString()
        val suffix = prefix.removePrefix(ICON_PREFIX)

        return prefix
            .takeIf { value -> value.startsWith(ICON_PREFIX) && suffix.all(Char::isIconNameCharacter) }
            ?.let(::IconCompletionContext)
    }

    private fun findPrefixStart(
        text: CharSequence,
        offset: Int,
    ): Int? {
        val latestStart = offset - ICON_PREFIX.length

        if (latestStart < 0) {
            return null
        }

        return (latestStart downTo 0)
            .firstOrNull { start ->
                text.regionMatches(start, ICON_PREFIX) &&
                    text.subSequence(start + ICON_PREFIX.length, offset).all(Char::isIconNameCharacter)
            }
    }

    private fun CharSequence.regionMatches(
        start: Int,
        expected: String,
    ): Boolean =
        start >= 0 &&
            start + expected.length <= length &&
            expected.indices.all { index -> this[start + index] == expected[index] }

    private val QUOTES = setOf('\'', '"', '`')
}

internal fun Char.isIconNameCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_' || this == '.'

internal val ICON_SUPPORTED_EXTENSIONS = setOf("ts", "tsx", "js", "jsx", "mjs", "cjs", "html")
