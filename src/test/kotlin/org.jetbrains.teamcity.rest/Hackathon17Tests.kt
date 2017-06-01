package org.jetbrains.teamcity.rest

import org.junit.Before

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
    
    val buildTypeID = BuildConfigurationId("TestProjectForRest_Build")

    //@Test
    fun test_run_build() {
        val build = teamcity.buildQueue().triggerBuild(TriggerRequest(BuildType(buildTypeID.stringId)))
        println(build)
    }

    //@Test
    fun test_run_build_and_get_info() {
        // trigger build -> Get triggered build from TC
        val triggeredBuild = teamcity.buildQueue().triggerBuild(TriggerRequest(BuildType(buildTypeID.stringId)))
        getBuild(triggeredBuild.id)
    }

    //@Test
    fun run_with_parameters() {
        val triggeredBuild = teamcity.buildQueue().triggerBuild(TriggerRequest(BuildType(buildTypeID.stringId), mapOf("a" to "b")))
        val build = getBuild(triggeredBuild.id)
        build.fetchParameters().forEach { println("${it.name}=${it.value}") }
    }


    //@Test
    fun test_for_build_finishing() {
        val triggeredBuild = teamcity.buildQueue().triggerBuild(TriggerRequest(BuildType(buildTypeID.stringId)))
        val build = awaitState(triggeredBuild.id, "finished", 60000)
        println(build)
        println(build.state)
    }

    private fun awaitState(id: Int, buildState: String, timeoutMsec: Long): Build {
        val curTime = System.currentTimeMillis()
        var b: Build? = null
        var state: String? = null
        while (buildState != state && System.currentTimeMillis() - curTime < timeoutMsec) {
            try {
                b = teamcity.build(BuildId(id.toString()))
                state = b.state
            } catch (e: KotlinNullPointerException) {
            }
            Thread.sleep(1000)
        }
        if (buildState != state) {
            throw RuntimeException("Timeout")
        }
        return b!!
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
