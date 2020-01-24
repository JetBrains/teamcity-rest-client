package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class BranchesTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_list_works_no_branches() {
        kotlinBuildsNoBranches()
                .withAllBranches()
                .withStatus(BuildStatus.SUCCESS)
                .limitResults(20)
                .all().forEach {
                    Assert.assertTrue(it.branch.isDefault)
                }
    }

    @Test
    fun test_list_works() {
        kotlinBuilds()
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

    @Ignore("There are no recent changes, because active development has been moved to buildserver")
    @Test
    fun test_kotlin_branches() {
        val branches = mutableSetOf<String>()
        kotlinBuilds()
                .withAllBranches()
                .withStatus(BuildStatus.SUCCESS)
                .limitResults(50)
                .all().forEach {
                    branches += it.branch.name!!
                    println(it)
                }

        Assert.assertTrue("Actual branches: $branches", branches.size > 1)
    }

    @Test
    fun test_kotlin_default() {
        val branches = mutableSetOf<String>()
        kotlinBuilds()
                .withStatus(BuildStatus.SUCCESS)
                .limitResults(50)
                .all().forEach {
                    branches += it.branch.name!!
                    println(it)
                }

        Assert.assertTrue("Actual branches: $branches", branches.size == 1)
    }

    private fun kotlinBuilds(): BuildLocator {
        return publicInstance()
                .builds()
                .fromConfiguration(changesBuildConfiguration)
    }

    private fun kotlinBuildsNoBranches(): BuildLocator {
        return publicInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
    }

}
