package org.taigaui.designtokens.index

import java.nio.file.Path

fun interface DesignTokenSourceExtractor {
    fun extract(sourceFile: Path): List<DesignTokenDeclaration>
}
