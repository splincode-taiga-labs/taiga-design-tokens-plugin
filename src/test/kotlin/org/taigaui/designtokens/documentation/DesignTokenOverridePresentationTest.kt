package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenOrigin
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenSourceFormat
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.DesignTokenVariant
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
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
                        requestedContext = LIGHT_IOS,
                        sharedAcrossPlatforms = true,
                    ),
                    resolution(
                        packageName = DESIGN_TOKENS_PACKAGE,
                        rawValue = "base",
                        value = "base",
                        requestedContext = LIGHT_ANDROID,
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
                        requestedContext = LIGHT_IOS,
                        sharedAcrossPlatforms = true,
                    ),
                    resolution(
                        packageName = PROPRIETARY_PACKAGE,
                        rawValue = "brand",
                        value = "brand",
                        requestedContext = LIGHT_ANDROID,
                        sharedAcrossPlatforms = true,
                    ),
                ),
            )
        val model = DesignTokenHoverPopupModel.create(TOKEN, groups)
        val baseRow =
            model.sections
                .first { section -> section.packageName == DESIGN_TOKENS_PACKAGE }
                .rows
                .single()
        val proprietaryRow =
            model.sections
                .first { section -> section.packageName == PROPRIETARY_PACKAGE }
                .rows
                .single()

        assertEquals("Overridden by @taiga-ui/proprietary", baseRow.overrideMessage)
        assertNull(proprietaryRow.overrideMessage)
    }

    @Test
    fun `project styles override installed package values`() {
        val model =
            DesignTokenHoverPopupModel.create(
                TOKEN,
                listOf(
                    group(
                        resolution(
                            packageName = DESIGN_TOKENS_PACKAGE,
                            rawValue = "package",
                            value = "package",
                            requestedContext = LIGHT_DESKTOP,
                            sharedAcrossPlatforms = true,
                        ),
                        resolution(
                            packageName = PROJECT_STYLES_PACKAGE,
                            rawValue = "project",
                            value = "project",
                            requestedContext = LIGHT_DESKTOP,
                            sharedAcrossPlatforms = true,
                        ),
                    ),
                ),
            )
        val packageRow =
            model.sections
                .first { section -> section.packageName == DESIGN_TOKENS_PACKAGE }
                .rows
                .single()
        val projectRow =
            model.sections
                .first { section -> section.packageName == PROJECT_STYLES_PACKAGE }
                .rows
                .single()

        assertEquals("Overridden by $PROJECT_STYLES_PACKAGE", packageRow.overrideMessage)
        assertNull(projectRow.overrideMessage)
    }

    @Test
    fun `selector-local project value overrides installed package value`() {
        val model =
            DesignTokenHoverPopupModel.create(
                TOKEN,
                listOf(
                    group(
                        resolution(
                            packageName = DESIGN_TOKENS_PACKAGE,
                            rawValue = "0.3s",
                            value = "0.3s",
                            requestedContext = LIGHT_DESKTOP,
                            sharedAcrossPlatforms = true,
                        ),
                        resolution(
                            packageName = PROJECT_STYLES_PACKAGE,
                            rawValue = "0",
                            value = "0",
                            requestedContext = LIGHT_DESKTOP,
                            sharedAcrossPlatforms = true,
                            localOverride = true,
                        ),
                    ),
                ),
            )
        val packageRow =
            model.sections
                .first { section -> section.packageName == DESIGN_TOKENS_PACKAGE }
                .rows
                .single()
        val projectRow =
            model.sections
                .first { section -> section.packageName == PROJECT_STYLES_PACKAGE }
                .rows
                .single()

        assertEquals("0", projectRow.resolvedValue)
        assertNull(projectRow.overrideMessage)
        assertEquals("Overridden by $PROJECT_STYLES_PACKAGE", packageRow.overrideMessage)
    }

    @Test
    fun `shows a root fallback only for contexts where it is effective`() {
        val rootDesktop = rootResolution(LIGHT_DESKTOP, "desktop-font")
        val rootIos = rootResolution(LIGHT_IOS, "desktop-font")
        val rootAndroid = rootResolution(LIGHT_ANDROID, "desktop-font")
        val mobileIos = mobileResolution(LIGHT_IOS, "mobile-font")
        val mobileAndroid = mobileResolution(LIGHT_ANDROID, "mobile-font")
        val section =
            DesignTokenHoverPopupModel
                .create(
                    TOKEN,
                    listOf(group(rootDesktop, rootIos, rootAndroid, mobileIos, mobileAndroid)),
                ).sections
                .single()

        assertEquals(
            setOf(
                "🖥️ Desktop · Light ☀️" to "desktop-font",
                "📱 Mobile · Light ☀️" to "mobile-font",
            ),
            section.rows.map { row -> row.platform to row.resolvedValue }.toSet(),
        )
        assertTrue(section.rows.all { row -> row.overrideMessage == null })
    }

    @Test
    fun `collapses equal root and mobile declarations into one applied row`() {
        val rootDesktop = rootResolution(LIGHT_DESKTOP, "same-value")
        val rootIos = rootResolution(LIGHT_IOS, "same-value")
        val rootAndroid = rootResolution(LIGHT_ANDROID, "same-value")
        val mobileIos = mobileResolution(LIGHT_IOS, "same-value")
        val mobileAndroid = mobileResolution(LIGHT_ANDROID, "same-value")
        val row =
            DesignTokenHoverPopupModel
                .create(
                    TOKEN,
                    listOf(group(rootDesktop, rootIos, rootAndroid, mobileIos, mobileAndroid)),
                ).sections
                .single()
                .rows
                .single()

        assertEquals("All platforms · Light ☀️", row.platform)
        assertEquals("same-value", row.resolvedValue)
        assertNull(row.overrideMessage)
    }

    @Test
    fun `platform scope wins before package layer`() {
        val proprietaryRoot =
            resolution(
                packageName = PROPRIETARY_PACKAGE,
                rawValue = "proprietary-root",
                value = "proprietary-root",
                requestedContext = LIGHT_IOS,
                sharedAcrossPlatforms = true,
            )
        val designTokensMobile =
            resolution(
                packageName = DESIGN_TOKENS_PACKAGE,
                rawValue = "design-mobile",
                value = "design-mobile",
                requestedContext = LIGHT_IOS,
                declarationContext = MOBILE_ANY_THEME,
                sharedAcrossPlatforms = false,
            )
        val model =
            DesignTokenHoverPopupModel.create(
                TOKEN,
                listOf(group(proprietaryRoot, designTokensMobile)),
            )
        val proprietaryRow =
            model.sections
                .first { section -> section.packageName == PROPRIETARY_PACKAGE }
                .rows
                .single()
        val designTokensRow =
            model.sections
                .first { section -> section.packageName == DESIGN_TOKENS_PACKAGE }
                .rows
                .single()

        assertEquals("Overridden by a platform-specific declaration", proprietaryRow.overrideMessage)
        assertNull(designTokensRow.overrideMessage)
    }

    private fun rootResolution(
        requestedContext: DesignTokenContext,
        value: String,
    ): DesignTokenVariantResolution =
        resolution(
            packageName = PROPRIETARY_PACKAGE,
            rawValue = "var(--tui-font-body-s)",
            value = value,
            requestedContext = requestedContext,
            sharedAcrossPlatforms = true,
        )

    private fun mobileResolution(
        requestedContext: DesignTokenContext,
        value: String,
    ): DesignTokenVariantResolution =
        resolution(
            packageName = PROPRIETARY_PACKAGE,
            rawValue = "var(--tui-font-body-s)",
            value = value,
            requestedContext = requestedContext,
            declarationContext = MOBILE_ANY_THEME,
            sharedAcrossPlatforms = false,
        )

    private fun group(vararg resolutions: DesignTokenVariantResolution): DesignTokenResolutionGroup =
        DesignTokenResolutionGroup(resolutions.toList())

    private fun resolution(
        packageName: String,
        rawValue: String,
        value: String,
        requestedContext: DesignTokenContext,
        declarationContext: DesignTokenContext = LIGHT_DESKTOP,
        sharedAcrossPlatforms: Boolean,
        localOverride: Boolean = false,
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
                            localOverride = localOverride,
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
        val LIGHT_IOS = DesignTokenContext(DesignTokenPlatform.IOS, DesignTokenTheme.LIGHT)
        val LIGHT_ANDROID = DesignTokenContext(DesignTokenPlatform.ANDROID, DesignTokenTheme.LIGHT)
        val MOBILE_ANY_THEME =
            DesignTokenContext(DesignTokenPlatform.MOBILE, DesignTokenTheme.UNSPECIFIED)
    }
}
