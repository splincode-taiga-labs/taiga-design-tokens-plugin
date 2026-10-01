package org.taigaui.designtokens.navigation

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.project.DesignTokenIndexService
import org.taigaui.designtokens.resolution.DesignTokenCandidateSelection
import org.taigaui.designtokens.resolution.DesignTokenCandidateSelector
import java.nio.file.Path

internal fun DesignTokenIndexService.navigationOriginsIfCached(
    sourceFile: Path,
    tokenName: String,
): List<DesignTokenOrigin>? =
    sourceFile
        .toAbsolutePath()
        .normalize()
        .takeIf(::isIndexCached)
        ?.let(::resolutionSnapshot)
        ?.mergedIndex
        ?.let { index ->
            val selector = DesignTokenCandidateSelector(index)

            NAVIGATION_CONTEXTS
                .flatMap { context ->
                    when (val selection = selector.select(tokenName, context)) {
                        is DesignTokenCandidateSelection.Selected -> listOf(selection.variant)
                        is DesignTokenCandidateSelection.Ambiguous -> selection.candidates
                        DesignTokenCandidateSelection.Missing -> emptyList()
                    }
                }.distinct()
                .flatMap { variant -> variant.origins }
                .distinct()
        }

private val NAVIGATION_CONTEXTS =
    listOf(
        DesignTokenPlatform.DESKTOP,
        DesignTokenPlatform.MOBILE,
        DesignTokenPlatform.IOS,
        DesignTokenPlatform.ANDROID,
    ).flatMap { platform ->
        listOf(
            DesignTokenTheme.LIGHT,
            DesignTokenTheme.DARK,
            DesignTokenTheme.UNSPECIFIED,
        ).map { theme ->
            DesignTokenContext(platform, theme)
        }
    }
