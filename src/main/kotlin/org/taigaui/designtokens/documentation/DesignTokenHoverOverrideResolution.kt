package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.packageinfo.TaigaUiPackagePrecedence
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution

internal fun List<DesignTokenVariantResolution>.withOverrideState(): List<DecoratedResolution> {
    val candidatesByContext = groupBy(DesignTokenVariantResolution::requestedContext)
    val decorated =
        map { resolution ->
            val effectiveCandidates =
                candidatesByContext
                    .getValue(resolution.requestedContext)
                    .effectiveCandidates()
            val overridingResolution = effectiveCandidates.firstOrNull()
            val overrideMessage =
                overridingResolution
                    ?.takeIf { resolution !in effectiveCandidates }
                    ?.let(resolution::overrideMessage)

            DecoratedResolution(
                resolution = resolution,
                packageName = resolution.sourcePackageName(),
                overrideMessage = overrideMessage,
            )
        }
    val activeVariants =
        decorated
            .filter { resolution -> resolution.overrideMessage == null }
            .map { resolution -> resolution.resolution.variant }
            .toSet()

    return decorated.filter { resolution ->
        resolution.overrideMessage == null || resolution.resolution.variant !in activeVariants
    }
}

private fun List<DesignTokenVariantResolution>.effectiveCandidates(): List<DesignTokenVariantResolution> {
    val platformSpecificity = maxOf(DesignTokenVariantResolution::platformSpecificity)
    val platformCandidates = filter { candidate -> candidate.platformSpecificity() == platformSpecificity }
    val themeSpecificity = platformCandidates.maxOf(DesignTokenVariantResolution::themeSpecificity)
    val scopedCandidates =
        platformCandidates.filter { candidate -> candidate.themeSpecificity() == themeSpecificity }

    return scopedCandidates.preferKnownPackageLayer()
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

private fun DesignTokenVariantResolution.overrideMessage(
    overridingResolution: DesignTokenVariantResolution,
): String {
    val overridingPackage = overridingResolution.sourcePackageName()

    return when {
        platformSpecificity() < overridingResolution.platformSpecificity() ->
            "Overridden by a platform-specific declaration"

        themeSpecificity() < overridingResolution.themeSpecificity() ->
            "Overridden by a theme-specific declaration"

        sourcePackageName() != overridingPackage -> "Overridden by $overridingPackage"
        else -> "Overridden by another declaration"
    }
}

internal data class DecoratedResolution(
    val resolution: DesignTokenVariantResolution,
    val packageName: String,
    val overrideMessage: String?,
)
