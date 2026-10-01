package org.taigaui.designtokens.packageinfo

import java.nio.file.Path

data class DesignTokenSourcePackage(
    val name: String,
    val root: Path,
    val realRoot: Path,
    val version: String,
    val sourceRoots: List<Path>,
)

data class DesignTokensPackage(
    val root: Path,
    val realRoot: Path,
    val version: String,
    val sourcePackages: List<DesignTokenSourcePackage> = emptyList(),
    val cacheVersion: String = version,
    val discoveryRoot: Path = root,
    val cacheIdentity: String = realRoot.toAbsolutePath().normalize().toString(),
    val invalidationRoots: Set<Path> = emptySet(),
    val workspaceRoot: Path? = null,
) {
    val effectiveSourcePackages: List<DesignTokenSourcePackage>
        get() =
            sourcePackages.ifEmpty {
                listOf(
                    DesignTokenSourcePackage(
                        name = DESIGN_TOKENS_PACKAGE,
                        root = root,
                        realRoot = realRoot,
                        version = version,
                        sourceRoots = listOf(realRoot),
                    ),
                )
            }

    private companion object {
        const val DESIGN_TOKENS_PACKAGE = "@taiga-ui/design-tokens"
    }
}
