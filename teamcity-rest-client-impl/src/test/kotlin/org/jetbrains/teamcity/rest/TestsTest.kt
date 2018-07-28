package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test

class TestsTest {
  @Before
  fun setupLog4j() {
    setupLog4jDebug()
  }

  @Test
  fun test_list_tests() {
    val tests = publicInstance().builds()
            .fromConfiguration(compileExamplesConfiguration)
            .limitResults(3)
            .list()
            .first().tests().toList()

    println("Total tests: ${tests.size}")
    println(tests.joinToString("\n"))
  }
}
