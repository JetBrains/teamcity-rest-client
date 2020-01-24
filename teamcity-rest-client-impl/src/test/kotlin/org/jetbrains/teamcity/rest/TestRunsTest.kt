package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

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
}
