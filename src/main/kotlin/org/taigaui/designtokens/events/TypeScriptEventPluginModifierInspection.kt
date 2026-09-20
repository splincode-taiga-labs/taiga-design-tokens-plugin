package org.taigaui.designtokens.events

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class TypeScriptEventPluginModifierInspection :
    LocalInspectionTool(),
    DumbAware {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                AngularHostBindingSupport.findAll(file).forEach { binding ->
                    EventPluginUnknownModifierFinder
                        .findInEventName(
                            eventName = binding.eventName,
                            eventNameStartOffset = binding.eventNameStartOffset,
                        ).forEach { unknown ->
                            holder
                                .problem(
                                    file,
                                    "Unknown Taiga UI event modifier '${unknown.modifier}'",
                                ).highlight(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                                .range(TextRange(unknown.startOffset, unknown.endOffset))
                                .register()
                        }

                    EventPluginDuplicateModifierFinder
                        .findInEventName(
                            eventName = binding.eventName,
                            eventNameStartOffset = binding.eventNameStartOffset,
                        ).forEach { duplicate ->
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
}
