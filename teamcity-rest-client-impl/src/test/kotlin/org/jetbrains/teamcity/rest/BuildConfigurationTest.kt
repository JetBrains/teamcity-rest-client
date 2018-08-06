package org.jetbrains.teamcity.rest

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
        val pausedBuildConfiguration = publicInstance().buildConfiguration(fSharpVSPowerToolsPausedConfiguration)
        Assert.assertTrue(pausedBuildConfiguration.paused)
    }

    @Test
    fun `active configuration has parameter of pause as false`() {
        val pausedBuildConfiguration = publicInstance().buildConfiguration(compilerAndPluginConfiguration)
        Assert.assertFalse(pausedBuildConfiguration.paused)
    }

    @Test
    fun `webUrl with default parameters`() {
        val conf = publicInstance().buildConfiguration(compilerAndPluginConfiguration)
        assertEquals(
                "$publicInstanceUrl/viewType.html?buildTypeId=${compilerAndPluginConfiguration.stringId}",
                conf.getHomeUrl())
    }

    @Test
    fun `webUrl with branch`() {
        val conf = publicInstance().buildConfiguration(compilerAndPluginConfiguration)
        assertEquals(
                "$publicInstanceUrl/viewType.html?buildTypeId=${compilerAndPluginConfiguration.stringId}&branch=%3Cdefault%3E",
                conf.getHomeUrl(branch = "<default>"))
    }
}
