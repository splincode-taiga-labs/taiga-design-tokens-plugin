package org.taigaui.designtokens.tokenindex

import java.nio.file.Path

class DesignTokenContextClassifier {
    fun classify(
        packageRoot: Path,
        sourceFile: Path,
    ): DesignTokenContext {
        val normalizedRoot = packageRoot.toAbsolutePath().normalize()
        val normalizedSourceFile = sourceFile.toAbsolutePath().normalize()

        if (!normalizedSourceFile.startsWith(normalizedRoot)) {
            return DesignTokenContext.UNSPECIFIED
        }

        val markers = markersFrom(normalizedRoot.relativize(normalizedSourceFile))

        return DesignTokenContext(
            platform = classifyPlatform(markers),
            theme = classifyTheme(markers),
        )
    }

    private fun markersFrom(relativePath: Path): Set<String> = buildSet {
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

    private fun classifyPlatform(markers: Set<String>): DesignTokenPlatform {
        val hasDesktop = DESKTOP_MARKER in markers
        val hasMobile = MOBILE_MARKER in markers

        return when {
            hasDesktop && !hasMobile -> DesignTokenPlatform.DESKTOP
            hasMobile && !hasDesktop -> DesignTokenPlatform.MOBILE
            else -> DesignTokenPlatform.UNSPECIFIED
        }
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
        const val DESKTOP_MARKER = "desktop"
        const val MOBILE_MARKER = "mobile"
        const val LIGHT_MARKER = "light"
        const val DARK_MARKER = "dark"
    }
}
