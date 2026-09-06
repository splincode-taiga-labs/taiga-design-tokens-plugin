package org.taigaui.designtokens.resolution

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.taigaui.designtokens.index.DesignTokenDeclaration
import org.taigaui.designtokens.index.DesignTokenIndex
import java.nio.file.Path

class DesignTokenValueResolverMemoizationTest {
    private val packageRoot =
        Path
            .of("build", "fixtures", "resolution-memoization")
            .toAbsolutePath()
            .normalize()

    @Test
    fun `parses equal raw values once across different variants`() {
        val index =
            index(
                token("--tui-first", "var(--tui-shared)"),
                token("--tui-second", "var(--tui-shared)"),
                token("--tui-shared", "#fff"),
            )
        val parseCalls = mutableMapOf<String, Int>()
        val resolver = resolver(index, parseCalls)

        resolver.resolve(index.find("--tui-first").single())
        resolver.resolve(index.find("--tui-second").single())

        assertEquals(1, parseCalls["var(--tui-shared)"])
        assertEquals(1, parseCalls["#fff"])
        assertEquals(2, resolver.parsedValueCacheSize)
    }

    @Test
    fun `reuses completed recursive resolution results`() {
        val index =
            index(
                token("--tui-root", "var(--tui-middle)"),
                token("--tui-middle", "var(--tui-terminal)"),
                token("--tui-terminal", "4px"),
            )
        val parseCalls = mutableMapOf<String, Int>()
        val resolver = resolver(index, parseCalls)
        val variant = index.find("--tui-root").single()

        val first = resolver.resolve(variant)
        val second = resolver.resolve(variant)

        assertSame(first.result, second.result)
        assertEquals(3, resolver.resolutionCacheSize)
        assertEquals(3, resolver.parsedValueCacheSize)
        assertTrue(parseCalls.values.all { calls -> calls == 1 })
    }

    @Test
    fun `does not memoize circular results and keeps entry-specific cycle chain`() {
        val index =
            index(
                token("--tui-a", "var(--tui-b)"),
                token("--tui-b", "var(--tui-a)"),
            )
        val resolver = DesignTokenValueResolver(index)

        val fromA = circular(resolver.resolve(index.find("--tui-a").single()))
        val fromB = circular(resolver.resolve(index.find("--tui-b").single()))

        assertEquals(listOf("--tui-a", "--tui-b", "--tui-a"), fromA.chain.map { node -> node.name })
        assertEquals(listOf("--tui-b", "--tui-a", "--tui-b"), fromB.chain.map { node -> node.name })
        assertEquals(0, resolver.resolutionCacheSize)
        assertEquals(2, resolver.parsedValueCacheSize)
    }

    @Test
    fun `memoizes ambiguity without changing candidate semantics`() {
        val index =
            DesignTokenIndex.build(
                packageRoot = packageRoot,
                declarations =
                    listOf(
                        declaration("--tui-root", "var(--tui-target)", "palette/dark.css", 1),
                        declaration("--tui-target", "#111", "palette/dark.css", 2),
                        declaration("--tui-target", "#222", "palette/scss/dark.scss", 3),
                    ),
            )
        val resolver = DesignTokenValueResolver(index)
        val variant = index.find("--tui-root").single()

        val first = resolver.resolve(variant)
        val second = resolver.resolve(variant)
        val firstReason = unresolved(first).reason as DesignTokenUnresolvedReason.AmbiguousReference
        val secondReason = unresolved(second).reason as DesignTokenUnresolvedReason.AmbiguousReference

        assertSame(first.result, second.result)
        assertEquals(listOf("#111", "#222"), firstReason.candidates.map { candidate -> candidate.rawValue })
        assertEquals(firstReason, secondReason)
        assertEquals(1, resolver.resolutionCacheSize)
    }

    private fun resolver(
        index: DesignTokenIndex,
        parseCalls: MutableMap<String, Int>,
    ): DesignTokenValueResolver =
        DesignTokenValueResolver(
            index = index,
            valueParser =
                DesignTokenValueParserAdapter { value ->
                    parseCalls[value] = parseCalls.getOrDefault(value, 0) + 1
                    DesignTokenValueParser.parse(value)
                },
        )

    private fun circular(resolution: DesignTokenVariantResolution): DesignTokenUnresolvedReason.CircularReference =
        unresolved(resolution).reason as DesignTokenUnresolvedReason.CircularReference

    private fun unresolved(resolution: DesignTokenVariantResolution): DesignTokenValueResolution.Unresolved =
        resolution.result as DesignTokenValueResolution.Unresolved

    private fun index(vararg tokens: Pair<String, String>): DesignTokenIndex =
        DesignTokenIndex.build(
            packageRoot = packageRoot,
            declarations =
                tokens.mapIndexed { index, (name, value) ->
                    declaration(name, value, "palette/base.css", index + 1)
                },
        )

    private fun token(
        name: String,
        value: String,
    ): Pair<String, String> = name to value

    private fun declaration(
        name: String,
        value: String,
        relativePath: String,
        line: Int,
    ): DesignTokenDeclaration =
        DesignTokenDeclaration(
            name = name,
            value = value,
            sourceFile = packageRoot.resolve(relativePath),
            line = line,
        )
}
