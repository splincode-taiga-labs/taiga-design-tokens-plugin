package org.taigaui.designtokens.tokenindex

import com.intellij.openapi.project.Project
import org.taigaui.designtokens.packageindex.DesignTokensPackage

class DesignTokensPackageScanner(
    private val sourceExtractor: DesignTokenSourceExtractor,
    private val sourceFileFinder: DesignTokenSourceFileFinder = DesignTokenSourceFileFinder(),
) {
    constructor(project: Project) : this(
        sourceExtractor = PsiDesignTokenSourceExtractor(project),
    )

    internal constructor() : this(
        sourceExtractor = DesignTokenDeclarationParser(),
    )

    fun scan(designTokensPackage: DesignTokensPackage): List<DesignTokenDeclaration> =
        sourceFileFinder.find(designTokensPackage.realRoot)
            .flatMap(sourceExtractor::extract)
}
