package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class BuildTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_to_string() {
        val builds = publicInstance().builds()
                .fromConfiguration(compileExamplesConfiguration)
                .limitResults(3)
                .list()

        println(builds.joinToString("\n"))
    }

    @Test
    fun test_build_fetch_revisions() {
        publicInstance().builds()
                .fromConfiguration(compileExamplesConfiguration)
                .limitResults(10)
                .list()
                .forEach {
                    val revisions = it.fetchRevisions()
                    Assert.assertTrue(revisions.isNotEmpty())
                }
    }
}
