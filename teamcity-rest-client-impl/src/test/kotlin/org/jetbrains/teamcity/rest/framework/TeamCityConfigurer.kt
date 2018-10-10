package org.jetbrains.teamcity.rest.framework

import org.jetbrains.teamcity.rest.TeamCityInstance
import org.jetbrains.teamcity.rest.TeamCityInstanceFactory
import org.slf4j.LoggerFactory

const val teamCityPort = 8111
const val teamCityUrl = "http://127.0.0.1:$teamCityPort"

object TeamCityConfigurer {
    private val log = LoggerFactory.getLogger(TeamCityConfigurer::class.java)

    // see docker-compose.yml
    private const val superUserPassword = "superUserTokenDA08FAD50DE9497DB3EB1CEBDF511ECC"

    val teamcity by lazy {
        val tc = TeamCityInstanceFactory.httpAuth(teamCityUrl, "", superUserPassword)
        authorizeAgentByName(tc, "agent")
        return@lazy tc
    }

    private fun authorizeAgentByName(tc: TeamCityInstance, name: String) {
        val agent = tc.agents().byName(name) ?: error("Agent is not found by name: $name")
        if (agent.authorized) return

        log.info("Authorizing agent $agent")
        agent.authorized = true
    }
}
