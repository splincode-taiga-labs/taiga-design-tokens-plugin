package org.taigaui.designtokens.units

import com.intellij.psi.PsiFile
import org.taigaui.designtokens.events.AngularHostBindingSupport

internal object AngularHostRemStyleBindingHintCollector {
    fun collect(file: PsiFile): List<RemInlayHint> =
        collect(file.text) { offset, binding ->
            AngularHostBindingSupport
                .findPropertyContext(file, offset)
                ?.property
                ?.name == binding
        }

    internal fun collect(content: CharSequence): List<RemInlayHint> = collect(content) { _, _ -> true }

    private fun collect(
        content: CharSequence,
        isAngularHostBinding: (Int, String) -> Boolean,
    ): List<RemInlayHint> =
        ANGULAR_HOST_REM_STYLE_BINDING
            .findAll(content)
            .mapNotNull { match ->
                val binding = match.groups[2]?.value ?: return@mapNotNull null
                val bindingOffset = match.groups[2]?.range?.first ?: return@mapNotNull null
                val value = match.groups[4]?.value?.toBigDecimalOrNull() ?: return@mapNotNull null

                if (!isAngularHostBinding(bindingOffset, binding)) {
                    return@mapNotNull null
                }

                RemInlayHint(
                    offset = match.range.last + 1,
                    text = " ${RemUnitConverter.pxPresentation(value)}",
                    tooltip = RemUnitConverter.presentation(value),
                )
            }.toList()
}

private const val NUMBER_PATTERN =
    "[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?"

private val ANGULAR_HOST_REM_STYLE_BINDING =
    Regex(
        pattern =
            """(["'])(\[style\.[A-Za-z_-][A-Za-z0-9_-]*\.rem])\1\s*:\s*(["'])\s*($NUMBER_PATTERN)\s*\3""",
    )
