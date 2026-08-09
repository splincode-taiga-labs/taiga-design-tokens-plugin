package org.taigaui.designtokens.resolution

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.DesignTokenVariant
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE

internal object ProjectStylesCandidateSelector {
    fun select(
        variants: List<DesignTokenVariant>,
        requestedContext: DesignTokenContext,
    ): List<DesignTokenVariant> {
        val candidates =
            variants.filter { variant ->
                variant.sourcePackageName() == PROJECT_STYLES_PACKAGE &&
                    variant.appliesTo(requestedContext)
            }

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val platformSpecificity = candidates.maxOf { variant -> variant.platformSpecificity(requestedContext) }
        val platformCandidates =
            candidates.filter { variant -> variant.platformSpecificity(requestedContext) == platformSpecificity }
        val themeSpecificity = platformCandidates.maxOf { variant -> variant.themeSpecificity() }

        return platformCandidates.filter { variant -> variant.themeSpecificity() == themeSpecificity }
    }

    private fun DesignTokenVariant.appliesTo(requestedContext: DesignTokenContext): Boolean {
        val sharedAcrossPlatforms = origins.any { origin -> origin.sharedAcrossPlatforms }
        val platformMatches =
            when {
                sharedAcrossPlatforms -> true
                context.platform == requestedContext.platform -> true
                context.platform == DesignTokenPlatform.MOBILE ->
                    requestedContext.platform == DesignTokenPlatform.IOS ||
                        requestedContext.platform == DesignTokenPlatform.ANDROID

                else -> false
            }
        val themeMatches =
            when (requestedContext.theme) {
                DesignTokenTheme.UNSPECIFIED -> context.theme == DesignTokenTheme.UNSPECIFIED
                else ->
                    context.theme == DesignTokenTheme.UNSPECIFIED ||
                        context.theme == requestedContext.theme
            }

        return platformMatches && themeMatches
    }

    private fun DesignTokenVariant.platformSpecificity(requestedContext: DesignTokenContext): Int =
        when {
            origins.any { origin -> origin.sharedAcrossPlatforms } -> 0
            context.platform == DesignTokenPlatform.MOBILE &&
                requestedContext.platform != DesignTokenPlatform.MOBILE -> 1

            else -> 2
        }

    private fun DesignTokenVariant.themeSpecificity(): Int =
        if (context.theme == DesignTokenTheme.UNSPECIFIED) {
            0
        } else {
            1
        }

    private fun DesignTokenVariant.sourcePackageName(): String? =
        origins
            .mapNotNull { origin -> origin.packageName }
            .distinct()
            .singleOrNull()
}
