package org.jetbrains.teamcity.rest

import java.io.File
import java.util.Date

public trait TeamCityInstance {
    fun builds(buildTypeId: BuildTypeId? = null,
               status: BuildStatus? = BuildStatus.SUCCESS,
               tags: List<String>? = null): List<Build>

    fun build(id: BuildId): Build
    fun project(id: ProjectId): Project
    fun rootProject(): Project

    companion object {
        fun builder(serverUrl: String): TeamCityInstanceBuilder = TeamCityInstanceBuilderImpl(serverUrl)
    }
}

public trait TeamCityInstanceBuilder {
    fun withDebugLogging(): TeamCityInstanceBuilder
    fun httpAuth(username: String, password: String): TeamCityInstanceBuilder

    fun build(): TeamCityInstance
}

public data class ProjectId(val stringId: String)

public data class BuildId(val stringId: String)

public data class BuildTypeId(val stringId: String)

public trait Project {
    val id: ProjectId
    val name: String
    val archived: Boolean
    val parentProjectId: ProjectId

    fun fetchChildProjects(): List<Project>
    fun fetchBuildTypes(): List<BuildType>
    fun fetchParameters(): List<Parameter>
}

public trait BuildType {
    val id: BuildTypeId
    val name: String
    val projectId: ProjectId

    fun buildTags(): List<String>
}

public trait Parameter {
    val name: String
    val value: String?
    val own: Boolean
}

public trait Build {
    val id: BuildId
    val buildNumber: String
    val status: BuildStatus

    fun fetchQueuedDate(): Date
    fun fetchStartDate(): Date
    fun fetchFinishDate(): Date

    fun addTag(tag: String)
    fun pin(comment: String = "pinned via REST API")
    fun getArtifacts(parentPath: String = ""): List<BuildArtifact>
    fun findArtifact(pattern: String, parentPath: String = ""): BuildArtifact
    fun downloadArtifacts(pattern: String, outputDir: File)
    fun downloadArtifact(artifactPath: String, output: File)
}

public trait BuildArtifact {
    val fileName: String
}

public enum class BuildStatus {
    SUCCESS
    FAILURE
    ERROR
}
