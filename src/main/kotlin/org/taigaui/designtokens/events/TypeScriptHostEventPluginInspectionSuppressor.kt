package org.taigaui.designtokens.events

import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.psi.PsiElement

class TypeScriptHostEventPluginInspectionSuppressor : InspectionSuppressor {
    override fun isSuppressedFor(
        element: PsiElement,
        toolId: String,
    ): Boolean =
        toolId == SPELLCHECKING_INSPECTION &&
            AngularHostBindingSupport
                .findContaining(element)
                ?.let { binding -> EventPluginBinding.parse(binding.source) != null } == true

    override fun getSuppressActions(
        element: PsiElement?,
        toolId: String,
    ): Array<SuppressQuickFix> = emptyArray()

    private companion object {
        const val SPELLCHECKING_INSPECTION = "SpellCheckingInspection"
    }
}
