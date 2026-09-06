package org.taigaui.designtokens.resolution

import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.DesignTokenVariant
import java.util.concurrent.ConcurrentHashMap

internal fun interface DesignTokenValueParserAdapter {
    fun parse(value: String): DesignTokenValueParseResult
}

class DesignTokenValueResolver internal constructor(
    index: DesignTokenIndex,
    private val valueParser: DesignTokenValueParserAdapter,
) {
    constructor(index: DesignTokenIndex) :
        this(
            index = index,
            valueParser = DesignTokenValueParserAdapter(DesignTokenValueParser::parse),
        )

    private val candidateSelector = DesignTokenCandidateSelector(index)
    private val variantsByName = index
    private val parsedValues = ConcurrentHashMap<String, DesignTokenValueParseResult>()
    private val resolutionResults = ConcurrentHashMap<ResolutionFrame, DesignTokenValueResolution>()

    internal val parsedValueCacheSize: Int
        get() = parsedValues.size

    internal val resolutionCacheSize: Int
        get() = resolutionResults.size

    fun resolve(variant: DesignTokenVariant): DesignTokenVariantResolution = resolve(variant, variant.context)

    fun resolve(
        variant: DesignTokenVariant,
        requestedContext: DesignTokenContext,
    ): DesignTokenVariantResolution =
        DesignTokenVariantResolution(
            variant = variant,
            result =
                resolveVariant(
                    frame = ResolutionFrame(variant, requestedContext),
                    stack = mutableListOf(),
                ),
            requestedContext = requestedContext,
        )

    fun resolve(name: String): List<DesignTokenVariantResolution> =
        variantsByName
            .find(name)
            .flatMap { variant ->
                variant.requestedContexts().map { context -> resolve(variant, context) }
            }

    fun resolveGrouped(name: String): List<DesignTokenResolutionGroup> {
        val groups = linkedMapOf<String, MutableList<DesignTokenVariantResolution>>()
        var unresolvedIndex = 0

        resolve(name).forEach { resolution ->
            val key =
                when (val result = resolution.result) {
                    is DesignTokenValueResolution.Resolved -> "resolved:${result.semanticKey()}"
                    is DesignTokenValueResolution.Unresolved -> "unresolved:${unresolvedIndex++}"
                }

            groups.getOrPut(key, ::mutableListOf).add(resolution)
        }

        return groups.values.map { resolutions ->
            DesignTokenResolutionGroup(resolutions.toList())
        }
    }

    private fun resolveVariant(
        frame: ResolutionFrame,
        stack: MutableList<ResolutionFrame>,
    ): DesignTokenValueResolution {
        val cycleStart = stack.indexOf(frame)

        return if (cycleStart >= 0) {
            circularResolution(frame, stack, cycleStart)
        } else {
            resolutionResults[frame] ?: resolveAndMemoize(frame, stack)
        }
    }

    private fun resolveAndMemoize(
        frame: ResolutionFrame,
        stack: MutableList<ResolutionFrame>,
    ): DesignTokenValueResolution {
        stack.add(frame)

        val result =
            try {
                resolveUncachedVariant(frame, stack)
            } finally {
                stack.removeAt(stack.lastIndex)
            }

        return if (result.containsCircularReference()) {
            result
        } else {
            resolutionResults.putIfAbsent(frame, result) ?: result
        }
    }

    private fun resolveUncachedVariant(
        frame: ResolutionFrame,
        stack: MutableList<ResolutionFrame>,
    ): DesignTokenValueResolution =
        when (
            val parsed =
                parsedValues.computeIfAbsent(
                    frame.variant.rawValue,
                    valueParser::parse,
                )
        ) {
            is DesignTokenValueParseResult.Parsed ->
                resolveParsedValue(
                    value = parsed.value,
                    owner = frame,
                    stack = stack,
                )

            is DesignTokenValueParseResult.Invalid ->
                DesignTokenValueResolution.Unresolved(
                    rawValue = frame.variant.rawValue,
                    reason =
                        DesignTokenUnresolvedReason.InvalidExpression(
                            offset = parsed.offset,
                            message = parsed.message,
                        ),
                )
        }

    private fun resolveParsedValue(
        value: ParsedDesignTokenValue,
        owner: ResolutionFrame,
        stack: MutableList<ResolutionFrame>,
    ): DesignTokenValueResolution {
        val resolvedValue = StringBuilder()
        val references = mutableListOf<DesignTokenReferenceResolution>()

        value.parts.forEach { part ->
            when (part) {
                is DesignTokenValuePart.Text -> resolvedValue.append(part.value)
                is DesignTokenValuePart.Reference -> {
                    val reference = resolveReference(part, owner, stack)
                    references.add(reference)

                    when (val effectiveResult = reference.effectiveResult) {
                        is DesignTokenValueResolution.Resolved -> {
                            resolvedValue.append(effectiveResult.value)
                        }

                        is DesignTokenValueResolution.Unresolved -> {
                            return DesignTokenValueResolution.Unresolved(
                                rawValue = value.rawValue,
                                reason = effectiveResult.reason,
                                references = references.toList(),
                            )
                        }
                    }
                }
            }
        }

        val finalValue = resolvedValue.toString()

        return DesignTokenValueResolution.Resolved(
            rawValue = value.rawValue,
            value = finalValue,
            references = references.toList(),
            color = DesignTokenColorDetector.detect(finalValue),
        )
    }

    private fun resolveReference(
        reference: DesignTokenValuePart.Reference,
        owner: ResolutionFrame,
        stack: MutableList<ResolutionFrame>,
    ): DesignTokenReferenceResolution {
        val selection =
            candidateSelector.select(
                name = reference.name,
                requestedContext = owner.requestedContext,
            )
        val primaryResult =
            when (selection) {
                is DesignTokenCandidateSelection.Selected ->
                    resolveVariant(
                        ResolutionFrame(selection.variant, owner.requestedContext),
                        stack,
                    )

                is DesignTokenCandidateSelection.Ambiguous ->
                    selection.toUnresolved(reference, owner.requestedContext)

                DesignTokenCandidateSelection.Missing ->
                    reference.toMissingResolution(owner.requestedContext)
            }
        val fallbackUsed =
            reference.fallback != null &&
                primaryResult is DesignTokenValueResolution.Unresolved &&
                canUseFallback(primaryResult.reason, owner)
        val fallbackResult =
            reference.fallback
                ?.takeIf { fallbackUsed }
                ?.let { fallback -> resolveParsedValue(fallback, owner, stack) }

        return DesignTokenReferenceResolution(
            name = reference.name,
            requestedContext = owner.requestedContext,
            selectedVariant = selection.selectedVariant(),
            primaryResult = primaryResult,
            fallbackRawValue = reference.fallback?.rawValue,
            fallbackResult = fallbackResult,
            fallbackUsed = fallbackUsed,
        )
    }

    private fun canUseFallback(
        reason: DesignTokenUnresolvedReason,
        owner: ResolutionFrame,
    ): Boolean =
        when (reason) {
            is DesignTokenUnresolvedReason.MissingReference,
            is DesignTokenUnresolvedReason.InvalidExpression,
            -> true

            is DesignTokenUnresolvedReason.AmbiguousReference -> false
            is DesignTokenUnresolvedReason.CircularReference -> owner.toNode() !in reason.chain
        }

    private fun circularResolution(
        frame: ResolutionFrame,
        stack: List<ResolutionFrame>,
        cycleStart: Int,
    ): DesignTokenValueResolution.Unresolved =
        DesignTokenValueResolution.Unresolved(
            rawValue = frame.variant.rawValue,
            reason =
                DesignTokenUnresolvedReason.CircularReference(
                    chain =
                        (stack.drop(cycleStart) + frame)
                            .map(ResolutionFrame::toNode),
                ),
        )

    private data class ResolutionFrame(
        val variant: DesignTokenVariant,
        val requestedContext: DesignTokenContext,
    ) {
        fun toNode(): DesignTokenResolutionNode =
            DesignTokenResolutionNode(
                name = variant.name,
                declarationContext = variant.context,
                requestedContext = requestedContext,
                rawValue = variant.rawValue,
            )
    }
}

