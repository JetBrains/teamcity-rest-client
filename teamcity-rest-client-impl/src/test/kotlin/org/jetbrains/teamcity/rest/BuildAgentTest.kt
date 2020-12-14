package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

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

    @Test
    fun compatible_with_configuration() {
        val compatibleAgents = publicInstance().buildAgents()
            .compatibleWith(manyTestsBuildConfiguration)
            .all()

        assertTrue { compatibleAgents.any() }
    }
}