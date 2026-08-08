package org.taigaui.designtokens.project

import java.nio.file.Files
import java.nio.file.Path

internal object ProjectStylesheetPathResolver {
    val supportedExtensions = listOf("less", "css", "scss")

    fun normalize(path: Path): Path = path.toAbsolutePath().normalize()

    fun isStylesheet(path: Path): Boolean = extension(path) in supportedExtensions

    fun isNodeModulesPath(
        path: Path,
        workspaceRoot: Path,
    ): Boolean = normalize(path).startsWith(normalize(workspaceRoot).resolve(NODE_MODULES))

    fun resolveSourceFile(path: Path): Path? {
        val normalizedPath = normalize(path)
        val fileName = normalizedPath.fileName?.toString().orEmpty()
        val extension = extension(normalizedPath)
        val candidates =
            buildList {
                add(normalizedPath)

                if (extension.isNotEmpty()) {
                    add(normalizedPath.resolveSibling("_$fileName"))
                } else {
                    supportedExtensions.forEach { supportedExtension ->
                        add(normalizedPath.resolveSibling("${normalizedPath.fileName}.$supportedExtension"))
                        add(normalizedPath.resolveSibling("_${normalizedPath.fileName}.$supportedExtension"))
                    }
                }

                supportedExtensions.forEach { supportedExtension ->
                    add(normalizedPath.resolve("index.$supportedExtension"))
                    add(normalizedPath.resolve("_index.$supportedExtension"))
                }
            }

        return candidates.firstOrNull { candidate ->
            Files.isRegularFile(candidate) && isStylesheet(candidate)
        }
    }

    fun isExternalImport(value: String): Boolean =
        value.startsWith("http://", ignoreCase = true) ||
            value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("data:", ignoreCase = true) ||
            value.startsWith("sass:", ignoreCase = true)

    fun isPackageImport(value: String): Boolean = value.startsWith("@taiga-ui/") || value.startsWith("node_modules/")

    private fun extension(path: Path): String =
        path.fileName
            ?.toString()
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            .orEmpty()

    private const val NODE_MODULES = "node_modules"
}
