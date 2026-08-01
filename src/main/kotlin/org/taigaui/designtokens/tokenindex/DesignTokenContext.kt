package org.taigaui.designtokens.tokenindex

enum class DesignTokenPlatform {
    DESKTOP,
    MOBILE,
}

enum class DesignTokenTheme {
    LIGHT,
    DARK,
    UNSPECIFIED,
}

data class DesignTokenContext(
    val platform: DesignTokenPlatform,
    val theme: DesignTokenTheme,
) {
    companion object {
        val DEFAULT =
            DesignTokenContext(
                platform = DesignTokenPlatform.DESKTOP,
                theme = DesignTokenTheme.UNSPECIFIED,
            )
    }
}
