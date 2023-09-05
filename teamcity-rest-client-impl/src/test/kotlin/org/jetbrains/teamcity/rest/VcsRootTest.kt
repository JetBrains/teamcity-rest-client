package org.jetbrains.teamcity.rest

import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.teamcity.rest.coroutines.TeamCityCoroutinesInstance
import org.junit.Before
import org.junit.Test

class VcsRootTest {

    private lateinit var customInstance: TeamCityInstance
    private lateinit var customCoroutinesInstance: TeamCityCoroutinesInstance

    @Before
    fun setupLog4j() {
        setupLog4jDebug()
        customInstance = customInstanceByConnectionFile()
        customCoroutinesInstance = customCoroutinesInstanceByConnectionFile()
    }

    @Test
    fun access_to_vcs_root_requires_credential() {
        val vcsRootLocator = publicInstance().vcsRoots()
        vcsRootLocator.all().toList()
    }

    @Test
    fun vcs_roots_are_loaded() {
        val vcsRootLocator = customInstance.vcsRoots()
        val vcsRoots = vcsRootLocator.all().toList()
        assertTrue("Some vcs roots should be loaded", vcsRoots.isNotEmpty())

        val vcsRootLocatorCoroutines = customCoroutinesInstance.vcsRoots()
        val vcsRootsAsync = runBlocking { vcsRootLocatorCoroutines.all().toList() }
        assertTrue("Some vcs roots should be loaded", vcsRootsAsync.isNotEmpty())
        assertEqualsAnyOrder(vcsRoots.map { it.id.stringId }, vcsRootsAsync.map { it.id.stringId })
    }

    @Test
    fun vcs_root_is_loaded_by_id() {
        val vcsRootId = VcsRootId("TestProject_GitProjectForWebTests")
        val vcsRoot = customInstanceByConnectionFile().vcsRoot(vcsRootId)
        assertNotNull("Vcs root should be loaded", vcsRoot)
    }

    @Test
    fun test_get_url() {
        val vcsRoot = publicInstance().vcsRoots().all().first()
        val url = vcsRoot.url
        assertNotNull("Vcs root url should be loaded", url)
    }

    @Test
    fun test_get_default_branch() {
        val vcsRoot = publicInstance().vcsRoots().all().first()
        val url = vcsRoot.defaultBranch
        assertNotNull("Vcs root default branch should be loaded", url)
    }
}