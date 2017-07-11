package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class BranchesTest {
  @Before
  fun setupLog4j() { setupLog4jDebug() }

  @Test
  fun test_list_works_no_branches() {
    kotlinBuildsNoBranches()
            .withAllBranches()
            .withStatus(BuildStatus.SUCCESS)
            .limitResults(20)
            .list().forEach {
      Assert.assertTrue(it.branch.isDefault)
    }
  }

  @Test
  fun test_list_works() {
    kotlinBuilds()
            .withAllBranches()
            .withStatus(BuildStatus.SUCCESS)
            .limitResults(20)
            .list().forEach {
      it.fetchParameters()
      it.fetchChanges().joinToString("\n")
      it.fetchRevisions()
      it.getArtifacts()
    }
  }

  @Test
  fun test_kotlin_branches() {
    val branches = mutableSetOf<String>()
    kotlinBuilds()
            .withAllBranches()
            .withStatus(BuildStatus.SUCCESS)
            .limitResults(50)
            .list().forEach {
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
            .list().forEach {
      branches += it.branch.name!!
      println(it)
    }

    Assert.assertTrue("Actual branches: $branches", branches.size == 1)
  }

  private fun kotlinBuilds(): BuildLocator {
    return publicInstance()
            .builds()
            .fromConfiguration(compilerAndPluginConfiguration)
  }

  private fun kotlinBuildsNoBranches(): BuildLocator {
    return publicInstance().builds()
            .fromConfiguration(compileExamplesConfiguration)
  }

}
