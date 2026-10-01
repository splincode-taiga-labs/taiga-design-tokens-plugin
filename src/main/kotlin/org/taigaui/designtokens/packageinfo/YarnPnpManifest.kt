package org.taigaui.designtokens.packageinfo

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayDeque

internal data class YarnPnpLocator(
    val name: String?,
    val reference: String?,
) {
    val displayName: String
        get() = (name ?: "<top-level>") + "@" + (reference ?: "<top-level>")
}

internal data class YarnPnpDependency(
    val name: String,
    val reference: String,
)

internal data class YarnPnpPackageInfo(
    val locator: YarnPnpLocator,
    val packageLocation: String,
    val packageDependencies: Map<String, YarnPnpDependency>,
    val discardFromLookup: Boolean,
)

internal data class YarnPnpManifest(
    val root: Path,
    val primarySource: Path,
    val sourceFiles: Set<Path>,
    val packages: Map<YarnPnpLocator, YarnPnpPackageInfo>,
    val enableTopLevelFallback: Boolean,
    val contentVersion: String,
) {
    fun findIssuer(source: Path): YarnPnpLocator? {
        val normalizedSource = source.toAbsolutePath().normalize()

        return packages.values
            .asSequence()
            .filterNot(YarnPnpPackageInfo::discardFromLookup)
            .mapNotNull { packageInfo ->
                packagePath(packageInfo.locator)
                    ?.takeIf(normalizedSource::startsWith)
                    ?.let { packagePath -> packageInfo.locator to packagePath.nameCount }
            }.maxByOrNull { (_, nameCount) -> nameCount }
            ?.first
            ?: topLevelLocator()
    }

    fun packagePath(locator: YarnPnpLocator): Path? =
        packages[locator]
            ?.packageLocation
            ?.let { location -> resolvePortablePackageLocation(root, location) }
            ?.takeUnless(::containsZipSegment)
            ?.let(::resolveYarnVirtualPath)

    fun reachableTaigaUiPackages(issuer: YarnPnpLocator): List<YarnPnpPackageInfo> {
        val result = linkedMapOf<YarnPnpLocator, YarnPnpPackageInfo>()

        collectReachableTaigaUiPackages(issuer, result)

        if (enableTopLevelFallback) {
            topLevelLocator()
                ?.takeIf { topLevel -> topLevel != issuer }
                ?.let { topLevel -> collectReachableTaigaUiPackages(topLevel, result) }
        }

        return result.values.toList()
    }

    private fun collectReachableTaigaUiPackages(
        start: YarnPnpLocator,
        result: MutableMap<YarnPnpLocator, YarnPnpPackageInfo>,
    ) {
        val queue = ArrayDeque<YarnPnpLocator>()
        val visited = mutableSetOf<YarnPnpLocator>()

        queue.add(start)

        while (queue.isNotEmpty()) {
            val locator = queue.removeFirst()

            if (!visited.add(locator)) {
                continue
            }

            val packageInfo = packages[locator] ?: continue

            packageInfo.packageDependencies.forEach { (requestName, dependency) ->
                val dependencyLocator = YarnPnpLocator(dependency.name, dependency.reference)
                val dependencyInfo = packages[dependencyLocator] ?: return@forEach
                val isTaigaUiPackage =
                    requestName.startsWith(TAIGA_UI_PACKAGE_PREFIX) ||
                        dependency.name.startsWith(TAIGA_UI_PACKAGE_PREFIX)

                if (isTaigaUiPackage) {
                    result.putIfAbsent(dependencyLocator, dependencyInfo)
                    queue.add(dependencyLocator)
                }
            }
        }
    }

    private fun topLevelLocator(): YarnPnpLocator? =
        packages.keys.firstOrNull { locator -> locator.name == null && locator.reference == null }

    private companion object {
        const val TAIGA_UI_PACKAGE_PREFIX = "@taiga-ui/"
    }
}

