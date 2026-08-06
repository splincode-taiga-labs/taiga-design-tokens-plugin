package org.taigaui.designtokens.index

import java.nio.file.Path

class DesignTokenIndex private constructor(
    private val variantsByName: Map<String, List<DesignTokenVariant>>,
    val variants: List<DesignTokenVariant>,
) {
    val names: List<String> = variantsByName.keys.toList()

    val originCount: Int = variants.sumOf { it.origins.size }

    fun find(name: String): List<DesignTokenVariant> = variantsByName[name].orEmpty()

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
                    val origin =
                        DesignTokenOrigin(
                            sourceFile = normalizedSourceFile,
                            line = declaration.line,
                            format = DesignTokenSourceFormat.from(normalizedSourceFile),
                            selectorChain = declaration.selectorChain,
                            packageName = declaration.packageName,
                            packageVersion = declaration.packageVersion,
                        )

                    contextClassifier
                        .classifyAll(
                            declaration.packageRoot ?: packageRoot,
                            declaration,
                        ).forEach { context ->
                            val key =
                                VariantKey(
                                    name = declaration.name,
                                    context = context,
                                    rawValue = declaration.value,
                                )

                            groupedOrigins.getOrPut(key, ::mutableListOf).add(origin)
                        }
                }

            val variants =
                groupedOrigins
                    .map { (key, origins) ->
                        DesignTokenVariant(
                            name = key.name,
                            context = key.context,
                            rawValue = key.rawValue,
                            origins =
                                origins
                                    .distinct()
                                    .sortedWith(ORIGIN_COMPARATOR),
                        )
                    }.sortedWith(VARIANT_COMPARATOR)

            val variantsByName = linkedMapOf<String, MutableList<DesignTokenVariant>>()

            variants.forEach { variant ->
                variantsByName.getOrPut(variant.name, ::mutableListOf).add(variant)
            }

            return DesignTokenIndex(
                variantsByName = variantsByName.mapValues { (_, values) -> values.toList() },
                variants = variants.toList(),
            )
        }

        private val DECLARATION_COMPARATOR =
            compareBy<DesignTokenDeclaration>(
                { declaration -> declaration.name },
                { declaration -> declaration.packageName.orEmpty() },
                { declaration -> declaration.sourceFile.toString() },
                { declaration -> declaration.line },
                { declaration -> declaration.value },
                { declaration -> declaration.selectorChain.joinToString() },
            )

        private val ORIGIN_COMPARATOR =
            compareBy<DesignTokenOrigin>(
                { origin -> packageRank(origin.packageName) },
                { origin -> origin.packageName.orEmpty() },
                { origin -> sourceFormatRank(origin.format) },
                { origin -> origin.sourceFile.toString() },
                { origin -> origin.line },
                { origin -> origin.selectorChain.joinToString() },
            )

        private val VARIANT_COMPARATOR =
            compareBy<DesignTokenVariant>(
                { variant -> variant.name },
                { variant -> platformRank(variant.context.platform) },
                { variant -> themeRank(variant.context.theme) },
                { variant -> variant.rawValue },
                {
                    it.origins
                        .first()
                        .sourceFile
                        .toString()
                },
                { variant -> variant.origins.first().line },
            )

        private fun packageRank(packageName: String?): Int =
            when (packageName) {
                "@taiga-ui/design-tokens" -> 0
                "@taiga-ui/styles" -> 1
                else -> 2
            }

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
                DesignTokenPlatform.IOS -> 1
                DesignTokenPlatform.ANDROID -> 2
                DesignTokenPlatform.MOBILE -> 3
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
