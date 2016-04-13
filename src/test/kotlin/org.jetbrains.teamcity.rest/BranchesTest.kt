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
      it.fetchChanges()
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

  private fun kotlinBuilds(): BuildLocator =
          TeamCityInstance.guestAuth("https://teamcity.jetbrains.com")
                  .builds()
                  .fromConfiguration(BuildConfigurationId("bt345"))

  private fun kotlinBuildsNoBranches(): BuildLocator =
          TeamCityInstance.guestAuth("https://teamcity.jetbrains.com")
                  .builds()
                  .fromConfiguration(BuildConfigurationId("bt446"))

}