internal class YarnPnpManifestReader {
    fun read(directory: Path): YarnPnpManifest? {
        val normalizedDirectory = directory.toAbsolutePath().normalize()
        val dataFile = normalizedDirectory.resolve(PNP_DATA_FILE)
        val loaderFile = normalizedDirectory.resolve(PNP_LOADER_FILE)
        val source =
            when {
                Files.isRegularFile(dataFile) -> dataFile
                Files.isRegularFile(loaderFile) -> loaderFile
                else -> return null
            }
        val json =
            when (source.fileName.toString()) {
                PNP_DATA_FILE -> runCatching { Files.readString(source) }.getOrNull()
                else ->
                    runCatching { Files.readString(source) }
                        .getOrNull()
                        ?.let(::extractInlineRuntimeState)
            } ?: return null

        return parseManifest(
            root = normalizedDirectory,
            primarySource = source,
            sourceFiles =
                buildSet {
                    if (Files.isRegularFile(loaderFile)) {
                        add(loaderFile)
                    }
                    if (Files.isRegularFile(dataFile)) {
                        add(dataFile)
                    }
                },
            json = json,
        )
    }

    private fun parseManifest(
        root: Path,
        primarySource: Path,
        sourceFiles: Set<Path>,
        json: String,
    ): YarnPnpManifest? =
        runCatching {
            val rootObject = JsonParser.parseString(json).asJsonObject
            val packages = parsePackages(rootObject)
            val enableTopLevelFallback =
                rootObject
                    .get("enableTopLevelFallback")
                    ?.takeUnless { element -> element.isJsonNull }
                    ?.asBoolean
                    ?: false

            YarnPnpManifest(
                root = root,
                primarySource = primarySource,
                sourceFiles =
                    sourceFiles
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .toSet(),
                packages = packages,
                enableTopLevelFallback = enableTopLevelFallback,
                contentVersion = fileVersion(primarySource),
            )
        }.getOrNull()

    private fun parsePackages(rootObject: JsonObject): Map<YarnPnpLocator, YarnPnpPackageInfo> {
        val registry =
            rootObject
                .get("packageRegistryData")
                ?.takeIf { element -> element.isJsonArray }
                ?.asJsonArray
                ?: return emptyMap()
        val packages = linkedMapOf<YarnPnpLocator, YarnPnpPackageInfo>()

        registry.forEach { packageGroupElement ->
            val packageGroup = packageGroupElement.asJsonArray
            val packageName = packageGroup[0].stringOrNull()
            val references = packageGroup[1].asJsonArray

            references.forEach referenceLoop@{ referenceElement ->
                val referenceGroup = referenceElement.asJsonArray
                val packageReference = referenceGroup[0].stringOrNull()
                val packageData = referenceGroup[1].asJsonObject
                val packageLocation =
                    packageData
                        .get("packageLocation")
                        ?.stringOrNull()
                        ?: return@referenceLoop
                val locator = YarnPnpLocator(packageName, packageReference)

                packages[locator] =
                    YarnPnpPackageInfo(
                        locator = locator,
                        packageLocation = packageLocation,
                        packageDependencies = parseDependencies(packageData),
                        discardFromLookup =
                            packageData
                                .get("discardFromLookup")
                                ?.takeUnless { element -> element.isJsonNull }
                                ?.asBoolean
                                ?: false,
                    )
            }
        }

        return packages
    }

    private fun parseDependencies(packageData: JsonObject): Map<String, YarnPnpDependency> {
        val dependencies =
            packageData
                .get("packageDependencies")
                ?.takeIf { element -> element.isJsonArray }
                ?.asJsonArray
                ?: return emptyMap()

        return buildMap {
            dependencies.forEach dependencyLoop@{ dependencyElement ->
                val dependency = dependencyElement.asJsonArray
                val requestName = dependency[0].stringOrNull() ?: return@dependencyLoop
                val target = dependency[1]

                when {
                    target.isJsonNull -> Unit
                    target.isJsonPrimitive ->
                        target.stringOrNull()?.let { reference ->
                            put(requestName, YarnPnpDependency(requestName, reference))
                        }

                    target.isJsonArray -> {
                        val alias = target.asJsonArray
                        val name = alias.getOrNull(0)?.stringOrNull()
                        val reference = alias.getOrNull(1)?.stringOrNull()

                        if (name != null && reference != null) {
                            put(requestName, YarnPnpDependency(name, reference))
                        }
                    }
                }
            }
        }
    }

