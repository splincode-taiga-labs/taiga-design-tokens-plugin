package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.resolution.DesignTokenColorValue
import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution
import java.awt.Color
import java.nio.file.Path
import java.util.Locale
import kotlin.math.roundToInt

internal data class DesignTokenHoverPopupModel(
    val tokenName: String,
    val description: String?,
    val sections: List<DesignTokenHoverPackageSection>,
) {
    val referenceChainCount: Int = sections.sumOf { section -> section.chains.size }

    companion object {
        fun create(
            tokenName: String,
            groups: List<DesignTokenResolutionGroup>,
        ): DesignTokenHoverPopupModel {
            val origins = groups.flatMap { group -> group.origins }
            val packageGroups = groups.splitBySourcePackage()

            return DesignTokenHoverPopupModel(
                tokenName = tokenName,
                description = DesignTokenDescriptionExtractor.extract(origins),
                sections =
                    packageGroups
                        .map { (packageName, packageResolutionGroups) ->
                            DesignTokenHoverPackageSection(
                                packageName = packageName,
                                rows = packageResolutionGroups.map(DesignTokenResolutionGroup::toHoverValueRow),
                                chains =
                                    packageResolutionGroups
                                        .filter { group -> group.representative.references.isNotEmpty() }
                                        .map { group -> group.toHoverReferenceChain(tokenName) },
                            )
                        }.sortedBy { section -> sourcePackageSortKey(section.packageName) },
            )
        }
    }
}

internal data class DesignTokenHoverPackageSection(
    val packageName: String,
    val rows: List<DesignTokenHoverValueRow>,
    val chains: List<DesignTokenHoverReferenceChain>,
)

internal data class DesignTokenHoverValueRow(
    val platform: String,
    val resolvedValue: String,
    val color: Color?,
    val navigationTarget: DesignTokenNavigationTarget?,
)

internal data class DesignTokenHoverReferenceChain(
    val platform: String,
    val lines: List<DesignTokenHoverReferenceLine>,
)

internal data class DesignTokenHoverReferenceLine(
    val text: String,
    val depth: Int,
    val root: Boolean = false,
    val color: Color? = null,
)

internal data class DesignTokenNavigationTarget(
    val sourceFile: Path,
    val line: Int,
)

private fun List<DesignTokenResolutionGroup>.splitBySourcePackage(): Map<String, List<DesignTokenResolutionGroup>> {
    val groupsByPackage = linkedMapOf<String, MutableList<DesignTokenResolutionGroup>>()

    forEach { group ->
        group.resolutions
            .flatMap(DesignTokenVariantResolution::splitBySourcePackage)
            .groupBy(DesignTokenVariantResolution::sourcePackageName)
            .forEach { (packageName, resolutions) ->
                groupsByPackage
                    .getOrPut(packageName, ::mutableListOf)
                    .add(DesignTokenResolutionGroup(resolutions))
            }
    }

    return groupsByPackage
}

private fun DesignTokenVariantResolution.splitBySourcePackage(): List<DesignTokenVariantResolution> {
    val originsByPackage =
        variant.origins.groupBy { origin -> origin.packageName ?: DEFAULT_SOURCE_PACKAGE }

    return if (originsByPackage.isEmpty()) {
        listOf(this)
    } else {
        originsByPackage.map { (_, origins) ->
            copy(variant = variant.copy(origins = origins))
        }
    }
}

private fun DesignTokenVariantResolution.sourcePackageName(): String =
    variant.origins.firstOrNull()?.packageName ?: DEFAULT_SOURCE_PACKAGE

private fun sourcePackageSortKey(packageName: String): String =
    "${sourcePackageRank(packageName)}:$packageName"

private fun sourcePackageRank(packageName: String): Int =
    when (packageName) {
        DEFAULT_SOURCE_PACKAGE -> 0
        "@taiga-ui/styles" -> 1
        "@taiga-ui/core" -> 2
        else -> 3
    }

private fun DesignTokenResolutionGroup.toHoverValueRow(): DesignTokenHoverValueRow =
    DesignTokenHoverValueRow(
        platform =
            resolutions
                .map { resolution -> resolution.variant.context }
                .hoverPlatformLabel(),
        resolvedValue = representative.hoverValueText(),
        color = representative.toAwtColorOrNull(),
        navigationTarget = navigationTarget(),
    )

private fun DesignTokenResolutionGroup.toHoverReferenceChain(tokenName: String): DesignTokenHoverReferenceChain =
    DesignTokenHoverReferenceChain(
        platform =
            resolutions
                .map { resolution -> resolution.variant.context }
                .hoverPlatformLabel(),
        lines =
            buildList {
                add(DesignTokenHoverReferenceLine(tokenName, depth = 0, root = true))
                appendReferences(representative.references, depth = 0)
            },
    )

