package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution

internal fun List<DecoratedResolution>.toDisplayGroups(): List<DisplayResolutionGroup> =
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

internal fun DisplayResolutionGroup.toHoverValueRow(
    includeSourceDetails: Boolean,
): DesignTokenHoverValueRow =
    DesignTokenHoverValueRow(
        platform = platformLabel(),
        resolvedValue = representative.hoverValueText(),
        color = representative.toHoverColorOrNull(),
        navigationTarget = navigationTarget(),
        overrideMessage = overrideMessage,
        sourceDetails = if (includeSourceDetails) sourceDetails() else emptyList(),
    )

internal fun DisplayResolutionGroup.platformLabel(): String =
    resolutions
        .toPresentationContexts()
        .hoverPlatformLabel()

private fun List<DesignTokenVariantResolution>.toPresentationContexts(): List<DesignTokenContext> =
    groupBy { resolution -> resolution.requestedContext.platform }
        .flatMap { (platform, platformResolutions) ->
            val requestedThemes =
                platformResolutions
                    .map { resolution -> resolution.requestedContext.theme }
                    .toSet()
            val inheritedAcrossThemes =
                platformResolutions.all { resolution ->
                    resolution.variant.context.theme == DesignTokenTheme.UNSPECIFIED
                }

            if (
                inheritedAcrossThemes &&
                DesignTokenTheme.LIGHT in requestedThemes &&
                DesignTokenTheme.DARK in requestedThemes
            ) {
                listOf(DesignTokenContext(platform, DesignTokenTheme.UNSPECIFIED))
            } else {
                platformResolutions.map(DesignTokenVariantResolution::requestedContext).distinct()
            }
        }

private fun DisplayResolutionGroup.sourceDetails(): List<DesignTokenSourceDetail> =
    resolutions
        .flatMap { resolution -> resolution.variant.origins }
        .map { origin ->
            DesignTokenSourceDetail(
                sourceFile = origin.sourceFile,
                line = origin.line,
                selectorChain = origin.selectorChain,
            )
        }.distinct()
        .sortedWith(
            compareBy(
                { detail -> detail.sourceFile.toString() },
                DesignTokenSourceDetail::line,
                { detail -> detail.selectorChain.joinToString() },
            ),
        )

private fun DisplayResolutionGroup.navigationTarget(): DesignTokenNavigationTarget? {
    val origins = resolutions.flatMap { resolution -> resolution.variant.origins }
    val origin =
        origins
            .filter { candidate -> candidate.cascadeOrder != null }
            .maxByOrNull { candidate -> requireNotNull(candidate.cascadeOrder) }
            ?: origins.firstOrNull()

    return origin?.let { candidate -> DesignTokenNavigationTarget(candidate.sourceFile, candidate.line) }
}

private fun DesignTokenValueResolution.displayKey(): String =
    when (this) {
        is DesignTokenValueResolution.Resolved -> color?.canonicalValue ?: value.trim()
        is DesignTokenValueResolution.Unresolved -> "${reason::class.simpleName}:$rawValue"
    }

private data class DisplayGroupKey(
    val resultKey: String,
    val overrideMessage: String?,
)

internal data class DisplayResolutionGroup(
    val resolutions: List<DesignTokenVariantResolution>,
    val overrideMessage: String?,
) {
    val representative: DesignTokenValueResolution = resolutions.first().result
}
