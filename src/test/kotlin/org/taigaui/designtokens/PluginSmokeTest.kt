package org.taigaui.designtokens

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PluginSmokeTest : BasePlatformTestCase() {
    fun testPluginLoads() {
        assertNotNull(project)
    }
}
