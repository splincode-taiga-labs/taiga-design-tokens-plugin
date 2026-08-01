package org.taigaui.designtokens.packageinfo

import java.nio.file.Files
import java.nio.file.Path

class PackageJsonReader {
    fun readVersion(packageJson: Path): String? {
        val content = runCatching { Files.readString(packageJson) }.getOrNull() ?: return null

        return VERSION_PROPERTY
            .find(content)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private companion object {
        val VERSION_PROPERTY = Regex("\"version\"\\s*:\\s*\"([^\"]+)\"")
    }
}
