package org.taigaui.designtokens.events

import com.intellij.lang.javascript.psi.JSLiteralExpression
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression
import com.intellij.lang.javascript.psi.JSProperty
import org.angular2.codeInsight.attributes.DomElementSchemaRegistry

internal fun AngularHostPropertyContext.standardDomProperties(): Set<String> {
    val hostProperty = hostObject.context as? JSProperty ?: return emptySet()
    val metadataObject = hostProperty.context as? JSObjectLiteralExpression ?: return emptySet()
    val selector =
        metadataObject.properties
            .firstOrNull { property -> property.name == SELECTOR_PROPERTY }
            ?.value
            ?.let { value -> value as? JSLiteralExpression }
            ?.stringValue
            ?: return emptySet()

    return selector
        .split(',')
        .asSequence()
        .map(String::trim)
        .mapNotNull { source ->
            SELECTOR_TAG
                .find(source)
                ?.groupValues
                ?.getOrNull(1)
        }.flatMap { tagName ->
            DomElementSchemaRegistry
                .getElementProperties("", tagName)
                .asSequence()
        }.toSet()
}

private const val SELECTOR_PROPERTY = "selector"
private val SELECTOR_TAG = Regex("""^([A-Za-z][A-Za-z0-9-]*)""")
