package org.jetbrains.teamcity.rest

import org.apache.log4j.*

//slf4j simple ignores debug output
fun setupLog4jDebug() {
    LogManager.resetConfiguration()
    Logger.getRootLogger().removeAllAppenders()
    Logger.getRootLogger().addAppender(ConsoleAppender(PatternLayout("TEST[%d] %6p [%15.15t] - %30.30c - %m %n")))
    Logger.getLogger("jetbrains").level = Level.DEBUG
    Logger.getLogger("org.apache.http").level = Level.ERROR
}

fun publicInstance() = TeamCityInstance.guestAuth("https://teamcity.jetbrains.com").withLogResponses()

val kotlinProject = ProjectId("Kotlin")
val compilerAndPluginConfiguration = BuildConfigurationId("bt345")
val compileExamplesConfiguration = BuildConfigurationId("bt446")
