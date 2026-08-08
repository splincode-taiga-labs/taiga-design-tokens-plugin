package org.taigaui.designtokens.packageinfo

import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE

internal object TaigaUiPackagePrecedence {
    fun rank(packageName: String?): Int? =
        when (packageName) {
            "@taiga-ui/design-tokens" -> 0
            "@taiga-ui/styles" -> 1
            "@taiga-ui/core" -> 2
            "@taiga-ui/proprietary" -> 3
            PROJECT_STYLES_PACKAGE -> 4
            else -> null
        }
}
