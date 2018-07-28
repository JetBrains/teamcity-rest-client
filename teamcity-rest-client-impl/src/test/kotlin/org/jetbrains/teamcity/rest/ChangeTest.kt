package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangeTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun webUrl() {
        val configuration = publicInstance().buildConfiguration(compilerAndPluginConfiguration)
        val change = publicInstance().builds()
                .fromConfiguration(configuration.id)
                .limitResults(10)
                .list()
                .firstOrNull { it.changes.isNotEmpty() }
                .let { build ->
                    assert(build != null) {
                        "Unable to find a build with changes (tried top 10) in ${configuration.getWebUrl(branch = "<default>")}"
                    }

                    build!!.changes.first()
                }
        assertEquals(
                "$publicInstanceUrl/viewModification.html?modId=${change.id.stringId}",
                change.getWebUrl()
        )
        assertEquals(
                "$publicInstanceUrl/viewModification.html?modId=${change.id.stringId}&personal=true&buildTypeId=xxx",
                change.getWebUrl(specificBuildConfigurationId = BuildConfigurationId("xxx"), includePersonalBuilds = true)
        )
    }

    @Test
    fun changeByVcsRevision() {
        val build = publicInstance().builds()
                .fromConfiguration(compilerAndPluginConfiguration)
                .limitResults(10)
                .list()
                .first { it.changes.isNotEmpty() }
        val change = build.changes.first()

        assertEquals(
                change.toString(),
                publicInstance().change(compilerAndPluginConfiguration, change.version).toString()
        )
        assertTrue(change.firstBuilds().map { it.toString() }.contains(build.toString()))
    }

    @Test
    fun buildByVcsRevision() {
        val build = publicInstance().builds()
                .fromConfiguration(compilerAndPluginConfiguration)
                .limitResults(10)
                .list()
                .first { it.changes.isNotEmpty() }
        val change = build.changes.first()

        val builds = publicInstance().builds()
                .fromConfiguration(compilerAndPluginConfiguration)
                .withVcsRevision(change.version)
                .list()
        assertTrue(builds.map { it.toString() }.contains(build.toString()))
    }
}