package org.taigaui.designtokens.resolution

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.DesignTokenVariant
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import org.taigaui.designtokens.packageinfo.TaigaUiPackagePrecedence

internal class DesignTokenCandidateSelector(
    private val index: DesignTokenIndex,
) {
    fun select(
        name: String,
        requestedContext: DesignTokenContext,
    ): DesignTokenCandidateSelection {
        val variants = index.find(name)
        val projectCandidates = variants.effectiveProjectCandidates(requestedContext)
        val candidates =
            projectCandidates.ifEmpty {
                contextPrecedence(requestedContext)
                    .asSequence()
                    .map { compatibleContext ->
                        variants.filter { variant -> variant.context == compatibleContext }
                    }.firstOrNull(List<DesignTokenVariant>::isNotEmpty)
                    .orEmpty()
                    .preferKnownPackageLayer()
            }

        return when (candidates.size) {
            0 -> DesignTokenCandidateSelection.Missing
            1 -> DesignTokenCandidateSelection.Selected(candidates.single())
            else -> DesignTokenCandidateSelection.Ambiguous(candidates.toList())
        }
    }

    private fun List<DesignTokenVariant>.effectiveProjectCandidates(
        requestedContext: DesignTokenContext,
    ): List<DesignTokenVariant> {
        val candidates =
            filter { variant ->
                variant.sourcePackageName() == PROJECT_STYLES_PACKAGE &&
                    variant.appliesToProjectContext(requestedContext)
            }

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val platformSpecificity = candidates.maxOf { variant -> variant.projectPlatformSpecificity(requestedContext) }
        val platformCandidates =
            candidates.filter { variant -> variant.projectPlatformSpecificity(requestedContext) == platformSpecificity }
        val themeSpecificity = platformCandidates.maxOf { variant -> variant.projectThemeSpecificity() }

        return platformCandidates.filter { variant -> variant.projectThemeSpecificity() == themeSpecificity }
    }

    private fun DesignTokenVariant.appliesToProjectContext(requestedContext: DesignTokenContext): Boolean {
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

    private fun DesignTokenVariant.projectPlatformSpecificity(requestedContext: DesignTokenContext): Int =
        when {
            origins.any { origin -> origin.sharedAcrossPlatforms } -> 0
            context.platform == DesignTokenPlatform.MOBILE &&
                requestedContext.platform != DesignTokenPlatform.MOBILE -> 1

            else -> 2
        }

    private fun DesignTokenVariant.projectThemeSpecificity(): Int =
        if (context.theme == DesignTokenTheme.UNSPECIFIED) {
            0
        } else {
            1
        }

    private fun List<DesignTokenVariant>.preferKnownPackageLayer(): List<DesignTokenVariant> =
        takeIf { candidates -> candidates.size >= 2 }
            ?.map { variant ->
                variant to TaigaUiPackagePrecedence.rank(variant.sourcePackageName())
            }?.takeUnless { candidates -> candidates.any { (_, rank) -> rank == null } }
            ?.let { rankedCandidates ->
                val highestRank = rankedCandidates.maxOf { (_, rank) -> requireNotNull(rank) }

                rankedCandidates
                    .filter { (_, rank) -> rank == highestRank }
                    .map(Pair<DesignTokenVariant, Int?>::first)
            } ?: this

    private fun DesignTokenVariant.sourcePackageName(): String? =
        origins
            .mapNotNull { origin -> origin.packageName }
            .distinct()
            .singleOrNull()

    private fun contextPrecedence(context: DesignTokenContext): List<DesignTokenContext> =
        when (context.platform) {
            DesignTokenPlatform.DESKTOP -> desktopPrecedence(context.theme)
            DesignTokenPlatform.MOBILE -> mobilePrecedence(context.theme)
            DesignTokenPlatform.IOS -> platformMobilePrecedence(DesignTokenPlatform.IOS, context.theme)
            DesignTokenPlatform.ANDROID -> platformMobilePrecedence(DesignTokenPlatform.ANDROID, context.theme)
        }

    private fun desktopPrecedence(theme: DesignTokenTheme): List<DesignTokenContext> =
        when (theme) {
            DesignTokenTheme.LIGHT,
            DesignTokenTheme.DARK,
            ->
                listOf(
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, theme),
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.UNSPECIFIED),
                )

            DesignTokenTheme.UNSPECIFIED ->
                listOf(
                    DesignTokenContext(
                        DesignTokenPlatform.DESKTOP,
                        DesignTokenTheme.UNSPECIFIED,
                    ),
                )
        }

    private fun mobilePrecedence(theme: DesignTokenTheme): List<DesignTokenContext> =
        when (theme) {
            DesignTokenTheme.LIGHT,
            DesignTokenTheme.DARK,
            ->
                listOf(
                    DesignTokenContext(DesignTokenPlatform.MOBILE, theme),
                    DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.UNSPECIFIED),
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, theme),
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.UNSPECIFIED),
                )

            DesignTokenTheme.UNSPECIFIED ->
                listOf(
                    DesignTokenContext(
                        DesignTokenPlatform.MOBILE,
                        DesignTokenTheme.UNSPECIFIED,
                    ),
                    DesignTokenContext(
                        DesignTokenPlatform.DESKTOP,
                        DesignTokenTheme.UNSPECIFIED,
                    ),
                )
        }

    private fun platformMobilePrecedence(
        platform: DesignTokenPlatform,
        theme: DesignTokenTheme,
    ): List<DesignTokenContext> =
        when (theme) {
            DesignTokenTheme.LIGHT,
            DesignTokenTheme.DARK,
            ->
                listOf(
                    DesignTokenContext(platform, theme),
                    DesignTokenContext(platform, DesignTokenTheme.UNSPECIFIED),
                    DesignTokenContext(DesignTokenPlatform.MOBILE, theme),
                    DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.UNSPECIFIED),
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, theme),
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.UNSPECIFIED),
                )

            DesignTokenTheme.UNSPECIFIED ->
                listOf(
                    DesignTokenContext(platform, DesignTokenTheme.UNSPECIFIED),
                    DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.UNSPECIFIED),
                    DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.UNSPECIFIED),
                )
        }
}

internal sealed interface DesignTokenCandidateSelection {
    data class Selected(
        val variant: DesignTokenVariant,
    ) : DesignTokenCandidateSelection

    data class Ambiguous(
        val candidates: List<DesignTokenVariant>,
    ) : DesignTokenCandidateSelection

    data object Missing : DesignTokenCandidateSelection
}
