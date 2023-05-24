package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

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

        println("Total tests: ${tests.size}")
        println(tests.joinToString("\n"))
    }

    @Test
    fun test_get_tests() {
        var tests = publicInstance().tests().forProject(testProject)
            .currentlyMuted(true)
            .limitResults(3)
            .all().toList()
        Assert.assertTrue( "No tests were provided for the test project", tests.isNotEmpty())
        val testId = tests.first().id
        tests = publicInstance().tests().byId(testId).all().toList()
        Assert.assertTrue( "Not exactly one test found by ID $testId, found: ${tests.size}", tests.size == 1)
        Assert.assertNotNull("test name is not set for the test with id $testId", tests.first().name)
    }
}
