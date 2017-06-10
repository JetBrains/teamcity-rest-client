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
    fun test_since_build() {
        val builds = publicInstance().builds()
                .fromConfiguration(compileExamplesConfiguration)
                .withAnyStatus()
                .limitResults(2)
                .list()

        Assert.assertEquals("Two builds expected", 2, builds.size)

        val newer = builds[0]
        val olderBuild = builds[1]

        run {
            val sinceLocatorById = publicInstance().builds().withAnyStatus().withId(olderBuild.id)
            val newerWithSinceById = publicInstance().builds()
                    .fromConfiguration(compileExamplesConfiguration)
                    .withAnyStatus()
                    .withSinceBuild(sinceLocatorById)
                    .list().last()
            Assert.assertEquals("Should be same build on fetching with since locator",
                    newer.id, newerWithSinceById.id)
        }

        run {
            // NOTE: Configuration is mandatory in since locator
            val sinceLocatorByNumber = publicInstance().builds()
                    .fromConfiguration(compileExamplesConfiguration).withAnyStatus().withNumber(olderBuild.buildNumber)
            val newerWithSinceByNumber = publicInstance().builds()
                    .fromConfiguration(compileExamplesConfiguration)
                    .withAnyStatus()
                    .withSinceBuild(sinceLocatorByNumber)
                    .list().last()
            Assert.assertEquals("Should be same build on fetching with since locator",
                    newer.id, newerWithSinceByNumber.id)
        }
    }
}
