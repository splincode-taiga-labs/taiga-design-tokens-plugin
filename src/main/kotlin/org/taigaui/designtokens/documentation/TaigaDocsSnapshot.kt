package org.taigaui.designtokens.documentation

internal class TaigaDocsSnapshot(
    val projectContext: TaigaUiProjectContext,
    val index: TaigaDocsIndex,
) {
    val entities: List<TaigaEntityDoc> = index.entities.filter(::isAvailableInProject)

    fun findByPublicSymbol(symbol: String): List<TaigaEntityDoc> =
        index.findByPublicSymbol(symbol).filter(::isAvailableInProject)

    fun findBySelector(selector: String): List<TaigaEntityDoc> =
        index.findBySelector(selector).filter(::isAvailableInProject)

    fun findBySectionId(sectionId: String): TaigaEntityDoc? =
        index.findBySectionId(sectionId)?.takeIf(::isAvailableInProject)

    private fun isAvailableInProject(entity: TaigaEntityDoc): Boolean =
        entity.packageNames.isEmpty() || entity.packageNames.any(projectContext.installedPackages::contains)
}
