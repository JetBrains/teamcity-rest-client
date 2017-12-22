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
        assert(publicInstance().buildQueueSize() >=0)
    }
    
    @Test
    fun test_kotlin_dev() {
        assert(publicInstance().buildQueueSize(ProjectId("Kotlin_dev")) >=0)
    }
}