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
        getBuild(triggeredBuild.id)
    }

    @Test
    fun run_with_parameters() {
        val triggeredBuild = teamcity.buildQueue().triggerBuild(TriggerRequest(buildTypeID, mapOf("a" to "b")))
        val build = getBuild(triggeredBuild.id)
        build.fetchParameters().forEach { println("${it.name}=${it.value}") }
    }

    private fun getBuild(id:Int): Build {
        // get build by build id
        var flag = false
        var buildStatus: BuildStatus? = null
        var b: Build? = null
        var attempts = 10

        while (!flag && attempts-- > 0) {
            try {
                b = teamcity.build(BuildId(id.toString()))
                buildStatus = b.status
                flag = true
            } catch (e: KotlinNullPointerException) {
                Thread.sleep(1000)
            }
        }
        b?.let { println(it) }
        buildStatus?.let { println(it) }
        return b!!
    }

}
