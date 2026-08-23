package org.taigaui.designtokens.icons

internal data class IconCompletionContext(
    val prefix: String,
)

internal object IconCompletionContextFinder {
    fun find(
        text: CharSequence,
        offset: Int,
    ): IconCompletionContext? =
        offset
            .takeIf { currentOffset -> currentOffset in 0..text.length }
            ?.let { currentOffset ->
                findPrefixStart(text, currentOffset)
                    ?.let { prefixStart -> createContext(text, currentOffset, prefixStart) }
            }

    private fun createContext(
        text: CharSequence,
        offset: Int,
        prefixStart: Int,
    ): IconCompletionContext? {
        val quoteOffset = prefixStart - 1
        val prefix = text.subSequence(prefixStart, offset).toString()
        val suffix = prefix.removePrefix(ICON_PREFIX)
        val validContext =
            quoteOffset >= 0 &&
                text[quoteOffset] in ICON_STRING_QUOTES &&
                prefix.startsWith(ICON_PREFIX) &&
                suffix.all(Char::isIconNameCharacter)

        return prefix
            .takeIf { validContext }
            ?.let(::IconCompletionContext)
    }

    private fun findPrefixStart(
        text: CharSequence,
        offset: Int,
    ): Int? {
        val latestStart = offset - ICON_PREFIX.length

        return (latestStart downTo 0)
            .takeIf { latestStart >= 0 }
            ?.firstOrNull { start ->
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
}

internal fun Char.isIconNameCharacter(): Boolean = isLetterOrDigit() || this == '-' || this == '_' || this == '.'

internal val ICON_SUPPORTED_EXTENSIONS = setOf("ts", "tsx", "js", "jsx", "mjs", "cjs", "html")
