package org.taigaui.designtokens.resolution

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.index.DesignTokenVariant

data class DesignTokenVariantResolution(
    val variant: DesignTokenVariant,
    val result: DesignTokenValueResolution,
)

sealed interface DesignTokenValueResolution {
    val rawValue: String
    val references: List<DesignTokenReferenceResolution>

    data class Resolved(
        override val rawValue: String,
        val value: String,
        override val references: List<DesignTokenReferenceResolution> = emptyList(),
        val color: DesignTokenColorValue? = null,
    ) : DesignTokenValueResolution

    data class Unresolved(
        override val rawValue: String,
        val reason: DesignTokenUnresolvedReason,
        override val references: List<DesignTokenReferenceResolution> = emptyList(),
    ) : DesignTokenValueResolution
}

data class DesignTokenReferenceResolution(
    val name: String,
    val requestedContext: DesignTokenContext,
    val selectedVariant: DesignTokenVariant?,
    val primaryResult: DesignTokenValueResolution,
    val fallbackRawValue: String?,
    val fallbackResult: DesignTokenValueResolution?,
    val fallbackUsed: Boolean,
) {
    val effectiveResult: DesignTokenValueResolution
        get() = if (fallbackUsed) requireNotNull(fallbackResult) else primaryResult
}

sealed interface DesignTokenUnresolvedReason {
    data class MissingReference(
        val name: String,
        val requestedContext: DesignTokenContext,
    ) : DesignTokenUnresolvedReason

    data class AmbiguousReference(
        val name: String,
        val requestedContext: DesignTokenContext,
        val candidates: List<DesignTokenVariant>,
    ) : DesignTokenUnresolvedReason

    data class CircularReference(
        val chain: List<DesignTokenResolutionNode>,
    ) : DesignTokenUnresolvedReason

    data class InvalidExpression(
        val offset: Int,
        val message: String,
    ) : DesignTokenUnresolvedReason
}

data class DesignTokenResolutionNode(
    val name: String,
    val context: DesignTokenContext,
    val rawValue: String,
) {
    companion object {
        fun from(variant: DesignTokenVariant): DesignTokenResolutionNode =
            DesignTokenResolutionNode(
                name = variant.name,
                context = variant.context,
                rawValue = variant.rawValue,
            )
    }
}

enum class DesignTokenColorFormat {
    HEX,
    FUNCTION,
    NAMED,
}

data class DesignTokenColorValue(
    val cssText: String,
    val canonicalValue: String,
    val format: DesignTokenColorFormat,
)

data class DesignTokenResolutionGroup(
    val resolutions: List<DesignTokenVariantResolution>,
) {
    init {
        require(resolutions.isNotEmpty()) {
            "A resolution group must contain at least one token resolution."
        }
    }

    val representative: DesignTokenValueResolution = resolutions.first().result

    val origins: List<DesignTokenOrigin> =
        resolutions
            .flatMap { resolution -> resolution.variant.origins }
            .distinct()
}
