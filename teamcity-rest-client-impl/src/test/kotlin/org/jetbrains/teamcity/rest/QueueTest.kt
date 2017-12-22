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
        publicInstance().queuedBuilds().forEach { println(it) }
    }

    @Test
    fun test_kotlin_dev() {
        publicInstance().queuedBuilds(ProjectId("Kotlin_dev")).forEach { println(it) }
    }
}