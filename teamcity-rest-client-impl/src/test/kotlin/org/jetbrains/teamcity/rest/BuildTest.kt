package org.jetbrains.teamcity.rest

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_to_string() {
        val builds = customInstanceByConnectionFile().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(3)
                .all()

        builds.forEach {
            it.getArtifacts()
            callPublicPropertiesAndFetchMethods(it)
        }
    }

    @Test
    fun since_date_and_until_date() {
        val monthAgo = GregorianCalendar()
        monthAgo.add(Calendar.MONTH, -1)
        val weekAgo = GregorianCalendar()
        monthAgo.add(Calendar.DAY_OF_MONTH, -7)

        val builds = publicInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(3)
                .since(monthAgo.toInstant())
                .until(weekAgo.toInstant())
                .all()

        for (build in builds) {
            assert(build.startDateTime!! >= monthAgo.toZonedDateTime() && build.startDateTime!! <= weekAgo.toZonedDateTime())
        }
    }

    @Test
    fun test_build_fetch_revisions() {
        publicInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
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
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(1)
                .all().first()

        build.statusText
    }

    @Test
    fun test_get_artifacts() {
        val build = customInstanceByConnectionFile().builds()
                .fromConfiguration(dependantBuildConfiguration)
                .limitResults(15)
                .all()
                .first { it.getArtifacts().isNotEmpty() }

        val artifacts = build.getArtifacts("internal")
        Assert.assertTrue(artifacts.any {
            it.fullName == "internal/gradle_test_report.zip" && it.name == "gradle_test_report.zip" && it.size != null
        })

        val artifactsRecursive = build.getArtifacts("internal", recursive = true)
        Assert.assertTrue(artifactsRecursive.size == artifacts.size)
    }

    @Test
    fun `should be able to find artifact recursively`() {
        val build = publicInstance().builds()
                .fromConfiguration(dependantBuildConfiguration)
                .withNumber("5")
                .includeFailed()
                .limitResults(1)
                .all()
                .first()

        val thrownException = catchThrowable { build.findArtifact("gradle_test_*.zip", "org/jetbrains", false) }

        assertThat(thrownException).isInstanceOf(TeamCityQueryException::class.java)
                .hasMessageContaining("gradle_test_*.zip not found in build 5")

        val existingArtifact = build.findArtifact("gradle_test_*.zip", "org/jetbrains", true)

        assertThat(existingArtifact.name).isEqualTo("gradle_test_report.zip")
    }

    @Test
    fun test_get_webUrl() {
        val build = publicInstance().builds()
                .fromConfiguration(changesBuildConfiguration)
                .limitResults(1)
                .all().first()

        assertEquals("$publicInstanceUrl/viewLog.html?buildId=${build.id.stringId}", build.getHomeUrl())
    }

    @Test
    fun test_snapshot_dependencies() {
        val build = publicInstance().builds()
                .fromConfiguration(dependantBuildConfiguration)
                .limitResults(1)
                .all().first()

        assertTrue(build.snapshotDependencies.isNotEmpty())
    }

    @Test
    fun test_get_tags() {
        val build = publicInstance().build(BuildId("241"))

        assertTrue(build.tags.isNotEmpty())
        assertTrue(build.tags.contains("1.0"))
    }

    @Test
    fun pagination() {
        val iterator = publicInstance().builds()
                .fromConfiguration(manyTestsBuildConfiguration)
                // Default reasonableMaxPageSize=1024, but we have only 100 builds in test data
                // Paging will never happen if we don't set page size explicitly
                .pageSize(5)
                .all()
                .iterator()

        var i = 0
        while (i++ < 100) {
            assertTrue(iterator.hasNext())
            assertNotNull(iterator.next())
        }
    }

    @Test
    fun test_parameters() {
        val build = customInstanceByConnectionFile().build(BuildId("241"))
        val parameters = build.parameters

        assertTrue(parameters.isNotEmpty())
        assertEquals(4, parameters.count())
        assertEquals("0", parameters.first { it.name == "system.FAILED_TESTS_PERCENTAGE" }.value)
    }

    @Test
    fun test_resulting_parameters() {
        val build = customInstanceByConnectionFile().build(BuildId("17"))
        val resultingParameters = build.getResultingParameters()

        assertTrue(resultingParameters.isNotEmpty())
        assertEquals(147, resultingParameters.count())
        assertEquals("1", resultingParameters.first { it.name == "build.counter" }.value)
    }
}
