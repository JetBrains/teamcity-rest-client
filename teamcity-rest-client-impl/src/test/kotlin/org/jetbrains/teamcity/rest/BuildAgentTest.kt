package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test

class BuildAgentTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_all() {
        publicInstance().buildAgents().all().forEach {
            callPublicPropertiesAndFetchMethods(it)
        }
    }
}