package org.jetbrains.teamcity.rest

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class BuildAgentPoolTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_all() {
        val pools = publicInstance().buildAgentPools().all().toList()
        pools.forEach(::callPublicPropertiesAndFetchMethods)

        val asyncPools = runBlocking { publicCoroutinesInstance().buildAgentPools().all().toList() }
        assertEqualsAnyOrder(pools.map { it.id.stringId }, asyncPools.map { it.id.stringId })
    }

    @Test
    fun test_equals_hashcode() {
        val id = publicInstance().buildAgentPools().all().first().id

        val firstBlocking = publicInstance().buildAgentPools().all().first { it.id == id }
        val secondBlocking = publicInstance().buildAgentPools().all().first { it.id == id }
        assertEquals(firstBlocking, secondBlocking)

        runBlocking {
            val first = publicCoroutinesInstance().buildAgentPools().all().first { it.id == id }
            val second = publicCoroutinesInstance().buildAgentPools().all().first { it.id == id }
            assertEquals(first, second)
        }
    }
}
