package org.jetbrains.teamcity.rest

import java.io.File
import java.io.OutputStream
import java.util.*

abstract class TeamCityInstance {
    abstract val serverUrl: String

    abstract fun withLogResponses(): TeamCityInstance

    abstract fun builds(): BuildLocator

    abstract fun build(id: BuildId): Build
    abstract fun build(buildType: BuildConfigurationId, number: String): Build?
    abstract fun buildConfiguration(id: BuildConfigurationId): BuildConfiguration
    abstract fun vcsRoots(): VcsRootLocator
    abstract fun vcsRoot(id: VcsRootId): VcsRoot
    abstract fun project(id: ProjectId): Project
    abstract fun rootProject(): Project
    abstract fun buildQueue(): BuildQueue
    abstract fun buildResults(): BuildResults
    abstract fun user(id: UserId): User
    abstract fun users(): UserLocator

    abstract fun getWebUrl(projectId: ProjectId, branch: String? = null): String
    abstract fun getWebUrl(buildConfigurationId: BuildConfigurationId, branch: String? = null): String
    abstract fun getWebUrl(buildId: BuildId): String
    abstract fun getWebUrl(userId: UserId): String
    abstract fun getWebUrl(projectId: ProjectId, testId: TestId): String
    abstract fun getWebUrl(queuedBuildId: QueuedBuildId): String
    abstract fun getWebUrl(changeId: ChangeId, specificBuildConfigurationId: BuildConfigurationId? = null, includePersonalBuilds: Boolean? = null): String

    companion object {
        private const val factoryFQN = "org.jetbrains.teamcity.rest.TeamCityInstanceFactory"

        @JvmStatic
        @Deprecated("Use [TeamCityInstanceFactory] class instead", ReplaceWith("TeamCityInstanceFactory.guestAuth(serverUrl)", factoryFQN))
        fun guestAuth(serverUrl: String): TeamCityInstance = TeamCityInstance::class.java.classLoader
                .loadClass(factoryFQN)
                .getMethod("guestAuth", String::class.java)
                .invoke(null, serverUrl) as TeamCityInstance

        @JvmStatic
        @Deprecated("Use [TeamCityInstanceFactory] class instead", ReplaceWith("TeamCityInstanceFactory.httpAuth(serverUrl, username, password)", factoryFQN))
        fun httpAuth(serverUrl: String, username: String, password: String): TeamCityInstance
                = TeamCityInstance::class.java.classLoader
                .loadClass(factoryFQN)
                .getMethod("httpAuth", String::class.java, String::class.java, String::class.java)
                .invoke(null, serverUrl, username, password) as TeamCityInstance
    }
}

interface VcsRootLocator {
    fun list(): Sequence<VcsRoot>
}

interface UserLocator {
    fun withId(id: UserId): UserLocator
    fun withUsername(name: String): UserLocator

    fun list(): List<User>
}

interface BuildLocator {
    fun fromConfiguration(buildConfigurationId: BuildConfigurationId): BuildLocator

    fun snapshotDependencyTo(buildId: BuildId): BuildLocator

    /**
     * By default only successful builds are returned, call this method to include failed builds as well.
     */
    fun withAnyStatus(): BuildLocator

    fun withStatus(status: BuildStatus): BuildLocator
    fun withTag(tag: String): BuildLocator

    fun withBranch(branch: String): BuildLocator

    /**
     * By default only builds from the default branch are returned, call this method to include builds from all branches.
     */
    fun withAllBranches(): BuildLocator

    fun pinnedOnly(): BuildLocator

    fun limitResults(count: Int): BuildLocator

    fun sinceDate(date: Date) : BuildLocator

    fun latest(): Build?
    fun list(): Sequence<Build>
}

data class ProjectId(val stringId: String) {
    override fun toString(): String = stringId
}

data class BuildId(val stringId: String) {
    override fun toString(): String = stringId
}

data class TestId(val stringId: String) {
    override fun toString(): String = stringId
}

data class QueuedBuildId(val stringId: String) {
    override fun toString(): String = stringId
}

data class ChangeId(val stringId: String) {
    override fun toString(): String = stringId
}

data class BuildConfigurationId(val stringId: String) {
    override fun toString(): String = stringId
}

data class VcsRootId(val stringId: String) {
    override fun toString(): String = stringId
}

data class BuildProblemId(val stringId: String) {
    override fun toString(): String = stringId
}

data class BuildProblemType(val stringType: String) {
    override fun toString(): String = stringType

    companion object {
        val SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE = BuildProblemType("SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE")
        val SNAPSHOT_DEPENDENCY_ERROR = BuildProblemType("SNAPSHOT_DEPENDENCY_ERROR")
        val FAILED_TESTS = BuildProblemType("TC_FAILED_TESTS")
    }
}

interface Project {
    val id: ProjectId
    val name: String
    val archived: Boolean
    val parentProjectId: ProjectId

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getWebUrl(branch: String? = null): String

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

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getWebUrl(branch: String? = null): String

