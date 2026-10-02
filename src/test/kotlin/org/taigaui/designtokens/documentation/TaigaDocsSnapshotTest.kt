package org.taigaui.designtokens.documentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaigaDocsSnapshotTest {
    @Test
    fun filtersDocumentationForPackagesNotInstalledInProject() {
        val source = requireNotNull(TaigaDocsSources.forMajor(5))
        val core = entity(source, "components/button", "@taiga-ui/core", "TuiButton")
        val kit = entity(source, "components/badge", "@taiga-ui/kit", "TuiBadge")
        val future = entity(source, "components/keypad", "@taiga-ui/core", "TuiKeypad", version = "5.19.0")
        val snapshot =
            TaigaDocsSnapshot(
                projectContext =
                    TaigaUiProjectContext(
                        version = "5.18.0",
                        majorVersion = 5,
                        versionSourcePackage = "@taiga-ui/core",
                        installedPackages = setOf("@taiga-ui/core"),
                        packageScopeIdentity = "fixture",
                    ),
                index = TaigaDocsIndex(source, listOf(core, kit, future)),
            )

        assertEquals(listOf(core), snapshot.entities)
        assertEquals(listOf(core), snapshot.findByPublicSymbol("TuiButton"))
        assertEquals(emptyList<TaigaEntityDoc>(), snapshot.findByPublicSymbol("TuiBadge"))
        assertEquals(emptyList<TaigaEntityDoc>(), snapshot.findByPublicSymbol("TuiKeypad"))
        assertNull(snapshot.findBySectionId("components/badge"))
        assertNull(snapshot.findBySectionId("components/keypad"))
    }

    private fun entity(
        source: TaigaDocsSource,
        sectionId: String,
        packageName: String,
        symbol: String,
        version: String = "5.0.0",
    ): TaigaEntityDoc =
        TaigaEntityDoc(
            sectionId = sectionId,
            title = symbol.removePrefix("Tui"),
            packageNames = setOf(packageName),
            kind = TaigaDocKind.COMPONENT,
            version = version,
            description = null,
            publicSymbols = setOf(symbol),
            selectors = emptySet(),
            inputs = emptyList(),
            outputs = emptyList(),
            example = null,
            documentationUri = source.documentationUri(sectionId),
        )
}
