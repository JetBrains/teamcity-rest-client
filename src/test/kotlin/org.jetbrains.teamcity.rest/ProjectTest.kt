package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class ProjectTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun `project by id`() {
        val project = publicInstance().project(kotlinProject)
        Assert.assertEquals("Kotlin", project.name)
    }

    @Test
    fun `build configuration by id`() {
        val configuration = publicInstance().buildConfiguration(compilerAndPluginConfiguration)
        Assert.assertEquals("Compiler and Plugin", configuration.name)
    }
}