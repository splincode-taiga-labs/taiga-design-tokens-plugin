package org.taigaui.designtokens.index

import org.taigaui.designtokens.packageinfo.DesignTokenSourcePackage
import org.taigaui.designtokens.packageinfo.DesignTokensPackage

class DesignTokensPackageScanner(
    private val sourceExtractor: DesignTokenSourceExtractor,
    private val sourceFileFinder: DesignTokenSourceFileFinder = DesignTokenSourceFileFinder(),
) {
    internal constructor() : this(
        sourceExtractor = DesignTokenDeclarationParser(),
    )

    fun scan(designTokensPackage: DesignTokensPackage): List<DesignTokenDeclaration> =
        if (designTokensPackage.sourcePackages.isEmpty()) {
            sourceFileFinder
                .find(designTokensPackage.realRoot)
                .flatMap(sourceExtractor::extract)
        } else {
            designTokensPackage.sourcePackages
                .flatMap(::scan)
                .distinct()
        }

    private fun scan(sourcePackage: DesignTokenSourcePackage): List<DesignTokenDeclaration> =
        sourcePackage.sourceRoots
            .flatMap(sourceFileFinder::find)
            .distinct()
            .flatMap(sourceExtractor::extract)
            .map { declaration ->
                declaration.copy(
                    packageName = sourcePackage.name,
                    packageVersion = sourcePackage.version,
                    packageRoot = sourcePackage.realRoot,
                )
            }
}
