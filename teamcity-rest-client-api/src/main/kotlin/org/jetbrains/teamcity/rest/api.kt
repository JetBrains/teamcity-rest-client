package org.jetbrains.teamcity.rest

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

abstract class TeamCityInstance : AutoCloseable {
    abstract val serverUrl: String

    abstract fun withLogResponses(): TeamCityInstance
    abstract fun withTimeout(timeout: Long, unit: TimeUnit): TeamCityInstance

    abstract fun builds(): BuildLocator
    abstract fun investigations(): InvestigationLocator

    abstract fun build(id: BuildId): Build
    abstract fun build(buildConfigurationId: BuildConfigurationId, number: String): Build?
    abstract fun buildConfiguration(id: BuildConfigurationId): BuildConfiguration
    abstract fun vcsRoots(): VcsRootLocator
    abstract fun vcsRoot(id: VcsRootId): VcsRoot
    abstract fun project(id: ProjectId): Project
    abstract fun rootProject(): Project
    abstract fun buildQueue(): BuildQueue
    abstract fun user(id: UserId): User
    abstract fun user(userName: String): User
    abstract fun users(): UserLocator
    abstract fun buildAgents(): BuildAgentLocator
    abstract fun buildAgentPools(): BuildAgentPoolLocator
    abstract fun testRuns(): TestRunsLocator

    abstract fun change(buildConfigurationId: BuildConfigurationId, vcsRevision: String): Change
    abstract fun change(id: ChangeId): Change

    @Deprecated(message = "use project(projectId).getHomeUrl(branch)",
                replaceWith = ReplaceWith("project(projectId).getHomeUrl(branch)"))
    abstract fun getWebUrl(projectId: ProjectId, branch: String? = null): String
    @Deprecated(message = "use buildConfiguration(buildConfigurationId).getHomeUrl(branch)",
                replaceWith = ReplaceWith("buildConfiguration(buildConfigurationId).getHomeUrl(branch)"))
    abstract fun getWebUrl(buildConfigurationId: BuildConfigurationId, branch: String? = null): String
    @Deprecated(message = "use build(buildId).getHomeUrl()",
                replaceWith = ReplaceWith("build(buildId).getHomeUrl()"))
    abstract fun getWebUrl(buildId: BuildId): String
    @Deprecated(message = "use change(changeId).getHomeUrl()",
            replaceWith = ReplaceWith("change(changeId).getHomeUrl(specificBuildConfigurationId, includePersonalBuilds)"))
    abstract fun getWebUrl(changeId: ChangeId, specificBuildConfigurationId: BuildConfigurationId? = null, includePersonalBuilds: Boolean? = null): String
    @Deprecated(message = "use buildQueue().queuedBuilds(projectId)",
                replaceWith = ReplaceWith("buildQueue().queuedBuilds(projectId)"))
    abstract fun queuedBuilds(projectId: ProjectId? = null): List<Build>

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
    fun all(): Sequence<VcsRoot>

    @Deprecated(message = "use all() which returns lazy sequence",
                replaceWith = ReplaceWith("all().toList()"))
    fun list(): List<VcsRoot>
}

interface BuildAgentLocator {
    fun all(): Sequence<BuildAgent>
}

interface BuildAgentPoolLocator {
    fun all(): Sequence<BuildAgentPool>
}

interface UserLocator {
    fun all(): Sequence<User>

    @Deprecated("use instance.user(id)")
    fun withId(id: UserId): UserLocator
    @Deprecated(message = "use instance.user(userName)")
    fun withUsername(name: String): UserLocator
    @Deprecated(message = "use all() method which returns lazy sequence",
            replaceWith = ReplaceWith("all().toList()"))
    fun list(): List<User>
}

interface BuildLocator {
    fun fromConfiguration(buildConfigurationId: BuildConfigurationId): BuildLocator

    fun withNumber(buildNumber: String): BuildLocator

    /**
     * Filters builds to include only ones which are built on top of the specified revision.
     */
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

    fun includePersonal() : BuildLocator
    fun onlyPersonal(): BuildLocator

    fun limitResults(count: Int): BuildLocator
    fun pageSize(pageSize: Int): BuildLocator

    fun since(date: Instant) : BuildLocator
    fun until(date: Instant) : BuildLocator

    fun latest(): Build?
    fun all(): Sequence<Build>

    @Deprecated(message = "use all() which returns lazy sequence",
                replaceWith = ReplaceWith("all().toList()"))
    fun list(): List<Build>
    @Deprecated(message = "use `since` with java.time.Instant",
                replaceWith = ReplaceWith("since(date.toInstant())"))
    fun sinceDate(date: Date) : BuildLocator
    @Deprecated(message = "use `until` with java.time.Instant",
                replaceWith = ReplaceWith("until(date.toInstant())"))
    fun untilDate(date: Date) : BuildLocator
    @Deprecated(message = "use includeFailed()",
                replaceWith = ReplaceWith("includeFailed()"))
    fun withAnyStatus(): BuildLocator
}

