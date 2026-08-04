package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.index.DesignTokenOrigin

internal fun Collection<DesignTokenOrigin>.hoverSourcePackageNames(): List<String> =
    mapNotNull(DesignTokenOrigin::packageName)
        .distinct()
        .sortedWith(
            compareBy<String>(
                ::sourcePackageRank,
                String::lowercase,
            ),
        )

private fun sourcePackageRank(packageName: String): Int =
    when (packageName) {
        "@taiga-ui/design-tokens" -> 0
        "@taiga-ui/styles" -> 1
        else -> 2
    }
