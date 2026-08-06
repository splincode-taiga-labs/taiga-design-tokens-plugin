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

internal fun DisplayResolutionGroup.toHoverValueRow(): DesignTokenHoverValueRow =
    DesignTokenHoverValueRow(
        platform = platformLabel(),
        resolvedValue = representative.hoverValueText(),
        color = representative.toHoverColorOrNull(),
        navigationTarget = navigationTarget(),
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

private fun DisplayResolutionGroup.navigationTarget(): DesignTokenNavigationTarget? =
    resolutions
        .flatMap { resolution -> resolution.variant.origins }
        .firstOrNull()
        ?.let { origin -> DesignTokenNavigationTarget(origin.sourceFile, origin.line) }

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
