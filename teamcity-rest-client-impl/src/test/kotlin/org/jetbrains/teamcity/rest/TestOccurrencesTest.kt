package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class TestOccurrencesTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_limit() {
        val testBuild: Build = publicInstance()
                .builds()
                .fromConfiguration(TeamCityRestApiClientsKotlinClientBuild)
                .latest() ?: throw IllegalArgumentException("At least one build should be found")

        val occurrences = publicInstance().testOccurrences().forBuild(testBuild.id).limitResults(3).all()
        Assert.assertEquals(occurrences.count(), 3)
        occurrences.forEach {
            callPublicPropertiesAndFetchMethods(it)
        }
    }

    @Test
    fun test_forBuild() {
        val testBuild: Build = publicInstance()
                .builds()
                .fromConfiguration(TeamCityRestApiClientsKotlinClientBuild)
                .latest() ?: throw IllegalArgumentException("At least one build should be found")

        val occurrences = publicInstance().testOccurrences().forBuild(testBuild.id).limitResults(3).all()
        Assert.assertTrue(occurrences.any())
        Assert.assertTrue(occurrences.all { x -> publicInstance().build(x.buildId).buildConfigurationId == TeamCityRestApiClientsKotlinClientBuild })
    }
}
