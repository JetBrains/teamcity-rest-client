package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

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

}