    fun fetchBuildTags(): List<String>

    fun fetchFinishBuildTriggers(): List<FinishBuildTrigger>

    fun fetchArtifactDependencies(): List<ArtifactDependency>

    fun setParameter(name: String, value: String)
}

interface BuildProblem {
    val id: BuildProblemId
    val type: BuildProblemType
    val identity: String
}

interface BuildProblemOccurrence {
    val buildProblem: BuildProblem
    val build: Build
    val details: String
    val additionalData: String?
}

interface Parameter {
    val name: String
    val value: String
    val own: Boolean
}

interface Branch {
    val name: String?
    val isDefault: Boolean
}

interface BuildCanceledInfo {
    val user: User?
    val cancelDate: Date
}

interface Build {
    val id: BuildId
    val buildTypeId: BuildConfigurationId
    val buildNumber: String
    val status: BuildStatus?
    val branch: Branch
    val state: BuildState
    val name: String
    val canceledInfo: BuildCanceledInfo?

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getWebUrl(): String

    fun fetchStatusText(): String
    fun fetchQueuedDate(): Date
    fun fetchStartDate(): Date
    fun fetchFinishDate(): Date

    fun fetchParameters(): List<Parameter>

    fun fetchRevisions(): List<Revision>

    fun fetchChanges(): List<Change>

    fun fetchPinInfo(): PinInfo?

    fun fetchTriggeredInfo(): TriggeredInfo?

    fun fetchTests(status: TestStatus? = null) : Sequence<TestOccurrence>

    fun fetchBuildProblems(): Sequence<BuildProblemOccurrence>

    fun addTag(tag: String)
    fun pin(comment: String = "pinned via REST API")
    fun unpin(comment: String = "unpinned via REST API")
    fun getArtifacts(parentPath: String = ""): List<BuildArtifact>
    fun findArtifact(pattern: String, parentPath: String = ""): BuildArtifact
    fun downloadArtifacts(pattern: String, outputDir: File)
    fun downloadArtifact(artifactPath: String, output: OutputStream)
    fun downloadArtifact(artifactPath: String, output: File)
}

interface QueuedBuild {
    val id: QueuedBuildId
    val buildTypeId: BuildConfigurationId
    val status: QueuedBuildStatus
    val branch : Branch

    fun getWebUrl(): String
}

enum class QueuedBuildStatus {
    QUEUED,
    FINISHED
}

interface Change {
    val id: ChangeId
    val version: String
    val username: String
    val user: User?
    val date: Date
    val comment: String

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getWebUrl(specificBuildConfigurationId: BuildConfigurationId? = null, includePersonalBuilds: Boolean? = null): String
}

data class UserId(val stringId: String)

interface User {
    val id: UserId
    val username: String
    val name: String
    val email: String

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getWebUrl(): String
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
    ERROR,
    UNKNOWN,
}

enum class BuildState {
    QUEUED,
    RUNNING,
    FINISHED,
    DELETED,
    UNKNOWN,
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

enum class TestStatus {
    SUCCESSFUL,
    IGNORED,
    FAILED,

    UNKNOWN,
}

interface TestOccurrence {
    val name : String
    val status: TestStatus
    val duration: Long
    val details : String
    val ignored: Boolean
    val currentlyMuted: Boolean
    val muted: Boolean

    val buildId: BuildId
    val testId: TestId
}

interface TriggeredInfo {
    val user: User?
    val build: Build?
}

interface FinishBuildTrigger {
    val initiatedBuildConfiguration: BuildConfigurationId
    val afterSuccessfulBuildOnly: Boolean
    val includedBranchPatterns: Set<String>
    val excludedBranchPatterns: Set<String>
}

interface ArtifactDependency {
    val dependsOnBuildConfiguration: BuildConfiguration
    val branch: String?
    val artifactRules: List<ArtifactRule>
    val cleanDestinationDirectory: Boolean
}

interface ArtifactRule {
    val include: Boolean
    /**
     * Specific file, directory, or wildcards to match multiple files can be used. Ant-like wildcards are supported.
     */
    val sourcePath: String
    /**
     * Follows general rules for sourcePath: ant-like wildcards are allowed.
     */
    val archivePath: String?
    /**
     * Destination directory where files are to be placed.
     */
    val destinationPath: String?
}

interface BuildQueue {
    fun triggerBuild(buildTypeId: BuildConfigurationId,
                     parameters: Map<String, String>? = null,
                     queueAtTop: Boolean? = null,
                     cleanSources: Boolean? = null,
                     rebuildAllDependencies: Boolean? = null,
                     comment: String? = null,
                     logicalBranchName: String? = null,
                     personal: Boolean? = null): BuildId
    fun cancelBuild(id: BuildId, comment: String = "", reAddIntoQueue: Boolean = false)
    fun queuedBuilds(projectId: ProjectId? = null): List<QueuedBuild>
}

interface BuildResults {
    fun tests(id: BuildId)
}
