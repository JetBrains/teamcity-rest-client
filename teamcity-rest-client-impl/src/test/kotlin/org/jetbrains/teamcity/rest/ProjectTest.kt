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
        val project = publicInstance().project(reportProject)
        assertEquals(reportProject, project.id)
        assertEquals("Project For Reports", project.name)
        assertFalse(project.archived)
    }

    @Test
    fun `build configuration by id`() {
        val configuration = publicInstance().buildConfiguration(runTestsBuildConfiguration)
        assertEquals(runTestsBuildConfiguration, configuration.id)
        assertEquals("Run Tests", configuration.name)
        assertEquals(testProject, configuration.projectId)
    }

    @Test
    fun `webUrl with default parameters`() {
        val proj = publicInstance().project(reportProject)
        kotlin.test.assertEquals(
                "$publicInstanceUrl/project.html?projectId=${reportProject.stringId}",
                proj.getHomeUrl())
    }

    @Test
    fun `webUrl with branch`() {
        val proj = publicInstance().project(reportProject)
        kotlin.test.assertEquals(
                "$publicInstanceUrl/project.html?projectId=${reportProject.stringId}&branch=%3Cdefault%3E",
                proj.getHomeUrl(branch = "<default>"))
    }
}