package org.taigaui.designtokens.resolution

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.DesignTokenVariant
import org.taigaui.designtokens.packageinfo.TaigaUiPackagePrecedence

internal class DesignTokenCandidateSelector(
    private val index: DesignTokenIndex,
) {
    fun select(
        name: String,
        requestedContext: DesignTokenContext,
    ): DesignTokenCandidateSelection {
        val variants = index.find(name)
        val projectCandidates = ProjectStylesCandidateSelector.select(variants, requestedContext)
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