private fun DesignTokenValueResolution.containsCircularReference(): Boolean =
    (this is DesignTokenValueResolution.Unresolved && reason is DesignTokenUnresolvedReason.CircularReference) ||
        references.any(DesignTokenReferenceResolution::containsCircularReference)

private fun DesignTokenReferenceResolution.containsCircularReference(): Boolean =
    primaryResult.containsCircularReference() ||
        fallbackResult?.containsCircularReference() == true

private fun DesignTokenVariant.requestedContexts(): List<DesignTokenContext> {
    val platforms =
        when {
            origins.any { origin -> origin.sharedAcrossPlatforms } ->
                listOf(
                    DesignTokenPlatform.DESKTOP,
                    DesignTokenPlatform.IOS,
                    DesignTokenPlatform.ANDROID,
                )

            context.platform == DesignTokenPlatform.MOBILE ->
                listOf(DesignTokenPlatform.IOS, DesignTokenPlatform.ANDROID)

            else -> listOf(context.platform)
        }
    val themes =
        if (context.theme == DesignTokenTheme.UNSPECIFIED) {
            listOf(DesignTokenTheme.LIGHT, DesignTokenTheme.DARK)
        } else {
            listOf(context.theme)
        }

    return platforms.flatMap { platform ->
        themes.map { theme -> DesignTokenContext(platform, theme) }
    }
}

private fun DesignTokenValueResolution.Resolved.semanticKey(): String = color?.canonicalValue ?: value.trim()

private fun DesignTokenCandidateSelection.selectedVariant(): DesignTokenVariant? =
    (this as? DesignTokenCandidateSelection.Selected)?.variant

private fun DesignTokenCandidateSelection.Ambiguous.toUnresolved(
    reference: DesignTokenValuePart.Reference,
    requestedContext: DesignTokenContext,
): DesignTokenValueResolution.Unresolved =
    DesignTokenValueResolution.Unresolved(
        rawValue = reference.expressionText(),
        reason =
            DesignTokenUnresolvedReason.AmbiguousReference(
                name = reference.name,
                requestedContext = requestedContext,
                candidates = candidates,
            ),
    )

private fun DesignTokenValuePart.Reference.toMissingResolution(
    requestedContext: DesignTokenContext,
): DesignTokenValueResolution.Unresolved =
    DesignTokenValueResolution.Unresolved(
        rawValue = expressionText(),
        reason =
            DesignTokenUnresolvedReason.MissingReference(
                name = name,
                requestedContext = requestedContext,
            ),
    )

private fun DesignTokenValuePart.Reference.expressionText(): String =
    buildString {
        append("var(")
        append(name)

        fallback?.let { fallback ->
            append(", ")
            append(fallback.rawValue)
        }

        append(')')
    }
