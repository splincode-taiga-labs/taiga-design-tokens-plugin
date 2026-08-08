package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.taigaui.designtokens.resolution.DesignTokenValueResolution

class DesignTokenHoverPresentationFormattingTest {
    @Test
    fun `sorts concrete platforms desktop mobile ios android`() {
        val rows =
            listOf(
                row("🤖 Android · Any theme"),
                row("📱 iOS · Any theme"),
                row("📱 Mobile · Any theme"),
                row("🖥️ Desktop · Dark 🌚"),
                row("🖥️ Desktop · Light ☀️"),
            ).sortedWith(VALUE_ROW_COMPARATOR)

        assertEquals(
            listOf(
                "🖥️ Desktop · Dark 🌚",
                "🖥️ Desktop · Light ☀️",
                "📱 Mobile · Any theme",
                "📱 iOS · Any theme",
                "🤖 Android · Any theme",
            ),
            rows.map(DesignTokenHoverValueRow::platform),
        )
    }

    @Test
    fun `uses the same platform order for reference chains`() {
        val chains =
            listOf(
                chain("🤖 Android · Any theme"),
                chain("📱 iOS · Any theme"),
                chain("📱 Mobile · Any theme"),
                chain("🖥️ Desktop · Any theme"),
            ).sortedWith(REFERENCE_CHAIN_COMPARATOR)

        assertEquals(
            listOf(
                "🖥️ Desktop · Any theme",
                "📱 Mobile · Any theme",
                "📱 iOS · Any theme",
                "🤖 Android · Any theme",
            ),
            chains.map(DesignTokenHoverReferenceChain::platform),
        )
    }

    @Test
    fun `shows scalar rem values in pixels in reference chains`() {
        assertEquals("0.5rem, 8px", resolved("0.5rem").hoverReferenceValueText(null))
        assertEquals("0.9375rem, 15px", resolved("0.9375rem").hoverReferenceValueText(null))
        assertEquals("1.25rem, 20px", resolved("1.25rem").hoverReferenceValueText(null))
    }

    @Test
    fun `does not guess pixel equivalent for rem expressions`() {
        val value = "calc(1rem + 2px)"

        assertEquals(value, resolved(value).hoverReferenceValueText(null))
    }

    private fun row(platform: String): DesignTokenHoverValueRow =
        DesignTokenHoverValueRow(
            platform = platform,
            resolvedValue = "value",
            color = null,
            navigationTarget = null,
        )

    private fun chain(platform: String): DesignTokenHoverReferenceChain =
        DesignTokenHoverReferenceChain(
            platform = platform,
            lines = emptyList(),
        )

    private fun resolved(value: String): DesignTokenValueResolution.Resolved =
        DesignTokenValueResolution.Resolved(
            rawValue = value,
            value = value,
        )
}
