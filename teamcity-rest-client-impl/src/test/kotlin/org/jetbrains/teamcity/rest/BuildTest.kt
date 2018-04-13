package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

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
    fun since_date() {
        val monthAgo = GregorianCalendar()
        monthAgo.add(Calendar.MONTH, -1)
        
        val builds = publicInstance().builds()
                .fromConfiguration(compileExamplesConfiguration)
                .limitResults(3)
                .sinceDate(monthAgo.time)
                .list()

        for (build in builds) {
            assert(build.fetchStartDate() >= monthAgo.time)
        }
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
    fun test_get_artifacts() {
        val build = publicInstance().builds()
                .fromConfiguration(kotlinDevCompilerAllPlugins)
                .limitResults(1)
                .list().first()

        val artifacts = build.getArtifacts("maven")
        Assert.assertTrue(artifacts.any { it.fullName == "maven/org" && it.name == "org" && it.size == null })

        val artifactsRecursive = build.getArtifacts("maven", recursive = true)
        Assert.assertTrue(artifactsRecursive.size > artifacts.size)
    }

    @Test
    fun test_get_webUrl() {
        val build = publicInstance().builds()
                .fromConfiguration(compilerAndPluginConfiguration)
                .limitResults(1)
                .list().first()

        assertEquals(
                "$publicInstanceUrl/viewLog.html?tab=buildResultsDiv&buildId=${build.id.stringId}",
                build.getWebUrl()
        )
    }
}
