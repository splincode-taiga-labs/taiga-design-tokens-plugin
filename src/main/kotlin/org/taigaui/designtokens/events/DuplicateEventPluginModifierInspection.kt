package org.taigaui.designtokens.events

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class DuplicateEventPluginModifierInspection :
    LocalInspectionTool(),
    DumbAware {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                EventPluginDuplicateModifierFinder
                    .findAll(file.text)
                    .forEach { duplicate ->
                        holder
                            .problem(
                                file,
                                "Duplicate Taiga UI event modifier '${duplicate.modifier}'",
                            ).highlight(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                            .range(TextRange(duplicate.startOffset, duplicate.endOffset))
                            .register()
                    }
            }
        }
}

internal data class EventPluginDuplicateModifier(
    val modifier: String,
    val startOffset: Int,
    val endOffset: Int,
)

internal object EventPluginDuplicateModifierFinder {
    fun findAll(text: String): List<EventPluginDuplicateModifier> =
        buildList {
            EVENT_BINDING_PATTERN.findAll(text).forEach { bindingMatch ->
                val bindingGroup = bindingMatch.groups[1] ?: return@forEach

                addAll(
                    findInEventName(
                        eventName = bindingGroup.value,
                        eventNameStartOffset = bindingGroup.range.first,
                    ),
                )
            }
        }

    fun findInEventName(
        eventName: String,
        eventNameStartOffset: Int,
    ): List<EventPluginDuplicateModifier> =
        buildList {
            val segments = EVENT_SEGMENT_PATTERN.findAll(eventName).toList()
            val firstModifierIndex =
                segments.indexOfFirst { segment -> EventPluginModifier.parse(segment.value) != null }

            if (firstModifierIndex <= 0) {
                return@buildList
            }

            val seenModifiers = mutableSetOf<String>()

            segments.drop(firstModifierIndex).forEach { segment ->
                val modifier = EventPluginModifier.parse(segment.value) ?: return@forEach
                val identity = modifier.identity()

                if (!seenModifiers.add(identity)) {
                    add(
                        EventPluginDuplicateModifier(
                            modifier = segment.value,
                            startOffset = eventNameStartOffset + segment.range.first,
                            endOffset = eventNameStartOffset + segment.range.last + 1,
                        ),
                    )
                }
            }
        }

    private fun EventPluginModifier.identity(): String =
        when {
            source == "silent" -> "zoneless"
            source.startsWith("debounce~") -> "debounce"
            source.startsWith("throttle~") -> "throttle"
            else -> source
        }

    private val EVENT_BINDING_PATTERN = Regex("""\(([^()\s="'=]+)\)(?=\s*=)""")
    private val EVENT_SEGMENT_PATTERN = Regex("[^.]+")
}
