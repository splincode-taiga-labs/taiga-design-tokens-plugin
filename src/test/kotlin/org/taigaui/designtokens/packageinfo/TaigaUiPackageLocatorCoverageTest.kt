package org.taigaui.designtokens.packageinfo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class TaigaUiPackageLocatorCoverageTest {
    @Test
    fun `locates nearest node modules scope and reads only valid Taiga packages`() {
        val workspace = Files.createTempDirectory("taiga-locator-node-modules")
        val scope = workspace.resolve("node_modules/@taiga-ui")
        val app = workspace.resolve("apps/demo/src/app.ts")

        try {
            write(app, "export {}")
            write(
                scope.resolve("core/package.json"),
                """{"name":"@taiga-ui/core","version":"5.1.0"}""",
            )
            write(
                scope.resolve("icons/package.json"),
                """{"name":"@taiga-ui/icons","version":"5.2.0"}""",
            )
            write(
                scope.resolve("wrong/package.json"),
                """{"name":"not-taiga","version":"1.0.0"}""",
            )
            Files.createDirectories(scope.resolve("missing-metadata"))

            val result = requireNotNull(TaigaUiPackageLocator().locate(app))

            assertEquals(scope.toAbsolutePath().normalize(), result.discoveryRoot)
            assertEquals(workspace.toAbsolutePath().normalize(), result.workspaceRoot)
            assertEquals(
                setOf("@taiga-ui/core", "@taiga-ui/icons"),
                result.packages.keys,
            )
            assertTrue(result.identity.startsWith("node-modules:"))
            assertTrue(result.contentVersion.contains("@taiga-ui/core@5.1.0"))
            assertTrue(result.contentVersion.contains("@taiga-ui/icons@5.2.0"))
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    @Test
    fun `locates from directory and normalizes real package roots`() {
        val workspace = Files.createTempDirectory("taiga-locator-directory")
        val scope = workspace.resolve("node_modules/@taiga-ui")
        val packageRoot = scope.resolve("core")

        try {
            Files.createDirectories(workspace.resolve("src"))
            write(
                packageRoot.resolve("package.json"),
                """{"name":"@taiga-ui/core","version":"5.0.0"}""",
            )

            val result =
                requireNotNull(
                    TaigaUiPackageLocator().locate(workspace.resolve("src")),
                )
            val core = requireNotNull(result.packages["@taiga-ui/core"])

            assertEquals(packageRoot.toAbsolutePath().normalize(), core.root)
            assertEquals(packageRoot.toRealPath(), core.realRoot)
            assertTrue(core.identity.startsWith("fs:"))
            assertEquals("5.0.0", core.contentVersion)
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    @Test
    fun `returns null when no taiga scope or pnp manifest can be found`() {
        val workspace = Files.createTempDirectory("taiga-locator-empty")

        try {
            val source = workspace.resolve("src/app.ts")
            write(source, "export {}")

            assertNull(TaigaUiPackageLocator().locate(source))
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    @Test
    fun `ignores malformed package json entries without failing whole scope`() {
        val workspace = Files.createTempDirectory("taiga-locator-malformed")
        val scope = workspace.resolve("node_modules/@taiga-ui")
        val source = workspace.resolve("src/app.ts")

        try {
            write(source, "export {}")
            write(scope.resolve("broken/package.json"), "{not-json")
            write(
                scope.resolve("core/package.json"),
                """{"name":"@taiga-ui/core","version":"5.0.0"}""",
            )

            val result = requireNotNull(TaigaUiPackageLocator().locate(source))

            assertEquals(setOf("@taiga-ui/core"), result.packages.keys)
            assertFalse(result.contentVersion.contains("broken"))
        } finally {
            workspace.toFile().deleteRecursively()
        }
    }

    @Test
    fun `scope cache key uses discovery directory or stable virtual identity`() {
        val physical = Files.createTempDirectory("taiga-scope-cache")

        try {
            val physicalScope =
                TaigaUiPackageScope(
                    workspaceRoot = physical,
                    discoveryRoot = physical,
                    packages = emptyMap(),
                    identity = "physical",
                    contentVersion = "1",
                )
            val virtualScope =
                TaigaUiPackageScope(
                    workspaceRoot = physical,
                    discoveryRoot = physical.resolve(".pnp.cjs"),
                    packages = emptyMap(),
                    identity = "virtual",
                    contentVersion = "1",
                )

            assertEquals(physical, physicalScope.cacheKey)
            assertTrue(virtualScope.cacheKey.isAbsolute)
            assertTrue(virtualScope.cacheKey.toString().contains("yarn-pnp-scopes"))
            assertEquals(
                virtualScope.cacheKey,
                TaigaUiPackageScope(
                    workspaceRoot = physical,
                    discoveryRoot = physical.resolve("different.pnp.cjs"),
                    packages = emptyMap(),
                    identity = "virtual",
                    contentVersion = "1",
                ).cacheKey,
            )
        } finally {
            physical.toFile().deleteRecursively()
        }
    }

    private fun write(
        path: Path,
        content: String,
    ) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
    }
}
