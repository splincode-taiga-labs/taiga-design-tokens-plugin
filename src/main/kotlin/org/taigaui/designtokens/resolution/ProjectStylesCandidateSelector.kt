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
        val applicableCandidates =
            variants.filter { variant ->
                variant.sourcePackageName() == PROJECT_STYLES_PACKAGE &&
                    variant.appliesTo(requestedContext)
            }
        val candidates =
            applicableCandidates
                .filter { variant -> variant.isLocalOverride() }
                .ifEmpty { applicableCandidates }

        if (candidates.isEmpty()) {
            return emptyList()
        }

        val platformSpecificity = candidates.maxOf { variant -> variant.platformSpecificity(requestedContext) }
        val platformCandidates =
            candidates.filter { variant -> variant.platformSpecificity(requestedContext) == platformSpecificity }
        val themeSpecificity = platformCandidates.maxOf { variant -> variant.themeSpecificity() }
        val themeCandidates =
            platformCandidates.filter { variant -> variant.themeSpecificity() == themeSpecificity }

        return themeCandidates.preferLatestCascadeOrder()
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

    private fun List<DesignTokenVariant>.preferLatestCascadeOrder(): List<DesignTokenVariant> {
        val orderedCandidates =
            mapNotNull { variant ->
                val order = variant.projectCascadeOrder()
                val scope = variant.projectCascadeScope()

                if (order == null || scope == null) {
                    null
                } else {
                    OrderedProjectCandidate(variant, order, scope)
                }
            }
        val comparable =
            orderedCandidates.size == size &&
                orderedCandidates.map(OrderedProjectCandidate::scope).distinct().size == 1

        return if (comparable) {
            val latestOrder = orderedCandidates.maxOf(OrderedProjectCandidate::order)

            orderedCandidates
                .filter { candidate -> candidate.order == latestOrder }
                .map(OrderedProjectCandidate::variant)
        } else {
            this
        }
    }

    private fun DesignTokenVariant.isLocalOverride(): Boolean = origins.any { origin -> origin.localOverride }

    private fun DesignTokenVariant.sourcePackageName(): String? =
        origins
            .mapNotNull { origin -> origin.packageName }
            .distinct()
            .singleOrNull()

    private data class OrderedProjectCandidate(
        val variant: DesignTokenVariant,
        val order: Int,
        val scope: List<String>,
    )
}

internal fun DesignTokenVariant.projectCascadeOrder(): Int? {
    val orders = origins.map { origin -> origin.cascadeOrder }

    return orders
        .takeIf { values -> values.isNotEmpty() && values.all { order -> order != null } }
        ?.filterNotNull()
        ?.maxOrNull()
}

internal fun DesignTokenVariant.projectCascadeScope(): List<String>? {
    val scopes =
        origins
            .map { origin -> origin.selectorChain.map(String::normalizeSelectorScope) }
            .distinct()

    return scopes.singleOrNull()
}

private fun String.normalizeSelectorScope(): String = replace(WHITESPACE, " ").trim()

private val WHITESPACE = Regex("""\s+""")
