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
                .fromConfiguration(compileExamplesConfiguration)
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
                .fromConfiguration(compileExamplesConfiguration)
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
        val build = customInstanceByConnectionFile().builds()
                .fromConfiguration(kotlinDevCompilerAllPlugins)
                .limitResults(15)
                .all()
                .first { it.getArtifacts().isNotEmpty() }

        val artifacts = build.getArtifacts("internal")
        Assert.assertTrue(artifacts.any {
            it.fullName == "internal/kotlin-ide-common.jar" && it.name == "kotlin-ide-common.jar" && it.size != null
        })

        val artifactsRecursive = build.getArtifacts("internal", recursive = true)
        Assert.assertTrue(artifactsRecursive.size == artifacts.size)
    }

    @Test
    fun `should be able to find artifact recursively`() {
        val build = publicInstance().builds()
                .fromConfiguration(compilerAndPluginConfiguration)
                .withNumber("1.1.50-dev-1499")
                .includeFailed()
                .limitResults(1)
                .all()
                .first()

        val thrownException = catchThrowable { build.findArtifact("kotlin-test-*99.jar", "maven/org/jetbrains/kotlin/kotlin-test", false) }

        assertThat(thrownException).isInstanceOf(TeamCityQueryException::class.java)
                                   .hasMessageContaining("Artifact kotlin-test-*99.jar not found in build 1.1.50-dev-1499.")

        val existingArtifact = build.findArtifact("kotlin-test-*99.jar", "maven/org/jetbrains/kotlin/kotlin-test", true)

        assertThat(existingArtifact.name).isEqualTo("kotlin-test-1.1.50-dev-1499.jar")
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

    @Test
    fun test_parameters() {
        val build = customInstanceByConnectionFile().build(BuildId("699994"))
        val parameters = build.parameters

        assertTrue(parameters.isNotEmpty())
        assertEquals(53, parameters.count())
        assertEquals("1.0.0", parameters.first { it.name == "kotlin.build.number" }.value)
    }

    @Test
    fun test_resulting_parameters() {
        val build = customInstanceByConnectionFile().build(BuildId("699994"))
        val resultingParameters = build.getResultingParameters()

        assertTrue(resultingParameters.isNotEmpty())
        assertEquals(1033, resultingParameters.count())
        assertEquals("29", resultingParameters.first { it.name == "build.counter" }.value)
    }
}
