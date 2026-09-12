package org.taigaui.designtokens.events

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecma6.ES6Decorator
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

internal data class AngularHostEventBinding(
    val source: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    val eventName: String
        get() = source.substring(1, source.lastIndex)

    val eventNameStartOffset: Int
        get() = startOffset + 1
}

internal object AngularHostBindingSupport {
    fun findAll(file: PsiFile): List<AngularHostEventBinding> =
        PsiTreeUtil
            .findChildrenOfType(file, JSProperty::class.java)
            .mapNotNull { property -> property.toAngularHostEventBinding() }

    fun findAt(
        file: PsiFile,
        offset: Int,
    ): AngularHostEventBinding? =
        ReadAction.compute<AngularHostEventBinding?, RuntimeException> {
            findAtInReadAction(file, offset)
        }

    private fun findAtInReadAction(
        file: PsiFile,
        offset: Int,
    ): AngularHostEventBinding? {
        val safeOffset = offset.coerceIn(0, maxOf(0, file.textLength - 1))
        val element = file.findElementAt(safeOffset) ?: return null
        val property = PsiTreeUtil.getParentOfType(element, JSProperty::class.java, false) ?: return null
        val binding = property.toAngularHostEventBinding() ?: return null

        return binding.takeIf { offset in binding.startOffset until binding.endOffset }
    }

    private fun JSProperty.toAngularHostEventBinding(): AngularHostEventBinding? {
        val source = name?.takeIf { it.startsWith('(') && it.endsWith(')') } ?: return null
        val hostObject = context as? JSObjectLiteralExpression ?: return null
        val hostProperty = hostObject.context as? JSProperty ?: return null

        if (hostProperty.name != HOST_PROPERTY) {
            return null
        }

        val metadataObject = hostProperty.context as? JSObjectLiteralExpression ?: return null
        val outerObject = PsiTreeUtil.getParentOfType(metadataObject, JSObjectLiteralExpression::class.java, true)

        if (outerObject != null) {
            return null
        }

        val decorator = PsiTreeUtil.getParentOfType(metadataObject, ES6Decorator::class.java, false) ?: return null

        if (decorator.decoratorName !in ANGULAR_ENTITY_DECORATORS) {
            return null
        }

        val identifier = nameIdentifier ?: return null
        val valueRange = ElementManipulators.getValueTextRange(identifier)
        val startOffset = identifier.textRange.startOffset + valueRange.startOffset
        val endOffset = identifier.textRange.startOffset + valueRange.endOffset

        return AngularHostEventBinding(
            source = source,
            startOffset = startOffset,
            endOffset = endOffset,
        )
    }

    private const val HOST_PROPERTY = "host"
    private val ANGULAR_ENTITY_DECORATORS = setOf("Component", "Directive")
}
