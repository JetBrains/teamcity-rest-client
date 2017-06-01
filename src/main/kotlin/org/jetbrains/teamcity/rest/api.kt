package org.jetbrains.teamcity.rest

import java.io.File
import java.util.*

abstract class TeamCityInstance {
    abstract fun withLogResponses() : TeamCityInstance

    abstract fun builds(): BuildLocator

    abstract fun build(id: BuildId): Build
    abstract fun build(buildType: BuildConfigurationId, number: String): Build
    abstract fun buildConfiguration(id: BuildConfigurationId): BuildConfiguration
    abstract fun vcsRoots(): VcsRootLocator
    abstract fun vcsRoot(id: VcsRootId): VcsRoot
    abstract fun project(id: ProjectId): Project
    abstract fun rootProject(): Project
    abstract fun buildQueue(): BuildQueue

    companion object {
        @JvmStatic
        fun guestAuth(serverUrl: String): TeamCityInstance = createGuestAuthInstance(serverUrl)

        @JvmStatic
        fun httpAuth(serverUrl: String, username: String, password: String): TeamCityInstance
                = createHttpAuthInstance(serverUrl, username, password)
    }
}

interface VcsRootLocator {
    fun list(): List<VcsRoot>
}

interface BuildLocator {
    fun fromConfiguration(buildConfigurationId: BuildConfigurationId): BuildLocator

    /**
     * By default only successful builds are returned, call this method to include failed builds as well.
     */
    fun withAnyStatus() : BuildLocator

    fun withStatus(status: BuildStatus): BuildLocator
    fun withTag(tag: String): BuildLocator

    fun withBranch(branch: String): BuildLocator

    /**
     * By default only builds from the default branch are returned, call this method to include builds from all branches.
     */
    fun withAllBranches() : BuildLocator

    fun pinnedOnly(): BuildLocator

    fun limitResults(count: Int): BuildLocator

    fun latest(): Build?
    fun list(): List<Build>
}

data class ProjectId(val stringId: String)

data class BuildId(val stringId: String)

data class ChangeId(val stringId: String)

data class BuildConfigurationId(val stringId: String)

data class VcsRootId(val stringId: String)

class TriggerRequest(val buildType: BuildType, parameters: Map<String, String> = hashMapOf()) {
    val properties: Parameters = Parameters(parameters.map { entry -> Property(entry.key, entry.value) }.toList())
}

data class Parameters(val property: List<Property> = emptyList())

data class Property(val name: String, val value: String)

data class BuildType(val id: String)

interface Project {
    val id: ProjectId
    val name: String
    val archived: Boolean
    val parentProjectId: ProjectId

    fun fetchChildProjects(): List<Project>
    fun fetchBuildConfigurations(): List<BuildConfiguration>
    fun fetchParameters(): List<Parameter>

    fun setParameter(name: String, value: String)
}

interface BuildConfiguration {
    val id: BuildConfigurationId
    val name: String
    val projectId: ProjectId
    val paused: Boolean

    fun fetchBuildTags(): List<String>

    fun setParameter(name: String, value: String)
}

interface Parameter {
    val name: String
    val value: String?
    val own: Boolean
}

interface Branch {
    val name : String?
    val isDefault : Boolean
}

interface Build {
    val id: BuildId
    val buildTypeId: String
    val buildNumber: String
    val status: BuildStatus
    val branch : Branch
    val state: String
    val webUrl: String

    fun fetchStatusText(): String
    fun fetchQueuedDate(): Date
    fun fetchStartDate(): Date
    fun fetchFinishDate(): Date

    fun fetchParameters(): List<Parameter>

    fun fetchRevisions(): List<Revision>

    fun fetchChanges(): List<Change>

    fun fetchPinInfo(): PinInfo?

    fun fetchTriggeredInfo(): TriggeredInfo?

    fun addTag(tag: String)
    fun pin(comment: String = "pinned via REST API")
    fun unpin(comment: String = "unpinned via REST API")
    fun getArtifacts(parentPath: String = ""): List<BuildArtifact>
    fun findArtifact(pattern: String, parentPath: String = ""): BuildArtifact
    fun downloadArtifacts(pattern: String, outputDir: File)
    fun downloadArtifact(artifactPath: String, output: File)
}

interface Change {
    val id: ChangeId
    val version: String
    val username: String
    val user: User?
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
    val size: Long?
    val modificationTime: Date

    fun download(output: File)
}

interface VcsRoot {
    val id: VcsRootId
    val name: String
}

enum class BuildStatus {
    SUCCESS,
    FAILURE,
    ERROR
}

interface PinInfo {
    val user: User
    val time: Date
}

interface Revision {
    val version: String
    val vcsBranchName: String
    val vcsRoot: VcsRoot
}

interface TriggeredInfo {
    val user: User?
    val build: Build?
}

interface TriggeredBuild {
    val id: Int
    val buildTypeId: String
}

interface BuildQueue {
    fun triggerBuild(triggerRequest: TriggerRequest): TriggeredBuild
}
