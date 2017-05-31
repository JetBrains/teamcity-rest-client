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

    @Test
    fun test_run_build_and_get_info() {
        // trigger build -> Get triggered build from TC
        val triggeredBuild = teamcity.buildQueue().triggerBuild(TriggerRequest(buildTypeID))
        // get build by build id
        var flag = false
        var buildNumber: String? = null
        var b: Build? = null
        var attempts = 10

        while (!flag && attempts-- > 0) {
            try {
                b = teamcity.build(BuildId(triggeredBuild.id.toString()))
                buildNumber = b.buildNumber
                flag = true
            } catch (e: KotlinNullPointerException) {
                Thread.sleep(1000)
            }
        }
        b?.let { println(it) }
        buildNumber?.let { println(it) }
    }

}
