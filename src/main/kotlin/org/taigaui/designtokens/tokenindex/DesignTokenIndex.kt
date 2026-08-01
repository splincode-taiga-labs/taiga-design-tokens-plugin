package org.taigaui.designtokens.tokenindex

import java.nio.file.Path

class DesignTokenIndex private constructor(
    private val variantsByName: Map<String, List<DesignTokenVariant>>,
    val variants: List<DesignTokenVariant>,
) {
    val names: List<String> = variantsByName.keys.toList()

    val originCount: Int = variants.sumOf { it.origins.size }

    fun find(name: String): List<DesignTokenVariant> =
        variantsByName[name].orEmpty()

    companion object {
        fun build(
            packageRoot: Path,
            declarations: List<DesignTokenDeclaration>,
            contextClassifier: DesignTokenContextClassifier = DesignTokenContextClassifier(),
        ): DesignTokenIndex {
            val groupedOrigins = linkedMapOf<VariantKey, MutableList<DesignTokenOrigin>>()

            declarations
                .sortedWith(DECLARATION_COMPARATOR)
                .forEach { declaration ->
                    val normalizedSourceFile = declaration.sourceFile.toAbsolutePath().normalize()
                    val key = VariantKey(
                        name = declaration.name,
                        context = contextClassifier.classify(packageRoot, declaration),
                        rawValue = declaration.value,
                    )
                    val origin = DesignTokenOrigin(
                        sourceFile = normalizedSourceFile,
                        line = declaration.line,
                        format = DesignTokenSourceFormat.from(normalizedSourceFile),
                        selectorChain = declaration.selectorChain,
                    )

                    groupedOrigins.getOrPut(key, ::mutableListOf).add(origin)
                }

            val variants = groupedOrigins
                .map { (key, origins) ->
                    DesignTokenVariant(
                        name = key.name,
                        context = key.context,
                        rawValue = key.rawValue,
                        origins = origins
                            .distinct()
                            .sortedWith(ORIGIN_COMPARATOR),
                    )
                }
                .sortedWith(VARIANT_COMPARATOR)

            val variantsByName = linkedMapOf<String, MutableList<DesignTokenVariant>>()

            variants.forEach { variant ->
                variantsByName.getOrPut(variant.name, ::mutableListOf).add(variant)
            }

            return DesignTokenIndex(
                variantsByName = variantsByName.mapValues { (_, values) -> values.toList() },
                variants = variants.toList(),
            )
        }

        private val DECLARATION_COMPARATOR = compareBy<DesignTokenDeclaration>(
            { it.name },
            { it.sourceFile.toString() },
            { it.line },
            { it.value },
            { it.selectorChain.joinToString() },
        )

        private val ORIGIN_COMPARATOR = compareBy<DesignTokenOrigin>(
            { sourceFormatRank(it.format) },
            { it.sourceFile.toString() },
            { it.line },
            { it.selectorChain.joinToString() },
        )

        private val VARIANT_COMPARATOR = compareBy<DesignTokenVariant>(
            { it.name },
            { platformRank(it.context.platform) },
            { themeRank(it.context.theme) },
            { it.rawValue },
            { it.origins.first().sourceFile.toString() },
            { it.origins.first().line },
        )

        private fun sourceFormatRank(format: DesignTokenSourceFormat): Int =
            when (format) {
                DesignTokenSourceFormat.CSS -> 0
                DesignTokenSourceFormat.LESS -> 1
                DesignTokenSourceFormat.SCSS -> 2
                DesignTokenSourceFormat.UNKNOWN -> 3
            }

        private fun platformRank(platform: DesignTokenPlatform): Int =
            when (platform) {
                DesignTokenPlatform.DESKTOP -> 0
                DesignTokenPlatform.MOBILE -> 1
            }

        private fun themeRank(theme: DesignTokenTheme): Int =
            when (theme) {
                DesignTokenTheme.LIGHT -> 0
                DesignTokenTheme.DARK -> 1
                DesignTokenTheme.UNSPECIFIED -> 2
            }
    }

    private data class VariantKey(
        val name: String,
        val context: DesignTokenContext,
        val rawValue: String,
    )
}
