package org.jetbrains.teamcity.rest.coroutines

import kotlinx.coroutines.flow.Flow
import org.jetbrains.teamcity.rest.*
import org.jetbrains.teamcity.rest.BuildLocatorSettings.BuildField
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.ZonedDateTime

/**
 * Kotlin Coroutines-based REST client implementaiton.
 *
 * Please note that repeated getter call on entities will return the same value because the result is cached:
 * ```kotlin
 * val tc = TeamCityInstanceBuilder("https://myserver.local").withGuestAuth().build()
 * val build = tc.builds().latest()!!
 *
 * val first = build.getState()  // suspending call
 * delay(1000000L)
 * val second = build.getState() // fast call; won't suspend, returns cached value
 * assert(first == second)       // the check will always pass, even if the build state has changed
 * ```
 */
interface TeamCityCoroutinesInstance : AutoCloseable, TeamCityInstanceSettings<TeamCityCoroutinesInstance> {
    fun builds(): BuildLocator
    fun investigations(): InvestigationLocator
    suspend fun createInvestigations(investigations: Collection<Investigation>)
    suspend fun deleteInvestigation(investigationId: InvestigationId)

    fun mutes(): MuteLocator
    suspend fun createMutes(mutes: List<Mute>)

    fun tests(): TestLocator
    suspend fun build(id: BuildId): Build
    suspend fun build(id: BuildId, prefetchFields: Set<BuildField> = emptySet()): Build
    suspend fun build(buildConfigurationId: BuildConfigurationId, number: String): Build?
    suspend fun buildConfiguration(id: BuildConfigurationId): BuildConfiguration
    fun vcsRoots(): VcsRootLocator
    suspend fun vcsRoot(id: VcsRootId): VcsRoot
    suspend fun project(id: ProjectId): Project
    suspend fun rootProject(): Project
    fun buildQueue(): BuildQueue
    suspend fun user(id: UserId): User
    suspend fun user(userName: String): User
    fun users(): UserLocator
    suspend fun buildAgent(id: BuildAgentId): BuildAgent
    suspend fun buildAgent(typeId: BuildAgentTypeId): BuildAgent
    fun buildAgents(): BuildAgentLocator
    fun buildAgentPools(): BuildAgentPoolLocator
    fun testRuns(): TestRunsLocator

    suspend fun change(buildConfigurationId: BuildConfigurationId, vcsRevision: String): Change
    suspend fun change(id: ChangeId): Change
    suspend fun test(testId: TestId): Test
}

interface VcsRootLocator : VcsRootLocatorSettings<VcsRootLocator> {
    fun all(): Flow<VcsRoot>
}

interface UserLocator : UserLocatorSettings<UserLocator> {
    fun all(): Flow<User>
}

interface BuildAgentLocator : BuildAgentLocatorSettings<BuildAgentLocator> {
    fun all(): Flow<BuildAgent>
}

interface BuildAgentPoolLocator {
    fun all(): Flow<BuildAgentPool>
}

interface BuildLocator : BuildLocatorSettings<BuildLocator> {
    suspend fun latest(): Build?
    fun all(): Flow<Build>
}

interface InvestigationLocator : InvestigationLocatorSettings<InvestigationLocator> {
    fun all(): Flow<Investigation>
}

interface MuteLocator : MuteLocatorSettings<MuteLocator> {
    fun all(): Flow<Mute>
}

interface TestLocator : TestLocatorSettings<TestLocator> {
    fun all(): Flow<Test>
}

interface TestRunsLocator : TestRunsLocatorSettings<TestRunsLocator> {
    fun all(): Flow<TestRun>
}

interface Project {
    val id: ProjectId
    suspend fun getName(): String
    suspend fun isArchived(): Boolean
    suspend fun getParentProjectId(): ProjectId?

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(branch: String? = null): String
    fun getTestHomeUrl(testId: TestId): String

    suspend fun getChildProjects(): List<Project>
    suspend fun getBuildConfigurations(): List<BuildConfiguration>
    suspend fun getParameters(): List<Parameter>

    suspend fun setParameter(name: String, value: String)
    suspend fun removeParameter(name: String)

    /**
     * See properties example from existing VCS roots via inspection of the following url:
     * https://teamcity/app/rest/vcs-roots/id:YourVcsRootId
     */
    suspend fun createVcsRoot(id: VcsRootId, name: String, type: VcsRootType, properties: Map<String, String>): VcsRoot

    suspend fun createProject(id: ProjectId, name: String): Project

