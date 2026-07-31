package org.taigaui.designtokens.tokenindex

enum class DesignTokenPlatform {
    DESKTOP,
    MOBILE,
    UNSPECIFIED,
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
        val UNSPECIFIED = DesignTokenContext(
            platform = DesignTokenPlatform.UNSPECIFIED,
            theme = DesignTokenTheme.UNSPECIFIED,
        )
    }
}
