package org.taigaui.designtokens.events

import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import com.intellij.lang.javascript.psi.ecma6.ES6Decorator
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
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

internal data class AngularHostPropertyContext(
    val property: JSProperty,
    val hostObject: JSObjectLiteralExpression,
    val nameStartOffset: Int,
    val nameEndOffset: Int,
)

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
            val safeOffset = offset.coerceIn(0, maxOf(0, file.textLength - 1))

            file
                .findElementAt(safeOffset)
                ?.let(::findContainingInReadAction)
                ?.takeIf { binding -> offset in binding.startOffset until binding.endOffset }
        }

    fun findContaining(element: PsiElement): AngularHostEventBinding? =
        ReadAction.compute<AngularHostEventBinding?, RuntimeException> {
            findContainingInReadAction(element)
        }

    fun findPropertyContext(
        file: PsiFile,
        offset: Int,
    ): AngularHostPropertyContext? =
        ReadAction.compute<AngularHostPropertyContext?, RuntimeException> {
            if (file.textLength == 0) {
                null
            } else {
                val safeOffset = offset.coerceIn(0, file.textLength - 1)

                file
                    .findElementAt(safeOffset)
                    ?.let { element ->
                        PsiTreeUtil.getParentOfType(element, JSProperty::class.java, false)
                    }?.toAngularHostPropertyContext()
            }
        }

    fun isInsideHostProperty(element: PsiElement): Boolean =
        ReadAction.compute<Boolean, RuntimeException> {
            PsiTreeUtil
                .getParentOfType(element, JSProperty::class.java, false)
                ?.toAngularHostPropertyContext() != null
        }

    fun isInsideHostProperty(
        file: PsiFile,
        offset: Int,
    ): Boolean =
        findPropertyContext(file, offset)
            ?.let { context ->
                offset in context.nameStartOffset..context.nameEndOffset
            } == true

    private fun findContainingInReadAction(element: PsiElement): AngularHostEventBinding? =
        PsiTreeUtil
            .getParentOfType(element, JSProperty::class.java, false)
            ?.toAngularHostEventBinding()

    private fun JSProperty.toAngularHostEventBinding(): AngularHostEventBinding? =
        name
            ?.takeIf { source -> source.startsWith('(') && source.endsWith(')') }
            ?.takeIf { isAngularHostProperty() }
            ?.let { source ->
                nameIdentifier?.let { identifier ->
                    val valueRange = ElementManipulators.getValueTextRange(identifier)

                    AngularHostEventBinding(
                        source = source,
                        startOffset = identifier.textRange.startOffset + valueRange.startOffset,
                        endOffset = identifier.textRange.startOffset + valueRange.endOffset,
                    )
                }
            }

    private fun JSProperty.toAngularHostPropertyContext(): AngularHostPropertyContext? =
        takeIf { property -> property.isAngularHostProperty() }
            ?.let { property ->
                val hostObject = property.context as? JSObjectLiteralExpression ?: return@let null
                val identifier = property.nameIdentifier ?: return@let null
                val valueRange = ElementManipulators.getValueTextRange(identifier)

                AngularHostPropertyContext(
                    property = property,
                    hostObject = hostObject,
                    nameStartOffset = identifier.textRange.startOffset + valueRange.startOffset,
                    nameEndOffset = identifier.textRange.startOffset + valueRange.endOffset,
                )
            }

    private fun JSProperty.isAngularHostProperty(): Boolean {
        val hostProperty = (context as? JSObjectLiteralExpression)?.context as? JSProperty
        val metadataObject = hostProperty?.context as? JSObjectLiteralExpression
        val outerObject =
            metadataObject?.let { objectLiteral ->
                PsiTreeUtil.getParentOfType(objectLiteral, JSObjectLiteralExpression::class.java, true)
            }
        val decorator =
            metadataObject?.let { objectLiteral ->
                PsiTreeUtil.getParentOfType(objectLiteral, ES6Decorator::class.java, false)
            }

        return hostProperty?.name == HOST_PROPERTY &&
            outerObject == null &&
            decorator?.decoratorName in ANGULAR_ENTITY_DECORATORS
    }

    private const val HOST_PROPERTY = "host"
    private val ANGULAR_ENTITY_DECORATORS = setOf("Component", "Directive")
}
