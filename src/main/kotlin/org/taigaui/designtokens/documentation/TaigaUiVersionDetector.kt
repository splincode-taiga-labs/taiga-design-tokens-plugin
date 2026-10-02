package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.packageinfo.PackageJsonReader
import org.taigaui.designtokens.packageinfo.TaigaUiPackageLocator
import java.nio.file.Path

internal data class TaigaUiProjectContext(
    val version: String,
    val majorVersion: Int,
    val versionSourcePackage: String,
    val installedPackages: Set<String>,
    val packageScopeIdentity: String,
)

internal class TaigaUiVersionDetector(
    private val packageLocator: TaigaUiPackageLocator = TaigaUiPackageLocator(),
    private val packageJsonReader: PackageJsonReader = PackageJsonReader(),
) {
    fun detect(sourceFile: Path): TaigaUiProjectContext? {
        val scope = packageLocator.locate(sourceFile) ?: return null
        val candidates =
            scope.packages.values
                .filterNot { located -> located.name in INDEPENDENT_VERSION_PACKAGES }
                .sortedWith(
                    compareBy(
                        { located -> if (located.name == CORE_PACKAGE) 0 else 1 },
                        { located -> located.name },
                    ),
                )

        candidates.forEach { located ->
            val version = packageJsonReader.readVersion(located.root.resolve(PACKAGE_JSON)) ?: return@forEach
            val majorVersion = parseMajor(version) ?: return@forEach

            return TaigaUiProjectContext(
                version = version,
                majorVersion = majorVersion,
                versionSourcePackage = located.name,
                installedPackages = scope.packages.keys.toSortedSet(),
                packageScopeIdentity = scope.identity,
            )
        }

        return null
    }

    private fun parseMajor(version: String): Int? =
        MAJOR_VERSION
            .find(version.trim())
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private companion object {
        const val CORE_PACKAGE = "@taiga-ui/core"
        const val PACKAGE_JSON = "package.json"
        val INDEPENDENT_VERSION_PACKAGES = setOf("@taiga-ui/design-tokens")
        val MAJOR_VERSION = Regex("^(\\d+)(?:\\.|-|$)")
    }
}
