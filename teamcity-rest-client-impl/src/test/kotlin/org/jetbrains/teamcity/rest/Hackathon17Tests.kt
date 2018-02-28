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
        val build = teamcity.buildQueue().triggerBuild(buildTypeId = buildTypeID)
        println(build)
    }

    //@Test
    fun test_run_build_and_get_info() {
        // trigger build -> Get triggered build from TC
        val triggeredBuild = teamcity.buildQueue().triggerBuild(buildTypeId = buildTypeID)
        val build = getBuild(triggeredBuild)
        println(build.name)
    }

//    @Test
    fun run_with_parameters() {
        val triggeredBuild = teamcity.buildQueue().triggerBuild(
                buildTypeId = buildTypeID,
                parameters = mapOf("a" to "b"))
        val build = getBuild(triggeredBuild)
        build.fetchParameters().forEach { println("${it.name}=${it.value}") }
    }

    //@Test
    fun trigger_and_cancel() {
        val triggeredBuild = teamcity.buildQueue().triggerBuild(buildTypeId = buildTypeID)
        teamcity.buildQueue().cancelBuild(triggeredBuild, comment = "hello!")
        awaitState(triggeredBuild, BuildState.FINISHED, 60000L)
    }


    //@Test
    fun test_for_build_finishing() {
        val triggeredBuild = teamcity.buildQueue().triggerBuild(buildTypeId = buildTypeID)
        val build = awaitState(triggeredBuild, BuildState.FINISHED, 60000)
        println(build)
        println(build.state)
    }

    //@Test
    fun test_trigger_from_build() {
        val triggeredBuild = teamcity.buildQueue().triggerBuild(
                buildTypeId = buildTypeID, parameters = mapOf("a" to "b"))
        val build = getBuild(triggeredBuild)

        val newTriggeredBuild = teamcity.buildQueue().triggerBuild(
                buildTypeId = buildTypeID,
                parameters = build.fetchParameters().associate { it.name to it.value }
        )

        val newBuild = awaitState(newTriggeredBuild, BuildState.FINISHED, 60000)
        println(newBuild)
        newBuild.fetchParameters().forEach { println("${it.name}=${it.value}") }
    }

    //@Test
    fun test_test_occurrences() {
        teamcity.buildResults().tests(BuildId(75.toString()))
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
