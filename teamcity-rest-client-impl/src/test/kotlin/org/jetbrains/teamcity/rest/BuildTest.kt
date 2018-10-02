package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.*

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
                .all()

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
                .all()

        for (build in builds) {
            assert(build.startDate!! >= monthAgo.time)
        }
    }

    @Test
    fun test_build_fetch_revisions() {
        publicInstance().builds()
                .fromConfiguration(compileExamplesConfiguration)
                .limitResults(10)
                .all()
                .forEach {
                    val revisions = it.revisions
                    Assert.assertTrue(revisions.isNotEmpty())
                }
    }

    @Test
    fun test_fetch_status() {
        val build = publicInstance().builds()
                .fromConfiguration(compileExamplesConfiguration)
                .limitResults(1)
                .all().first()

        build.statusText
    }

    @Test
    fun test_get_artifacts() {
        val build = publicInstance().builds()
                .fromConfiguration(kotlinDevCompilerAllPlugins)
                .limitResults(15)
                .all()
                .first { it.getArtifacts().isNotEmpty() }

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
                .all().first()

        assertEquals("$publicInstanceUrl/viewLog.html?buildId=${build.id.stringId}", build.getHomeUrl())
    }

    @Test
    fun test_snapshot_dependencies() {
        val build = publicInstance().builds()
                .fromConfiguration(compilerAndPluginConfiguration)
                .limitResults(1)
                .all().first()

        assertTrue(build.snapshotDependencies.isNotEmpty())
    }

    @Test
    fun test_get_tags() {
        val build = publicInstance().build(BuildId("699994"))

        assertTrue(build.tags.isNotEmpty())
        assertTrue(build.tags.contains("1.0"))
    }

    @Test
    fun pagination() {
        val iterator = publicInstance().builds()
                .fromConfiguration(KotlinDevBuildNumber)
                .all()
                .iterator()

        var i = 0
        while (i++ < 303) {
            assertTrue(iterator.hasNext())
            assertNotNull(iterator.next())
        }
    }
}
