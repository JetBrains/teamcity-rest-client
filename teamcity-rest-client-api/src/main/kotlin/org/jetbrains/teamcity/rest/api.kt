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

    abstract fun change(buildType: BuildConfigurationId, vcsRevision: String): Change
    abstract fun change(id: ChangeId): Change

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

data class VcsRootType(val stringType: String) {
    companion object {
        val GIT = VcsRootType("jetbrains.git")
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

    fun withNumber(buildNumber: String): BuildLocator

    fun withVcsRevision(vcsRevision: String): BuildLocator

    fun snapshotDependencyTo(buildId: BuildId): BuildLocator

    /**
     * By default only successful builds are returned, call this method to include failed builds as well.
     */
    fun includeFailed(): BuildLocator

    /**
     * By default only finished builds are returned
     */
    fun includeRunning(): BuildLocator
    fun onlyRunning(): BuildLocator

    /**
     * By default canceled builds are not returned
     */
    fun includeCanceled(): BuildLocator
    fun onlyCanceled(): BuildLocator

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
    val parentProjectId: ProjectId?

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(branch: String? = null): String
    fun getTestHomeUrl(testId: TestId): String

    fun fetchChildProjects(): List<Project>
    fun fetchBuildConfigurations(): List<BuildConfiguration>
    fun fetchParameters(): List<Parameter>

    fun setParameter(name: String, value: String)

    fun createVcsRoot(id: VcsRootId, name: String, type: VcsRootType, properties: Map<String, String>): VcsRoot
    fun createProject(id: ProjectId, name: String): Project
    fun createBuildConfiguration(buildTypeDescriptionXml: String): BuildConfiguration
}

interface BuildConfiguration {
    val id: BuildConfigurationId
    val name: String
    val projectId: ProjectId
    val paused: Boolean

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(branch: String? = null): String

    val buildTags: List<String>
    val finishBuildTriggers: List<FinishBuildTrigger>
    val artifactDependencies: List<ArtifactDependency>

    fun setParameter(name: String, value: String)

    fun runBuild(parameters: Map<String, String>? = null,
                 queueAtTop: Boolean? = null,
                 cleanSources: Boolean? = null,
                 rebuildAllDependencies: Boolean? = null,
                 comment: String? = null,
                 logicalBranchName: String? = null,
                 personal: Boolean? = null): Build
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
    val buildNumber: String?
    val status: BuildStatus?
    val branch: Branch
    val state: BuildState
    val name: String
    val canceledInfo: BuildCanceledInfo?

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getBuildHomeUrl(): String

    val statusText: String?
    val queuedDate: Date
    val startDate: Date?
    val finishDate: Date?

    val parameters: List<Parameter>

    val revisions: List<Revision>

    val changes: List<Change>

    val pinInfo: PinInfo?

    val triggeredInfo: TriggeredInfo?

    fun tests(status: TestStatus? = null) : Sequence<TestOccurrence>

    val buildProblems: Sequence<BuildProblemOccurrence>

    fun addTag(tag: String)
    fun pin(comment: String = "pinned via REST API")
    fun unpin(comment: String = "unpinned via REST API")
    fun getArtifacts(parentPath: String = "", recursive: Boolean = false, hidden: Boolean = false): List<BuildArtifact>
    fun findArtifact(pattern: String, parentPath: String = ""): BuildArtifact
    fun downloadArtifacts(pattern: String, outputDir: File)
    fun downloadArtifact(artifactPath: String, output: OutputStream)
    fun downloadArtifact(artifactPath: String, output: File)
    fun downloadBuildLog(output: File)

    fun cancel(comment: String = "", reAddIntoQueue: Boolean = false)
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
    fun getHomeUrl(specificBuildConfigurationId: BuildConfigurationId? = null, includePersonalBuilds: Boolean? = null): String

    fun firstBuilds(): List<Build>
}

data class UserId(val stringId: String)

interface User {
    val id: UserId
    val username: String
    val name: String
    val email: String?

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(): String
}

interface BuildArtifact {
    /** Artifact name without path. e.g. my.jar */
    val name: String
    /** Artifact name with path. e.g. directory/my.jar */
    val fullName: String
    val size: Long?
    val modificationTime: Date

    fun download(output: File)
}

interface VcsRoot {
    val id: VcsRootId
    val name: String

    fun getUrl(): String?
    fun getDefaultBranch(): String?
}

interface VcsRootInstance {
    val vcsRootId: VcsRootId
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
    val vcsRootInstance: VcsRootInstance
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

open class TeamCityRestException(message: String?, cause: Throwable?) : RuntimeException(message, cause)

open class TeamCityQueryException(message: String?, cause: Throwable? = null) : TeamCityRestException(message, cause)

open class TeamCityConversationException(message: String?, cause: Throwable? = null) : TeamCityRestException(message, cause)

interface BuildQueue {
    fun removeBuild(id: BuildId, comment: String = "", reAddIntoQueue: Boolean = false)
    fun queuedBuilds(projectId: ProjectId? = null): Sequence<Build>
}

interface BuildResults {
    fun tests(id: BuildId)
}
