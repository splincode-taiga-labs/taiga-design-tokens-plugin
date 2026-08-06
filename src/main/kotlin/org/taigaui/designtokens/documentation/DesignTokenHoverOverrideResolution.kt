package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution

internal fun List<DesignTokenVariantResolution>.withOverrideState(): List<DecoratedResolution> {
    val candidatesByContext = groupBy(DesignTokenVariantResolution::requestedContext)

    return map { resolution ->
        val candidates = candidatesByContext.getValue(resolution.requestedContext)
        val bestScore = candidates.maxOf(DesignTokenVariantResolution::cascadeScore)
        val overridingResolution =
            candidates
                .filter { candidate -> candidate.cascadeScore() == bestScore }
                .sortedByDescending { candidate -> candidate.sourcePackageName().packagePrecedence() }
                .firstOrNull()
        val overrideMessage =
            overridingResolution
                ?.takeIf { resolution.cascadeScore() < bestScore }
                ?.let(resolution::overrideMessage)

        DecoratedResolution(
            resolution = resolution,
            packageName = resolution.sourcePackageName(),
            overrideMessage = overrideMessage,
        )
    }
}

private fun DesignTokenVariantResolution.cascadeScore(): CascadeScore =
    CascadeScore(
        packagePrecedence = sourcePackageName().packagePrecedence(),
        platformSpecificity = platformSpecificity(),
        themeSpecificity =
            if (variant.context.theme == DesignTokenTheme.UNSPECIFIED) {
                0
            } else {
                1
            },
    )

private fun DesignTokenVariantResolution.platformSpecificity(): Int =
    when {
        variant.origins.any { origin -> origin.sharedAcrossPlatforms } -> 0
        variant.context.platform == DesignTokenPlatform.MOBILE -> 1
        else -> 2
    }

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

private fun String.packagePrecedence(): Int =
    when (this) {
        DEFAULT_SOURCE_PACKAGE -> 0
        "@taiga-ui/styles" -> 1
        "@taiga-ui/core" -> 2
        else -> 3
    }

internal data class DecoratedResolution(
    val resolution: DesignTokenVariantResolution,
    val packageName: String,
    val overrideMessage: String?,
)

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
