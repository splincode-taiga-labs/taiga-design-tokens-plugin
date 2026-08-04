package org.taigaui.designtokens.packageinfo

import java.nio.file.Files
import java.nio.file.Path

class DesignTokensPackageResolver(
    private val packageJsonReader: PackageJsonReader = PackageJsonReader(),
) {
    fun resolve(start: Path): DesignTokensPackage? =
        findNearestTaigaUiScope(start)
            ?.let { scopeRoot ->
                val sourcePackages = discoverSourcePackages(scopeRoot)

                sourcePackages
                    .takeIf(List<DesignTokenSourcePackage>::isNotEmpty)
                    ?.let(::createPackageSet)
            }

    private fun createPackageSet(sourcePackages: List<DesignTokenSourcePackage>): DesignTokensPackage {
        val primary = sourcePackages.minBy(::sourcePackageRank)

        return DesignTokensPackage(
            root = primary.root,
            realRoot = primary.realRoot,
            version = primary.version,
            sourcePackages = sourcePackages,
            cacheVersion = sourcePackages.packageSetVersion(),
        )
    }

    private fun findNearestTaigaUiScope(start: Path): Path? {
        val normalizedStart = start.toAbsolutePath().normalize()
        val startDirectory =
            when {
                Files.isDirectory(normalizedStart) -> normalizedStart
                Files.isRegularFile(normalizedStart) -> normalizedStart.parent
                else -> normalizedStart
            }

        return generateSequence(startDirectory) { directory -> directory.parent }
            .map { directory -> directory.resolve(TAIGA_UI_SCOPE) }
            .firstOrNull(Files::isDirectory)
    }

    private fun discoverSourcePackages(scopeRoot: Path): List<DesignTokenSourcePackage> =
        runCatching {
            Files.list(scopeRoot).use { paths ->
                paths
                    .filter(Files::isDirectory)
                    .map(::readSourcePackage)
                    .filter { sourcePackage -> sourcePackage != null }
                    .map { sourcePackage -> requireNotNull(sourcePackage) }
                    .sorted(SOURCE_PACKAGE_COMPARATOR)
                    .toList()
            }
        }.getOrElse { emptyList() }

    private fun readSourcePackage(logicalRoot: Path): DesignTokenSourcePackage? =
        packageJsonReader
            .readMetadata(logicalRoot.resolve(PACKAGE_JSON))
            ?.takeIf { metadata -> metadata.name.startsWith(TAIGA_UI_PACKAGE_PREFIX) }
            ?.let { metadata -> createSourcePackage(logicalRoot, metadata) }

    private fun createSourcePackage(
        logicalRoot: Path,
        metadata: PackageJsonMetadata,
    ): DesignTokenSourcePackage? {
        val realRoot = logicalRoot.toRealPathOrSelf()
        val sourceRoots = metadata.resolveSourceRoots(realRoot)

        return sourceRoots
            .takeIf(List<Path>::isNotEmpty)
            ?.let { roots ->
                DesignTokenSourcePackage(
                    name = metadata.name,
                    root = logicalRoot.toAbsolutePath().normalize(),
                    realRoot = realRoot,
                    version = metadata.version,
                    sourceRoots = roots,
                )
            }
    }

    private fun PackageJsonMetadata.resolveSourceRoots(realRoot: Path): List<Path> =
        when {
            name == DESIGN_TOKENS_PACKAGE -> listOf(realRoot)
            name == STYLES_PACKAGE && exportsPackageRoot -> listOf(realRoot)
            exportsStyles && Files.isDirectory(realRoot.resolve(STYLES_DIRECTORY)) ->
                listOf(realRoot.resolve(STYLES_DIRECTORY))

            else -> emptyList()
        }

    private fun sourcePackageRank(sourcePackage: DesignTokenSourcePackage): Int =
        when (sourcePackage.name) {
            DESIGN_TOKENS_PACKAGE -> 0
            STYLES_PACKAGE -> 1
            else -> 2
        }

    private fun List<DesignTokenSourcePackage>.packageSetVersion(): String =
        joinToString(separator = "|") { sourcePackage ->
            val directory =
                sourcePackage.root.fileName
                    ?.toString()
                    .orEmpty()

            "${sourcePackage.name}@${sourcePackage.version}:$directory"
        }

    private fun Path.toRealPathOrSelf(): Path =
        runCatching { toRealPath() }
            .getOrElse { toAbsolutePath().normalize() }

    private companion object {
        val TAIGA_UI_SCOPE = Path.of("node_modules", "@taiga-ui")
        val SOURCE_PACKAGE_COMPARATOR =
            compareBy<DesignTokenSourcePackage>(
                { sourcePackage -> sourcePackage.name },
                { sourcePackage -> sourcePackage.version },
                { sourcePackage -> sourcePackage.realRoot.toString() },
            )
        const val PACKAGE_JSON = "package.json"
        const val STYLES_DIRECTORY = "styles"
        const val TAIGA_UI_PACKAGE_PREFIX = "@taiga-ui/"
        const val DESIGN_TOKENS_PACKAGE = "@taiga-ui/design-tokens"
        const val STYLES_PACKAGE = "@taiga-ui/styles"
    }
}
