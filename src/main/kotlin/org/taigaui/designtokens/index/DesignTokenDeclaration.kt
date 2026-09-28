package org.taigaui.designtokens.index

import java.nio.file.Path

data class DesignTokenDeclaration(
    val name: String,
    val value: String,
    val sourceFile: Path,
    val line: Int,
    val selectorChain: List<String> = emptyList(),
    val packageName: String? = null,
    val packageVersion: String? = null,
    val packageRoot: Path? = null,
    val cascadeOrder: Int? = null,
    val localOverride: Boolean = false,
    val deprecation: DesignTokenDeprecation? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is DesignTokenDeclaration &&
            name == other.name &&
            value == other.value &&
            sourceFile == other.sourceFile &&
            line == other.line &&
            selectorChain == other.selectorChain

    override fun hashCode(): Int {
        var result = name.hashCode()

        result = 31 * result + value.hashCode()
        result = 31 * result + sourceFile.hashCode()
        result = 31 * result + line
        result = 31 * result + selectorChain.hashCode()

        return result
    }
}
