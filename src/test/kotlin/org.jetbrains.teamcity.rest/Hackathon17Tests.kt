package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test

/**
 * Created with IntelliJ IDEA.
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */

class Hackathon17Tests {

    private lateinit var teamcity: TeamCityInstance

    @Before
    fun setupLog4j() {
        setupLog4jDebug()
        teamcity = customInstanceByConnectionFile()
    }

    val project = ProjectId("TestProjectForRest")
    val buildTypeID = BuildConfigurationId("TestProjectForRest_Build")

    @Test
    fun test_run_build() {
        val build = teamcity.buildQueue().triggerBuild(TriggerRequest(buildTypeID))
        println(build)
    }

}
