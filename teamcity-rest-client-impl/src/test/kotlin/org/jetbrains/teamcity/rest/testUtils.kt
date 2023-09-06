package org.jetbrains.teamcity.rest

import org.apache.log4j.*
import org.junit.Assert.assertEquals
import java.io.File
import java.io.FileInputStream
import java.util.*
import kotlin.reflect.KFunction
import kotlin.reflect.KProperty
import kotlin.reflect.full.valueParameters

private val TEAMCITY_CONNECTION_FILE_PATH = "teamcity_connection.properties"

//slf4j simple ignores debug output
fun setupLog4jDebug() {
    LogManager.resetConfiguration()
    Logger.getRootLogger().removeAllAppenders()
    Logger.getRootLogger().addAppender(ConsoleAppender(PatternLayout("TEST[%d] %6p [%15.15t] - %30.30c - %m %n")))
    Logger.getLogger("jetbrains").level = Level.DEBUG
    Logger.getLogger("org.apache.http").level = Level.ERROR
}

val publicInstanceUrl = "http://localhost:8111"

fun testInstanceBuilder(serverUrl: String = publicInstanceUrl) =
    TeamCityInstanceBuilder(serverUrl).setResponsesLoggingEnabled(true)

fun haveCustomInstance(): Boolean = ConnectionPropertiesFileLoader(TEAMCITY_CONNECTION_FILE_PATH).validate()
private fun customInstanceByConnectionFileBuilder(): TeamCityInstanceBuilder {
    val connectionPropertiesFileLoader = ConnectionPropertiesFileLoader(TEAMCITY_CONNECTION_FILE_PATH)
    return if (connectionPropertiesFileLoader.validate()) {
        val connectionConfig = connectionPropertiesFileLoader.fetch()
        testInstanceBuilder(connectionConfig.serverUrl)
            .withHttpAuth(connectionConfig.username, connectionConfig.password)
            .setResponsesLoggingEnabled(true)
    } else {
        testInstanceBuilder(publicInstanceUrl)
    }
}

fun publicInstance() = testInstanceBuilder().buildBlockingInstance()
fun publicCoroutinesInstance() = testInstanceBuilder().build()

fun customInstanceByConnectionFile() = customInstanceByConnectionFileBuilder().buildBlockingInstance()
fun customCoroutinesInstanceByConnectionFile() = customInstanceByConnectionFileBuilder().build()


val reportProject = ProjectId("ProjectForReports")
val testProject = ProjectId("TestProject")
val mutesProject = ProjectId("ProjectForMutes")
val changesBuildConfiguration = BuildConfigurationId("ProjectForSidebarCounters_MultibranchChange")
val testsBuildConfiguration = BuildConfigurationId("ProjectForSidebarCounters_MultibranchTestResult")
val runTestsBuildConfiguration = BuildConfigurationId("TestProject_RunTests")
val dependantBuildConfiguration = BuildConfigurationId("TeamcityTestMetadataDemo_TestMetadataDemo")
val pausedBuildConfiguration = BuildConfigurationId("ProjectForReports_TestPaused")
val manyTestsBuildConfiguration = BuildConfigurationId("TeamcityTestData_Test")

internal class ConnectionPropertiesFileLoader(filePath: String) {

    private val connectionFile: File?

    init {
        val classLoader = javaClass.classLoader
        connectionFile = classLoader.getResource(filePath)?.let { File(it.file) }
    }

    fun fetch(): ConnectionConfig {
        if (!validate()) {
            throw IllegalStateException("Properties are invalid")
        }
        val connectionProperties = Properties()
        connectionProperties.load(FileInputStream(connectionFile))
        return ConnectionConfig(
                connectionProperties.getProperty(SERVER_URL),
                connectionProperties.getProperty(USERNAME),
                connectionProperties.getProperty(PASSWORD))
    }

    fun validate(): Boolean {
        if (connectionFile == null || !connectionFile.exists()) return false
        val connectionProperties = Properties()
        connectionProperties.load(FileInputStream(connectionFile))
        return validateConnectionProperties(connectionProperties)
    }

    private fun validateConnectionProperties(connectionProperties: Properties): Boolean {
        return validPropertyValue(connectionProperties.getProperty(SERVER_URL))
                && validPropertyValue(connectionProperties.getProperty(USERNAME))
                && validPropertyValue(connectionProperties.getProperty(PASSWORD))
    }

    private fun validPropertyValue(value: String?): Boolean {
        return (value != null) && (!value.isNullOrEmpty())
    }

    companion object {
        val SERVER_URL = "serverUrl"
        val USERNAME = "username"
        val PASSWORD = "password"
    }
}

internal class ConnectionConfig(val serverUrl: String, val username: String, val password: String)

inline fun <reified T> callPublicPropertiesAndFetchMethods(instance: T) {
    instance.toString()

    for (member in T::class.members) {
        when (member) {
            is KProperty<*> -> {
                member.getter.call(instance)
//                    println("${member.name} = ${member.getter.call(instance)}")
            }

            is KFunction<*> -> if (
                    member.name.startsWith("fetch") ||
                    member.name.startsWith("get")) {
                if (member.valueParameters.isEmpty()) {
                    member.call(instance)
//                    println("${member.name} = ${member.call(instance)}")
                }
            }
        }
    }
}

fun <C: Comparable<C>, I : Iterable<C>> assertEqualsAnyOrder(first: I, second: I) {
    assertEquals(first.sorted(), second.sorted())
}