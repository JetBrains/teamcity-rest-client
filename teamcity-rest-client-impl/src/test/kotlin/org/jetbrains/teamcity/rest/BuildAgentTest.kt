package org.jetbrains.teamcity.rest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildAgentTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_all() {
        val buildAgents = publicInstance().buildAgents().all().toList()
        buildAgents.forEach(::callPublicPropertiesAndFetchMethods)

        runBlocking {
            val buildAgentsAsync = publicCoroutinesInstance().buildAgents().all().toList()
            assertEqualsAnyOrder(buildAgents.map { it.id.stringId }, buildAgentsAsync.map { it.id.stringId })
        }
    }

    @Test
    fun test_equals_hashcode() {
        val buildAgentId = publicInstance().buildAgents().all().first().id

        val firstBlocking = publicInstance().buildAgent(buildAgentId)
        val secondBlocking = publicInstance().buildAgent(buildAgentId)
        assertEquals(firstBlocking, secondBlocking)

        runBlocking {
            val first = publicCoroutinesInstance().buildAgent(buildAgentId)
            val second = publicCoroutinesInstance().buildAgent(buildAgentId)
            assertEquals(first, second)
        }
    }

    @Test
    @Ignore("Requires a running agent which we don't have yet. See TW-90032")
    fun compatible_with_configuration() {
        val compatibleAgents = publicInstance().buildAgents()
            .compatibleWith(manyTestsBuildConfiguration)
            .all()
            .toList()
        assertTrue { compatibleAgents.any() }

        runBlocking {
            val compatibleAgentsAsync = publicCoroutinesInstance().buildAgents()
                .compatibleWith(manyTestsBuildConfiguration)
                .all()
                .toList()
            assertTrue(compatibleAgents.any())
            assertEqualsAnyOrder(compatibleAgents.map { it.id.stringId }, compatibleAgentsAsync.map { it.id.stringId })
        }
    }
}
