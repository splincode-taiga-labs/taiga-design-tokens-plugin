package org.taigaui.designtokens.index

import java.nio.file.Path

data class DesignTokenDeclaration(
    val name: String,
    val value: String,
    val sourceFile: Path,
    val line: Int,
    val selectorChain: List<String> = emptyList(),
)
