package org.taigaui.designtokens.events

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoFilter
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiFile

class EventPluginKeyEventHighlightInfoFilter : HighlightInfoFilter {
    override fun accept(
        highlightInfo: HighlightInfo,
        psiFile: PsiFile?,
    ): Boolean {
        if (psiFile == null || highlightInfo.severity.compareTo(HighlightSeverity.WEAK_WARNING) < 0) {
            return true
        }

        if (isFalseAngularHostBindingWarning(highlightInfo, psiFile)) {
            return false
        }

        val inspectionToolId = highlightInfo.inspectionToolId

        if (inspectionToolId != null && inspectionToolId !in CONFLICTING_INSPECTIONS) {
            return true
        }

        val reference =
            EventPluginBindingAtOffsetFinder.find(
                file = psiFile,
                text = psiFile.text,
                offset = highlightInfo.startOffset,
            ) ?: return true

        if (!AngularExtendedKeyEventSupport.isValid(reference.binding.event)) {
            return true
        }

        val eventStart = reference.startOffset + 1
        val eventEnd = eventStart + reference.binding.event.length

        return !rangesIntersect(
            highlightInfo.startOffset,
            highlightInfo.endOffset,
            eventStart,
            eventEnd,
        )
    }

    private fun isFalseAngularHostBindingWarning(
        highlightInfo: HighlightInfo,
        psiFile: PsiFile,
    ): Boolean {
        if (highlightInfo.severity.compareTo(HighlightSeverity.ERROR) >= 0) {
            return false
        }

        val hostBinding =
            AngularHostBindingSupport.findAt(
                psiFile,
                highlightInfo.startOffset,
            ) ?: return false

        if (EventPluginBinding.parse(hostBinding.source) == null) {
            return false
        }

        return rangesIntersect(
            highlightInfo.startOffset,
            highlightInfo.endOffset,
            hostBinding.startOffset,
            hostBinding.endOffset,
        )
    }

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

        if (parts.size < 2 || parts.first().lowercase() !in KEY_EVENTS) {
            return false
        }

        val modifiers = parts.drop(1).dropLast(1).map { part -> part.lowercase() }
        val keyName = parts.last()
        val hasInvalidModifiers = modifiers.any { modifier -> modifier !in KEY_EVENT_MODIFIERS }
        val hasDuplicateModifiers = modifiers.distinct().size != modifiers.size

        if (hasInvalidModifiers || hasDuplicateModifiers) {
            return false
        }

        return if ("code" in modifiers) {
            isCodeKeyName(keyName)
        } else {
            isKeyName(keyName)
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
