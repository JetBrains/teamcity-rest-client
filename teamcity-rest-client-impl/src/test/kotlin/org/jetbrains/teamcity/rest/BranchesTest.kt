package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class BranchesTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_list_works_all_branches() {
        getBuildsNoBranches()
                .withAllBranches()
                .withStatus(BuildStatus.SUCCESS)
                .limitResults(20)
                .all().forEach {
                    Assert.assertTrue(it.branch.isDefault)
                }
    }

    @Test
    fun test_list_works() {
        getBuildsBranches()
                .withAllBranches()
                .withStatus(BuildStatus.SUCCESS)
                .limitResults(20)
                .all().forEach {
                    it.parameters
                    it.changes.joinToString("\n")
                    it.revisions
                    it.getArtifacts()
                }
    }

    @Test
    fun test_multi_branches() {
        val branches = mutableSetOf<String>()
        getBuildsBranches()
                .withAllBranches()
                .withStatus(BuildStatus.FAILURE)
                .limitResults(50)
                .all().forEach {
                    branches += it.branch.name!!
                    println(it)
                }

        Assert.assertTrue("Actual branches: $branches", branches.size > 1)
    }

    @Test
    fun test_single_branch() {
        val branches = mutableSetOf<String>()
        getBuildsBranches()
                .withStatus(BuildStatus.SUCCESS)
                .limitResults(50)
                .all().forEach {
                    branches += it.branch.name!!
                    println(it)
                }

        Assert.assertTrue("Actual branches: $branches", branches.size == 1)
    }

    private fun getBuildsBranches(): BuildLocator {
        return publicInstance()
                .builds()
                .fromConfiguration(changesBuildConfiguration)
    }

    private fun getBuildsNoBranches(): BuildLocator {
        return publicInstance()
                .builds()
                .fromConfiguration(runTestsBuildConfiguration)
    }

}
