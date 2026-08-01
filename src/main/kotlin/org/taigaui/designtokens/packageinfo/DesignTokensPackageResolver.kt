package org.taigaui.designtokens.packageinfo

import java.nio.file.Files
import java.nio.file.Path

class DesignTokensPackageResolver(
    private val packageJsonReader: PackageJsonReader = PackageJsonReader(),
) {
    fun resolve(start: Path): DesignTokensPackage? {
        val normalizedStart = start.toAbsolutePath().normalize()
        val startDirectory =
            when {
                Files.isDirectory(normalizedStart) -> normalizedStart
                Files.isRegularFile(normalizedStart) -> normalizedStart.parent
                else -> normalizedStart
            }

        return generateSequence(startDirectory) { it.parent }
            .map { it.resolve(PACKAGE_JSON) }
            .firstOrNull { Files.isRegularFile(it) }
            ?.let(::readPackage)
    }

    private fun readPackage(packageJson: Path): DesignTokensPackage? {
        val version = packageJsonReader.readVersion(packageJson) ?: return null
        val root = packageJson.parent.toAbsolutePath().normalize()
        val realRoot = runCatching { root.toRealPath() }.getOrElse { root }

        return DesignTokensPackage(
            root = root,
            realRoot = realRoot,
            version = version,
        )
    }

    private companion object {
        val PACKAGE_JSON =
            Path.of(
                "node_modules",
                "@taiga-ui",
                "design-tokens",
                "package.json",
            )
    }
}
