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
import org.taigaui.designtokens.resolution.DesignTokenValueResolution
import org.taigaui.designtokens.resolution.DesignTokenVariantResolution
import java.nio.file.Path

class ProjectStylesOverridePresentationTest {
    @Test
    fun `generic project override mutes a more specific package declaration`() {
        val packageResolution = resolution(packageVariant(DARK_DESKTOP, sharedAcrossPlatforms = true), DARK_DESKTOP)
        val projectResolution = resolution(projectVariant(ANY_DESKTOP, sharedAcrossPlatforms = true), DARK_DESKTOP)
        val decorated = listOf(packageResolution, projectResolution).withOverrideState()

        assertEquals(
            "Overridden by $PROJECT_STYLES_PACKAGE",
            decorated.single { item -> item.packageName == DESIGN_TOKENS_PACKAGE }.overrideMessage,
        )
        assertNull(decorated.single { item -> item.packageName == PROJECT_STYLES_PACKAGE }.overrideMessage)
    }

    @Test
    fun `dark project override mutes only desktop dark package context`() {
        val packageVariant = packageVariant(DARK_DESKTOP, sharedAcrossPlatforms = true)
        val projectVariant = projectVariant(DARK_DESKTOP, sharedAcrossPlatforms = false)
        val decorated =
            listOf(
                resolution(packageVariant, DARK_DESKTOP),
                resolution(packageVariant, DARK_IOS),
                resolution(packageVariant, DARK_ANDROID),
                resolution(projectVariant, DARK_DESKTOP),
            ).withOverrideState()
        val packageResolutions = decorated.filter { item -> item.packageName == DESIGN_TOKENS_PACKAGE }

        assertEquals(3, packageResolutions.size)
        assertEquals(
            "Overridden by $PROJECT_STYLES_PACKAGE",
            packageResolutions.single { item -> item.resolution.requestedContext == DARK_DESKTOP }.overrideMessage,
        )
        assertNull(packageResolutions.single { item -> item.resolution.requestedContext == DARK_IOS }.overrideMessage)
        assertNull(
            packageResolutions.single { item -> item.resolution.requestedContext == DARK_ANDROID }.overrideMessage,
        )
    }

    @Test
    fun `ios dark project override mutes only ios dark package context`() {
        val packageVariant = packageVariant(DARK_DESKTOP, sharedAcrossPlatforms = true)
        val projectVariant = projectVariant(DARK_IOS, sharedAcrossPlatforms = false)
        val decorated =
            listOf(
                resolution(packageVariant, DARK_DESKTOP),
                resolution(packageVariant, DARK_IOS),
                resolution(packageVariant, DARK_ANDROID),
                resolution(projectVariant, DARK_IOS),
            ).withOverrideState()
        val packageResolutions = decorated.filter { item -> item.packageName == DESIGN_TOKENS_PACKAGE }

        assertEquals(3, packageResolutions.size)
        assertNull(
            packageResolutions.single { item -> item.resolution.requestedContext == DARK_DESKTOP }.overrideMessage,
        )
        assertEquals(
            "Overridden by $PROJECT_STYLES_PACKAGE",
            packageResolutions.single { item -> item.resolution.requestedContext == DARK_IOS }.overrideMessage,
        )
        assertNull(
            packageResolutions.single { item -> item.resolution.requestedContext == DARK_ANDROID }.overrideMessage,
        )
        assertTrue(decorated.any { item -> item.packageName == PROJECT_STYLES_PACKAGE && item.overrideMessage == null })
    }

    private fun packageVariant(
        context: DesignTokenContext,
        sharedAcrossPlatforms: Boolean,
    ): DesignTokenVariant =
        variant(
            packageName = DESIGN_TOKENS_PACKAGE,
            context = context,
            value = "package",
            sharedAcrossPlatforms = sharedAcrossPlatforms,
        )

    private fun projectVariant(
        context: DesignTokenContext,
        sharedAcrossPlatforms: Boolean,
    ): DesignTokenVariant =
        variant(
            packageName = PROJECT_STYLES_PACKAGE,
            context = context,
            value = "project",
            sharedAcrossPlatforms = sharedAcrossPlatforms,
        )

    private fun variant(
        packageName: String,
        context: DesignTokenContext,
        value: String,
        sharedAcrossPlatforms: Boolean,
    ): DesignTokenVariant =
        DesignTokenVariant(
            name = TOKEN,
            context = context,
            rawValue = value,
            origins =
                listOf(
                    DesignTokenOrigin(
                        sourceFile = Path.of("styles/$packageName.less"),
                        line = 1,
                        format = DesignTokenSourceFormat.LESS,
                        selectorChain = listOf(":root"),
                        packageName = packageName,
                        packageVersion = "1.0.0",
                        sharedAcrossPlatforms = sharedAcrossPlatforms,
                    ),
                ),
        )

    private fun resolution(
        variant: DesignTokenVariant,
        requestedContext: DesignTokenContext,
    ): DesignTokenVariantResolution =
        DesignTokenVariantResolution(
            variant = variant,
            result =
                DesignTokenValueResolution.Resolved(
                    rawValue = variant.rawValue,
                    value = variant.rawValue,
                ),
            requestedContext = requestedContext,
        )

    private companion object {
        const val TOKEN = "--tui-text-primary"
        const val DESIGN_TOKENS_PACKAGE = "@taiga-ui/design-tokens"
        val ANY_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.UNSPECIFIED)
        val DARK_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.DARK)
        val DARK_IOS = DesignTokenContext(DesignTokenPlatform.IOS, DesignTokenTheme.DARK)
        val DARK_ANDROID = DesignTokenContext(DesignTokenPlatform.ANDROID, DesignTokenTheme.DARK)
    }
}
