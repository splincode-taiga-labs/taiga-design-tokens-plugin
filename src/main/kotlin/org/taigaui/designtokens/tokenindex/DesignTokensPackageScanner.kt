package org.taigaui.designtokens.tokenindex

import org.taigaui.designtokens.packageindex.DesignTokensPackage

class DesignTokensPackageScanner(
    private val sourceExtractor: DesignTokenSourceExtractor,
    private val sourceFileFinder: DesignTokenSourceFileFinder = DesignTokenSourceFileFinder(),
) {
    fun scan(designTokensPackage: DesignTokensPackage): List<DesignTokenDeclaration> =
        sourceFileFinder
            .find(designTokensPackage.realRoot)
            .flatMap(sourceExtractor::extract)
}
