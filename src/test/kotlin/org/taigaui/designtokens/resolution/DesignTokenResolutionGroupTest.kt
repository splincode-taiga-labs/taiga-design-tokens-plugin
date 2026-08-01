package org.taigaui.designtokens.resolution

import org.junit.Assert.assertEquals
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path

class DesignTokenResolutionGroupTest {
    @Test
    fun `keeps root origins separate from complete reference tree origins`() {
        val packageRoot =
            Path
                .of("build", "fixtures", "resolution-group")
                .toAbsolutePath()
                .normalize()
        val declarations =
            listOf(
                declaration(packageRoot, "palette/light.css", ROOT, "var(--tui-light-const)", 1),
                declaration(packageRoot, "palette/dark.css", ROOT, "var(--tui-dark-const)", 2),
                declaration(packageRoot, "palette/light.css", "--tui-light-const", "#fff", 3),
                declaration(packageRoot, "palette/dark.css", "--tui-dark-const", "#FFFFFF", 4),
            )
        val index = DesignTokenIndex.build(packageRoot, declarations)
        val group = DesignTokenValueResolver(index).resolveGrouped(ROOT).single()

        assertEquals(2, group.origins.size)
        assertEquals(4, group.allOrigins.size)
    }

    private fun declaration(
        packageRoot: Path,
        relativePath: String,
        name: String,
        value: String,
        line: Int,
    ): DesignTokenDeclaration =
        DesignTokenDeclaration(
            name = name,
            value = value,
            sourceFile = packageRoot.resolve(relativePath),
            line = line,
        )

    private companion object {
        const val ROOT = "--tui-root"
    }
}
