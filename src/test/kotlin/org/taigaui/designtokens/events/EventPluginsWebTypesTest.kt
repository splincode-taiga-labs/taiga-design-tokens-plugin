package org.taigaui.designtokens.events

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventPluginsWebTypesTest {
    @Test
    fun `event plugins fallback web types are packaged`() {
        val resource = javaClass.classLoader.getResource("web-types/event-plugins@3.1.0.web-types.json")

        assertNotNull("event plugins fallback Web Types must be available on the test classpath", resource)

        val webTypes = resource!!.readText()

        assertTrue(webTypes.contains("\"name\": \"@taiga-ui/event-plugins\""))
        assertTrue(webTypes.contains("\"version\": \"3.1.0\""))
        assertTrue(webTypes.contains("\"ng-custom-events\""))
        assertTrue(webTypes.contains("\"#item:event\""))
        assertTrue(webTypes.contains("\"#...\""))
        assertTrue(webTypes.contains("\"#item:modifier\""))
        assertTrue(webTypes.contains("\"#item:key event modifier\""))
        assertTrue(webTypes.contains("\"#item:key name\""))
        assertTrue(webTypes.contains("\"#item:global target\""))
        assertTrue(webTypes.contains("\"name\": \"visualViewport\""))
        assertTrue(webTypes.contains("\"name\": \"document.body\""))
        assertTrue(webTypes.contains("\"regex\": \"keydown\""))
        assertTrue(webTypes.contains("\"regex\": \"keyup\""))
        assertTrue(webTypes.contains("\"regex\": \"space|dot|esc|escape|enter"))
        assertTrue(webTypes.contains("\"name\": \"stop\""))
        assertTrue(webTypes.contains("\"name\": \"prevent\""))
        assertTrue(webTypes.contains("\"longtap\""))
        assertTrue(webTypes.contains("\"resize\""))
        assertTrue(webTypes.contains("debounce~<delay>ms"))
        assertTrue(webTypes.contains("throttle~<delay>ms"))

        val taigaModifiers =
            webTypes.substring(
                webTypes.indexOf("\"items\": \"ng-event-plugins-modifiers\""),
            )
        val modifierPattern = taigaModifiers.substringBefore("\"ng-event-plugins-key-event-modifiers\"")

        assertTrue(modifierPattern.contains("\"unique\": true"))
    }

    @Test
    fun `plugin descriptor registers event plugin support`() {
        val descriptor = javaClass.classLoader.getResource("META-INF/plugin.xml")

        assertNotNull("META-INF/plugin.xml must be available on the test classpath", descriptor)

        val pluginXml = descriptor!!.readText()
        val hostCompletionStart = pluginXml.indexOf("id=\"TaigaUIEventPluginHostCompletionContributor\"")
        val hostCompletionEnd = pluginXml.indexOf("/>", hostCompletionStart)
        val hostCompletionRegistration = pluginXml.substring(hostCompletionStart, hostCompletionEnd)

        assertTrue(pluginXml.contains("<polySymbols.webTypes"))
        assertTrue(pluginXml.contains("source=\"web-types/event-plugins@3.1.0.web-types.json\""))
        assertTrue(pluginXml.contains("enableByDefault=\"false\""))
        assertTrue(pluginXml.contains("UnknownEventPluginModifierInspection"))
        assertTrue(pluginXml.contains("DuplicateEventPluginModifierInspection"))
        assertTrue(pluginXml.contains("TypeScriptEventPluginModifierInspection"))
        assertTrue(pluginXml.contains("TypeScriptHostEventPluginCompletionContributor"))
        assertTrue(pluginXml.contains("TypeScriptHostEventPluginCompletionAutoPopupHandler"))
        assertTrue(pluginXml.contains("TypeScriptHostEventPluginInspectionSuppressor"))
        assertTrue(hostCompletionRegistration.contains("language=\"TypeScript\""))
        assertTrue(hostCompletionRegistration.contains("before JSPatternBasedCompletionContributor"))
        assertTrue(hostCompletionRegistration.contains("before JSCompletionContributor"))
        assertTrue(pluginXml.contains("shortName=\"UnknownTaigaUIEventModifier\""))
        assertTrue(pluginXml.contains("shortName=\"DuplicateTaigaUIEventModifier\""))
        assertTrue(pluginXml.contains("shortName=\"InvalidTaigaUIEventModifierInHostBinding\""))
        assertTrue(pluginXml.contains("language=\"HtmlCompatible\""))
        assertTrue(pluginXml.contains("language=\"TypeScript\""))
        assertTrue(pluginXml.contains("level=\"ERROR\""))
        assertTrue(pluginXml.contains("<daemon.highlightInfoFilter"))
        assertTrue(pluginXml.contains("EventPluginKeyEventHighlightInfoFilter"))
        assertTrue(pluginXml.contains("<editorFactoryMouseListener"))
        assertTrue(pluginXml.contains("<editorFactoryMouseMotionListener"))
        assertTrue(pluginXml.contains("EventPluginsHoverPopupListener"))
    }
}
