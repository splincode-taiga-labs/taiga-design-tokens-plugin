package org.taigaui.designtokens.events

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

class EventPluginKeyEventHighlightInfoFilter : HighlightInfoFilter {
    override fun accept(
        highlightInfo: HighlightInfo,
        psiFile: PsiFile?,
    ): Boolean =
        psiFile?.let { file ->
            when {
                isSuppressedAngularHostBindingDiagnostic(highlightInfo, file) -> false
                highlightInfo.severity.compareTo(HighlightSeverity.WEAK_WARNING) < 0 -> true
                !isPotentiallyConflictingInspection(highlightInfo) -> true
                else -> !isFalseExtendedKeyEventDiagnostic(highlightInfo, file)
            }
        } ?: true

    private fun isSuppressedAngularHostBindingDiagnostic(
        highlightInfo: HighlightInfo,
        psiFile: PsiFile,
    ): Boolean =
        hostBindingAtHighlight(highlightInfo, psiFile)
            ?.let { hostBinding ->
                when {
                    GlobalEventPluginBindingSupport.isValid(hostBinding.source) ->
                        isPotentiallyConflictingInspection(highlightInfo) &&
                            rangesIntersect(
                                highlightInfo.startOffset,
                                highlightInfo.endOffset,
                                hostBinding.startOffset,
                                hostBinding.endOffset,
                            )

                    else ->
                        EventPluginBinding.parse(hostBinding.source)?.let { binding ->
                            when {
                                highlightInfo.severity.compareTo(HighlightSeverity.WEAK_WARNING) < 0 ->
                                    modifierRanges(hostBinding, binding).any { range ->
                                        rangesIntersect(
                                            highlightInfo.startOffset,
                                            highlightInfo.endOffset,
                                            range.startOffset,
                                            range.endOffset,
                                        )
                                    }

                                isPotentiallyConflictingInspection(highlightInfo) ->
                                    rangesIntersect(
                                        highlightInfo.startOffset,
                                        highlightInfo.endOffset,
                                        hostBinding.startOffset,
                                        hostBinding.endOffset,
                                    )

                                else -> false
                            }
                        } ?: false
                }
            } == true

    private fun modifierRanges(
        hostBinding: AngularHostEventBinding,
        binding: EventPluginBinding,
    ): List<TextRange> {
        var startOffset = hostBinding.startOffset + 1 + binding.event.length + 1

        return binding.modifiers.map { modifier ->
            TextRange(startOffset, startOffset + modifier.source.length).also { range ->
                startOffset = range.endOffset + 1
            }
        }
    }

    private fun hostBindingAtHighlight(
        highlightInfo: HighlightInfo,
        psiFile: PsiFile,
    ): AngularHostEventBinding? =
        sequenceOf(
            highlightInfo.startOffset,
            highlightInfo.startOffset + 1,
            highlightInfo.endOffset - 1,
        ).map { offset -> offset.coerceIn(0, maxOf(0, psiFile.textLength - 1)) }
            .distinct()
            .mapNotNull { offset -> AngularHostBindingSupport.findAt(psiFile, offset) }
            .firstOrNull()

    private fun isPotentiallyConflictingInspection(highlightInfo: HighlightInfo): Boolean =
        highlightInfo.inspectionToolId?.let(CONFLICTING_INSPECTIONS::contains) != false

    private fun isFalseExtendedKeyEventDiagnostic(
        highlightInfo: HighlightInfo,
        psiFile: PsiFile,
    ): Boolean =
        EventPluginBindingAtOffsetFinder
            .find(
                file = psiFile,
                text = psiFile.text,
                offset = highlightInfo.startOffset,
            )?.takeIf { reference -> AngularExtendedKeyEventSupport.isValid(reference.binding.event) }
            ?.let { reference ->
                val eventStart = reference.startOffset + 1
                val eventEnd = eventStart + reference.binding.event.length

                rangesIntersect(
                    highlightInfo.startOffset,
                    highlightInfo.endOffset,
                    eventStart,
                    eventEnd,
                )
            } == true

    private fun rangesIntersect(
        firstStart: Int,
        firstEnd: Int,
        secondStart: Int,
        secondEnd: Int,
    ): Boolean = firstStart < secondEnd && firstEnd > secondStart

