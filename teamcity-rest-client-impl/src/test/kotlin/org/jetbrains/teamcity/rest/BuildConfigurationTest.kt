package org.jetbrains.teamcity.rest

import kotlinx.coroutines.runBlocking
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class BuildConfigurationTest {

    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun `paused configuration has paused parameter`() {
        val pausedBuildConfiguration = publicInstance().buildConfiguration(pausedBuildConfiguration)
        Assert.assertTrue(pausedBuildConfiguration.paused)
    }

    @Test
    fun `active configuration has parameter of pause as false`() {
        val pausedBuildConfiguration = publicInstance().buildConfiguration(changesBuildConfiguration)
        Assert.assertFalse(pausedBuildConfiguration.paused)
    }

    @Test
    fun `webUrl with default parameters`() {
        val conf = publicInstance().buildConfiguration(changesBuildConfiguration)
        assertEquals(
                "$publicInstanceUrl/buildConfiguration/${changesBuildConfiguration.stringId}",
                conf.getHomeUrl())
    }

    @Test
    fun `webUrl with branch`() {
        val conf = publicInstance().buildConfiguration(changesBuildConfiguration)
        assertEquals(
                "$publicInstanceUrl/buildConfiguration/${changesBuildConfiguration.stringId}?branch=%3Cdefault%3E",
                conf.getHomeUrl(branch = "<default>"))
    }

    @Test
    fun `project parameter test`() {
        val testParamName = "rest-client-test-build-configuration-parameter"
        val testParamValue = "rest-client-test-build-configuration-value"
        val buildConfiguration = publicInstance().buildConfiguration(changesBuildConfiguration)

        buildConfiguration.removeParameter(testParamName)
        val paramsBefore = buildConfiguration.getParameters().associate { it.name to it.value }
        Assert.assertFalse(paramsBefore.containsKey(testParamName))

        buildConfiguration.setParameter(testParamName, testParamValue)
        val paramsAfter = buildConfiguration.getParameters().associate { it.name to it.value }
        Assert.assertEquals(testParamValue, paramsAfter[testParamName])

        buildConfiguration.removeParameter(testParamName)
        val paramsAfterRemoval = buildConfiguration.getParameters().associate { it.name to it.value }
        Assert.assertFalse(paramsAfterRemoval.containsKey(testParamName))
    }

    @Test
    fun test_equals_hashcode() {
        val id = publicInstance().builds().all().first().buildConfigurationId

        val firstBlocking = publicInstance().buildConfiguration(id)
        val secondBlocking = publicInstance().buildConfiguration(id)
        assertEquals(firstBlocking, secondBlocking)

        runBlocking {
            val first = publicCoroutinesInstance().buildConfiguration(id)
            val second = publicCoroutinesInstance().buildConfiguration(id)
            assertEquals(first, second)
        }
    }
}
