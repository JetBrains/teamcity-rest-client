package org.jetbrains.teamcity.rest

import kotlinx.coroutines.runBlocking
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

    @Test
    fun `project parameter test`() {
        val testParamName = "rest-client-test-project-parameter"
        val testParamValue = "rest-client-test-project-parameter-value"

        publicInstance().project(reportProject).removeParameter(testParamName)
        val project = publicInstance().project(reportProject)

        val paramsBefore = project.parameters.associate { it.name to it.value }
        assertFalse(paramsBefore.containsKey(testParamName))

        project.setParameter(testParamName, testParamValue)
        val paramsAfter = publicInstance().project(reportProject).parameters.associate { it.name to it.value }
        assertEquals(testParamValue, paramsAfter[testParamName])

        project.removeParameter(testParamName)
        val paramsAfterRemoval = publicInstance().project(reportProject).parameters.associate { it.name to it.value }
        assertFalse(paramsAfterRemoval.containsKey(testParamName))
    }

    @Test
    fun test_equals_hashcode() {
        val id = publicInstance().rootProject().id

        val firstBlocking = publicInstance().project(id)
        val secondBlocking = publicInstance().project(id)
        kotlin.test.assertEquals(firstBlocking, secondBlocking)

        runBlocking {
            val first = publicCoroutinesInstance().project(id)
            val second = publicCoroutinesInstance().project(id)
            kotlin.test.assertEquals(first, second)
        }
    }
}