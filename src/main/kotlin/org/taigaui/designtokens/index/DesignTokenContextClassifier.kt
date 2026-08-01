package org.taigaui.designtokens.index

import java.nio.file.Path

class DesignTokenContextClassifier {
    fun classify(
        packageRoot: Path,
        declaration: DesignTokenDeclaration,
    ): DesignTokenContext {
        val markers =
            pathMarkers(
                packageRoot = packageRoot,
                sourceFile = declaration.sourceFile,
            )

        return DesignTokenContext(
            platform = classifyPlatform(markers, declaration.selectorChain),
            theme = classifyTheme(markers, declaration.selectorChain),
        )
    }

    private fun pathMarkers(
        packageRoot: Path,
        sourceFile: Path,
    ): Set<String> {
        val normalizedRoot = packageRoot.toAbsolutePath().normalize()
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()

        return if (normalizedSourceFile.startsWith(normalizedRoot)) {
            markersFrom(normalizedRoot.relativize(normalizedSourceFile))
        } else {
            emptySet()
        }
    }

    private fun markersFrom(relativePath: Path): Set<String> =
        buildSet {
            relativePath.forEach { segment ->
                add(segment.toString().lowercase())
            }

            relativePath.fileName
                ?.toString()
                ?.lowercase()
                ?.let { fileName ->
                    add(fileName.substringBeforeLast('.', missingDelimiterValue = fileName))
                }
        }

    private fun classifyPlatform(
        markers: Set<String>,
        selectorChain: List<String>,
    ): DesignTokenPlatform =
        if (MOBILE_MARKER in markers || selectorChain.containsMatch(MOBILE_PLATFORM_SELECTOR)) {
            DesignTokenPlatform.MOBILE
        } else {
            DesignTokenPlatform.DESKTOP
        }

    private fun classifyTheme(
        markers: Set<String>,
        selectorChain: List<String>,
    ): DesignTokenTheme {
        val hasLight = LIGHT_MARKER in markers || selectorChain.containsMatch(LIGHT_THEME_SELECTOR)
        val hasDark = DARK_MARKER in markers || selectorChain.containsMatch(DARK_THEME_SELECTOR)

        return when {
            hasLight && !hasDark -> DesignTokenTheme.LIGHT
            hasDark && !hasLight -> DesignTokenTheme.DARK
            else -> DesignTokenTheme.UNSPECIFIED
        }
    }

    private fun List<String>.containsMatch(pattern: Regex): Boolean = any(pattern::containsMatchIn)

    private companion object {
        const val MOBILE_MARKER = "mobile"
        const val LIGHT_MARKER = "light"
        const val DARK_MARKER = "dark"

        val MOBILE_PLATFORM_SELECTOR =
            attributeSelector(
                attribute = "(?:tuiPlatform|data-platform)",
                value = "(?:android|ios)",
            )
        val LIGHT_THEME_SELECTOR = attributeSelector(attribute = "tuiTheme", value = "light")
        val DARK_THEME_SELECTOR = attributeSelector(attribute = "tuiTheme", value = "dark")

        fun attributeSelector(
            attribute: String,
            value: String,
        ): Regex =
            Regex(
                pattern = """\[\s*$attribute\s*=\s*(?:['"]$value['"]|$value)\s*]""",
                option = RegexOption.IGNORE_CASE,
            )
    }
}
