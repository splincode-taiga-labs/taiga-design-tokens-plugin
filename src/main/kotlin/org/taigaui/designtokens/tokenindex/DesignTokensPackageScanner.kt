package org.taigaui.designtokens.tokenindex

import com.intellij.openapi.project.Project
import org.taigaui.designtokens.packageindex.DesignTokensPackage

class DesignTokensPackageScanner(
    private val sourceFileFinder: DesignTokenSourceFileFinder = DesignTokenSourceFileFinder(),
    private val sourceExtractor: DesignTokenSourceExtractor = DesignTokenDeclarationParser(),
) {
    constructor(project: Project) : this(
        sourceExtractor = PsiDesignTokenSourceExtractor(project),
    )

    fun scan(designTokensPackage: DesignTokensPackage): List<DesignTokenDeclaration> =
        sourceFileFinder.find(designTokensPackage.realRoot)
            .flatMap(sourceExtractor::extract)
}
