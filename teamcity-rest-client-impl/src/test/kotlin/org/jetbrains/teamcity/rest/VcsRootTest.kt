package org.jetbrains.teamcity.rest

import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class VcsRootTest {

    private lateinit var customInstance: TeamCityInstance

    @Before
    fun setupLog4j() {
        setupLog4jDebug()
        customInstance = customInstanceByConnectionFile()
    }

    @Test
    fun access_to_vcs_root_requires_credential() {
        val vcsRootLocator = vcsRootsFromPublicInstance()
        vcsRootLocator.list()
    }

    @Ignore("teamcity_connection.properties should be updated;" +
            "Vcs roots should be accessed")
    @Test
    fun vcs_roots_are_loaded() {
        val vcsRootLocator = vcsRootsFromCustomInstance()
        val vcsRoots = vcsRootLocator.list()
        assertTrue("Some vcs roots should be loaded", vcsRoots.isNotEmpty())
    }

    @Ignore("teamcity_connection.properties should be updated;" +
            "Vcs root id should be defined for custom teamcity server")
    @Test
    fun vcs_root_is_loaded_by_id() {
        val vcsRootId = VcsRootId("ProjectForTests_Absolutely_Unique_Id")
        val vcsRoot = customInstanceByConnectionFile().vcsRoot(vcsRootId)
        assertNotNull("Vcs root should be loaded", vcsRoot)
    }

    @Test
    fun test_fetch_vcs_root_properties() {
        val vcsRoot = vcsRootsFromPublicInstance().list().first()
        val vcsRootProperties = vcsRoot.fetchVcsRootProperties()
        assertNotNull("Vcs root properties should be loaded", vcsRootProperties)
    }

    private fun vcsRootsFromPublicInstance(): VcsRootLocator {
        return publicInstance().vcsRoots()
    }

    private fun vcsRootsFromCustomInstance(): VcsRootLocator {
        return customInstance.vcsRoots()
    }

}