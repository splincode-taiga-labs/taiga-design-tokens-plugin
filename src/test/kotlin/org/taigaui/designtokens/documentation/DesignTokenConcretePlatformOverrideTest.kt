package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
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

class DesignTokenConcretePlatformOverrideTest {
    @Test
    fun `ios declaration overrides generic mobile only for ios`() {
        val genericIos = resolution(MOBILE, IOS, "mobile")
        val genericAndroid = resolution(MOBILE, ANDROID, "mobile")
        val explicitIos = resolution(IOS, IOS, "ios")
        val section =
            DesignTokenHoverPopupModel
                .create(
                    TOKEN,
                    listOf(DesignTokenResolutionGroup(listOf(genericIos, genericAndroid, explicitIos))),
                ).sections
                .single()
        val appliedRows = section.rows.filter { row -> row.overrideMessage == null }
        val overriddenRow = section.rows.single { row -> row.overrideMessage != null }

        assertEquals(
            setOf(
                "📱 iOS · Light ☀️" to "ios",
                "🤖 Android · Light ☀️" to "mobile",
            ),
            appliedRows.map { row -> row.platform to row.resolvedValue }.toSet(),
        )
        assertEquals("📱 iOS · Light ☀️", overriddenRow.platform)
        assertEquals(
            "Overridden by a platform-specific declaration",
            overriddenRow.overrideMessage,
        )
    }

    private fun resolution(
        declarationPlatform: DesignTokenPlatform,
        requestedPlatform: DesignTokenPlatform,
        value: String,
    ): DesignTokenVariantResolution {
        val context = DesignTokenContext(declarationPlatform, DesignTokenTheme.UNSPECIFIED)

        return DesignTokenVariantResolution(
            variant =
                DesignTokenVariant(
                    name = TOKEN,
                    context = context,
                    rawValue = value,
                    origins =
                        listOf(
                            DesignTokenOrigin(
                                sourceFile = Path.of("styles/mobile.less"),
                                line = 1,
                                format = DesignTokenSourceFormat.LESS,
                                selectorChain = listOf("[data-platform='$requestedPlatform']"),
                                packageName = PACKAGE,
                                packageVersion = "1.0.0",
                            ),
                        ),
                ),
            result = DesignTokenValueResolution.Resolved(rawValue = value, value = value),
            requestedContext = DesignTokenContext(requestedPlatform, DesignTokenTheme.LIGHT),
        )
    }

    private companion object {
        const val TOKEN = "--tui-font-text-s"
        const val PACKAGE = "@taiga-ui/proprietary"
        val MOBILE = DesignTokenPlatform.MOBILE
        val IOS = DesignTokenPlatform.IOS
        val ANDROID = DesignTokenPlatform.ANDROID
    }
}
