package org.taigaui.designtokens.tokenindex

import java.nio.file.Path

fun interface DesignTokenSourceExtractor {
    fun extract(sourceFile: Path): List<DesignTokenDeclaration>
}
