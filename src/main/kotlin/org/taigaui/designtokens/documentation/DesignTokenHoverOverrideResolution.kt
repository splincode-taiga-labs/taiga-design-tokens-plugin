package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import org.taigaui.designtokens.packageinfo.TaigaUiPackagePrecedence
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution
import org.taigaui.designtokens.resolution.projectCascadeOrder
import org.taigaui.designtokens.resolution.projectCascadeScope

internal fun List<DesignTokenVariantResolution>.withOverrideState(): List<DecoratedResolution> {
    val candidatesByContext = groupBy(DesignTokenVariantResolution::requestedContext)
    val decorated =
        map { resolution ->
            val effectiveCandidates =
                candidatesByContext
                    .getValue(resolution.requestedContext)
                    .effectiveCandidates()
            val overridingResolution = effectiveCandidates.firstOrNull()
            val isOverridden = overridingResolution != null && resolution !in effectiveCandidates
            val overrideMessage =
                overridingResolution
                    ?.takeIf { isOverridden }
                    ?.let(resolution::overrideMessage)

            DecoratedResolution(
                resolution = resolution,
                packageName = resolution.sourcePackageName(),
                overrideMessage = overrideMessage,
                overridingPackageName =
                    overridingResolution
                        ?.takeIf { isOverridden }
                        ?.sourcePackageName(),
            )
        }
    val activeVariants =
        decorated
            .filter { resolution -> resolution.overrideMessage == null }
            .map { resolution -> resolution.resolution.variant }
            .toSet()

    return decorated.filter { resolution ->
        resolution.overrideMessage == null ||
            resolution.overridingPackageName == PROJECT_STYLES_PACKAGE ||
            resolution.resolution.variant !in activeVariants
    }
}

private fun List<DesignTokenVariantResolution>.effectiveCandidates(): List<DesignTokenVariantResolution> {
    val projectCandidates = filter { candidate -> candidate.sourcePackageName() == PROJECT_STYLES_PACKAGE }

    return if (projectCandidates.isNotEmpty()) {
        projectCandidates
            .mostSpecificCandidates()
            .preferLatestProjectCascade()
    } else {
        mostSpecificCandidates().preferKnownPackageLayer()
    }
}

private fun List<DesignTokenVariantResolution>.mostSpecificCandidates(): List<DesignTokenVariantResolution> {
    val platformSpecificity = maxOf(DesignTokenVariantResolution::platformSpecificity)
    val platformCandidates = filter { candidate -> candidate.platformSpecificity() == platformSpecificity }
    val themeSpecificity = platformCandidates.maxOf(DesignTokenVariantResolution::themeSpecificity)

    return platformCandidates.filter { candidate -> candidate.themeSpecificity() == themeSpecificity }
}

private fun List<DesignTokenVariantResolution>.preferLatestProjectCascade(): List<DesignTokenVariantResolution> {
    val orderedCandidates =
        mapNotNull { candidate ->
            val order = candidate.variant.projectCascadeOrder()
            val scope = candidate.variant.projectCascadeScope()

            if (order == null || scope == null) {
                null
            } else {
                OrderedProjectResolution(candidate, order, scope)
            }
        }
    val comparable =
        orderedCandidates.size == size &&
            orderedCandidates.map(OrderedProjectResolution::scope).distinct().size == 1

    return if (comparable) {
        val latestOrder = orderedCandidates.maxOf(OrderedProjectResolution::order)

        orderedCandidates
            .filter { candidate -> candidate.order == latestOrder }
            .map(OrderedProjectResolution::resolution)
    } else {
        this
    }
}

private fun List<DesignTokenVariantResolution>.preferKnownPackageLayer(): List<DesignTokenVariantResolution> =
    takeIf { candidates ->
        candidates.map(DesignTokenVariantResolution::sourcePackageName).distinct().size >= 2
    }?.map { candidate ->
        candidate to TaigaUiPackagePrecedence.rank(candidate.sourcePackageName())
    }?.takeUnless { candidates -> candidates.any { (_, rank) -> rank == null } }
        ?.let { rankedCandidates ->
            val highestRank = rankedCandidates.maxOf { (_, rank) -> requireNotNull(rank) }

            rankedCandidates
                .filter { (_, rank) -> rank == highestRank }
                .map(Pair<DesignTokenVariantResolution, Int?>::first)
        } ?: this

private fun DesignTokenVariantResolution.platformSpecificity(): Int =
    when {
        variant.origins.any { origin -> origin.sharedAcrossPlatforms } -> 0
        variant.context.platform == DesignTokenPlatform.MOBILE -> 1
        else -> 2
    }

private fun DesignTokenVariantResolution.themeSpecificity(): Int =
    if (variant.context.theme == DesignTokenTheme.UNSPECIFIED) {
        0
    } else {
        1
    }

private fun DesignTokenVariantResolution.overrideMessage(overridingResolution: DesignTokenVariantResolution): String {
    val overridingPackage = overridingResolution.sourcePackageName()

    return when {
        overridingPackage == PROJECT_STYLES_PACKAGE && sourcePackageName() != overridingPackage ->
            "Overridden by $PROJECT_STYLES_PACKAGE"

        platformSpecificity() < overridingResolution.platformSpecificity() ->
            "Overridden by a platform-specific declaration"

        themeSpecificity() < overridingResolution.themeSpecificity() ->
            "Overridden by a theme-specific declaration"

        overridingPackage == PROJECT_STYLES_PACKAGE &&
            sourcePackageName() == PROJECT_STYLES_PACKAGE &&
            isEarlierThan(overridingResolution) ->
            "Overridden by a later $PROJECT_STYLES_PACKAGE declaration"

        sourcePackageName() != overridingPackage -> "Overridden by $overridingPackage"
        else -> "Overridden by another declaration"
    }
}

private fun DesignTokenVariantResolution.isEarlierThan(other: DesignTokenVariantResolution): Boolean {
    val currentOrder = variant.projectCascadeOrder()
    val otherOrder = other.variant.projectCascadeOrder()
    val currentScope = variant.projectCascadeScope()
    val otherScope = other.variant.projectCascadeScope()

    return currentOrder != null &&
        otherOrder != null &&
        currentScope != null &&
        currentScope == otherScope &&
        currentOrder < otherOrder
}

private data class OrderedProjectResolution(
    val resolution: DesignTokenVariantResolution,
    val order: Int,
    val scope: List<String>,
)

internal data class DecoratedResolution(
    val resolution: DesignTokenVariantResolution,
    val packageName: String,
    val overrideMessage: String?,
    val overridingPackageName: String? = null,
)
