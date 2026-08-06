package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution

internal fun List<DesignTokenResolutionGroup>.toHoverPackageSections(
    tokenName: String,
): List<DesignTokenHoverPackageSection> =
    splitBySourcePackage()
        .map { (packageName, packageGroups) ->
            DesignTokenHoverPackageSection(
                packageName = packageName,
                rows = packageGroups.map(DesignTokenResolutionGroup::toHoverValueRow),
                chains = packageGroups.map { group -> group.toHoverReferenceChain(tokenName) },
            )
        }.sortedBy { section -> section.packageName.packageSortKey() }

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

    return originsByPackage
        .takeIf(Map<String, *>::isNotEmpty)
        ?.map { (_, origins) -> copy(variant = variant.copy(origins = origins)) }
        ?: listOf(this)
}

private fun DesignTokenVariantResolution.sourcePackageName(): String =
    variant.origins.firstOrNull()?.packageName ?: DEFAULT_SOURCE_PACKAGE

private fun DesignTokenResolutionGroup.toHoverValueRow(): DesignTokenHoverValueRow =
    DesignTokenHoverValueRow(
        platform = hoverPlatformLabel(),
        resolvedValue = representative.hoverValueText(),
        color = representative.toHoverColorOrNull(),
        navigationTarget = navigationTarget(),
    )

private fun DesignTokenResolutionGroup.toHoverReferenceChain(tokenName: String): DesignTokenHoverReferenceChain =
    DesignTokenHoverReferenceChain(
        platform = hoverPlatformLabel(),
        lines =
            buildList {
                add(DesignTokenHoverReferenceLine(tokenName, depth = 0, root = true))

                if (representative.references.isEmpty()) {
                    addTerminalValue(representative, depth = 0)
                } else {
                    appendReferences(representative.references, depth = 0)
                }
            },
    )

private fun DesignTokenResolutionGroup.hoverPlatformLabel(): String {
    val contexts = resolutions.map { resolution -> resolution.variant.context }
    val sharedAcrossPlatforms =
        resolutions.all { resolution ->
            resolution.variant.origins.any { origin -> origin.sharedAcrossPlatforms }
        }

    return if (sharedAcrossPlatforms) {
        contexts
            .flatMap(DesignTokenContext::forDesktopAndMobile)
            .hoverPlatformLabel()
    } else {
        contexts.hoverPlatformLabel()
    }
}

private fun DesignTokenContext.forDesktopAndMobile(): List<DesignTokenContext> =
    listOf(
        copy(platform = DesignTokenPlatform.DESKTOP),
        copy(platform = DesignTokenPlatform.MOBILE),
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
            addTerminalValue(result, depth)
        } else {
            appendReferences(result.references, depth + 1)
        }
    }
}

private fun MutableList<DesignTokenHoverReferenceLine>.addTerminalValue(
    result: DesignTokenValueResolution,
    depth: Int,
) {
    val color = result.toHoverColorOrNull()

    add(
        DesignTokenHoverReferenceLine(
            text = result.hoverReferenceValueText(color),
            depth = depth,
            color = color,
        ),
    )
}

private fun DesignTokenResolutionGroup.navigationTarget(): DesignTokenNavigationTarget? =
    origins.firstOrNull()?.let { origin ->
        DesignTokenNavigationTarget(origin.sourceFile, origin.line)
    }

private fun String.packageSortKey(): String = "${packageRank()}:$this"

private fun String.packageRank(): Int =
    when (this) {
        DEFAULT_SOURCE_PACKAGE -> 0
        "@taiga-ui/styles" -> 1
        "@taiga-ui/core" -> 2
        else -> 3
    }

private const val DEFAULT_SOURCE_PACKAGE = "@taiga-ui/design-tokens"
