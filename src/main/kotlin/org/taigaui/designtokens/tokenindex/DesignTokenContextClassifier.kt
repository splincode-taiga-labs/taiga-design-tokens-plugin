package org.taigaui.designtokens.tokenindex

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
            theme = classifyTheme(markers),
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
        if (MOBILE_MARKER in markers || selectorChain.any(MOBILE_PLATFORM_SELECTOR::containsMatchIn)) {
            DesignTokenPlatform.MOBILE
        } else {
            DesignTokenPlatform.DESKTOP
        }

    private fun classifyTheme(markers: Set<String>): DesignTokenTheme {
        val hasLight = LIGHT_MARKER in markers
        val hasDark = DARK_MARKER in markers

        return when {
            hasLight && !hasDark -> DesignTokenTheme.LIGHT
            hasDark && !hasLight -> DesignTokenTheme.DARK
            else -> DesignTokenTheme.UNSPECIFIED
        }
    }

    private companion object {
        const val MOBILE_MARKER = "mobile"
        const val LIGHT_MARKER = "light"
        const val DARK_MARKER = "dark"

        val MOBILE_PLATFORM_SELECTOR =
            Regex(
                pattern = """\[\s*tuiPlatform\s*=\s*(['"])(?:android|ios)\1\s*]""",
                option = RegexOption.IGNORE_CASE,
            )
    }
}
