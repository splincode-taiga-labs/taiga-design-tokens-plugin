package org.taigaui.designtokens.icons

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

internal fun interface IconCatalogFetcher {
    fun fetch(): String?
}

internal class IconCatalogLoader(
    private val remoteFetcher: IconCatalogFetcher = TbankIconCatalogFetcher(),
) {
    fun resolveScopeRoot(sourceFile: Path): Path? {
        val normalizedSource = sourceFile.toAbsolutePath().normalize()
        val startDirectory =
            when {
                Files.isDirectory(normalizedSource) -> normalizedSource
                normalizedSource.parent != null -> normalizedSource.parent
                else -> return null
            }

        return generateSequence(startDirectory) { directory -> directory.parent }
            .map { directory -> directory.resolve(TAIGA_UI_SCOPE) }
            .firstOrNull(Files::isDirectory)
    }

    fun load(scopeRoot: Path): List<String> {
        val normalizedScope = scopeRoot.toAbsolutePath().normalize()
        val publicIcons = scanSvgIcons(normalizedScope.resolve(ICONS_SOURCE))
        val proprietaryRoot = normalizedScope.resolve(PROPRIETARY_PACKAGE)

        if (!Files.isDirectory(proprietaryRoot)) {
            return publicIcons
        }

        val tdsIconsRoot = normalizedScope.resolve(TDS_ICONS_SOURCE)
        val proprietaryIcons =
            if (Files.isDirectory(tdsIconsRoot)) {
                scanSvgIcons(tdsIconsRoot)
            } else {
                remoteFetcher
                    .fetch()
                    ?.let(TbankIconCatalogParser::parse)
                    .orEmpty()
            }

        return (publicIcons + proprietaryIcons)
            .distinct()
            .sorted()
    }

    private fun scanSvgIcons(root: Path): List<String> {
        if (!Files.isDirectory(root)) {
            return emptyList()
        }

        return runCatching {
            Files.walk(root).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { file -> file.fileName.toString().endsWith(SVG_EXTENSION, ignoreCase = true) }
                    .map { file -> file.toIconName(root) }
                    .sorted()
                    .toList()
            }
        }.getOrElse { emptyList() }
    }

    private fun Path.toIconName(root: Path): String {
        val relative = root.relativize(this)
        val segments =
            (0 until relative.nameCount)
                .map { index -> relative.getName(index).toString() }
                .toMutableList()
        val lastIndex = segments.lastIndex

        segments[lastIndex] = segments[lastIndex].removeSuffix(SVG_EXTENSION)

        return ICON_PREFIX + segments.joinToString(".")
    }

    private companion object {
        val TAIGA_UI_SCOPE = Path.of("node_modules", "@taiga-ui")
        val ICONS_SOURCE = Path.of("icons", "src")
        val TDS_ICONS_SOURCE = Path.of("tds-icons", "src")
        const val PROPRIETARY_PACKAGE = "proprietary"
        const val SVG_EXTENSION = ".svg"
    }
}

internal object TbankIconCatalogParser {
    fun parse(content: String): List<String> {
        val iconsBody = ICONS_OBJECT.find(content)?.groupValues?.get(1) ?: return emptyList()

        return ICON_GROUP
            .findAll(iconsBody)
            .flatMap { group ->
                val path = group.groupValues[1].replace('/', '.')

                ICON_NAME
                    .findAll(group.groupValues[2])
                    .map { match -> "$ICON_PREFIX$path.${match.groupValues[1]}" }
            }.distinct()
            .sorted()
            .toList()
    }

    private val ICONS_OBJECT =
        Regex(
            pattern = "\\\"icons\\\"\\s*:\\s*\\{(.*?)\\}\\s*(?:,|\\})",
            option = RegexOption.DOT_MATCHES_ALL,
        )
    private val ICON_GROUP =
        Regex(
            pattern = "\\\"([^\\\"]+)\\\"\\s*:\\s*\\[(.*?)]",
            option = RegexOption.DOT_MATCHES_ALL,
        )
    private val ICON_NAME = Regex("\\\"([^\\\"]+)\\\"")
}

private class TbankIconCatalogFetcher : IconCatalogFetcher {
    private val client =
        HttpClient
            .newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    override fun fetch(): String? =
        runCatching {
            val request =
                HttpRequest
                    .newBuilder(URI.create(ICONS_CATALOG_URL))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))

            response.body().takeIf { response.statusCode() in HTTP_SUCCESS }
        }.getOrNull()

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(8)
        val HTTP_SUCCESS = 200..299
        const val ICONS_CATALOG_URL = "https://cdn.tbank.ru/core/design-tokens/v1/web/data.json"
    }
}

internal const val ICON_PREFIX = "@tui."
