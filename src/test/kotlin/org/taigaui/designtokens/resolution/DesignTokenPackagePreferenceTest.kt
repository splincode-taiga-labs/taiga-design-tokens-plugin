package org.taigaui.designtokens.resolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path

class DesignTokenPackagePreferenceTest {
    @Test
    fun `prefers a referenced token from the owning package`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(CORE_PACKAGE, TARGET, "#111"),
                declaration(DESIGN_TOKENS_PACKAGE, TARGET, "#222"),
            )
        val result = resolveRoot(index)
        val selectedPackage =
            result.references
                .single()
                .selectedVariant
                ?.packageName()

        assertEquals("#111", result.value)
        assertEquals(CORE_PACKAGE, selectedPackage)
    }

    @Test
    fun `falls back to another package when the owning package has no candidate`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(DESIGN_TOKENS_PACKAGE, TARGET, "#222"),
            )
        val result = resolveRoot(index)
        val selectedPackage =
            result.references
                .single()
                .selectedVariant
                ?.packageName()

        assertEquals("#222", result.value)
        assertEquals(DESIGN_TOKENS_PACKAGE, selectedPackage)
    }

    @Test
    fun `keeps identical declarations separate by source package`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "same-value"),
                declaration(DESIGN_TOKENS_PACKAGE, ROOT, "same-value"),
            )

        assertEquals(2, index.find(ROOT).size)
        assertEquals(
            setOf(CORE_PACKAGE, DESIGN_TOKENS_PACKAGE),
            index.find(ROOT).mapNotNull { variant -> variant.packageName() }.toSet(),
        )
    }

    private fun resolveRoot(index: DesignTokenIndex): DesignTokenValueResolution.Resolved {
        val resolution = DesignTokenValueResolver(index).resolve(index.find(ROOT).single())

        assertTrue(resolution.result is DesignTokenValueResolution.Resolved)

        return resolution.result as DesignTokenValueResolution.Resolved
    }

    private fun index(vararg declarations: DesignTokenDeclaration): DesignTokenIndex =
        DesignTokenIndex.build(
            packageRoot = FIXTURE_ROOT,
            declarations = declarations.toList(),
        )

    private fun declaration(
        packageName: String,
        name: String,
        value: String,
    ): DesignTokenDeclaration {
        val packageRoot = FIXTURE_ROOT.resolve(packageName.substringAfterLast('/'))

        return DesignTokenDeclaration(
            name = name,
            value = value,
            sourceFile = packageRoot.resolve("styles/base.less"),
            line = 1,
            selectorChain = listOf(":root"),
            packageName = packageName,
            packageVersion = "1.0.0",
            packageRoot = packageRoot,
        )
    }

    private fun org.taigaui.designtokens.index.DesignTokenVariant.packageName(): String? =
        origins.firstOrNull()?.packageName

    private companion object {
        const val ROOT = "--tui-root"
        const val TARGET = "--tui-target"
        const val CORE_PACKAGE = "@taiga-ui/core"
        const val DESIGN_TOKENS_PACKAGE = "@taiga-ui/design-tokens"
        val FIXTURE_ROOT: Path =
            Path
                .of("build", "fixtures", "package-preference")
                .toAbsolutePath()
                .normalize()
    }
}
