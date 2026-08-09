package org.taigaui.designtokens.resolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenContext
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenIndex
import org.taigaui.designtokens.index.DesignTokenPlatform
import org.taigaui.designtokens.index.DesignTokenTheme
import org.taigaui.designtokens.index.PROJECT_STYLES_PACKAGE
import java.nio.file.Path

class ProjectStylesPackagePreferenceTest {
    @Test
    fun `project root override wins over a theme-specific package declaration`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(
                    packageName = DESIGN_TOKENS_PACKAGE,
                    name = TARGET,
                    value = "#222",
                    selectorChain = listOf("[tuiTheme='dark']"),
                ),
                declaration(
                    packageName = PROJECT_STYLES_PACKAGE,
                    name = TARGET,
                    value = "red",
                    selectorChain = listOf(":root"),
                ),
            )
        val result = resolveRoot(index, DARK_DESKTOP)

        assertEquals("red", result.value)
        assertEquals(PROJECT_STYLES_PACKAGE, result.selectedReferencePackage())
    }

    @Test
    fun `project dark override applies only to desktop dark context`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(DESIGN_TOKENS_PACKAGE, TARGET, "#222"),
                declaration(
                    packageName = PROJECT_STYLES_PACKAGE,
                    name = TARGET,
                    value = "blue",
                    selectorChain = listOf("[tuiTheme='dark']"),
                ),
            )

        assertEquals("blue", resolveRoot(index, DARK_DESKTOP).value)
        assertEquals("#222", resolveRoot(index, LIGHT_DESKTOP).value)
        assertEquals("#222", resolveRoot(index, DARK_IOS).value)
    }

    @Test
    fun `project ios dark override applies only to ios dark context`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(DESIGN_TOKENS_PACKAGE, TARGET, "#222"),
                declaration(
                    packageName = PROJECT_STYLES_PACKAGE,
                    name = TARGET,
                    value = "blue",
                    selectorChain = listOf("[tuiPlatform='ios'][tuiTheme='dark']"),
                ),
            )

        assertEquals("blue", resolveRoot(index, DARK_IOS).value)
        assertEquals("#222", resolveRoot(index, DARK_ANDROID).value)
        assertEquals("#222", resolveRoot(index, DARK_DESKTOP).value)
    }

    private fun resolveRoot(
        index: DesignTokenIndex,
        requestedContext: DesignTokenContext,
    ): DesignTokenValueResolution.Resolved {
        val resolution =
            DesignTokenValueResolver(index).resolve(
                variant = index.find(ROOT).single(),
                requestedContext = requestedContext,
            )

        assertTrue(resolution.result is DesignTokenValueResolution.Resolved)

        return resolution.result as DesignTokenValueResolution.Resolved
    }

    private fun DesignTokenValueResolution.Resolved.selectedReferencePackage(): String? =
        references
            .single()
            .selectedVariant
            ?.origins
            ?.firstOrNull()
            ?.packageName

    private fun index(vararg declarations: DesignTokenDeclaration): DesignTokenIndex =
        DesignTokenIndex.build(
            packageRoot = FIXTURE_ROOT,
            declarations = declarations.toList(),
        )

    private fun declaration(
        packageName: String,
        name: String,
        value: String,
        selectorChain: List<String> = listOf(":root"),
    ): DesignTokenDeclaration {
        val packageRoot =
            if (packageName == PROJECT_STYLES_PACKAGE) {
                FIXTURE_ROOT.resolve("project")
            } else {
                FIXTURE_ROOT.resolve(packageName.substringAfterLast('/'))
            }

        return DesignTokenDeclaration(
            name = name,
            value = value,
            sourceFile = packageRoot.resolve("styles/base.less"),
            line = 1,
            selectorChain = selectorChain,
            packageName = packageName,
            packageVersion = "1.0.0",
            packageRoot = packageRoot,
        )
    }

    private companion object {
        const val ROOT = "--tui-root"
        const val TARGET = "--tui-target"
        const val CORE_PACKAGE = "@taiga-ui/core"
        const val DESIGN_TOKENS_PACKAGE = "@taiga-ui/design-tokens"
        val DARK_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.DARK)
        val LIGHT_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.LIGHT)
        val DARK_IOS = DesignTokenContext(DesignTokenPlatform.IOS, DesignTokenTheme.DARK)
        val DARK_ANDROID = DesignTokenContext(DesignTokenPlatform.ANDROID, DesignTokenTheme.DARK)
        val FIXTURE_ROOT: Path =
            Path
                .of("build", "fixtures", "project-style-package-preference")
                .toAbsolutePath()
                .normalize()
    }
}
