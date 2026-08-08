package org.taigaui.designtokens.documentation

import java.awt.Point

internal object DesignTokenReferenceHitTester {
    fun contains(
        start: Point,
        end: Point,
        lineHeight: Int,
        pointer: Point,
    ): Boolean =
        start.y == end.y &&
            pointer.x in start.x until end.x &&
            pointer.y in start.y until (start.y + lineHeight)
}
