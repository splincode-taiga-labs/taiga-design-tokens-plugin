package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenSourceFormat
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.DesignTokenVariant
import org.taigaui.designtokens.resolution.DesignTokenColorFormat
import org.taigaui.designtokens.resolution.DesignTokenColorValue
import org.taigaui.designtokens.resolution.DesignTokenReferenceResolution
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenUnresolvedReason
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution
import java.nio.file.Path

class DesignTokenHoverPopupModelTest {
    @Test
    fun `keeps a direct value once`() {
        val model = DesignTokenHoverPopupModel.create(TOKEN, listOf(group(resolved("#fff", "#fff"))))
        val row = model.rows.single()

        assertEquals("#fff", row.resolvedValue)
        assertFalse(row.showsResolution)
        assertNotNull(row.color)
        assertTrue(model.chains.isEmpty())
    }

    @Test
    fun `shows referenced value in its original CSS color notation`() {
        val cssColor = "rgba(0, 0, 0, 0.54)"
        val terminalVariant = variant("--tui-const-black-alpha-54", cssColor, line = 2)
        val terminalResult =
            resolved(
                rawValue = cssColor,
                value = "#0000008A",
                cssText = cssColor,
                canonicalValue = "#0000008A",
            )
        val rootResult =
            DesignTokenValueResolution.Resolved(
                rawValue = "var(--tui-const-black-alpha-54)",
                value = "#0000008A",
                color = terminalResult.color,
                references =
                    listOf(
                        DesignTokenReferenceResolution(
                            name = "--tui-const-black-alpha-54",
                            requestedContext = LIGHT_DESKTOP,
                            selectedVariant = terminalVariant,
                            primaryResult = terminalResult,
                            fallbackRawValue = null,
                            fallbackResult = null,
                            fallbackUsed = false,
                        ),
                    ),
            )
        val model = DesignTokenHoverPopupModel.create(TOKEN, listOf(group(rootResult)))
        val row = model.rows.single()
        val chain = model.chains.single()

        assertEquals("🖥️ Desktop · Light ☀️", row.platform)
        assertEquals(listOf("var(--tui-const-black-alpha-54)"), row.rawValues)
        assertEquals(cssColor, row.resolvedValue)
        assertTrue(row.showsResolution)
        assertEquals(
            listOf(TOKEN, "--tui-const-black-alpha-54", cssColor),
            chain.lines.map(DesignTokenHoverReferenceLine::text),
        )
    }

    @Test
    fun `collapses a complete equivalent context set`() {
        val model =
            DesignTokenHoverPopupModel.create(
                TOKEN,
                listOf(group(resolved("#fff", "#fff"), ALL_CONTEXTS)),
            )

        assertEquals("All platforms · Light ☀️ and dark 🌚", model.rows.single().platform)
    }

    @Test
    fun `keeps incomplete context combinations explicit`() {
        val model =
            DesignTokenHoverPopupModel.create(
                TOKEN,
                listOf(group(resolved("#fff", "#fff"), listOf(LIGHT_DESKTOP, DARK_MOBILE))),
            )

        assertEquals("🖥️ Desktop · Light ☀️, 📱 Mobile · Dark 🌚", model.rows.single().platform)
    }

    @Test
    fun `shows unresolved reasons`() {
        val result =
            DesignTokenValueResolution.Unresolved(
                rawValue = "var(--tui-missing)",
                reason =
                    DesignTokenUnresolvedReason.MissingReference(
                        name = "--tui-missing",
                        requestedContext = LIGHT_DESKTOP,
                    ),
            )
        val row = DesignTokenHoverPopupModel.create(TOKEN, listOf(group(result))).rows.single()

        assertEquals("Missing reference: --tui-missing", row.resolvedValue)
        assertTrue(row.showsResolution)
    }

    private fun group(
        result: DesignTokenValueResolution,
        contexts: List<DesignTokenContext> = listOf(LIGHT_DESKTOP),
    ): DesignTokenResolutionGroup =
        DesignTokenResolutionGroup(
            contexts.map { context ->
                DesignTokenVariantResolution(
                    variant = variant(TOKEN, result.rawValue, context = context),
                    result = result,
                )
            },
        )

    private fun resolved(
        rawValue: String,
        value: String,
        cssText: String = value,
        canonicalValue: String = value.normalizeTestColor(),
    ): DesignTokenValueResolution.Resolved =
        DesignTokenValueResolution.Resolved(
            rawValue = rawValue,
            value = value,
            color =
                DesignTokenColorValue(
                    cssText = cssText,
                    canonicalValue = canonicalValue,
                    format =
                        if (cssText.startsWith("rgb")) {
                            DesignTokenColorFormat.FUNCTION
                        } else {
                            DesignTokenColorFormat.HEX
                        },
                ),
        )

    private fun String.normalizeTestColor(): String =
        when (lowercase()) {
            "#fff" -> "#ffffff"
            else -> this
        }

    private fun variant(
        name: String,
        rawValue: String,
        line: Int = 1,
        context: DesignTokenContext = LIGHT_DESKTOP,
    ): DesignTokenVariant =
        DesignTokenVariant(
            name = name,
            context = context,
            rawValue = rawValue,
            origins =
                listOf(
                    DesignTokenOrigin(
                        sourceFile = Path.of("palette/light.css"),
                        line = line,
                        format = DesignTokenSourceFormat.CSS,
                        selectorChain = listOf(":root", "[tuiTheme='light']"),
                    ),
                ),
        )

    private companion object {
        const val TOKEN = "--tui-text-secondary"
        val LIGHT_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.LIGHT)
        val DARK_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.DARK)
        val LIGHT_MOBILE = DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.LIGHT)
        val DARK_MOBILE = DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.DARK)
        val ALL_CONTEXTS = listOf(LIGHT_DESKTOP, DARK_DESKTOP, LIGHT_MOBILE, DARK_MOBILE)
    }
}
