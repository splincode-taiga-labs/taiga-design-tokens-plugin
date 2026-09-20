package org.taigaui.designtokens.events

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import kotlin.math.min

class UnknownEventPluginModifierInspection :
    LocalInspectionTool(),
    DumbAware {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                EventPluginUnknownModifierFinder
                    .findAll(file.text)
                    .forEach { unknown ->
                        holder
                            .problem(
                                file,
                                "Unknown Taiga UI event modifier '${unknown.modifier}'",
                            ).highlight(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                            .range(TextRange(unknown.startOffset, unknown.endOffset))
                            .register()
                    }
            }
        }
}

internal data class EventPluginUnknownModifier(
    val modifier: String,
    val startOffset: Int,
    val endOffset: Int,
)

internal object EventPluginUnknownModifierFinder {
    fun findAll(text: String): List<EventPluginUnknownModifier> =
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
    ): List<EventPluginUnknownModifier> =
        buildList {
            val segments = EVENT_SEGMENT_PATTERN.findAll(eventName).toList()
            val firstModifierIndex =
                segments.indexOfFirst { segment -> EventPluginModifier.parse(segment.value) != null }

            segments.forEachIndexed { index, segment ->
                val source = segment.value

                if (EventPluginModifier.parse(source) != null || index == 0) {
                    return@forEachIndexed
                }

                val followsTaigaModifier = firstModifierIndex > 0 && index > firstModifierIndex
                val looksLikeTaigaModifier = source.looksLikeTaigaModifier()

                if (followsTaigaModifier || looksLikeTaigaModifier) {
                    add(
                        EventPluginUnknownModifier(
                            modifier = source,
                            startOffset = eventNameStartOffset + segment.range.first,
                            endOffset = eventNameStartOffset + segment.range.last + 1,
                        ),
                    )
                }
            }
        }

    private fun String.looksLikeTaigaModifier(): Boolean =
        startsWith("debounce~") ||
            startsWith("throttle~") ||
            KNOWN_MODIFIERS.any { modifier -> levenshteinDistance(modifier, this) <= MAX_TYPO_DISTANCE }

    private fun levenshteinDistance(
        left: String,
        right: String,
    ): Int {
        var previous = IntArray(right.length + 1) { index -> index }

        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1

            right.forEachIndexed { rightIndex, rightChar ->
                val substitutionCost = if (leftChar == rightChar) 0 else 1
                current[rightIndex + 1] =
                    min(
                        min(current[rightIndex] + 1, previous[rightIndex + 1] + 1),
                        previous[rightIndex] + substitutionCost,
                    )
            }

            previous = current
        }

        return previous[right.length]
    }

    private val EVENT_BINDING_PATTERN = Regex("""\(([^()\s="'=]+)\)(?=\s*=)""")
    private val EVENT_SEGMENT_PATTERN = Regex("[^.]+")
    private val KNOWN_MODIFIERS =
        listOf(
            "capture",
            "once",
            "passive",
            "prevent",
            "self",
            "silent",
            "zoneless",
            "stop",
        )

    private const val MAX_TYPO_DISTANCE = 2
}
