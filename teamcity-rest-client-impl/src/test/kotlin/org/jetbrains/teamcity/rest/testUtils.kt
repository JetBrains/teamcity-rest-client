package org.jetbrains.teamcity.rest

import org.apache.log4j.*
import java.io.File
import java.io.FileInputStream
import java.util.*

private val TEAMCITY_CONNECTION_FILE_PATH = "teamcity_connection.properties"

//slf4j simple ignores debug output
fun setupLog4jDebug() {
    LogManager.resetConfiguration()
    Logger.getRootLogger().removeAllAppenders()
    Logger.getRootLogger().addAppender(ConsoleAppender(PatternLayout("TEST[%d] %6p [%15.15t] - %30.30c - %m %n")))
    Logger.getLogger("jetbrains").level = Level.DEBUG
    Logger.getLogger("org.apache.http").level = Level.ERROR
}

val publicInstanceUrl = "https://teamcity.jetbrains.com"

fun publicInstance() = TeamCityInstance.guestAuth(publicInstanceUrl).withLogResponses()

fun customInstance(serverUrl: String, username: String, password: String) = TeamCityInstance
        .httpAuth(serverUrl, username, password)
        .withLogResponses()

fun customInstanceByConnectionFile(): TeamCityInstance {
    val connectionPropertiesFileLoader = ConnectionPropertiesFileLoader(TEAMCITY_CONNECTION_FILE_PATH)
    return if (connectionPropertiesFileLoader.validate()) {
        val connectionConfig = connectionPropertiesFileLoader.fetch()
        customInstance(connectionConfig.serverUrl,
                connectionConfig.username,
                connectionConfig.password)
    } else {
        publicInstance()
    }
}

val kotlinProject = ProjectId("Kotlin")
val compilerAndPluginConfiguration = BuildConfigurationId("bt345")
val compileExamplesConfiguration = BuildConfigurationId("bt446")
val fSharpVSPowerToolsPausedConfiguration = BuildConfigurationId("bt1208")

internal class ConnectionPropertiesFileLoader(filePath: String) {

    private val connectionFile: File

    init {
        val classLoader = javaClass.classLoader
        connectionFile = File(classLoader.getResource(filePath).file)
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
        if (!connectionFile.exists()) return false
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
