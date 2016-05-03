package org.jetbrains.teamcity.rest

import org.apache.log4j.*
import org.junit.Assert
import org.junit.Before
import org.junit.Test

//slf4j simple ignores debug output
fun setupLog4jDebug() {
  LogManager.resetConfiguration()
  Logger.getRootLogger().removeAllAppenders()
  Logger.getRootLogger().addAppender(ConsoleAppender(PatternLayout("TEST[%d] %6p [%15.15t] - %30.30c - %m %n")))
  Logger.getLogger("jetbrains").level = Level.DEBUG
  Logger.getLogger("org.apache.http").level = Level.ERROR
}

class BranchesTest {
  @Before
  fun setupLog4j() { setupLog4jDebug() }

  @Test
  fun test_list_works_no_branches() {
    val noBranchesLocator = kotlinBuildsNoBranches()
            .withAllBranches()
            .withStatus(BuildStatus.SUCCESS)
            .limitResults(20) as BuildLocator
    noBranchesLocator
            .list().forEach {
      Assert.assertTrue(it.branch.isDefault)
    }
  }
  @Test
  fun test_list_works() {
    val buildLocator = kotlinBuilds()
            .withAllBranches()
            .withStatus(BuildStatus.SUCCESS)
            .limitResults(20) as BuildLocator
    buildLocator
            .list().forEach {
      it.fetchParameters()
      it.fetchChanges()
      it.getArtifacts()
    }
  }

  @Test
  fun test_kotlin_branches() {
    val branches = mutableSetOf<String>()
    val branchesLocator = kotlinBuilds()
            .withAllBranches()
            .withStatus(BuildStatus.SUCCESS)
            .limitResults(50) as BuildLocator
    branchesLocator
            .list().forEach {
      branches += it.branch.name!!
      println(it)
    }

    Assert.assertTrue("Actual branches: $branches", branches.size > 1)
  }

  @Test
  fun test_kotlin_default() {
    val branches = mutableSetOf<String>()
    val buildLocator = kotlinBuilds()
            .withStatus(BuildStatus.SUCCESS)
            .limitResults(50) as BuildLocator
    buildLocator
            .list().forEach {
      branches += it.branch.name!!
      println(it)
    }

    Assert.assertTrue("Actual branches: $branches", branches.size == 1)
  }

  @Test
  fun test_kotlin_queuelength() {
    val queuedBuildsLocator = kotlinBuildQueue()

    queuedBuildsLocator
            .list().forEach {
      println(it)
    }
  }

  private fun kotlinBuilds(): BuildLocator =
          TeamCityInstance.guestAuth("https://teamcity.jetbrains.com")
                  .withLogResponses()
                  .builds()
                  .fromConfiguration(BuildConfigurationId("bt345")) as BuildLocator

  private fun kotlinBuildsNoBranches(): BuildLocator =
          TeamCityInstance.guestAuth("https://teamcity.jetbrains.com")
                  .withLogResponses()
                  .builds()
                  .fromConfiguration(BuildConfigurationId("bt446")) as BuildLocator

  private fun kotlinBuildQueue(): QueuedBuildLocator =
          TeamCityInstance.guestAuth("https://teamcity.jetbrains.com").queuedBuilds()
}