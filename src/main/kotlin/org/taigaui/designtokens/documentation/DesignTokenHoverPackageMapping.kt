package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution

internal fun List<DesignTokenResolutionGroup>.toHoverPackageSections(
    tokenName: String,
): List<DesignTokenHoverPackageSection> {
    val resolutions =
        flatMap { group -> group.resolutions }
            .flatMap(DesignTokenVariantResolution::splitBySourcePackage)
    val decorated = resolutions.withOverrideState()

    return decorated
        .groupBy(DecoratedResolution::packageName)
        .map { (packageName, packageResolutions) ->
            val displayGroups = packageResolutions.toDisplayGroups()

            DesignTokenHoverPackageSection(
                packageName = packageName,
                rows =
                    displayGroups
                        .map(DisplayResolutionGroup::toHoverValueRow)
                        .sortedWith(VALUE_ROW_COMPARATOR),
                chains =
                    displayGroups
                        .map { group -> group.toHoverReferenceChain(tokenName) }
                        .sortedWith(REFERENCE_CHAIN_COMPARATOR),
            )
        }.sortedBy { section -> section.packageName.packageSortKey() }
}

private fun DesignTokenVariantResolution.splitBySourcePackage(): List<DesignTokenVariantResolution> {
    val originsByPackage =
        variant.origins.groupBy { origin -> origin.packageName ?: DEFAULT_SOURCE_PACKAGE }

    return originsByPackage
        .takeIf(Map<String, *>::isNotEmpty)
        ?.map { (_, origins) -> copy(variant = variant.copy(origins = origins)) }
        ?: listOf(this)
}

private fun List<DesignTokenVariantResolution>.withOverrideState(): List<DecoratedResolution> {
    val candidatesByContext = groupBy(DesignTokenVariantResolution::requestedContext)

    return map { resolution ->
        val packageName = resolution.sourcePackageName()
        val candidates = candidatesByContext.getValue(resolution.requestedContext)
        val bestScore = candidates.maxOf(DesignTokenVariantResolution::cascadeScore)
        val score = resolution.cascadeScore()
        val overridingResolution =
            candidates
                .filter { candidate -> candidate.cascadeScore() == bestScore }
                .sortedByDescending { candidate -> candidate.sourcePackageName().packagePrecedence() }
                .firstOrNull()
        val overrideMessage =
            if (score < bestScore && overridingResolution != null) {
                resolution.overrideMessage(overridingResolution)
            } else {
                null
            }

        DecoratedResolution(
            resolution = resolution,
            packageName = packageName,
            overrideMessage = overrideMessage,
        )
    }
}

private fun List<DecoratedResolution>.toDisplayGroups(): List<DisplayResolutionGroup> =
    groupBy { decorated ->
        DisplayGroupKey(
            resultKey = decorated.resolution.result.displayKey(),
            overrideMessage = decorated.overrideMessage,
        )
    }.values.map { decorated ->
        DisplayResolutionGroup(
            resolutions = decorated.map(DecoratedResolution::resolution),
            overrideMessage = decorated.first().overrideMessage,
        )
    }

private fun DisplayResolutionGroup.toHoverValueRow(): DesignTokenHoverValueRow =
    DesignTokenHoverValueRow(
        platform = platformLabel(),
        resolvedValue = representative.hoverValueText(),
        color = representative.toHoverColorOrNull(),
        navigationTarget = navigationTarget(),
        overrideMessage = overrideMessage,
    )

private fun DisplayResolutionGroup.toHoverReferenceChain(
    tokenName: String,
): DesignTokenHoverReferenceChain =
    DesignTokenHoverReferenceChain(
        platform = platformLabel(),
        lines =
            buildList {
                add(DesignTokenHoverReferenceLine(tokenName, depth = 0, root = true))

                if (representative.references.isEmpty()) {
                    addTerminalValue(representative, depth = 0)
                } else {
                    appendReferences(representative.references, depth = 0)
                }
            },
        overrideMessage = overrideMessage,
    )

private fun DisplayResolutionGroup.platformLabel(): String =
    resolutions
        .map(DesignTokenVariantResolution::requestedContext)
        .collapseThemes()
        .hoverPlatformLabel()

