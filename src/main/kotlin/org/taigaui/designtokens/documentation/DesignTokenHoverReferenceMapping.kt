package org.taigaui.designtokens.documentation

import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenValueResolution

internal fun DisplayResolutionGroup.toHoverReferenceChain(tokenName: String): DesignTokenHoverReferenceChain =
    DesignTokenHoverReferenceChain(
        platform = platformLabel(),
        lines =
            buildList {
                add(DesignTokenHoverReferenceLine(tokenName, depth = 0, root = true))

                if (representative.references.isEmpty()) {
                    addTerminalValue(representative, depth = 0)
                } else {
                    appendReferences(representative.references, depth = 0)
                }
            },
        overrideMessage = overrideMessage,
    )

private fun MutableList<DesignTokenHoverReferenceLine>.appendReferences(
    references: List<DesignTokenReferenceResolution>,
    depth: Int,
) {
    references.forEach { reference ->
        add(
            DesignTokenHoverReferenceLine(
                text = reference.name + if (reference.fallbackUsed) " (fallback)" else "",
                depth = depth,
            ),
        )

        val result = reference.effectiveResult

        if (result.references.isEmpty()) {
            addTerminalValue(result, depth)
        } else {
            appendReferences(result.references, depth + 1)
        }
    }
}

private fun MutableList<DesignTokenHoverReferenceLine>.addTerminalValue(
    result: DesignTokenValueResolution,
    depth: Int,
) {
    val color = result.toHoverColorOrNull()

    add(
        DesignTokenHoverReferenceLine(
            text = result.hoverReferenceValueText(color),
            depth = depth,
            color = color,
        ),
    )
}
