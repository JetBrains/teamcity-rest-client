package org.jetbrains.teamcity.rest

import java.io.File
import java.util.*

interface TeamCityInstance {
    fun builds(): BuildLocator

    fun build(id: BuildId): Build
    fun project(id: ProjectId): Project
    fun rootProject(): Project

    companion object {
        fun guestAuth(serverUrl: String): TeamCityInstance = createGuestAuthInstance(serverUrl)
        fun httpAuth(serverUrl: String, username: String, password: String): TeamCityInstance
                = createHttpAuthInstance(serverUrl, username, password)
    }
}

interface BuildLocator {
    fun fromConfiguration(buildConfigurationId: BuildConfigurationId): BuildLocator
    fun withAnyStatus() : BuildLocator
    fun withStatus(status: BuildStatus): BuildLocator
    fun withTag(tag: String): BuildLocator
    fun withBranch(branch: String): BuildLocator
    fun withAllBranches() : BuildLocator
    fun limitResults(count: Int): BuildLocator

    fun latest(): Build?
    fun list(): List<Build>
}

data class ProjectId(val stringId: String)

data class BuildId(val stringId: String)

data class ChangeId(val stringId: String)

data class BuildConfigurationId(val stringId: String)

interface Project {
    val id: ProjectId
    val name: String
    val archived: Boolean
    val parentProjectId: ProjectId

    fun fetchChildProjects(): List<Project>
    fun fetchBuildConfigurations(): List<BuildConfiguration>
    fun fetchParameters(): List<Parameter>
}

interface BuildConfiguration {
    val id: BuildConfigurationId
    val name: String
    val projectId: ProjectId

    fun fetchBuildTags(): List<String>
}

interface Parameter {
    val name: String
    val value: String?
    val own: Boolean
}

interface Build {
    val id: BuildId
    val buildNumber: String
    val status: BuildStatus
    val branch : String?
    val isDefaultBranch : Boolean

    fun fetchQueuedDate(): Date
    fun fetchStartDate(): Date
    fun fetchFinishDate(): Date

    fun fetchParameters(): List<Parameter>

    fun fetchChanges(): List<Change>

    fun addTag(tag: String)
    fun pin(comment: String = "pinned via REST API")
    fun getArtifacts(parentPath: String = ""): List<BuildArtifact>
    fun findArtifact(pattern: String, parentPath: String = ""): BuildArtifact
    fun downloadArtifacts(pattern: String, outputDir: File)
    fun downloadArtifact(artifactPath: String, output: File)
}

interface Change {
    val id: ChangeId
    val version: String
    val user: User
    val date: Date
    val comment: String
}

interface User {
    val id: String
    val username: String
    val name: String
}

interface BuildArtifact {
    val fileName: String

    fun download(output: File)
}

enum class BuildStatus {
    SUCCESS,
    FAILURE,
    ERROR
}
