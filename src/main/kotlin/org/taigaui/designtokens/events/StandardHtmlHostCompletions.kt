package org.taigaui.designtokens.events

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.polySymbols.html.HtmlDescriptorUtils

internal data class StandardHtmlHostCompletions(
    val attributes: List<String>,
    val events: List<String>,
)

internal fun Project.standardHtmlHostCompletions(): StandardHtmlHostCompletions =
    getUserData(STANDARD_HTML_HOST_COMPLETIONS_KEY)
        ?: buildStandardHtmlHostCompletions().also { completions ->
            putUserData(STANDARD_HTML_HOST_COMPLETIONS_KEY, completions)
        }

private fun Project.buildStandardHtmlHostCompletions(): StandardHtmlHostCompletions {
    val attributes =
        HtmlDescriptorUtils
            .getHtmlNSDescriptor(this)
            ?.getAllElementsDescriptors(null)
            ?.asSequence()
            ?.flatMap { descriptor ->
                descriptor
                    .getAttributesDescriptors(null)
                    .asSequence()
            }?.map { descriptor -> descriptor.name }
            ?.filterNot { name -> ':' in name }
            ?.distinct()
            ?.sorted()
            ?.toList()
            .orEmpty()
    val events =
        attributes
            .asSequence()
            .filter { name -> name.length > 2 && name.startsWith("on", ignoreCase = true) }
            .map { name -> name.drop(2) }
            .distinct()
            .sorted()
            .toList()

    return StandardHtmlHostCompletions(
        attributes = attributes,
        events = events,
    )
}

private val STANDARD_HTML_HOST_COMPLETIONS_KEY =
    Key.create<StandardHtmlHostCompletions>("taiga.ui.standard.html.host.completions")
