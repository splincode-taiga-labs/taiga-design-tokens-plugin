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
        hasInstalledPackage(entity) && wasIntroducedByInstalledVersion(entity)

    private fun hasInstalledPackage(entity: TaigaEntityDoc): Boolean =
        entity.packageNames.isEmpty() || entity.packageNames.any(projectContext.installedPackages::contains)

    private fun wasIntroducedByInstalledVersion(entity: TaigaEntityDoc): Boolean {
        val introduced = entity.version?.toNumericVersion()
        val installed = projectContext.version.toNumericVersion()

        return introduced == null || installed == null || introduced <= installed
    }
}

private data class NumericVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<NumericVersion> {
    override fun compareTo(other: NumericVersion): Int =
        compareValuesBy(this, other, NumericVersion::major, NumericVersion::minor, NumericVersion::patch)
}

private fun String.toNumericVersion(): NumericVersion? =
    VERSION.find(trim())?.let { match ->
        NumericVersion(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toIntOrNull() ?: 0,
            patch = match.groupValues[3].toIntOrNull() ?: 0,
        )
    }

private val VERSION = Regex("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?")