private fun List<DesignTokenContext>.collapseThemes(): List<DesignTokenContext> =
    distinct()
        .groupBy(DesignTokenContext::platform)
        .flatMap { (platform, contexts) ->
            val themes = contexts.map(DesignTokenContext::theme).toSet()

            if (DesignTokenTheme.LIGHT in themes && DesignTokenTheme.DARK in themes) {
                listOf(DesignTokenContext(platform, DesignTokenTheme.UNSPECIFIED))
            } else {
                contexts
            }
        }

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

private fun DisplayResolutionGroup.navigationTarget(): DesignTokenNavigationTarget? =
    resolutions
        .flatMap { resolution -> resolution.variant.origins }
        .firstOrNull()
        ?.let { origin -> DesignTokenNavigationTarget(origin.sourceFile, origin.line) }

private fun DesignTokenVariantResolution.sourcePackageName(): String =
    variant.origins.firstOrNull()?.packageName ?: DEFAULT_SOURCE_PACKAGE

private fun DesignTokenVariantResolution.cascadeScore(): CascadeScore =
    CascadeScore(
        packagePrecedence = sourcePackageName().packagePrecedence(),
        platformSpecificity =
            if (variant.origins.any { origin -> origin.sharedAcrossPlatforms }) {
                0
            } else {
                1
            },
        themeSpecificity =
            if (variant.context.theme == DesignTokenTheme.UNSPECIFIED) {
                0
            } else {
                1
            },
    )

private fun DesignTokenVariantResolution.overrideMessage(
    overridingResolution: DesignTokenVariantResolution,
): String {
    val overridingPackage = overridingResolution.sourcePackageName()

    return if (sourcePackageName() == overridingPackage) {
        "Overridden by a more specific declaration"
    } else {
        "Overridden by $overridingPackage"
    }
}

private fun DesignTokenValueResolution.displayKey(): String =
    when (this) {
        is DesignTokenValueResolution.Resolved ->
            color?.canonicalValue ?: value.trim()

        is DesignTokenValueResolution.Unresolved ->
            "${reason::class.simpleName}:$rawValue"
    }

private fun String.packageSortKey(): String = "${packageDisplayRank()}:$this"

private fun String.packageDisplayRank(): Int =
    when (this) {
        DEFAULT_SOURCE_PACKAGE -> 0
        "@taiga-ui/styles" -> 1
        "@taiga-ui/core" -> 2
        else -> 3
    }

private fun String.packagePrecedence(): Int =
    when (this) {
        DEFAULT_SOURCE_PACKAGE -> 0
        "@taiga-ui/styles" -> 1
        "@taiga-ui/core" -> 2
        else -> 3
    }

private data class DecoratedResolution(
    val resolution: DesignTokenVariantResolution,
    val packageName: String,
    val overrideMessage: String?,
)

private data class DisplayGroupKey(
    val resultKey: String,
    val overrideMessage: String?,
)

private data class DisplayResolutionGroup(
    val resolutions: List<DesignTokenVariantResolution>,
    val overrideMessage: String?,
) {
    val representative: DesignTokenValueResolution = resolutions.first().result
}

private data class CascadeScore(
    val packagePrecedence: Int,
    val platformSpecificity: Int,
    val themeSpecificity: Int,
) : Comparable<CascadeScore> {
    override fun compareTo(other: CascadeScore): Int =
        compareValuesBy(
            this,
            other,
            CascadeScore::packagePrecedence,
            CascadeScore::platformSpecificity,
            CascadeScore::themeSpecificity,
        )
}

private val VALUE_ROW_COMPARATOR =
    compareBy<DesignTokenHoverValueRow>(
        { row -> row.overrideMessage != null },
        DesignTokenHoverValueRow::platform,
        DesignTokenHoverValueRow::resolvedValue,
    )
private val REFERENCE_CHAIN_COMPARATOR =
    compareBy<DesignTokenHoverReferenceChain>(
        { chain -> chain.overrideMessage != null },
        DesignTokenHoverReferenceChain::platform,
    )
private const val DEFAULT_SOURCE_PACKAGE = "@taiga-ui/design-tokens"