interface InvestigationLocator {
    fun limitResults(count: Int): InvestigationLocator
    fun forProject(projectId: ProjectId): InvestigationLocator
    fun withTargetType(targetType: InvestigationTargetType): InvestigationLocator
    fun all(): Sequence<Investigation>
}

interface TestRunsLocator {
    fun limitResults(count: Int): TestRunsLocator
    fun pageSize(pageSize: Int): TestRunsLocator
    fun forBuild(buildId: BuildId): TestRunsLocator
    fun forTest(testId: TestId): TestRunsLocator
    fun forProject(projectId: ProjectId): TestRunsLocator
    fun withStatus(testStatus: TestStatus): TestRunsLocator
    fun all(): Sequence<TestRun>
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

data class BuildAgentPoolId(val stringId: String) {
    override fun toString(): String = stringId
}

data class BuildAgentId(val stringId: String) {
    override fun toString(): String = stringId
}

data class InvestigationId(val stringId: String) {
    override fun toString(): String = stringId
}

data class BuildProblemType(val stringType: String) {
    override fun toString(): String = stringType

    val isSnapshotDependencyError: Boolean
        get() = stringType == "SNAPSHOT_DEPENDENCY_ERROR_BUILD_PROCEEDS_TYPE" ||
                stringType == "SNAPSHOT_DEPENDENCY_ERROR"

