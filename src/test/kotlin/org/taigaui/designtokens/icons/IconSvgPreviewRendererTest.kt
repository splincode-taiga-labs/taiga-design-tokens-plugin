package org.taigaui.designtokens.icons

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.swing.ImageIcon

class IconSvgPreviewRendererTest : BasePlatformTestCase() {
    fun testRendersSvgAtRequestedLogicalSizeWithoutRasterizingSource() {
        val svg =
            Files.createTempFile("taiga-ui-icon-preview", ".svg").also { path ->
                Files.writeString(
                    path,
                    """
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">
                        <rect width="16" height="16" fill="#000"/>
                    </svg>
                    """.trimIndent(),
                )
            }

        try {
            val icon =
                requireNotNull(
                    IconSvgPreviewRenderer().render(
                        source = IconSvgSource.Local(svg),
                        logicalSize = ICON_PREVIEW_LOGICAL_SIZE,
                    ),
                )

            assertEquals(ICON_PREVIEW_LOGICAL_SIZE, icon.iconWidth)
            assertEquals(ICON_PREVIEW_LOGICAL_SIZE, icon.iconHeight)
            assertFalse("SVG preview must stay vector-backed until paint", icon is ImageIcon)

            val image =
                BufferedImage(
                    ICON_PREVIEW_LOGICAL_SIZE,
                    ICON_PREVIEW_LOGICAL_SIZE,
                    BufferedImage.TYPE_INT_ARGB,
                )
            val graphics = image.createGraphics()

            try {
                icon.paintIcon(null, graphics, 0, 0)
            } finally {
                graphics.dispose()
            }

            val paintedPixels =
                (0 until ICON_PREVIEW_LOGICAL_SIZE).sumOf { y ->
                    (0 until ICON_PREVIEW_LOGICAL_SIZE).count { x ->
                        image.getRGB(x, y).ushr(24) != 0
                    }
                }

            assertTrue(
                "Expected vector preview to fill the requested viewport",
                paintedPixels >= ICON_PREVIEW_LOGICAL_SIZE * ICON_PREVIEW_LOGICAL_SIZE * 3 / 4,
            )
        } finally {
            Files.deleteIfExists(svg)
        }
    }
}