    /**
     * XML in the same format as
     * https://teamcity/app/rest/buildTypes/YourBuildConfigurationId
     * returns
     */
    suspend fun createBuildConfiguration(buildConfigurationDescriptionXml: String): BuildConfiguration

    fun getMutes(): Flow<Mute>
    suspend fun assignToAgentPool(agentPoolId: BuildAgentPoolId)
}

interface BuildConfiguration {
    val id: BuildConfigurationId
    suspend fun getName(): String
    suspend fun getProjectId(): ProjectId
    suspend fun getProjectName(): String
    suspend fun isPaused(): Boolean
    suspend fun getType(): BuildConfigurationType

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(branch: String? = null): String

    suspend fun getBuildTags(): List<String>
    suspend fun getFinishBuildTriggers(): List<FinishBuildTrigger>
    suspend fun getArtifactDependencies(): List<ArtifactDependency>
    suspend fun getSnapshotDependencies(): List<SnapshotDependency>

    suspend fun getParameters(): List<Parameter>
    suspend fun setParameter(name: String, value: String)
    suspend fun removeParameter(name: String)

    suspend fun getBuildCounter(): Int
    suspend fun setBuildCounter(value: Int)

    suspend fun getBuildNumberFormat(): String
    suspend fun setBuildNumberFormat(format: String)

    suspend fun runBuild(
        parameters: Map<String, String>? = null,
        queueAtTop: Boolean = false,
        cleanSources: Boolean? = null,
        rebuildAllDependencies: Boolean = false,
        comment: String? = null,
        logicalBranchName: String? = null,
        agentId: String? = null,
        personal: Boolean = false,
        revisions: List<SpecifiedRevision>? = null,
        dependencies: List<BuildId>? = null
    ): Build
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
    val muted: Boolean
    val currentlyMuted: Boolean
    val currentlyInvestigated: Boolean
}

interface Parameter {
    val name: String
    val value: String?
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
    val text: String
}

interface Build {
    val id: BuildId
    suspend fun getBuildConfigurationId(): BuildConfigurationId
    suspend fun getProjectId(): ProjectId
    suspend fun getBuildNumber(): String?
    suspend fun getStatus(): BuildStatus?
    suspend fun getBranch(): Branch
    suspend fun getState(): BuildState
    suspend fun isPersonal(): Boolean
    suspend fun getName(): String
    suspend fun getProjectName(): String
    suspend fun getCanceledInfo(): BuildCanceledInfo?
    suspend fun getComment(): BuildCommentInfo?

    suspend fun isComposite(): Boolean?

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(): String

    suspend fun getStatusText(): String?
    suspend fun getQueuedDateTime(): ZonedDateTime?
    suspend fun getStartDateTime(): ZonedDateTime?
    suspend fun getFinishDateTime(): ZonedDateTime?

    suspend fun getRunningInfo(): BuildRunningInfo?

    suspend fun getParameters(): List<Parameter>

    suspend fun getTags(): List<String>

    /**
     * The same as revisions table on the build's Changes tab in TeamCity UI:
     * it lists the revisions of all of the VCS repositories associated with this build
     * that will be checked out by the build on the agent.
     */
    suspend fun getRevisions(): List<Revision>

    /**
     * Changes is meant to represent changes the same way as displayed in the build's Changes in TeamCity UI.
     * In the most cases these are the commits between the current and previous build.
     */
    suspend fun getChanges(): List<Change>

    /**
     * All snapshot-dependency-linked builds this build depends on
     */
    suspend fun getSnapshotDependencies(): List<Build>

    suspend fun getPinInfo(): PinInfo?

    suspend fun getTriggeredInfo(): TriggeredInfo?

    suspend fun getAgent(): BuildAgent?

    /**
     * Both `getAgentName` and `getAgentTypeId` may be not-null while `getAgent` is null
     * in case of already removed cloud agent instance
     */
    suspend fun getAgentName(): String?
    suspend fun getAgentTypeId(): String?

    suspend fun isDetachedFromAgent(): Boolean

    suspend fun isFailedToStart(): Boolean

    fun getTestRuns(status: TestStatus? = null): Flow<TestRun>

    fun getBuildProblems(): Flow<BuildProblemOccurrence>

    suspend fun isHistory(): Boolean

