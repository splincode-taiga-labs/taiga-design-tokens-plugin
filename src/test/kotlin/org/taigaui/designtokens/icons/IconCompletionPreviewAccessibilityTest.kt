package org.taigaui.designtokens.icons

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.components.JBLabel
import java.awt.Component
import java.awt.Container
import java.awt.image.BufferedImage
import javax.swing.ImageIcon

class IconCompletionPreviewAccessibilityTest : BasePlatformTestCase() {
    fun testPreviewIsPassiveAndExposesSelectedIconToAccessibility() {
        val panel = IconCompletionPreviewPanel()

        assertFalse(panel.isFocusable)
        panel.accessibleContext?.let { context ->
            assertEquals("Taiga UI icon completion preview", context.accessibleName)
        }

        panel.showLoading("@tui.search")
        panel.accessibleContext?.let { context ->
            assertEquals(
                "Loading icon preview for @tui.search.",
                context.accessibleDescription,
            )
        }

        panel.showIcon(
            iconName = "@tui.search",
            icon = ImageIcon(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)),
        )

        panel.accessibleContext?.let { context ->
            assertEquals("Visual preview of @tui.search.", context.accessibleDescription)
        }

        val iconLabel =
            panel
                .descendants()
                .filterIsInstance<JBLabel>()
                .single { label -> label.icon != null }

        iconLabel.accessibleContext?.let { context ->
            assertEquals("@tui.search icon preview", context.accessibleName)
            assertEquals("Visual preview of @tui.search.", context.accessibleDescription)
        }
    }
}

private fun Container.descendants(): Sequence<Component> =
    components.asSequence().flatMap { component ->
        sequenceOf(component) +
            if (component is Container) {
                component.descendants()
            } else {
                emptySequence()
            }
    }
