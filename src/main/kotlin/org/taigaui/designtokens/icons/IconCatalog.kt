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

internal sealed interface IconSvgSource {
    val uri: URI

    data class Local(
        val path: Path,
    ) : IconSvgSource {
        override val uri: URI = path.toUri()
    }

    data class Remote(
        override val uri: URI,
    ) : IconSvgSource
}

internal data class IconCatalogEntry(
    val name: String,
    val svgSource: IconSvgSource,
)

internal class IconCatalog(
    entries: List<IconCatalogEntry>,
) {
    private val entriesByName = entries.associateBy(IconCatalogEntry::name)

    val names: List<String> = entriesByName.keys.sorted()

    fun svgSource(iconName: String): IconSvgSource? = entriesByName[iconName]?.svgSource
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

    fun load(scopeRoot: Path): List<String> = loadCatalog(scopeRoot).names

    fun loadCatalog(scopeRoot: Path): IconCatalog {
        val normalizedScope = scopeRoot.toAbsolutePath().normalize()
        val proprietaryRoot = normalizedScope.resolve(PROPRIETARY_PACKAGE)
        val entries =
            if (Files.isDirectory(proprietaryRoot)) {
                loadProprietaryIcons(normalizedScope)
            } else {
                scanSvgIcons(normalizedScope.resolve(ICONS_SOURCE))
            }

        return IconCatalog(entries)
    }

    private fun loadProprietaryIcons(scopeRoot: Path): List<IconCatalogEntry> {
        val tdsIconsRoot = scopeRoot.resolve(TDS_ICONS_SOURCE)

        return if (Files.isDirectory(tdsIconsRoot)) {
            scanSvgIcons(tdsIconsRoot)
        } else {
            runCatching {
                remoteFetcher
                    .fetch()
                    ?.let(TbankIconCatalogParser::parseEntries)
                    .orEmpty()
            }.getOrElse { emptyList() }
        }
    }

    private fun scanSvgIcons(root: Path): List<IconCatalogEntry> {
        if (!Files.isDirectory(root)) {
            return emptyList()
        }

        return runCatching {
            Files.walk(root).use { paths ->
                paths
                    .filter(Files::isRegularFile)
                    .filter { file -> file.fileName.toString().endsWith(SVG_EXTENSION, ignoreCase = true) }
                    .map { file -> IconCatalogEntry(file.toIconName(root), IconSvgSource.Local(file)) }
                    .sorted(compareBy(IconCatalogEntry::name))
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
    fun parse(content: String): List<String> = parseEntries(content).map(IconCatalogEntry::name)

    fun parseEntries(content: String): List<IconCatalogEntry> {
        val iconsBody = ICONS_OBJECT.find(content)?.groupValues?.get(1) ?: return emptyList()

        return ICON_GROUP
            .findAll(iconsBody)
            .flatMap { group ->
                val path = normalizeGroupPath(group.groupValues[1])

                if (path == null) {
                    emptySequence()
                } else {
                    ICON_NAME
                        .findAll(group.groupValues[2])
                        .mapNotNull { match -> createRemoteEntry(path, match.groupValues[1]) }
                }
            }.distinctBy(IconCatalogEntry::name)
            .sortedBy(IconCatalogEntry::name)
            .toList()
    }

    private fun createRemoteEntry(
        path: String,
        rawIconName: String,
    ): IconCatalogEntry? {
        val iconName = normalizeSegment(rawIconName) ?: return null
        val namePath = path.replace('/', '.')
        val uri = runCatching { URI.create("$ICONS_BASE_URL/$path/$iconName.svg") }.getOrNull()

        return uri?.let { sourceUri ->
            IconCatalogEntry(
                name = "$ICON_PREFIX$namePath.$iconName",
                svgSource = IconSvgSource.Remote(sourceUri),
            )
        }
    }

    private fun normalizeGroupPath(rawPath: String): String? {
        val segments = rawPath.split('/').map(String::trim)

        return segments
            .takeIf { values -> values.isNotEmpty() && values.all { segment -> normalizeSegment(segment) != null } }
            ?.joinToString("/")
    }

    private fun normalizeSegment(value: String): String? =
        value
            .trim()
            .takeIf { segment -> segment.isNotEmpty() && segment.all(Char::isIconNameCharacter) }

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
        const val ICONS_CATALOG_URL = "$ICONS_BASE_URL/data.json"
    }
}

internal const val ICON_PREFIX = "@tui."
internal const val ICONS_BASE_URL = "https://cdn.tbank.ru/core/design-tokens/v1/web"
