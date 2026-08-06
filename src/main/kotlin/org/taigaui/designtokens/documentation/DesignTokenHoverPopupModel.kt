package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import java.awt.Color
import java.nio.file.Path

internal data class DesignTokenHoverPopupModel(
    val tokenName: String,
    val description: String?,
    val sections: List<DesignTokenHoverPackageSection>,
) {
    val referenceChainCount: Int = sections.sumOf { section -> section.chains.size }

    companion object {
        fun create(
            tokenName: String,
            groups: List<DesignTokenResolutionGroup>,
        ): DesignTokenHoverPopupModel =
            DesignTokenHoverPopupModel(
                tokenName = tokenName,
                description =
                    DesignTokenDescriptionExtractor.extract(
                        groups.flatMap { group -> group.origins },
                    ),
                sections = groups.toHoverPackageSections(tokenName),
            )
    }
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
)

internal data class DesignTokenHoverReferenceChain(
    val platform: String,
    val lines: List<DesignTokenHoverReferenceLine>,
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