    private companion object {
        val CONFLICTING_INSPECTIONS =
            setOf(
                "AngularUndefinedBinding",
                "HtmlUnknownAttribute",
            )
    }
}

internal object AngularExtendedKeyEventSupport {
    fun isValid(event: String): Boolean {
        val parts = event.split('.')
        val validEventPrefix = parts.size >= 2 && parts.first().lowercase() in KEY_EVENTS

        return if (!validEventPrefix) {
            false
        } else {
            val modifiers = parts.drop(1).dropLast(1).map { part -> part.lowercase() }
            val keyName = parts.last()
            val hasInvalidModifiers = modifiers.any { modifier -> modifier !in KEY_EVENT_MODIFIERS }
            val hasDuplicateModifiers = modifiers.distinct().size != modifiers.size

            when {
                hasInvalidModifiers || hasDuplicateModifiers -> false
                "code" in modifiers -> isCodeKeyName(keyName)
                else -> isKeyName(keyName)
            }
        }
    }

    private fun isKeyName(keyName: String): Boolean {
        val normalized = keyName.lowercase()
        val isStandardCharacter =
            normalized.length == 1 &&
                (normalized[0].isLetterOrDigit() || normalized[0] in STANDARD_KEY_SYMBOLS)

        return normalized in SPECIAL_KEY_NAMES ||
            normalized.matches(FUNCTION_KEY) ||
            isStandardCharacter
    }

    private fun isCodeKeyName(keyName: String): Boolean {
        val normalized = keyName.lowercase()

        return normalized in CODE_KEY_NAMES ||
            normalized.matches(CODE_DIGIT_KEY) ||
            normalized.matches(CODE_NUMPAD_KEY) ||
            normalized.matches(CODE_LETTER_KEY) ||
            normalized.matches(CODE_FUNCTION_KEY)
    }

    private val KEY_EVENTS = setOf("keydown", "keyup")
    private val KEY_EVENT_MODIFIERS = setOf("alt", "control", "meta", "shift", "code")
    private val SPECIAL_KEY_NAMES =
        setOf(
            "space",
            "dot",
            "esc",
            "escape",
            "enter",
            "tab",
            "arrowdown",
            "arrowleft",
            "arrowright",
            "arrowup",
            "end",
            "home",
            "pagedown",
            "pageup",
            "backspace",
            "delete",
            "insert",
            "contextmenu",
            "help",
            "printscreen",
            "os",
        )
    private val CODE_KEY_NAMES =
        setOf(
            "space",
            "dot",
            "escape",
            "enter",
            "tab",
            "arrowdown",
            "arrowleft",
            "arrowright",
            "arrowup",
            "end",
            "home",
            "pagedown",
            "pageup",
            "backspace",
            "delete",
            "insert",
            "contextmenu",
            "help",
            "printscreen",
            "altright",
            "backquote",
            "backslash",
            "bracketleft",
            "bracketright",
            "capslock",
            "comma",
            "controlleft",
            "controlright",
            "equal",
            "intlbackslash",
            "intlro",
            "intlyen",
            "lang1",
            "lang2",
            "mediatracknext",
            "mediatrackprevious",
            "metaleft",
            "metaright",
            "minus",
            "numlock",
            "numpadadd",
            "numpadcomma",
            "numpaddecimal",
            "numpaddivide",
            "numpadmultiply",
            "numpadsubtract",
            "osleft",
            "period",
            "quote",
            "scrolllock",
            "semicolon",
            "shiftleft",
            "shiftright",
            "slash",
            "unidentified",
            "volumedown",
            "volumemute",
            "volumeup",
            "wakeup",
        )
    private val STANDARD_KEY_SYMBOLS = "`~!@#$%^&*()_+-[]{}|;:,?".toSet()
    private val FUNCTION_KEY = Regex("f(?:[1-9]|1[0-9]|20)")
    private val CODE_DIGIT_KEY = Regex("digit[0-9]")
    private val CODE_NUMPAD_KEY = Regex("numpad[0-9]")
    private val CODE_LETTER_KEY = Regex("key[a-z]")
    private val CODE_FUNCTION_KEY = Regex("f(?:[0-9]|1[0-9]|2[0-4])")
}
