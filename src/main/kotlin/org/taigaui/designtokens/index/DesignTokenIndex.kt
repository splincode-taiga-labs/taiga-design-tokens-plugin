package org.taigaui.designtokens.index

import java.nio.file.Path

class DesignTokenIndex private constructor(
    private val variantsByName: Map<String, List<DesignTokenVariant>>,
    val variants: List<DesignTokenVariant>,
) {
    val names: List<String> = variantsByName.keys.toList()

    val originCount: Int = variants.sumOf { it.origins.size }

    fun find(name: String): List<DesignTokenVariant> = variantsByName[name].orEmpty()

    fun deprecationFor(name: String): DesignTokenDeprecation? {
        val variants = find(name)

        if (variants.any(DesignTokenVariant::hasProjectOrigin)) {
            return null
        }

        return variants
            .flatMap { variant -> variant.origins }
            .mapNotNull(DesignTokenOrigin::deprecation)
            .distinct()
            .singleOrNull()
    }

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
                    val declarationPackageRoot = declaration.packageRoot ?: packageRoot
                    val key =
                        VariantKey(
                            name = declaration.name,
                            context = contextClassifier.classify(declarationPackageRoot, declaration),
                            rawValue = declaration.value,
                            packageName = declaration.packageName,
                        )
                    val origin =
                        DesignTokenOrigin(
                            sourceFile = normalizedSourceFile,
                            line = declaration.line,
                            format = DesignTokenSourceFormat.from(normalizedSourceFile),
                            selectorChain = declaration.selectorChain,
                            packageName = declaration.packageName,
                            packageVersion = declaration.packageVersion,
                            sharedAcrossPlatforms =
                                contextClassifier.isSharedAcrossPlatforms(
                                    declarationPackageRoot,
                                    declaration,
                                ),
                            cascadeOrder = declaration.cascadeOrder,
                            deprecation = declaration.deprecation,
                        )

                    groupedOrigins.getOrPut(key, ::mutableListOf).add(origin)
                }

            return fromVariants(
                groupedOrigins.map { (key, origins) ->
                    DesignTokenVariant(
                        name = key.name,
                        context = key.context,
                        rawValue = key.rawValue,
                        origins =
                            origins
                                .distinct()
                                .sortedWith(ORIGIN_COMPARATOR),
                    )
                },
            )
        }

        fun merge(indexes: Collection<DesignTokenIndex>): DesignTokenIndex =
            fromVariants(
                indexes
                    .flatMap(DesignTokenIndex::variants)
                    .distinct(),
            )

        private fun fromVariants(sourceVariants: List<DesignTokenVariant>): DesignTokenIndex {
            val variants = sourceVariants.sortedWith(VARIANT_COMPARATOR)
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
                { variant -> packageRank(variant.origins.firstOrNull()?.packageName) },
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
                "@taiga-ui/core" -> 2
                "@taiga-ui/proprietary" -> 3
                PROJECT_STYLES_PACKAGE -> 4
                null -> 5
                else -> 6
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
        val packageName: String?,
    )
}

private fun DesignTokenVariant.hasProjectOrigin(): Boolean =
    origins.any { origin -> origin.packageName == PROJECT_STYLES_PACKAGE }

internal const val PROJECT_STYLES_PACKAGE = "Project styles"
