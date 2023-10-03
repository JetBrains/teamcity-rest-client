package org.jetbrains.teamcity.rest

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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


    @Test
    fun test_equals_hashcode() {
        val buildBlocking = publicInstance().builds()
            .fromConfiguration(runTestsBuildConfiguration)
            .limitResults(3)
            .all()
            .first()

        val id = buildBlocking.testRuns().first().testOccurrenceId

        val firstBlocking = buildBlocking.testRuns().first { it.testOccurrenceId == id }
        val secondBlocking = buildBlocking.testRuns().first { it.testOccurrenceId == id }
        assertEquals(firstBlocking, secondBlocking)

        runBlocking {
            val build = publicCoroutinesInstance().build(buildBlocking.id)
            val first = build.getTestRuns().first { it.testOccurrenceId == id }
            val second = build.getTestRuns().first { it.testOccurrenceId == id }
            assertEquals(first, second)
        }
    }

    @Test
    fun prefetch_fields_test() {
        val testBuild: Build = publicInstance()
            .builds()
            .fromConfiguration(testsBuildConfiguration)
            .withBranch("branch-1571048951")
            .latest() ?: throw IllegalArgumentException("At least one build should be found")

        val testRuns = publicInstance()
            .testRuns()
            .forBuild(testBuild.id)
            .prefetchFields(fields = emptyArray())
            .limitResults(3)
            .all()
            .map { it.name } // will fetch full bean
            .toList()

        assertEqualsAnyOrder(testRuns, listOf("NumbersTest.testNumsNotEqual", "NumbersTest.testNumsAreEqual"))
    }
}
