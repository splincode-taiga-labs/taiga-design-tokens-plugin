package org.taigaui.designtokens.index

import java.nio.file.Path

enum class DesignTokenSourceFormat {
    CSS,
    LESS,
    SCSS,
    UNKNOWN,
    ;

    companion object {
        fun from(sourceFile: Path): DesignTokenSourceFormat =
            when (
                sourceFile.fileName
                    ?.toString()
                    ?.substringAfterLast('.', missingDelimiterValue = "")
                    ?.lowercase()
            ) {
                "css" -> CSS
                "less" -> LESS
                "scss" -> SCSS
                else -> UNKNOWN
            }
    }
}

data class DesignTokenOrigin(
    val sourceFile: Path,
    val line: Int,
    val format: DesignTokenSourceFormat,
    val selectorChain: List<String> = emptyList(),
    val packageName: String? = null,
    val packageVersion: String? = null,
    val sharedAcrossPlatforms: Boolean = false,
    val cascadeOrder: Int? = null,
)
