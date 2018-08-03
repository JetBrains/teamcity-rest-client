@file:Suppress("unused")

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
        val build = teamcity.buildConfiguration(buildTypeID).runBuild()
        println(build)
    }

    //@Test
    fun test_run_build_and_get_info() {
        // trigger build -> Get triggered build from TC
        val triggeredBuild = teamcity.buildConfiguration(buildTypeID).runBuild()
        println(triggeredBuild.name)
    }

//    @Test
    fun run_with_parameters() {
        val triggeredBuild = teamcity.buildConfiguration(buildTypeID).runBuild(
                parameters = mapOf("a" to "b"))
        triggeredBuild.parameters.forEach { println("${it.name}=${it.value}") }
    }

    //@Test
    fun trigger_and_cancel() {
        val triggeredBuild = teamcity.buildConfiguration(buildTypeID).runBuild()
        triggeredBuild.cancel(comment = "hello!")
        awaitState(triggeredBuild.id, BuildState.FINISHED, 60000L)
    }


    //@Test
    fun test_for_build_finishing() {
        val triggeredBuild = teamcity.buildConfiguration(buildTypeID).runBuild()
        val build = awaitState(triggeredBuild.id, BuildState.FINISHED, 60000)
        println(build)
        println(build.state)
    }

    //@Test
    fun test_trigger_from_build() {
        val triggeredBuild = teamcity.buildConfiguration(buildTypeID).runBuild(
                parameters = mapOf("a" to "b"))
        val build = getBuild(triggeredBuild.id)

        val newTriggeredBuild = teamcity.buildConfiguration(buildTypeID).runBuild(
                parameters = build.parameters.associate { it.name to it.value }
        )

        val newBuild = awaitState(newTriggeredBuild.id, BuildState.FINISHED, 60000)
        println(newBuild)
        newBuild.parameters.forEach { println("${it.name}=${it.value}") }
    }

    private fun awaitState(id: BuildId, buildState: BuildState, timeoutMsec: Long): Build {
        val curTime = System.currentTimeMillis()
        var b: Build? = null
        var state: BuildState? = null
        while (buildState != state && System.currentTimeMillis() - curTime < timeoutMsec) {
            try {
                b = teamcity.build(id)
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

    private fun getBuild(id: BuildId): Build {
        // get build by build id
        var flag = false
        var buildStatus: BuildStatus? = null
        var b: Build? = null
        var attempts = 10

        while (!flag && attempts-- > 0) {
            try {
                b = teamcity.build(id)
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
