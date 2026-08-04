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
        designTokensPackage.effectiveSourcePackages
            .flatMap(::scan)
            .distinct()
            .sortedWith(DECLARATION_COMPARATOR)

    private fun scan(sourcePackage: DesignTokenSourcePackage): List<DesignTokenDeclaration> =
        sourcePackage.sourceRoots
            .flatMap(sourceFileFinder::find)
            .distinct()
            .flatMap(sourceExtractor::extract)
            .map { declaration ->
                declaration.copy(
                    packageName = sourcePackage.name,
                    packageVersion = sourcePackage.version,
                )
            }

    private companion object {
        val DECLARATION_COMPARATOR =
            compareBy<DesignTokenDeclaration>(
                { declaration -> declaration.name },
                { declaration -> declaration.packageName.orEmpty() },
                { declaration -> declaration.sourceFile.toString() },
                { declaration -> declaration.line },
                { declaration -> declaration.value },
            )
    }
}