    suspend fun addTag(tag: String)
    suspend fun setComment(comment: String)
    suspend fun replaceTags(tags: List<String>)
    suspend fun pin(comment: String = "pinned via REST API")
    suspend fun unpin(comment: String = "unpinned via REST API")
    suspend fun getArtifacts(parentPath: String = "", recursive: Boolean = false, hidden: Boolean = false): List<BuildArtifact>
    suspend fun findArtifact(pattern: String, parentPath: String = ""): BuildArtifact
    suspend fun findArtifact(pattern: String, parentPath: String = "", recursive: Boolean = false): BuildArtifact
    suspend fun downloadArtifacts(pattern: String, outputDir: File)
    suspend fun downloadArtifact(artifactPath: String, output: OutputStream)
    suspend fun downloadArtifact(artifactPath: String, output: File)
    suspend fun openArtifactInputStream(artifactPath: String): InputStream
    suspend fun downloadBuildLog(output: File)
    suspend fun cancel(comment: String = "", reAddIntoQueue: Boolean = false)
    suspend fun getResultingParameters(): List<Parameter>
    suspend fun finish()
    suspend fun log(message: String)
    suspend fun getStatistics(): List<Property>
    suspend fun getQueuedWaitReasons(): List<Property>
    fun testRunsLocator(status: TestStatus?): TestRunsLocator
    suspend fun markAsSuccessful(comment: String)
    suspend fun markAsFailed(comment: String)
}

interface Issue {
    val resolutionTime: ZonedDateTime?
    val comment: String
    val resolveMethod: InvestigationResolveMethod
    val targetType: InvestigationTargetType
    val testIds: List<TestId>?
    val problemIds: List<BuildProblemId>?
    val scope: InvestigationScope
    val reporter: UserId?
    val reportedAt: ZonedDateTime?
}

interface Investigation : Issue {
    val id: InvestigationId
    val assignee: UserId
    val state: InvestigationState
}

interface Mute : Issue {
    val id: InvestigationId
    val assignee: UserId?
}

interface Test {
    val id: TestId
    suspend fun getName(): String
    suspend fun getParsedNamePackage(): String
    suspend fun getParsedNameSuite(): String
    suspend fun getParsedNameClass(): String
    suspend fun getParsedShortName(): String
    suspend fun getParsedNameWithoutPrefix(): String
    suspend fun getParsedMethodName(): String
    suspend fun getParsedNameWithParameters(): String
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
    suspend fun getVersion(): String
    suspend fun getUsername(): String
    suspend fun getUser(): User?
    suspend fun getDateTime(): ZonedDateTime
    suspend fun getComment(): String
    suspend fun getVcsRootInstance(): VcsRootInstance?
    suspend fun getFiles(): List<ChangeFile>

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(
        specificBuildConfigurationId: BuildConfigurationId? = null,
        includePersonalBuilds: Boolean? = null
    ): String

    /**
     * Returns an uncertain amount of builds which contain the revision. The builds are not necessarily from the same
     * configuration as the revision. The feature is experimental, see https://youtrack.jetbrains.com/issue/TW-24633
     */
    suspend fun firstBuilds(): List<Build>
    suspend fun getRegistrationDate(): ZonedDateTime
}

interface ChangeFile {
    val fileRevisionBeforeChange: String?
    val fileRevisionAfterChange: String?
    val changeType: ChangeType

    /**
     * Full file path, may include VCS URL
     */
    val filePath: String?

    /**
     * File path relative to VCS root directory.
     */
    val relativeFilePath: String?
}

interface User {
    val id: UserId
    suspend fun getUsername(): String
    suspend fun getName(): String?
    suspend fun getEmail(): String?

    suspend fun getRoles(): List<AssignedRole>
    suspend fun addRole(roleId: RoleId, roleScope: RoleScope)
    suspend fun deleteRole(roleId: RoleId, roleScope: RoleScope)

    /**
     * Web UI URL for user, especially useful for error and log messages
     */
    fun getHomeUrl(): String
}

interface AssignedRole {
    val id: RoleId
    val scope: RoleScope
}

interface BuildArtifact {
    /** Artifact name without path. e.g. my.jar */
    val name: String
    /** Artifact name with path. e.g. directory/my.jar */
    val fullName: String
    val size: Long?
    val modificationDateTime: ZonedDateTime

    val build: Build

    suspend fun download(output: File)
    suspend fun download(output: OutputStream)
    suspend fun openArtifactInputStream(): InputStream
}

interface VcsRoot {
    val id: VcsRootId
    suspend fun getName(): String

    suspend fun getUrl(): String?
    suspend fun getDefaultBranch(): String?
}

