package org.jetbrains.teamcity.rest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(kotlinProject, project.id)
        assertEquals("Kotlin", project.name)
        assertFalse(project.archived)
    }

    @Test
    fun `build configuration by id`() {
        val configuration = publicInstance().buildConfiguration(compileExamplesConfiguration)
        assertEquals(compileExamplesConfiguration, configuration.id)
        assertEquals("Compile Kotlin examples", configuration.name)
        assertEquals(kotlinProject, configuration.projectId)
    }
}