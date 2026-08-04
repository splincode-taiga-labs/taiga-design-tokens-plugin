package org.taigaui.designtokens.packageinfo

import java.nio.file.Files
import java.nio.file.Path

data class PackageJsonMetadata(
    val name: String,
    val version: String,
    val exportsStyles: Boolean,
    val exportsPackageRoot: Boolean,
)

class PackageJsonReader {
    fun readVersion(packageJson: Path): String? = readMetadata(packageJson)?.version

    fun readMetadata(packageJson: Path): PackageJsonMetadata? {
        val content = runCatching { Files.readString(packageJson) }.getOrNull() ?: return null
        val name = content.readProperty(NAME_PROPERTY) ?: return null
        val version = content.readProperty(VERSION_PROPERTY) ?: return null

        return PackageJsonMetadata(
            name = name,
            version = version,
            exportsStyles = STYLES_EXPORT.containsMatchIn(content),
            exportsPackageRoot = PACKAGE_ROOT_EXPORT.containsMatchIn(content),
        )
    }

    private fun String.readProperty(pattern: Regex): String? =
        pattern
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf(String::isNotEmpty)

    private companion object {
        val NAME_PROPERTY = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"")
        val VERSION_PROPERTY = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
        val STYLES_EXPORT = Regex("\"\\./styles(?:/\\*)?\"\\s*:")
        val PACKAGE_ROOT_EXPORT = Regex("\"\\./\\*\"\\s*:")
    }
}