interface BuildAgent {
    val id: BuildAgentId
    suspend fun getTypeId(): BuildAgentTypeId
    suspend fun getName(): String
    suspend fun getPool(): BuildAgentPool
    suspend fun isConnected(): Boolean
    suspend fun isEnabled(): Boolean
    suspend fun isAuthorized(): Boolean
    suspend fun isOutdated(): Boolean
    suspend fun getIpAddress(): String
    suspend fun getParameters(): List<Parameter>
    suspend fun getEnabledInfo(): BuildAgentEnabledInfo?
    suspend fun getAuthorizedInfo(): BuildAgentAuthorizedInfo?
    suspend fun getCurrentBuild(): Build?
    fun getHomeUrl(): String

    suspend fun getCompatibleBuildConfigurations(): CompatibleBuildConfigurations
    suspend fun setCompatibleBuildConfigurations(value: CompatibleBuildConfigurations)
}

interface BuildAgentPool {
    val id: BuildAgentPoolId
    suspend fun getName(): String

    suspend fun getProjects(): List<Project>
    suspend fun getAgents(): List<BuildAgent>
}

interface VcsRootInstance {
    val vcsRootId: VcsRootId
    val name: String
}

interface PinInfo {
    val user: User?
    val dateTime: ZonedDateTime
    val text: String?
}

interface Revision {
    val version: String
    val vcsBranchName: String?
    val vcsRootInstance: VcsRootInstance
}

interface TestRun {
    val testOccurrenceId: TestOccurrenceId
    suspend fun getName(): String
    suspend fun getStatus(): TestStatus

    /**
     * Test run duration. It may be ZERO if a test finished too fast (<1ms)
     */
    suspend fun getDuration(): Duration

    suspend fun getDetails(): String
    suspend fun isIgnored(): Boolean

    /**
     * Current 'muted' status of this test on TeamCity
     */
    suspend fun isCurrentlyMuted(): Boolean

    /**
     * Muted at the moment of running tests
     */
    suspend fun isMutedAtRunningTime(): Boolean

    /**
     * Newly failed test or not
     */
    suspend fun isNewFailure(): Boolean

    suspend fun getBuildId(): BuildId
    suspend fun getFixedIn(): BuildId?
    suspend fun getFirstFailedIn(): BuildId?
    suspend fun getTestId(): TestId
    suspend fun getMetadataValues(): List<String>?
    suspend fun getLogAnchor(): String
    suspend fun getTest(): Test
}

interface TriggeredInfo {
    val user: User?
    val build: Build?
    val type: String
}

interface FinishBuildTrigger {
    val initiatedBuildConfiguration: BuildConfigurationId
    val afterSuccessfulBuildOnly: Boolean
    val includedBranchPatterns: Set<String>
    val excludedBranchPatterns: Set<String>
}

interface SnapshotDependency {
    val id: BuildConfigurationId
    suspend fun getBuildConfiguration(): BuildConfiguration
}

interface ArtifactDependency {
    val id: ArtifactDependencyId
    suspend fun getDependsOnBuildConfiguration(): BuildConfiguration
    suspend fun getBranch(): String?
    suspend fun getArtifactRules(): List<ArtifactRule>
    suspend fun isCleanDestinationDirectory(): Boolean
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
    suspend fun removeBuild(id: BuildId, comment: String = "", reAddIntoQueue: Boolean = false)

    /**
     * Use [prefetchFields] to manually select Build fields to prefetch.
     * By default [BuildField.essentialFields] are fetched.
     */
    fun queuedBuilds(
        projectId: ProjectId? = null,
        prefetchFields: Set<BuildField> = BuildField.essentialFields
    ): Flow<Build>

    /**
     * Use [prefetchFields] to manually select Build fields to prefetch.
     * By default [BuildField.essentialFields] are fetched.
     */
    fun queuedBuilds(
        buildConfigurationId: BuildConfigurationId,
        prefetchFields: Set<BuildField> = BuildField.essentialFields
    ): Flow<Build>
}

sealed class InvestigationScope {
    class InProject(val projectId: ProjectId): InvestigationScope()
    class InBuildConfiguration(val configurationId: BuildConfigurationId): InvestigationScope()
    class InBuildConfigurations(val configurationIds: List<BuildConfigurationId>): InvestigationScope()
}

interface CompatibleBuildConfigurations {
    val buildConfigurationIds: List<BuildConfigurationId>
    val policy: CompatibleBuildConfigurationsPolicy
}
