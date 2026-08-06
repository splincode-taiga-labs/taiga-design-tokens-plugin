package org.taigaui.designtokens.resolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path

class DesignTokenPackagePreferenceTest {
    @Test
    fun `selects the highest known package layer for a reference`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(CORE_PACKAGE, TARGET, "#111"),
                declaration(DESIGN_TOKENS_PACKAGE, TARGET, "#222"),
            )
        val result = resolveRoot(index)

        assertEquals("#111", result.value)
        assertEquals(CORE_PACKAGE, result.selectedReferencePackage())
    }

    @Test
    fun `css references are not scoped to the owning package`() {
        val index =
            index(
                declaration(DESIGN_TOKENS_PACKAGE, ROOT, "var($TARGET)"),
                declaration(CORE_PACKAGE, TARGET, "#111"),
                declaration(DESIGN_TOKENS_PACKAGE, TARGET, "#222"),
            )
        val result = resolveRoot(index)

        assertEquals("#111", result.value)
        assertEquals(CORE_PACKAGE, result.selectedReferencePackage())
    }

    @Test
    fun `uses a lower package layer when no override exists`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(DESIGN_TOKENS_PACKAGE, TARGET, "#222"),
            )
        val result = resolveRoot(index)

        assertEquals("#222", result.value)
        assertEquals(DESIGN_TOKENS_PACKAGE, result.selectedReferencePackage())
    }

    @Test
    fun `keeps unknown package order ambiguous`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(CORE_PACKAGE, TARGET, "#111"),
                declaration(UNKNOWN_PACKAGE, TARGET, "#333"),
            )
        val resolution = DesignTokenValueResolver(index).resolve(index.find(ROOT).single())
        val result = resolution.result as DesignTokenValueResolution.Unresolved

        assertTrue(result.reason is DesignTokenUnresolvedReason.AmbiguousReference)
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

    private fun DesignTokenValueResolution.Resolved.selectedReferencePackage(): String? =
        references
            .single()
            .selectedVariant
            ?.packageName()

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
        const val UNKNOWN_PACKAGE = "@taiga-ui/custom-theme"
        val FIXTURE_ROOT: Path =
            Path
                .of("build", "fixtures", "package-preference")
                .toAbsolutePath()
                .normalize()
    }
}
