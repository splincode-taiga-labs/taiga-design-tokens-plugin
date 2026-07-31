package org.taigaui.designtokens.tokenindex

data class DesignTokenVariant(
    val name: String,
    val context: DesignTokenContext,
    val rawValue: String,
    val origins: List<DesignTokenOrigin>,
) {
    init {
        require(origins.isNotEmpty()) {
            "A logical token variant must retain at least one physical origin."
        }
    }
}
