package org.taigaui.designtokens.tokenindex

import org.taigaui.designtokens.packageindex.DesignTokensPackage

class DesignTokensPackageScanner(
    private val sourceExtractor: DesignTokenSourceExtractor,
    private val sourceFileFinder: DesignTokenSourceFileFinder = DesignTokenSourceFileFinder(),
) {
    internal constructor() : this(
        sourceExtractor = DesignTokenDeclarationParser(),
    )

    fun scan(designTokensPackage: DesignTokensPackage): List<DesignTokenDeclaration> =
        sourceFileFinder
            .find(designTokensPackage.realRoot)
            .flatMap(sourceExtractor::extract)
}
