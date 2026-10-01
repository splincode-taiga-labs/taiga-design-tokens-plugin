package org.taigaui.designtokens.project

import java.nio.file.Path

internal class ProjectStylesheetImportResolver(
    private val imports: (Path) -> List<String>,
) {
    fun resolveImports(
        sourceFile: Path,
        projectRoot: Path,
        workspaceRoot: Path,
    ): List<Path> =
        imports(sourceFile).mapNotNull { importPath ->
            resolveImport(
                sourceFile = sourceFile,
                importPath = importPath,
                projectRoot = projectRoot,
                workspaceRoot = workspaceRoot,
            )
        }

    private fun resolveImport(
        sourceFile: Path,
        importPath: String,
        projectRoot: Path,
        workspaceRoot: Path,
    ): Path? {
        val normalizedImport =
            importPath
                .substringBefore('?')
                .substringBefore('#')
                .removePrefix("~")
                .trim()

        if (
            normalizedImport.isEmpty() ||
            ProjectStylesheetPathResolver.isExternalImport(normalizedImport) ||
            ProjectStylesheetPathResolver.isPackageImport(normalizedImport)
        ) {
            return null
        }

        val importWithoutLeadingSlash = normalizedImport.removePrefix("/")

        return buildList {
            sourceFile.parent?.let { directory -> add(directory.resolve(normalizedImport)) }
            add(projectRoot.resolve(importWithoutLeadingSlash))
            add(workspaceRoot.resolve(importWithoutLeadingSlash))
        }.asSequence()
            .map(ProjectStylesheetPathResolver::normalize)
            .filter { path -> path.startsWith(workspaceRoot) }
            .filterNot { path -> ProjectStylesheetPathResolver.isNodeModulesPath(path, workspaceRoot) }
            .mapNotNull(ProjectStylesheetPathResolver::resolveSourceFile)
            .firstOrNull()
    }
}

internal object ProjectStylesheetImportParser {
    fun parse(content: CharSequence): List<String> =
        IMPORT_DIRECTIVE_PATTERN
            .findAll(content)
            .flatMap { directive -> importPaths(directive.groupValues[2]) }
            .filter(String::isNotEmpty)
            .toList()

    private fun importPaths(body: String): Sequence<String> {
        val quoted =
            QUOTED_IMPORT_PATTERN
                .findAll(body)
                .map { match -> match.groupValues[1] }
                .toList()

        return if (quoted.isNotEmpty()) {
            quoted.asSequence()
        } else {
            URL_IMPORT_PATTERN
                .findAll(body)
                .map { match -> match.groupValues[1].trim() }
        }
    }

    private val IMPORT_DIRECTIVE_PATTERN = Regex("""(?is)@(import|use|forward)\s+(.*?);""")
    private val QUOTED_IMPORT_PATTERN = Regex("""[\"']([^\"']+)[\"']""")
    private val URL_IMPORT_PATTERN = Regex("""(?i)url\(\s*[^\"']*?([^\s)]+)\s*\)""")
}
