package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import java.awt.Color
import java.nio.file.Path

internal data class DesignTokenHoverPopupModel(
    val tokenName: String,
    val rows: List<DesignTokenHoverValueRow>,
    val chains: List<DesignTokenHoverReferenceChain>,
    val navigationTarget: DesignTokenNavigationTarget?,
) {
    val copyValue: String =
        rows
            .map(DesignTokenHoverValueRow::resolvedValue)
            .distinct()
            .let { values ->
                if (values.size == 1) {
                    values.single()
                } else {
                    rows.joinToString(separator = System.lineSeparator()) { row ->
                        "${row.platform}: ${row.resolvedValue}"
                    }
                }
            }

    companion object {
        fun create(
            tokenName: String,
            groups: List<DesignTokenResolutionGroup>,
        ): DesignTokenHoverPopupModel =
            DesignTokenHoverPopupModel(
                tokenName = tokenName,
                rows = groups.map(DesignTokenResolutionGroup::toHoverValueRow),
                chains =
                    groups
                        .filter { group -> group.representative.references.isNotEmpty() }
                        .map { group -> group.toHoverReferenceChain(tokenName) },
                navigationTarget = groups.firstNotNullOfOrNull(DesignTokenResolutionGroup::navigationTarget),
            )
    }
}

internal data class DesignTokenHoverValueRow(
    val platform: String,
    val rawValues: List<String>,
    val resolvedValue: String,
    val color: Color?,
) {
    val showsResolution: Boolean = rawValues.any { rawValue -> rawValue != resolvedValue }
}

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

private fun DesignTokenResolutionGroup.toHoverValueRow(): DesignTokenHoverValueRow =
    DesignTokenHoverValueRow(
        platform =
            resolutions
                .map { resolution -> resolution.variant.context }
                .hoverPlatformLabel(),
        rawValues = resolutions.map { resolution -> resolution.variant.rawValue }.distinct(),
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
            add(
                DesignTokenHoverReferenceLine(
                    text = result.hoverValueText(),
                    depth = depth,
                    color = result.toAwtColorOrNull(),
                ),
            )
        } else {
            appendReferences(result.references, depth + 1)
        }
    }
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

private const val RGBA_COMPONENTS_COUNT = 4
