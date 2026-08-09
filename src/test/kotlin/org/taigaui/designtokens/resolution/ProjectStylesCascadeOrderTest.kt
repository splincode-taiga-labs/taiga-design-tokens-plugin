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

class ProjectStylesCascadeOrderTest {
    @Test
    fun `later project declaration wins at equal specificity`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(PROJECT_STYLES_PACKAGE, TARGET, "blue", cascadeOrder = 0),
                declaration(PROJECT_STYLES_PACKAGE, TARGET, "red", cascadeOrder = 1),
            )

        assertEquals("red", resolveRoot(index, DARK_DESKTOP).value)
        assertEquals("red", resolveRoot(index, LIGHT_DESKTOP).value)
    }

    @Test
    fun `more specific project declaration wins before cascade order`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(
                    packageName = PROJECT_STYLES_PACKAGE,
                    name = TARGET,
                    value = "blue",
                    selectorChain = listOf("[tuiTheme='dark']"),
                    cascadeOrder = 0,
                ),
                declaration(PROJECT_STYLES_PACKAGE, TARGET, "red", cascadeOrder = 1),
            )

        assertEquals("blue", resolveRoot(index, DARK_DESKTOP).value)
        assertEquals("red", resolveRoot(index, LIGHT_DESKTOP).value)
    }

    @Test
    fun `project declarations without proven order stay ambiguous`() {
        val index =
            index(
                declaration(CORE_PACKAGE, ROOT, "var($TARGET)"),
                declaration(PROJECT_STYLES_PACKAGE, TARGET, "blue"),
                declaration(PROJECT_STYLES_PACKAGE, TARGET, "red"),
            )
        val resolution =
            DesignTokenValueResolver(index).resolve(
                variant = index.find(ROOT).single(),
                requestedContext = DARK_DESKTOP,
            )

        assertTrue(resolution.result is DesignTokenValueResolution.Unresolved)
        val unresolved = resolution.result as DesignTokenValueResolution.Unresolved

        assertTrue(unresolved.reason is DesignTokenUnresolvedReason.AmbiguousReference)
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
        cascadeOrder: Int? = null,
    ): DesignTokenDeclaration {
        val packageRoot =
            if (packageName == PROJECT_STYLES_PACKAGE) {
                FIXTURE_ROOT.resolve("project")
            } else {
                FIXTURE_ROOT.resolve(packageName.substringAfterLast('/'))
            }
        val line = cascadeOrder?.plus(1) ?: 1

        return DesignTokenDeclaration(
            name = name,
            value = value,
            sourceFile = packageRoot.resolve("styles/base.less"),
            line = line,
            selectorChain = selectorChain,
            packageName = packageName,
            packageVersion = "1.0.0",
            packageRoot = packageRoot,
            cascadeOrder = cascadeOrder,
        )
    }

    private companion object {
        const val ROOT = "--tui-root"
        const val TARGET = "--tui-target"
        const val CORE_PACKAGE = "@taiga-ui/core"
        val DARK_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.DARK)
        val LIGHT_DESKTOP = DesignTokenContext(DesignTokenPlatform.DESKTOP, DesignTokenTheme.LIGHT)
        val FIXTURE_ROOT: Path =
            Path
                .of("build", "fixtures", "project-cascade-order")
                .toAbsolutePath()
                .normalize()
    }
}
