package org.taigaui.designtokens.tokenindex

import org.taigaui.designtokens.packageindex.DesignTokensPackage

class DesignTokensPackageScanner(
    private val sourceFileFinder: DesignTokenSourceFileFinder = DesignTokenSourceFileFinder(),
    private val declarationParser: DesignTokenDeclarationParser = DesignTokenDeclarationParser(),
) {
    fun scan(designTokensPackage: DesignTokensPackage): List<DesignTokenDeclaration> =
        sourceFileFinder.find(designTokensPackage.realRoot)
            .flatMap(declarationParser::parse)
}
