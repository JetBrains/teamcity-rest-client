package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test

class ShutdownTest {
    @Before
    fun setupLog4j() = setupLog4jDebug()

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
