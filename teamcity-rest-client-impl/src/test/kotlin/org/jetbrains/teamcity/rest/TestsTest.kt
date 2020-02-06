package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test

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
}
