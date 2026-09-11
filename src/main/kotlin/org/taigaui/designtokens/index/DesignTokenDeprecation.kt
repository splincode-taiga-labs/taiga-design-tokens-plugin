package org.taigaui.designtokens.index

data class DesignTokenDeprecation(
    val message: String? = null,
    val replacement: String? = null,
)

internal object DesignTokenDeprecationParser {
    fun parse(
        commentText: String,
        tokenName: String,
    ): DesignTokenDeprecation? {
        val normalized = commentText.normalizeComment()
        val marker = DEPRECATED_MARKER.find(normalized) ?: return null
        val details =
            normalized
                .substring(marker.range.last + 1)
                .trim()
                .removePrefix(":")
                .trim()
        val replacements =
            TOKEN_NAME
                .findAll(details)
                .map(MatchResult::value)
                .filterNot(tokenName::equals)
                .distinct()
                .toList()

        return DesignTokenDeprecation(
            message = details.takeIf(String::isNotEmpty),
            replacement = replacements.singleOrNull(),
        )
    }
}

private fun String.normalizeComment(): String =
    lineSequence()
        .map(String::trim)
        .map { line ->
            line
                .removeSuffix("*/")
                .removePrefix("/**")
                .removePrefix("/*")
                .removePrefix("//")
                .removePrefix("*")
                .trim()
        }.filter(String::isNotEmpty)
        .joinToString(separator = " ")
        .replace(WHITESPACE, " ")
        .trim()

private val DEPRECATED_MARKER = Regex("@deprecated\\b", RegexOption.IGNORE_CASE)
private val TOKEN_NAME = Regex("--tui-[A-Za-z0-9_-]+")
private val WHITESPACE = Regex("\\s+")
