package org.taigaui.designtokens.icons

internal data class IconReferenceAtOffset(
    val name: String,
    val startOffset: Int,
    val endOffset: Int,
)

internal object IconReferenceAtOffsetFinder {
    fun find(
        text: CharSequence,
        offset: Int,
    ): IconReferenceAtOffset? =
        sequenceOf(offset, offset - 1)
            .filter { current -> current in text.indices }
            .mapNotNull { current -> findAt(text, current) }
            .firstOrNull()

    private fun findAt(
        text: CharSequence,
        offset: Int,
    ): IconReferenceAtOffset? {
        if (!text[offset].isIconReferenceCharacter()) {
            return null
        }

        var start = offset
        var end = offset + 1

        while (start > 0 && text[start - 1].isIconReferenceCharacter()) {
            start--
        }

        while (end < text.length && text[end].isIconReferenceCharacter()) {
            end++
        }

        val name = text.subSequence(start, end).toString()
        val validReference =
            start > 0 &&
                text[start - 1] in ICON_STRING_QUOTES &&
                name.startsWith(ICON_PREFIX) &&
                name.length > ICON_PREFIX.length &&
                name.removePrefix(ICON_PREFIX).all(Char::isIconNameCharacter)

        return name
            .takeIf { validReference }
            ?.let { iconName ->
                IconReferenceAtOffset(
                    name = iconName,
                    startOffset = start,
                    endOffset = end,
                )
            }
    }
}

private fun Char.isIconReferenceCharacter(): Boolean = this == '@' || isIconNameCharacter()

internal val ICON_STRING_QUOTES = setOf('\'', '"', '`')
