package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestRunsTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_limit() {
        val testBuild: Build = publicInstance()
                .builds()
                .fromConfiguration(testsBuildConfiguration)
                .latest() ?: throw IllegalArgumentException("At least one build should be found")

        val testRuns = publicInstance().testRuns().forBuild(testBuild.id).limitResults(3).all()
        Assert.assertEquals(testRuns.count(), 2)
        testRuns.forEach {
            callPublicPropertiesAndFetchMethods(it)
        }
    }

    @Test
    fun test_forBuild() {
        val testBuild: Build = publicInstance()
                .builds()
                .fromConfiguration(testsBuildConfiguration)
                .latest() ?: throw IllegalArgumentException("At least one build should be found")

        val testRuns = publicInstance().testRuns().forBuild(testBuild.id).limitResults(3).all()
        Assert.assertTrue(testRuns.any())
        Assert.assertTrue(testRuns.all { x -> publicInstance().build(x.buildId).buildConfigurationId == testsBuildConfiguration })
    }

    @Test
    fun test_status_locator() {
        val testBuild: Build = publicInstance()
            .builds()
            .fromConfiguration(failedTestsBuildConfiguration)
            .includeFailed()
            .latest() ?: throw IllegalArgumentException("At least one build should be found")

        val failed = publicInstance().testRuns()
            .forBuild(testBuild.id)
            .withStatus(TestStatus.FAILED)
            .all()
            .toList()
        assertEquals(2, failed.size)
        assertTrue(failed.all { it.name.contains("failing_") })

        val successful = publicInstance().testRuns()
            .forBuild(testBuild.id)
            .withStatus(TestStatus.SUCCESSFUL)
            .all()
            .toList()
        assertEquals(2, successful.size)
        assertTrue(successful.all { it.name.contains("successful_") })
    }
}
