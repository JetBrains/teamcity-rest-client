package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test

class BuildAgentPoolTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_all() {
        publicInstance().buildAgentPools().all().forEach {
            callPublicPropertiesAndFetchMethods(it)
        }
    }
}
