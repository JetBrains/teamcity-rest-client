package org.jetbrains.teamcity.rest

import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
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
        vcsRootLocator.all().toList()
    }

    @Test
    fun vcs_roots_are_loaded() {
        val vcsRootLocator = vcsRootsFromCustomInstance()
        val vcsRoots = vcsRootLocator.all().toList()
        assertTrue("Some vcs roots should be loaded", vcsRoots.isNotEmpty())
    }

    @Test
    fun vcs_root_is_loaded_by_id() {
        val vcsRootId = VcsRootId("TestProject_GitProjectForWebTests")
        val vcsRoot = customInstanceByConnectionFile().vcsRoot(vcsRootId)
        assertNotNull("Vcs root should be loaded", vcsRoot)
    }

    @Test
    fun test_get_url() {
        val vcsRoot = vcsRootsFromPublicInstance().all().first()
        val url = vcsRoot.url
        assertNotNull("Vcs root url should be loaded", url)
    }

    @Test
    fun test_get_default_branch() {
        val vcsRoot = vcsRootsFromPublicInstance().all().first()
        val url = vcsRoot.defaultBranch
        assertNotNull("Vcs root default branch should be loaded", url)
    }

    private fun vcsRootsFromPublicInstance(): VcsRootLocator {
        return publicInstance().vcsRoots()
    }

    private fun vcsRootsFromCustomInstance(): VcsRootLocator {
        return customInstance.vcsRoots()
    }

}