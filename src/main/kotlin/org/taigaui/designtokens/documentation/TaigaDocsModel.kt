package org.taigaui.designtokens.documentation

import java.net.URI

internal enum class TaigaDocKind {
    COMPONENT,
    DIRECTIVE,
    PIPE,
    SERVICE,
    TYPE,
    TOKEN,
    UTILITY,
    CLASS,
    UNKNOWN;

    companion object {
        fun from(
            route: String,
            title: String,
            metadataType: String?,
        ): TaigaDocKind =
            when {
                title.equals("tokens", ignoreCase = true) -> TOKEN
                route == "components" || metadataType == "components" -> COMPONENT
                route == "directives" || metadataType == "directives" -> DIRECTIVE
                route == "pipes" || metadataType == "pipes" -> PIPE
                route == "services" || metadataType == "services" -> SERVICE
                route == "types" || metadataType == "types" -> TYPE
                route == "tokens" || metadataType == "tokens" -> TOKEN
                route == "utils" || metadataType?.contains("utils") == true -> UTILITY
                route == "classes" || metadataType == "classes" -> CLASS
                else -> UNKNOWN
            }
    }
}

internal data class TaigaApiProperty(
    val name: String,
    val signature: String,
    val documentedType: String?,
    val description: String?,
)

internal data class TaigaExample(
    val language: String?,
    val code: String,
)

internal data class TaigaEntityDoc(
    val sectionId: String,
    val title: String,
    val packageNames: Set<String>,
    val kind: TaigaDocKind,
    val version: String?,
    val description: String?,
    val publicSymbols: Set<String>,
    val selectors: Set<String>,
    val inputs: List<TaigaApiProperty>,
    val outputs: List<TaigaApiProperty>,
    val example: TaigaExample?,
    val documentationUri: URI,
)

internal class TaigaDocsIndex(
    val source: TaigaDocsSource,
    entities: Collection<TaigaEntityDoc>,
) {
    val entities: List<TaigaEntityDoc> = entities.toList()
    private val byPublicSymbol: Map<String, List<TaigaEntityDoc>> =
        this.entities
            .flatMap { entity -> entity.publicSymbols.map { symbol -> symbol to entity } }
            .groupBy(
                keySelector = Pair<String, TaigaEntityDoc>::first,
                valueTransform = Pair<String, TaigaEntityDoc>::second,
            )
    private val bySelector: Map<String, List<TaigaEntityDoc>> =
        this.entities
            .flatMap { entity -> entity.selectors.map { selector -> selector to entity } }
            .groupBy(
                keySelector = Pair<String, TaigaEntityDoc>::first,
                valueTransform = Pair<String, TaigaEntityDoc>::second,
            )
    private val bySectionId: Map<String, TaigaEntityDoc> =
        this.entities.associateBy { entity -> entity.sectionId.lowercase() }

    fun findByPublicSymbol(symbol: String): List<TaigaEntityDoc> = byPublicSymbol[symbol].orEmpty()

    fun findBySelector(selector: String): List<TaigaEntityDoc> = bySelector[selector].orEmpty()

    fun findBySectionId(sectionId: String): TaigaEntityDoc? = bySectionId[sectionId.lowercase()]
}
