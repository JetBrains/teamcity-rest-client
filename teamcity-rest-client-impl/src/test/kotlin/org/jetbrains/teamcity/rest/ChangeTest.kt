package org.jetbrains.teamcity.rest

import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangeTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Ignore("There are no recent changes, because active development has been moved to buildserver")
    @Test
    fun webUrl() {
        val configuration = publicInstance().buildConfiguration(changesBuildConfiguration)
        val change = publicInstance().builds()
                .fromConfiguration(configuration.id)
                .limitResults(10)
                .all()
                .firstOrNull { it.changes.isNotEmpty() }
                .let { build ->
                    assert(build != null) {
                        "Unable to find a build with changes (tried top 10) in ${configuration.getHomeUrl(branch = "<default>")}"
                    }

                    build!!.changes.first()
                }
        assertEquals(
                "$publicInstanceUrl/viewModification.html?modId=${change.id.stringId}",
                change.getHomeUrl()
        )
        assertEquals(
                "$publicInstanceUrl/viewModification.html?modId=${change.id.stringId}&personal=true&buildTypeId=xxx",
                change.getHomeUrl(specificBuildConfigurationId = BuildConfigurationId("xxx"), includePersonalBuilds = true)
        )
    }

    @Ignore("There are no recent changes, because active development has been moved to buildserver")
    @Test
    fun changeByVcsRevision() {
        val build = publicInstance().builds()
                .fromConfiguration(changesBuildConfiguration)
                .limitResults(10)
                .all()
                .first { it.changes.isNotEmpty() }
        val change = build.changes.first()

        assertEquals(
                change.toString(),
                publicInstance().change(changesBuildConfiguration, change.version).toString()
        )
        assertTrue(change.firstBuilds().map { it.toString() }.contains(build.toString()))
    }

    @Ignore("There are no recent changes, because active development has been moved to buildserver")
    @Test
    fun buildByVcsRevision() {
        val build = publicInstance().builds()
                .fromConfiguration(changesBuildConfiguration)
                .limitResults(10)
                .all()
                .first { it.changes.isNotEmpty() }
        val change = build.changes.first()

        val builds = publicInstance().builds()
                .fromConfiguration(changesBuildConfiguration)
                .withVcsRevision(change.version)
                .all()
        assertTrue(builds.map { it.toString() }.contains(build.toString()))
    }
}