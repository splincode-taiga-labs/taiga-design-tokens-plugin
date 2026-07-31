package org.taigaui.designtokens.packageindex

import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.taigaui.designtokens.tokenindex.DesignTokenIndex
import org.taigaui.designtokens.tokenindex.DesignTokensPackageScanner
import java.nio.file.Files
import java.nio.file.Path

class DesignTokensNpmPackageDiagnosticTest {
    private val projectRoot = Path.of("").toAbsolutePath().normalize()
    private val packageJson = projectRoot.resolve(
        "node_modules/@taiga-ui/design-tokens/package.json",
    )

    @Test
    fun `prints real package structure and duplicate candidates`() {
        assumeTrue(Files.isRegularFile(packageJson))

        val packageInfo = DesignTokensPackageResolver().resolve(
            projectRoot.resolve("build.gradle.kts"),
        )
        assertNotNull(packageInfo)

        val sourceFiles = Files.walk(packageInfo!!.realRoot).use { paths ->
            paths
                .filter(Files::isRegularFile)
                .map(packageInfo.realRoot::relativize)
                .map(Path::toString)
                .filter { it.endsWith(".css") || it.endsWith(".less") || it.endsWith(".scss") }
                .sorted()
                .toList()
        }
        val declarations = DesignTokensPackageScanner().scan(packageInfo)
        val variants = DesignTokenIndex
            .build(packageInfo.realRoot, declarations)
            .find("--tui-background-base")

        println("REAL_TOKEN_SOURCE_FILES")
        println(sourceFiles.joinToString("\n"))
        println("REAL_BACKGROUND_BASE_VARIANTS")
        println(
            variants.joinToString("\n\n") { variant ->
                buildString {
                    append("context=")
                    append(variant.context)
                    append(" value=")
                    appendLine(variant.rawValue)
                    append(
                        variant.origins.joinToString("\n") { origin ->
                            "  ${packageInfo.realRoot.relativize(origin.sourceFile)}:${origin.line} ${origin.format}"
                        },
                    )
                }
            },
        )
    }
}
