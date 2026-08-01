package org.taigaui.designtokens.documentation

import org.junit.Assert.assertFalse
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

class DesignTokenDocumentationHtmlRendererTest {
    @Test
    fun `renders compact hint with resolved color swatch`() {
        val html =
            DesignTokenDocumentationHtmlRenderer.renderHint(
                TOKEN,
                listOf(group(resolved("var(--tui-white)", "#fff"))),
            )

        assertTrue(html.contains("<b>$TOKEN</b>"))
        assertTrue(html.contains("background-color:#ffffff"))
        assertTrue(html.contains("<code>#fff</code>"))
    }

    @Test
    fun `renders contexts raw value references selectors and all origins`() {
        val terminalVariant = variant("--tui-white", "#fff", line = 2)
        val terminalResult = resolved("#fff", "#fff")
        val rootResult =
            DesignTokenValueResolution.Resolved(
                rawValue = "var(--tui-white)",
                value = "#fff",
                color = terminalResult.color,
                references =
                    listOf(
                        DesignTokenReferenceResolution(
                            name = "--tui-white",
                            requestedContext = LIGHT_DESKTOP,
                            selectedVariant = terminalVariant,
                            primaryResult = terminalResult,
                            fallbackRawValue = null,
                            fallbackResult = null,
                            fallbackUsed = false,
                        ),
                    ),
            )
        val html = DesignTokenDocumentationHtmlRenderer.render(TOKEN, listOf(group(rootResult)))

        assertTrue(html.contains("Desktop · Light"))
        assertTrue(html.contains("<b>Raw:</b> <code>var(--tui-white)</code>"))
        assertTrue(html.contains("<code>--tui-white</code> → <code>#fff</code>"))
        assertTrue(html.contains("palette/light.css:1"))
        assertTrue(html.contains("palette/light.css:2"))
        assertTrue(html.contains(":root → [tuiTheme=&#39;light&#39;]"))
    }

    @Test
    fun `renders unresolved reason without color swatch`() {
        val result =
            DesignTokenValueResolution.Unresolved(
                rawValue = "var(--tui-missing)",
                reason =
                    DesignTokenUnresolvedReason.MissingReference(
                        name = "--tui-missing",
                        requestedContext = LIGHT_DESKTOP,
                    ),
            )
        val html = DesignTokenDocumentationHtmlRenderer.render(TOKEN, listOf(group(result)))

        assertTrue(html.contains("Missing reference: --tui-missing"))
        assertFalse(html.contains("background-color:"))
    }

    @Test
    fun `escapes token values and selector text`() {
        val unsafeVariant =
            variant(
                name = "--tui-unsafe",
                rawValue = "<script>alert('x')</script>",
                selectorChain = listOf("[data-value='<unsafe>']"),
            )
        val group =
            DesignTokenResolutionGroup(
                listOf(
                    DesignTokenVariantResolution(
                        variant = unsafeVariant,
                        result =
                            DesignTokenValueResolution.Resolved(
                                rawValue = unsafeVariant.rawValue,
                                value = unsafeVariant.rawValue,
                            ),
                    ),
                ),
            )
        val html = DesignTokenDocumentationHtmlRenderer.render("--tui-<unsafe>", listOf(group))

        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("--tui-&lt;unsafe&gt;"))
        assertTrue(html.contains("&lt;unsafe&gt;"))
    }

    private fun group(result: DesignTokenValueResolution): DesignTokenResolutionGroup =
        DesignTokenResolutionGroup(
            listOf(
                DesignTokenVariantResolution(
                    variant = variant(TOKEN, result.rawValue),
                    result = result,
                ),
            ),
        )

    private fun resolved(
        rawValue: String,
        value: String,
    ): DesignTokenValueResolution.Resolved =
        DesignTokenValueResolution.Resolved(
            rawValue = rawValue,
            value = value,
            color =
                DesignTokenColorValue(
                    cssText = value,
                    canonicalValue = "#ffffff",
                    format = DesignTokenColorFormat.HEX,
                ),
        )

    private fun variant(
        name: String,
        rawValue: String,
        line: Int = 1,
        selectorChain: List<String> = listOf(":root", "[tuiTheme='light']"),
    ): DesignTokenVariant =
        DesignTokenVariant(
            name = name,
            context = LIGHT_DESKTOP,
            rawValue = rawValue,
            origins =
                listOf(
                    DesignTokenOrigin(
                        sourceFile = Path.of("palette/light.css"),
                        line = line,
                        format = DesignTokenSourceFormat.CSS,
                        selectorChain = selectorChain,
                    ),
                ),
        )

    private companion object {
        const val TOKEN = "--tui-background-base"
        val LIGHT_DESKTOP =
            DesignTokenContext(
                platform = DesignTokenPlatform.DESKTOP,
                theme = DesignTokenTheme.LIGHT,
            )
    }
}
