package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test

class QueueTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_all() {
        publicInstance().buildQueue().queuedBuilds().forEach {
            it.toString() // toString prints all properties thus evaluating them
            println(it)
        }
    }

    @Test
    fun test_project() {
        publicInstance().buildQueue().queuedBuilds(ProjectId("Test Project")).forEach {
            it.toString() // toString prints all properties thus evaluating them
            println(it)
        }
    }
}