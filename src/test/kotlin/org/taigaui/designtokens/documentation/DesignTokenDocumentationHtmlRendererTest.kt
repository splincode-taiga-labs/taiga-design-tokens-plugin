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
    fun `renders context token value final value and reference chain as a table`() {
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

        assertTrue(html.contains("<th align='left'>Context</th>"))
        assertTrue(html.contains("<th align='left'>Token value</th>"))
        assertTrue(html.contains("<th align='left'>Final value</th>"))
        assertTrue(html.contains("<td valign='top'>Desktop · Light</td>"))
        assertTrue(html.contains("<code>var(--tui-white)</code>"))
        assertTrue(html.contains("<code>#fff</code>"))
        assertTrue(html.contains("<b>Reference chain:</b>"))
        assertTrue(html.contains("<code>--tui-white</code> → <code>#fff</code>"))
        assertFalse(html.contains("Sources:"))
        assertFalse(html.contains("palette/light.css"))
        assertFalse(html.contains("tuiTheme"))
    }

    @Test
    fun `collapses all equivalent platform and theme contexts into one label`() {
        val html =
            DesignTokenDocumentationHtmlRenderer.render(
                TOKEN,
                listOf(group(resolved("var(--tui-white)", "#fff"), ALL_CONTEXTS)),
            )

        assertTrue(html.contains("<td valign='top'>All platforms · Light and dark</td>"))
        assertFalse(html.contains("Desktop · Light, Desktop · Dark"))
    }

    @Test
    fun `keeps incomplete context combinations explicit`() {
        val html =
            DesignTokenDocumentationHtmlRenderer.render(
                TOKEN,
                listOf(
                    group(
                        resolved("var(--tui-white)", "#fff"),
                        listOf(LIGHT_DESKTOP, DARK_MOBILE),
                    ),
                ),
            )

        assertTrue(html.contains("Desktop · Light, Mobile · Dark"))
        assertFalse(html.contains("All platforms · Light and dark"))
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
    fun `escapes token names and values`() {
        val unsafeVariant =
            variant(
                name = "--tui-unsafe",
                rawValue = "<script>alert('x')</script>",
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
                        selectorChain = selectorChain,
                    ),
                ),
        )

    private companion object {
        const val TOKEN = "--tui-background-base"
        val LIGHT_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.LIGHT)
        val DARK_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.DARK)
        val LIGHT_MOBILE = DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.LIGHT)
        val DARK_MOBILE = DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.DARK)
        val ALL_CONTEXTS = listOf(LIGHT_DESKTOP, DARK_DESKTOP, LIGHT_MOBILE, DARK_MOBILE)
    }
}