private fun MutableList<DesignTokenHoverReferenceLine>.appendReferences(
    references: List<DesignTokenReferenceResolution>,
    depth: Int,
) {
    references.forEach { reference ->
        add(
            DesignTokenHoverReferenceLine(
                text = reference.name + if (reference.fallbackUsed) " (fallback)" else "",
                depth = depth,
            ),
        )

        val result = reference.effectiveResult

        if (result.references.isEmpty()) {
            val color = result.toAwtColorOrNull()

            add(
                DesignTokenHoverReferenceLine(
                    text = result.hoverReferenceValueText(color),
                    depth = depth,
                    color = color,
                ),
            )
        } else {
            appendReferences(result.references, depth + 1)
        }
    }
}

private fun DesignTokenValueResolution.hoverReferenceValueText(color: Color?): String {
    val value = hoverValueText()
    val rgba = color?.toRgbaText() ?: return value

    return if (value.startsWith("rgba(", ignoreCase = true)) {
        value
    } else {
        "$value, $rgba"
    }
}

private fun Color.toRgbaText(): String {
    val alphaValue =
        if (alpha == OPAQUE_ALPHA) {
            "1"
        } else {
            String
                .format(Locale.ROOT, "%.2f", alpha.toDouble() / OPAQUE_ALPHA)
                .trimEnd('0')
                .trimEnd('.')
        }

    return "rgba($red, $green, $blue, $alphaValue)"
}

private fun DesignTokenResolutionGroup.navigationTarget(): DesignTokenNavigationTarget? =
    origins.firstOrNull()?.let { origin ->
        DesignTokenNavigationTarget(origin.sourceFile, origin.line)
    }

private fun DesignTokenValueResolution.toAwtColorOrNull(): Color? {
    val color = (this as? DesignTokenValueResolution.Resolved)?.color ?: return null

    return color.toAwtColorOrNull()
}

private fun DesignTokenColorValue.toAwtColorOrNull(): Color? =
    canonicalValue.toHexColorOrNull()
        ?: cssText.toRgbColorOrNull()
        ?: canonicalValue.toRgbColorOrNull()

private fun String.toHexColorOrNull(): Color? {
    val hex = removePrefix("#")
    val expanded =
        when (hex.length) {
            3 -> hex.flatMapCharacters() + "ff"
            4 -> hex.flatMapCharacters()
            6 -> hex + "ff"
            8 -> hex
            else -> return null
        }
    val components = expanded.chunked(2).mapNotNull { component -> component.toIntOrNull(16) }

    return if (components.size == RGBA_COMPONENTS_COUNT) {
        Color(components[0], components[1], components[2], components[3])
    } else {
        null
    }
}

private fun String.toRgbColorOrNull(): Color? {
    val match = RGB_FUNCTION.matchEntire(trim()) ?: return null
    val function = match.groupValues[1].lowercase()
    val body = match.groupValues[2].trim()
    val parts =
        if (body.contains(',')) {
            body.split(',').map(String::trim)
        } else {
            body.replace("/", " / ").split(Regex("\\s+")).filter(String::isNotEmpty)
        }
    val slashIndex = parts.indexOf("/")
    val channels = if (slashIndex >= 0) parts.take(slashIndex) else parts.take(3)
    val alphaText =
        when {
            slashIndex >= 0 -> parts.getOrNull(slashIndex + 1)
            function == "rgba" -> parts.getOrNull(3)
            else -> null
        }

    if (channels.size != RGB_CHANNEL_COUNT) {
        return null
    }

    val red = channels[0].toRgbChannelOrNull() ?: return null
    val green = channels[1].toRgbChannelOrNull() ?: return null
    val blue = channels[2].toRgbChannelOrNull() ?: return null
    val alpha = alphaText?.toAlphaChannelOrNull() ?: OPAQUE_ALPHA

    return Color(red, green, blue, alpha)
}

private fun String.toRgbChannelOrNull(): Int =
    if (endsWith('%')) {
        removeSuffix("%")
            .toDoubleOrNull()
            ?.coerceIn(0.0, 100.0)
            ?.let { value -> (value * OPAQUE_ALPHA / 100.0).roundToInt() }
    } else {
        toDoubleOrNull()
            ?.coerceIn(0.0, OPAQUE_ALPHA.toDouble())
            ?.roundToInt()
    }

private fun String.toAlphaChannelOrNull(): Int =
    if (endsWith('%')) {
        removeSuffix("%")
            .toDoubleOrNull()
            ?.coerceIn(0.0, 100.0)
            ?.let { value -> (value * OPAQUE_ALPHA / 100.0).roundToInt() }
    } else {
        toDoubleOrNull()
            ?.coerceIn(0.0, 1.0)
            ?.let { value -> (value * OPAQUE_ALPHA).roundToInt() }
    }

private fun String.flatMapCharacters(): String =
    buildString(length * 2) {
        this@flatMapCharacters.forEach { character ->
            append(character)
            append(character)
        }
    }

private val RGB_FUNCTION = Regex("""(?i)(rgb|rgba)\((.*)\)""")
private const val DEFAULT_SOURCE_PACKAGE = "@taiga-ui/design-tokens"
private const val OPAQUE_ALPHA = 255
private const val RGB_CHANNEL_COUNT = 3
private const val RGBA_COMPONENTS_COUNT = 4
