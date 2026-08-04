package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.awt.Color
import java.nio.file.Path
import java.util.Locale

internal data class DesignTokenHoverPopupModel(
    val tokenName: String,
    val description: String?,
    val sourcePackages: List<String>,
    val rows: List<DesignTokenHoverValueRow>,
    val chains: List<DesignTokenHoverReferenceChain>,
    val navigationTarget: DesignTokenNavigationTarget?,
) {
    companion object {
        fun create(
            tokenName: String,
            groups: List<DesignTokenResolutionGroup>,
        ): DesignTokenHoverPopupModel {
            val origins = groups.flatMap { group -> group.origins }

            return DesignTokenHoverPopupModel(
                tokenName = tokenName,
                description = DesignTokenDescriptionExtractor.extract(origins),
                sourcePackages = origins.sourcePackageNames(),
                rows = groups.map(DesignTokenResolutionGroup::toHoverValueRow),
                chains =
                    groups
                        .filter { group -> group.representative.references.isNotEmpty() }
                        .map { group -> group.toHoverReferenceChain(tokenName) },
                navigationTarget = groups.firstNotNullOfOrNull(DesignTokenResolutionGroup::navigationTarget),
            )
        }
    }
}

internal data class DesignTokenHoverValueRow(
    val platform: String,
    val resolvedValue: String,
    val color: Color?,
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

private fun List<DesignTokenOrigin>.sourcePackageNames(): List<String> =
    mapNotNull(DesignTokenOrigin::packageName)
        .distinct()
        .sortedWith(compareBy(::sourcePackageRank, String::lowercase))

private fun sourcePackageRank(packageName: String): Int =
    when (packageName) {
        "@taiga-ui/design-tokens" -> 0
        "@taiga-ui/styles" -> 1
        else -> 2
    }

private fun DesignTokenResolutionGroup.toHoverValueRow(): DesignTokenHoverValueRow =
    DesignTokenHoverValueRow(
        platform =
            resolutions
                .map { resolution -> resolution.variant.context }
                .hoverPlatformLabel(),
        resolvedValue = representative.hoverValueText(),
        color = representative.toAwtColorOrNull(),
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

    return color.canonicalValue.toAwtColorOrNull()
}

private fun String.toAwtColorOrNull(): Color? {
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

private fun String.flatMapCharacters(): String =
    buildString(length * 2) {
        this@flatMapCharacters.forEach { character ->
            append(character)
            append(character)
        }
    }

private const val OPAQUE_ALPHA = 255
private const val RGBA_COMPONENTS_COUNT = 4
