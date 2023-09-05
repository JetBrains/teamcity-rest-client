package org.jetbrains.teamcity.rest

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class TestsTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_deprecated_list_tests() {
        val tests = publicInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(3)
                .all()
                .first().tests().toList()

        println("Total tests: ${tests.size}")
        println(tests.joinToString("\n"))
    }

    @Test
    fun test_runs_tests() {
        val tests = publicInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(3)
                .all()
                .first().testRuns().toList()

        println("Total tests sync: ${tests.size}")
        println(tests.joinToString("\n"))

        val testsAsync = runBlocking {
             publicCoroutinesInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(3)
                .all()
                .first().getTestRuns().toList()
        }
        println("Total tests async: ${testsAsync.size}")
        println(testsAsync.joinToString("\n"))

        assertEquals(tests.size, testsAsync.size)
        runBlocking {
            assertEqualsAnyOrder(tests.map { it.testId.stringId }, testsAsync.map { it.getTestId().stringId })
        }
    }

    @Test
    fun test_get_tests() {
        val tests = publicInstance().tests().forProject(testProject)
            .currentlyMuted(true)
            .limitResults(3)
            .all().toList()
        Assert.assertTrue( "No tests were provided for the test project", tests.isNotEmpty())
        val testId = tests.first().id
        val testsById = publicInstance().tests().byId(testId).all().toList()
        Assert.assertTrue( "Not exactly one test found by ID $testId, found: ${testsById.size}", testsById.size == 1)
        Assert.assertNotNull("test name is not set for the test with id $testId", testsById.first().name)
        
        runBlocking {
            val testsAsync = publicCoroutinesInstance().tests().forProject(testProject)
                .currentlyMuted(true)
                .limitResults(3)
                .all().toList()
            Assert.assertTrue("No tests were provided for the test project", testsAsync.isNotEmpty())
            val testsByIdAsync = publicCoroutinesInstance().tests().byId(testId).all().toList()
            Assert.assertTrue("Not exactly one test found by ID $testId, found: ${testsByIdAsync.size}", testsByIdAsync.size == 1)
            Assert.assertNotNull("test name is not set for the test with id $testId", testsByIdAsync.first().getName())
            assertEqualsAnyOrder(testsById.map { it.id.stringId }, testsByIdAsync.map { it.id.stringId })
        }
    }
}
