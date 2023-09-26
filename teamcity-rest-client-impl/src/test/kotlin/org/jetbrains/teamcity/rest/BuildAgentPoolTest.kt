package org.jetbrains.teamcity.rest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

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
}
