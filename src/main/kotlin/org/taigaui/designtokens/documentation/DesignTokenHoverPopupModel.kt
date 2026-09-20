package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenDeprecation
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import java.awt.Color
import java.nio.file.Path

internal data class DesignTokenHoverPopupModel(
    val tokenName: String,
    val description: String?,
    val sections: List<DesignTokenHoverPackageSection>,
    val deprecation: DesignTokenDeprecation? = null,
) {
    val referenceChainCount: Int = sections.sumOf { section -> section.chains.size }

    companion object {
        fun create(
            tokenName: String,
            groups: List<DesignTokenResolutionGroup>,
            includeSourceDetails: Boolean = false,
        ): DesignTokenHoverPopupModel {
            val origins = groups.flatMap { group -> group.origins }

            return DesignTokenHoverPopupModel(
                tokenName = tokenName,
                description =
                    DesignTokenDescriptionExtractor.extract(
                        origins.filter { origin -> origin.deprecation == null },
                    ),
                sections =
                    groups.toHoverPackageSections(
                        tokenName = tokenName,
                        includeSourceDetails = includeSourceDetails,
                    ),
                deprecation = origins.effectiveDeprecation(),
            )
        }

        fun notFound(
            tokenName: String,
            suggestions: List<String>,
        ): DesignTokenHoverPopupModel =
            DesignTokenHoverPopupModel(
                tokenName = tokenName,
                description =
                    buildString {
                        append("Nothing found")

                        if (suggestions.isNotEmpty()) {
                            appendLine()
                            appendLine()
                            appendLine("Did you mean another variable?")
                            append(suggestions.joinToString("\n"))
                        }
                    },
                sections = emptyList(),
            )
    }
}

private fun List<DesignTokenOrigin>.effectiveDeprecation(): DesignTokenDeprecation? {
    val projectOrigins = filter { origin -> origin.packageName == PROJECT_STYLES_PACKAGE }

    return projectOrigins
        .ifEmpty { this }
        .mapNotNull(DesignTokenOrigin::deprecation)
        .distinct()
        .singleOrNull()
}

internal data class DesignTokenHoverPackageSection(
    val packageName: String,
    val rows: List<DesignTokenHoverValueRow>,
    val chains: List<DesignTokenHoverReferenceChain>,
)

internal data class DesignTokenHoverValueRow(
    val platform: String,
    val resolvedValue: String,
    val color: Color?,
    val navigationTarget: DesignTokenNavigationTarget?,
    val overrideMessage: String? = null,
    val sourceDetails: List<DesignTokenSourceDetail> = emptyList(),
)

internal data class DesignTokenSourceDetail(
    val sourceFile: Path,
    val line: Int,
    val selectorChain: List<String>,
)

internal data class DesignTokenHoverReferenceChain(
    val platform: String,
    val lines: List<DesignTokenHoverReferenceLine>,
    val overrideMessage: String? = null,
)

internal data class DesignTokenHoverReferenceLine(
    val text: String,
    val depth: Int,
    val root: Boolean = false,
    val color: Color? = null,
)

internal data class DesignTokenNavigationTarget(
    val sourceFile: Path,
    val line: Int,
)
