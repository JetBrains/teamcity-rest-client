package org.jetbrains.teamcity.rest

import org.junit.Test

class ShutdownTest {
    @Test
    fun testEmpty() {
        publicInstance().close()
    }

    @Test
    fun testUsed() {
        publicInstance().use {
            it.rootProject()
        }
    }
}