    private fun extractInlineRuntimeState(content: String): String? {
        val markerIndex = content.indexOf(RAW_RUNTIME_STATE)

        if (markerIndex < 0) {
            return null
        }

        val assignmentIndex = content.indexOf('=', markerIndex + RAW_RUNTIME_STATE.length)

        if (assignmentIndex < 0) {
            return null
        }

        var index = assignmentIndex + 1

        while (index < content.length && content[index].isWhitespace()) {
            index++
        }

        if (index >= content.length || content[index] !in setOf('\'', '"')) {
            return null
        }

        val quote = content[index++]
        val decoded = StringBuilder()

        while (index < content.length) {
            val character = content[index++]

            if (character == quote) {
                return decoded.toString()
            }

            if (character != '\\') {
                decoded.append(character)
                continue
            }

            if (index >= content.length) {
                return null
            }

            when (val escaped = content[index++]) {
                '\n' -> Unit
                '\r' -> {
                    if (index < content.length && content[index] == '\n') {
                        index++
                    }
                }

                '\\' -> decoded.append('\\')
                '\'' -> decoded.append('\'')
                '"' -> decoded.append('"')
                'n' -> decoded.append('\n')
                'r' -> decoded.append('\r')
                't' -> decoded.append('\t')
                'b' -> decoded.append('\b')
                'f' -> decoded.append('\u000C')
                'v' -> decoded.append('\u000B')
                '0' -> decoded.append('\u0000')
                'x' -> {
                    val decodedCharacter = content.decodeHexEscape(index, HEX_ESCAPE_LENGTH) ?: return null
                    decoded.append(decodedCharacter)
                    index += HEX_ESCAPE_LENGTH
                }

                'u' -> {
                    val decodedCharacter = content.decodeHexEscape(index, UNICODE_ESCAPE_LENGTH) ?: return null
                    decoded.append(decodedCharacter)
                    index += UNICODE_ESCAPE_LENGTH
                }

                else -> decoded.append(escaped)
            }
        }

        return null
    }

    private fun String.decodeHexEscape(
        start: Int,
        length: Int,
    ): Char? {
        if (start + length > this.length) {
            return null
        }

        return substring(start, start + length).toIntOrNull(HEX_RADIX)?.toChar()
    }

    private fun fileVersion(path: Path): String =
        runCatching {
            Files.size(path).toString() + ":" + Files.getLastModifiedTime(path).toMillis()
        }.getOrDefault("unknown")

    private fun JsonElement.stringOrNull(): String? =
        takeUnless { element -> element.isJsonNull }
            ?.takeIf { element -> element.isJsonPrimitive }
            ?.asString

    private fun JsonArray.getOrNull(index: Int): JsonElement? =
        takeIf { array -> index in 0 until array.size() }
            ?.get(index)

    private companion object {
        const val PNP_LOADER_FILE = ".pnp.cjs"
        const val PNP_DATA_FILE = ".pnp.data.json"
        const val RAW_RUNTIME_STATE = "RAW_RUNTIME_STATE"
        const val HEX_ESCAPE_LENGTH = 2
        const val UNICODE_ESCAPE_LENGTH = 4
        const val HEX_RADIX = 16
    }
}

internal fun resolvePortablePackageLocation(
    manifestRoot: Path,
    packageLocation: String,
): Path {
    val portablePath = packageLocation.replace('\\', '/')

    return manifestRoot
        .resolve(Path.of(portablePath))
        .toAbsolutePath()
        .normalize()
}

internal fun containsZipSegment(path: Path): Boolean =
    (0 until path.nameCount).any { index ->
        path.getName(index).toString().endsWith(".zip", ignoreCase = true)
    }

internal fun resolveYarnVirtualPath(path: Path): Path {
    var current = path.toAbsolutePath().normalize()

    while (true) {
        val virtualIndex =
            (0 until current.nameCount)
                .firstOrNull { index ->
                    current.getName(index).toString() in setOf("__virtual__", "\$\$virtual")
                }
                ?: return current

        if (virtualIndex + VIRTUAL_PATH_METADATA_SEGMENTS > current.nameCount) {
            return current
        }

        val depth =
            current
                .getName(virtualIndex + 2)
                .toString()
                .toIntOrNull()
                ?: return current
        var base = current.root ?: Path.of("")

        for (index in 0 until virtualIndex) {
            base = base.resolve(current.getName(index).toString())
        }

        repeat(depth) {
            base = base.parent ?: return current
        }

        for (index in (virtualIndex + VIRTUAL_PATH_METADATA_SEGMENTS) until current.nameCount) {
            base = base.resolve(current.getName(index).toString())
        }

        current = base.toAbsolutePath().normalize()
    }
}

private const val VIRTUAL_PATH_METADATA_SEGMENTS = 3
