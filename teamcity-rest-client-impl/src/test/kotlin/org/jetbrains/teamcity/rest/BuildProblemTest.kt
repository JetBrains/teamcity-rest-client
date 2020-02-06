package org.jetbrains.teamcity.rest

import org.junit.Assume
import org.junit.Before
import org.junit.Test

class BuildProblemTest {
    private lateinit var instance: TeamCityInstance

    @Before
    fun setup() {
        Assume.assumeTrue(haveCustomInstance())

        setupLog4jDebug()

        // requires admin credentials to teamcity instance
        instance = customInstanceByConnectionFile()
    }

    @Test
    fun fetch_problems() {
        val buildProblems = instance.build(BuildId("9")).buildProblems
        println(buildProblems.joinToString("\n"))
    }
}