    companion object {
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

    val childProjects: List<Project>
    val buildConfigurations: List<BuildConfiguration>
    val parameters: List<Parameter>

    fun setParameter(name: String, value: String)

    /**
     * See properties example from existing VCS roots via inspection of the following url:
     * https://teamcity/app/rest/vcs-roots/id:YourVcsRootId
     */
    fun createVcsRoot(id: VcsRootId, name: String, type: VcsRootType, properties: Map<String, String>): VcsRoot

    fun createProject(id: ProjectId, name: String): Project

    /**
     * XML in the same format as
     * https://teamcity/app/rest/buildTypes/YourBuildConfigurationId
     * returns
     */
    fun createBuildConfiguration(buildConfigurationDescriptionXml: String): BuildConfiguration

    @Deprecated(message = "use getHomeUrl(branch)",
                replaceWith = ReplaceWith("getHomeUrl(branch"))
    fun getWebUrl(branch: String? = null): String

    @Deprecated(message = "use childProjects",
                replaceWith = ReplaceWith("childProjects"))
    fun fetchChildProjects(): List<Project>
    @Deprecated(message = "use buildConfigurations",
            replaceWith = ReplaceWith("buildConfigurations"))
    fun fetchBuildConfigurations(): List<BuildConfiguration>
    @Deprecated(message = "use parameters",
            replaceWith = ReplaceWith("parameters"))
    fun fetchParameters(): List<Parameter>
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

    var buildCounter: Int
    var buildNumberFormat: String

    fun runBuild(parameters: Map<String, String>? = null,
                 queueAtTop: Boolean = false,
                 cleanSources: Boolean? = null,
                 rebuildAllDependencies: Boolean = false,
                 comment: String? = null,
                 logicalBranchName: String? = null,
                 personal: Boolean = false): Build

    @Deprecated(message = "use getHomeUrl(branch)",
                replaceWith = ReplaceWith("getHomeUrl(branch)"))
    fun getWebUrl(branch: String? = null): String
    @Deprecated(message = "use buildTags",
                replaceWith = ReplaceWith("buildTags"))
    fun fetchBuildTags(): List<String>
    @Deprecated(message = "use finishBuildTriggers",
                replaceWith = ReplaceWith("finishBuildTriggers"))
    fun fetchFinishBuildTriggers(): List<FinishBuildTrigger>
    @Deprecated(message = "use artifactDependencies",
                replaceWith = ReplaceWith("artifactDependencies"))
    fun fetchArtifactDependencies(): List<ArtifactDependency>
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

interface BuildCommentInfo {
    val user: User?
    val timestamp: ZonedDateTime
    val text: String
}

interface BuildAgentEnabledInfo {
    val user: User?
    val timestamp: ZonedDateTime
    val text: String
}

interface BuildAgentAuthorizedInfo {
    val user: User?
    val timestamp: ZonedDateTime
    val text: String
}

interface BuildCanceledInfo {
    val user: User?
    val cancelDateTime: ZonedDateTime

    @Deprecated(message = "use cancelDateTime", replaceWith = ReplaceWith("Date.from(cancelDateTime.toInstant())"))
    val cancelDate: Date
}

interface Build {
    val id: BuildId
    val buildConfigurationId: BuildConfigurationId
    val buildNumber: String?
    val status: BuildStatus?
    val branch: Branch
    val state: BuildState
    val name: String
    val canceledInfo: BuildCanceledInfo?
    val comment: BuildCommentInfo?

    val composite: Boolean?
    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(): String

    val statusText: String?
    val queuedDateTime: ZonedDateTime
    val startDateTime: ZonedDateTime?
    val finishDateTime: ZonedDateTime?

    val runningInfo: BuildRunningInfo?

    val parameters: List<Parameter>

    val tags: List<String>

    /**
     * The same as revisions table on the build's Changes tab in TeamCity UI:
     * it lists the revisions of all of the VCS repositories associated with this build
     * that will be checked out by the build on the agent.
     */
    val revisions: List<Revision>

    /**
     * Changes is meant to represent changes the same way as displayed in the build's Changes in TeamCity UI.
     * In the most cases these are the commits between the current and previous build.
     */
    val changes: List<Change>

    /**
     * All snapshot-dependency-linked builds this build depends on
     */
    val snapshotDependencies: List<Build>

    val pinInfo: PinInfo?

    val triggeredInfo: TriggeredInfo?

    val agent: BuildAgent?

    @Suppress("DEPRECATION")
    @Deprecated(message = "Deprecated due to unclear naming. use testRuns()", replaceWith = ReplaceWith("testRuns()"))
    fun tests(status: TestStatus? = null) : Sequence<TestOccurrence>

    fun testRuns(status: TestStatus? = null) : Sequence<TestRun>

    val buildProblems: Sequence<BuildProblemOccurrence>

    fun addTag(tag: String)
    fun replaceTags(tags: List<String>)
    fun pin(comment: String = "pinned via REST API")
    fun unpin(comment: String = "unpinned via REST API")
    fun getArtifacts(parentPath: String = "", recursive: Boolean = false, hidden: Boolean = false): List<BuildArtifact>
    fun findArtifact(pattern: String, parentPath: String = ""): BuildArtifact
    fun findArtifact(pattern: String, parentPath: String = "", recursive: Boolean = false): BuildArtifact
    fun downloadArtifacts(pattern: String, outputDir: File)
    fun downloadArtifact(artifactPath: String, output: OutputStream)
    fun downloadArtifact(artifactPath: String, output: File)
    fun openArtifactInputStream(artifactPath: String): InputStream
    fun downloadBuildLog(output: File)
    fun cancel(comment: String = "", reAddIntoQueue: Boolean = false)
    fun getResultingParameters(): List<Parameter>

    @Deprecated(message = "use getHomeUrl()", replaceWith = ReplaceWith("getHomeUrl()"))
    fun getWebUrl(): String
    @Deprecated(message = "use statusText", replaceWith = ReplaceWith("statusText"))
    fun fetchStatusText(): String?
    @Deprecated(message = "use queuedDateTime", replaceWith = ReplaceWith("Date.from(queuedDateTime.toInstant())"))
    fun fetchQueuedDate(): Date
    @Deprecated(message = "use startDateTime", replaceWith = ReplaceWith("startDateTime?.toInstant()?.let { Date.from(it) }"))
    fun fetchStartDate(): Date?
    @Deprecated(message = "use finishDateTime", replaceWith = ReplaceWith("finishDateTime?.toInstant()?.let { Date.from(it) }"))
    fun fetchFinishDate(): Date?
    @Deprecated(message = "use parameters", replaceWith = ReplaceWith("parameters"))
    fun fetchParameters(): List<Parameter>
    @Deprecated(message = "use revisions", replaceWith = ReplaceWith("revisions"))
    fun fetchRevisions(): List<Revision>
    @Deprecated(message = "use changes", replaceWith = ReplaceWith("changes"))
    fun fetchChanges(): List<Change>
    @Deprecated(message = "use pinInfo", replaceWith = ReplaceWith("pinInfo"))
    fun fetchPinInfo(): PinInfo?
    @Deprecated(message = "use triggeredInfo", replaceWith = ReplaceWith("triggeredInfo"))
    fun fetchTriggeredInfo(): TriggeredInfo?
    @Deprecated(message = "use buildConfigurationId", replaceWith = ReplaceWith("buildConfigurationId"))
    val buildTypeId: BuildConfigurationId
    @Deprecated(message = "use queuedDateTime", replaceWith = ReplaceWith("Date.from(queuedDateTime.toInstant())"))
    val queuedDate: Date
    @Deprecated(message = "use startDateTime", replaceWith = ReplaceWith("startDateTime?.toInstant()?.let { Date.from(it) }"))
    val startDate: Date?
    @Deprecated(message = "use finishDateTime", replaceWith = ReplaceWith("finishDateTime?.toInstant()?.let { Date.from(it) }"))
    val finishDate: Date?
}

interface Investigation {
    val id: InvestigationId
    val state: InvestigationState
    val assignee: User
    val reporter: User?
    val comment: String
    val resolveMethod: InvestigationResolveMethod
    val targetType: InvestigationTargetType
    val testIds: List<TestId>?
    val problemIds: List<BuildProblemId>?
    val scope: InvestigationScope
}

interface BuildRunningInfo {
    val percentageComplete: Int
    val elapsedSeconds: Long
    val estimatedTotalSeconds: Long
    val outdated: Boolean
    val probablyHanging: Boolean
}

interface Change {
    val id: ChangeId
    val version: String
    val username: String
    val user: User?
    val dateTime: ZonedDateTime
    val comment: String

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(specificBuildConfigurationId: BuildConfigurationId? = null, includePersonalBuilds: Boolean? = null): String

    /**
     * Returns an uncertain amount of builds which contain the revision. The builds are not necessarily from the same
     * configuration as the revision. The feature is experimental, see https://youtrack.jetbrains.com/issue/TW-24633
     */
    fun firstBuilds(): List<Build>

    @Deprecated(message = "use getHomeUrl()",
                replaceWith = ReplaceWith("getHomeUrl(specificBuildConfigurationId, includePersonalBuilds)"))
    fun getWebUrl(specificBuildConfigurationId: BuildConfigurationId? = null, includePersonalBuilds: Boolean? = null): String
    @Deprecated(message = "use datetime",
            replaceWith = ReplaceWith("Date.from(datetime.toInstant())"))
    val date: Date
}

data class UserId(val stringId: String) {
    override fun toString(): String = stringId
}

interface User {
    val id: UserId
    val username: String
    val name: String?
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
    val modificationDateTime: ZonedDateTime

    val build: Build

    fun download(output: File)
    fun download(output: OutputStream)
    fun openArtifactInputStream(): InputStream

    @Deprecated(message = "use modificationDateTime",
            replaceWith = ReplaceWith("Date.from(modificationDateTime.toInstant())"))
    val modificationTime: Date
}

interface VcsRoot {
    val id: VcsRootId
    val name: String

    val url: String?
    val defaultBranch: String?
}

interface BuildAgent {
    val id: BuildAgentId
    val name: String
    val pool: BuildAgentPool

    val connected: Boolean
    val enabled: Boolean
    val authorized: Boolean
    val outdated: Boolean

    val ipAddress: String

    val parameters: List<Parameter>
    val enabledInfo: BuildAgentEnabledInfo?
    val authorizedInfo: BuildAgentAuthorizedInfo?

    val currentBuild: Build?

    fun getHomeUrl(): String
}

interface BuildAgentPool {
    val id: BuildAgentPoolId
    val name: String

    val projects: List<Project>
    val agents: List<BuildAgent>
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

enum class InvestigationState {
    TAKEN,
    FIXED,
    GIVEN_UP
}

enum class InvestigationResolveMethod {
    MANUALLY,
    WHEN_FIXED;
}

enum class InvestigationTargetType(val value: String) {
    TEST("test"),
    BUILD_PROBLEM("problem"),
    BUILD_CONFIGURATION("anyProblem")
}

interface PinInfo {
    val user: User
    val dateTime: ZonedDateTime

    @Deprecated(message = "use dateTime",
            replaceWith = ReplaceWith("Date.from(dateTime.toInstant())"))
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
    UNKNOWN
}

@Deprecated(message = "Deprecated due to unclear naming. use TestRun class", replaceWith = ReplaceWith("TestRun"))
interface TestOccurrence {
    val name : String
    val status: TestStatus

    /**
     * Test run duration. It may be ZERO if a test finished too fast (<1ms)
     */
    val duration: Duration

    val details : String
    val ignored: Boolean

    /**
     * Current 'muted' status of this test on TeamCity
     */
    val currentlyMuted: Boolean

    /**
     * Muted at the moment of running tests
     */
    val muted: Boolean

    /**
     * Newly failed test or not
     */
    val newFailure: Boolean

    val buildId: BuildId
    val testId: TestId
}

@Suppress("DEPRECATION")
interface TestRun : TestOccurrence

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

sealed class InvestigationScope {
    class InProject(val project: Project): InvestigationScope()
    class InBuildConfiguration(val configuration: BuildConfiguration): InvestigationScope()
}
