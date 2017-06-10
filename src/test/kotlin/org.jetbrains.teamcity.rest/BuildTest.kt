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

    @Test
    fun test_fetch_status() {
        val build = publicInstance().builds()
                .fromConfiguration(compileExamplesConfiguration)
                .limitResults(1)
                .list().first()

        build.fetchStatusText()
    }

    @Test
    fun test_failed_to_start_build() {
        fun getBuildWithFailedToStart(failedToStart: Boolean?): Build? {
            val locator = publicInstance().builds()
                    .fromConfiguration(compileExamplesConfiguration)
                    .limitResults(1)

            when (failedToStart) {
                true -> locator.failedToStartOnly()
                false -> locator.excludeFailedToStart()
                null -> locator.includeFailedToStart()
            }

            return locator.list().firstOrNull()
        }

        val failedToStartBuild = getBuildWithFailedToStart(true)
        println("Failed to start: $failedToStartBuild")
        if (failedToStartBuild != null) {
            Assert.assertEquals("Bad failedToStart value", true, failedToStartBuild.failedToStart)
        }

        val normaBuild = getBuildWithFailedToStart(false)
        println("Normal build: $normaBuild")
        if (normaBuild != null) {
            Assert.assertEquals("Bad failedToStart value", false, normaBuild.failedToStart)
        }

        val anyBuild = getBuildWithFailedToStart(null)
        println("First build: $anyBuild")
        if (failedToStartBuild != null || normaBuild != null) {
            Assert.assertNotNull(anyBuild)

            val failedToStartId = failedToStartBuild?.id?.stringId?.toInt() ?: 0
            val normalId = normaBuild?.id?.stringId?.toInt() ?: 0
            val maxId = Math.max(failedToStartId, normalId)

            val anyId = anyBuild!!.id.stringId.toInt()

            Assert.assertEquals("Wrong build id for query with any failedToStart value", maxId, anyId)
        }
    }
}
