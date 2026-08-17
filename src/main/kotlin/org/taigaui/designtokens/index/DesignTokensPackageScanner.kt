package org.taigaui.designtokens.index

import org.taigaui.designtokens.packageinfo.DesignTokenSourcePackage
import org.taigaui.designtokens.packageinfo.DesignTokensPackage
import java.nio.file.Path

class DesignTokensPackageScanner(
    private val sourceExtractor: DesignTokenSourceExtractor,
    private val sourceFileFinder: DesignTokenSourceFileFinder = DesignTokenSourceFileFinder(),
) {
    private val importGraph = DesignTokenPackageImportGraph()

    internal constructor() : this(
        sourceExtractor = DesignTokenDeclarationParser(),
    )

    fun scan(designTokensPackage: DesignTokensPackage): List<DesignTokenDeclaration> =
        if (designTokensPackage.sourcePackages.isEmpty()) {
            sourceFileFinder
                .find(designTokensPackage.realRoot)
                .flatMap(sourceExtractor::extract)
        } else {
            scanInstalledPackages(designTokensPackage.sourcePackages)
        }

    fun scanAll(designTokensPackage: DesignTokensPackage): List<DesignTokenDeclaration> =
        if (designTokensPackage.sourcePackages.isEmpty()) {
            sourceFileFinder
                .find(designTokensPackage.realRoot)
                .flatMap(sourceExtractor::extract)
        } else {
            designTokensPackage.sourcePackages
                .flatMap(::scan)
                .distinct()
        }

    private fun scanInstalledPackages(sourcePackages: List<DesignTokenSourcePackage>): List<DesignTokenDeclaration> {
        val proprietary =
            sourcePackages.firstOrNull { sourcePackage ->
                sourcePackage.name == PROPRIETARY_PACKAGE
            }

        if (proprietary == null) {
            return sourcePackages
                .flatMap(::scan)
                .distinct()
        }

        val proprietaryFiles = proprietary.sourceFiles()
        val reachableFiles =
            importGraph.findReachableFiles(
                entryFiles = proprietaryFiles,
                sourcePackages = sourcePackages,
            )

        return sourcePackages
            .flatMap { sourcePackage ->
                val files =
                    if (sourcePackage == proprietary) {
                        proprietaryFiles
                    } else {
                        reachableFiles[sourcePackage.name].orEmpty()
                    }

                scan(sourcePackage, files)
            }.distinct()
    }

    private fun scan(sourcePackage: DesignTokenSourcePackage): List<DesignTokenDeclaration> =
        scan(sourcePackage, sourcePackage.sourceFiles())

    private fun scan(
        sourcePackage: DesignTokenSourcePackage,
        sourceFiles: List<Path>,
    ): List<DesignTokenDeclaration> =
        sourceFiles
            .flatMap(sourceExtractor::extract)
            .map { declaration ->
                declaration.copy(
                    packageName = sourcePackage.name,
                    packageVersion = sourcePackage.version,
                    packageRoot = sourcePackage.realRoot,
                )
            }

    private fun DesignTokenSourcePackage.sourceFiles(): List<Path> =
        sourceRoots
            .flatMap(sourceFileFinder::find)
            .distinct()

    private companion object {
        const val PROPRIETARY_PACKAGE = "@taiga-ui/proprietary"
    }
}
