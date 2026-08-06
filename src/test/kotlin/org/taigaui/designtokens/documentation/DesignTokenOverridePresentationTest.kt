package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenSourceFormat
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.DesignTokenVariant
import org.taigaui.designtokens.resolution.DesignTokenResolutionGroup
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution
import java.nio.file.Path

class DesignTokenOverridePresentationTest {
    @Test
    fun `marks lower priority packages as not applied`() {
        val groups =
            listOf(
                group(
                    resolution(
                        packageName = DESIGN_TOKENS_PACKAGE,
                        rawValue = "base",
                        value = "base",
                        requestedContext = LIGHT_DESKTOP,
                        sharedAcrossPlatforms = true,
                    ),
                    resolution(
                        packageName = DESIGN_TOKENS_PACKAGE,
                        rawValue = "base",
                        value = "base",
                        requestedContext = LIGHT_MOBILE,
                        sharedAcrossPlatforms = true,
                    ),
                ),
                group(
                    resolution(
                        packageName = PROPRIETARY_PACKAGE,
                        rawValue = "brand",
                        value = "brand",
                        requestedContext = LIGHT_DESKTOP,
                        sharedAcrossPlatforms = true,
                    ),
                    resolution(
                        packageName = PROPRIETARY_PACKAGE,
                        rawValue = "brand",
                        value = "brand",
                        requestedContext = LIGHT_MOBILE,
                        sharedAcrossPlatforms = true,
                    ),
                ),
            )
        val model = DesignTokenHoverPopupModel.create(TOKEN, groups)
        val baseRow = model.sections.first { it.packageName == DESIGN_TOKENS_PACKAGE }.rows.single()
        val proprietaryRow = model.sections.first { it.packageName == PROPRIETARY_PACKAGE }.rows.single()

        assertEquals("Overridden by @taiga-ui/proprietary", baseRow.overrideMessage)
        assertNull(proprietaryRow.overrideMessage)
    }

    @Test
    fun `splits root fallback when a mobile declaration overrides it`() {
        val rootDesktop =
            resolution(
                packageName = PROPRIETARY_PACKAGE,
                rawValue = "var(--tui-font-body-s)",
                value = "desktop-font",
                requestedContext = LIGHT_DESKTOP,
                sharedAcrossPlatforms = true,
            )
        val rootMobile =
            resolution(
                packageName = PROPRIETARY_PACKAGE,
                rawValue = "var(--tui-font-body-s)",
                value = "mobile-font",
                requestedContext = LIGHT_MOBILE,
                sharedAcrossPlatforms = true,
            )
        val mobileOverride =
            resolution(
                packageName = PROPRIETARY_PACKAGE,
                rawValue = "var(--tui-font-body-s)",
                value = "mobile-font",
                requestedContext = LIGHT_MOBILE,
                declarationContext = LIGHT_MOBILE,
                sharedAcrossPlatforms = false,
            )
        val section =
            DesignTokenHoverPopupModel
                .create(TOKEN, listOf(group(rootDesktop, rootMobile, mobileOverride)))
                .sections
                .single()
        val appliedRows = section.rows.filter { row -> row.overrideMessage == null }
        val overriddenRow = section.rows.single { row -> row.overrideMessage != null }

        assertEquals(
            setOf("🖥️ Desktop · Light ☀️", "📱 Mobile · Light ☀️"),
            appliedRows.map { row -> row.platform }.toSet(),
        )
        assertEquals("📱 Mobile · Light ☀️", overriddenRow.platform)
        assertEquals("Overridden by a more specific declaration", overriddenRow.overrideMessage)
    }

    private fun group(vararg resolutions: DesignTokenVariantResolution): DesignTokenResolutionGroup =
        DesignTokenResolutionGroup(resolutions.toList())

    private fun resolution(
        packageName: String,
        rawValue: String,
        value: String,
        requestedContext: DesignTokenContext,
        declarationContext: DesignTokenContext = LIGHT_DESKTOP,
        sharedAcrossPlatforms: Boolean,
    ): DesignTokenVariantResolution {
        val packageDirectory = packageName.substringAfterLast('/')
        val sourceFile = Path.of("node_modules/@taiga-ui/$packageDirectory/styles/variables.less")
        val variant =
            DesignTokenVariant(
                name = TOKEN,
                context = declarationContext,
                rawValue = rawValue,
                origins =
                    listOf(
                        DesignTokenOrigin(
                            sourceFile = sourceFile,
                            line = 1,
                            format = DesignTokenSourceFormat.LESS,
                            selectorChain = listOf(":root"),
                            packageName = packageName,
                            packageVersion = "1.0.0",
                            sharedAcrossPlatforms = sharedAcrossPlatforms,
                        ),
                    ),
            )

        return DesignTokenVariantResolution(
            variant = variant,
            result = DesignTokenValueResolution.Resolved(rawValue = rawValue, value = value),
            requestedContext = requestedContext,
        )
    }

    private companion object {
        const val TOKEN = "--tui-font-text-s"
        const val DESIGN_TOKENS_PACKAGE = "@taiga-ui/design-tokens"
        const val PROPRIETARY_PACKAGE = "@taiga-ui/proprietary"
        val LIGHT_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.LIGHT)
        val LIGHT_MOBILE = DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.LIGHT)
    }
}
