package org.taigaui.designtokens.tokenindex

import java.nio.file.Files
import java.nio.file.Path

class DesignTokenSourceFileFinder {
    fun find(packageRoot: Path): List<Path> {
        val normalizedRoot = packageRoot.toAbsolutePath().normalize()

        if (!Files.isDirectory(normalizedRoot)) {
            return emptyList()
        }

        return runCatching {
            Files.walk(normalizedRoot).use { paths ->
                paths
                    .filter { Files.isRegularFile(it) }
                    .filter(::isSupportedSourceFile)
                    .map { it.toAbsolutePath().normalize() }
                    .sorted(compareBy<Path> { it.toString() })
                    .toList()
            }
        }.getOrElse { emptyList() }
    }

    private fun isSupportedSourceFile(path: Path): Boolean {
        val extension =
            path.fileName
                .toString()
                .substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()

        return extension in SUPPORTED_EXTENSIONS
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("css", "scss", "less")
    }
}
