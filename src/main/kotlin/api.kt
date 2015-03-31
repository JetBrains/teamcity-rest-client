package org.jetbrains.teamcity.rest

import org.apache.commons.codec.binary.Base64
import java.io.File

public trait TeamCityInstance {
    fun builds(buildTypeId: BuildTypeId, status: BuildStatus? = BuildStatus.SUCCESS, tag: String? = null): List<Build>
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

public trait ProjectInfo {
    val id: ProjectId
    val name: String
    val archived: Boolean
    val parentProjectId: ProjectId

    fun project(): Project
}

public trait BuildTypeInfo {
    val id: BuildTypeId
    val name: String
    val projectId: ProjectId

    fun buildTags(): List<String>
}

public trait Project: ProjectInfo {
    val childProjects: List<ProjectInfo>
    val buildTypes: List<BuildTypeInfo>
    val parameters: List<PropertyInfo>
}

public trait PropertyInfo {
    val name: String
    val value: String?
    val own: Boolean
}

public trait Build {
    val id: BuildId
    val buildNumber: String
    val status: BuildStatus

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
