package org.jetbrains.teamcity.rest

import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.jetbrains.teamcity.rest.coroutines.*
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import java.util.concurrent.TimeUnit

private fun <T> lazyBlocking(block: suspend () -> T): Lazy<T> = lazy { runBlocking { block() } }

internal class TeamCityInstanceBlockingBridge(
    private val delegate: TeamCityCoroutinesInstanceEx
) : TeamCityInstance() {
    override val serverUrl: String by lazy { delegate.serverUrl }

    @TestOnly
    internal fun toBuilder() = delegate.toBuilder()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun withLogResponses() = delegate.toBuilder()
        .setResponsesLoggingEnabled(true)
        .buildBlockingInstance()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun withTimeout(timeout: Long, unit: TimeUnit) = delegate.toBuilder()
        .withTimeout(timeout, unit)
        .buildBlockingInstance()

    override fun builds(): BuildLocator {
        return BuildLocatorBridge(delegate.builds() as BuildLocatorEx)
    }

    override fun investigations(): InvestigationLocator =
        InvestigationLocatorBridge(delegate.investigations() as InvestigationLocatorEx)

    override fun mutes(): MuteLocator = MuteLocatorBridge(delegate.mutes() as MuteLocatorEx)

    override fun tests(): TestLocator = TestLocatorBridge(delegate.tests() as TestLocatorEx)

    override fun build(id: BuildId): Build = runBlocking {
        BuildBridge(delegate.build(id))
    }

    override fun build(buildConfigurationId: BuildConfigurationId, number: String): Build? = runBlocking {
        delegate.build(buildConfigurationId, number)?.let(::BuildBridge)
    }

    override fun buildConfiguration(id: BuildConfigurationId): BuildConfiguration =
        BuildConfigurationBridge(runBlocking { delegate.buildConfiguration(id) })

    override fun vcsRoots(): VcsRootLocator = VcsRootLocatorBridge(delegate.vcsRoots() as VcsRootLocatorEx)

    override fun vcsRoot(id: VcsRootId): VcsRoot = VcsRootBridge(runBlocking { delegate.vcsRoot(id) })

    override fun project(id: ProjectId): Project = ProjectBridge(runBlocking { delegate.project(id) })

    override fun rootProject(): Project = ProjectBridge(runBlocking { delegate.rootProject() })

    override fun buildQueue(): BuildQueue = BuildQueueBridge(delegate.buildQueue() as BuildQueueEx)

    override fun user(id: UserId): User = UserBridge(runBlocking { delegate.user(id) })

    override fun user(userName: String): User = UserBridge(runBlocking { delegate.user(userName) })

    override fun users(): UserLocator = UserLocatorBridge(this, delegate.users() as UserLocatorEx)

    override fun buildAgents(): BuildAgentLocator = BuildAgentLocatorBridge(delegate.buildAgents() as BuildAgentLocatorEx)

    override fun buildAgentPools(): BuildAgentPoolLocator = BuildAgentPoolLocatorBridge(delegate.buildAgentPools() as BuildAgentPoolLocatorEx)

    override fun testRuns(): TestRunsLocator = TestRunsLocatorBridge(delegate.testRuns() as TestRunsLocatorEx)

    override fun change(buildConfigurationId: BuildConfigurationId, vcsRevision: String): Change =
        ChangeBridge(runBlocking { delegate.change(buildConfigurationId, vcsRevision) })

    override fun change(id: ChangeId): Change = ChangeBridge(runBlocking { delegate.change(id) })

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getWebUrl(projectId: ProjectId, branch: String?): String =
        runBlocking { delegate.project(projectId) }.getHomeUrl(branch)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getWebUrl(buildConfigurationId: BuildConfigurationId, branch: String?): String =
        runBlocking { delegate.buildConfiguration(buildConfigurationId) }.getHomeUrl(branch)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getWebUrl(buildId: BuildId): String = runBlocking { delegate.build(buildId) }.getHomeUrl()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getWebUrl(
        changeId: ChangeId,
        specificBuildConfigurationId: BuildConfigurationId?,
        includePersonalBuilds: Boolean?
    ) = runBlocking { delegate.change(changeId) }.getHomeUrl(specificBuildConfigurationId, includePersonalBuilds)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun queuedBuilds(projectId: ProjectId?): List<Build> = buildQueue().queuedBuilds(projectId).toList()

    override fun close() = delegate.close()
}

private class BuildQueueBridge(
    private val delegate: BuildQueueEx
) : BuildQueue {
    override fun removeBuild(id: BuildId, comment: String, reAddIntoQueue: Boolean) = runBlocking {
        delegate.removeBuild(id, comment, reAddIntoQueue)
    }

    override fun queuedBuilds(projectId: ProjectId?): Sequence<Build> =
        delegate.queuedBuildsSeq(projectId).map(::BuildBridge)

    override fun queuedBuilds(buildConfigurationId: BuildConfigurationId): Sequence<Build> =
        delegate.queuedBuildsSeq(buildConfigurationId).map(::BuildBridge)
}

private class VcsRootLocatorBridge(
    private val delegate: VcsRootLocatorEx
) : VcsRootLocator {
    override fun all(): Sequence<VcsRoot> = delegate.allSeq().map(::VcsRootBridge)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun list(): List<VcsRoot> = all().toList()
}


private class UserLocatorBridge(
    private val instance: TeamCityInstanceBlockingBridge,
    private val delegate: UserLocatorEx
) : UserLocator {
    private var id: UserId? = null
    private var username: String? = null

    @Suppress("OVERRIDE_DEPRECATION")
    override fun withId(id: UserId): UserLocator {
        this.id = id
        return this
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun withUsername(name: String): UserLocator {
        this.username = name
        return this
    }

    override fun all(): Sequence<User> {
        val id = id
        val username = username
        require(id == null || username == null) { "UserLocator accepts only id or username, not both" }

        return when {
            id != null -> sequenceOf(instance.user(id))
            username != null -> sequenceOf(instance.user(username))
            else -> delegate.allSeq().map(::UserBridge)
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun list(): List<User> = all().toList()
}

private class BuildAgentLocatorBridge(
    private val delegate: BuildAgentLocatorEx
) : BuildAgentLocator {
    override fun all(): Sequence<BuildAgent> = delegate.allSeq().map(::BuildAgentBridge)

    override fun compatibleWith(buildConfigurationId: BuildConfigurationId): BuildAgentLocator {
        delegate.compatibleWith(buildConfigurationId)
        return this
    }
}

private class BuildAgentPoolLocatorBridge(
    private val delegate: BuildAgentPoolLocatorEx
) : BuildAgentPoolLocator {
    override fun all(): Sequence<BuildAgentPool> =
        delegate.allSeq().map(::BuildAgentPoolBridge)
}

private class TestRunsLocatorBridge(
    private val delegate: TestRunsLocatorEx
) : TestRunsLocator {
    override fun all(): Sequence<TestRun> = delegate.allSeq().map(::TestRunBridge)

    override fun limitResults(count: Int): TestRunsLocator {
        delegate.limitResults(count)
        return this
    }

    override fun pageSize(pageSize: Int): TestRunsLocator {
        delegate.pageSize(pageSize)
        return this
    }

    override fun forBuild(buildId: BuildId): TestRunsLocator {
        delegate.forBuild(buildId)
        return this
    }

    override fun forTest(testId: TestId): TestRunsLocator {
        delegate.forTest(testId)
        return this
    }

    override fun forProject(projectId: ProjectId): TestRunsLocator {
        delegate.forProject(projectId)
        return this
    }

    override fun withStatus(testStatus: TestStatus): TestRunsLocator {
        delegate.withStatus(testStatus)
        return this
    }

    override fun withoutDetailsField(): TestRunsLocator {
        delegate.withoutDetailsField()
        return this
    }

    override fun expandMultipleInvocations(): TestRunsLocator {
        delegate.expandMultipleInvocations()
        return this
    }
}

private class BuildLocatorBridge(
    private val delegate: BuildLocatorEx
) : BuildLocator {
    override fun latest(): Build? = runBlocking { delegate.latest()?.let { BuildBridge(it) } }

    override fun all(): Sequence<Build> = delegate.allSeq().map(::BuildBridge)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun list(): List<Build> = all().toList()

    override fun forProject(projectId: ProjectId): BuildLocator {
        delegate.forProject(projectId)
        return this
    }

    override fun fromConfiguration(buildConfigurationId: BuildConfigurationId): BuildLocator {
        delegate.fromConfiguration(buildConfigurationId)
        return this
    }

    override fun withNumber(buildNumber: String): BuildLocator {
        delegate.withNumber(buildNumber)
        return this
    }

    override fun withVcsRevision(vcsRevision: String): BuildLocator {
        delegate.withVcsRevision(vcsRevision)
        return this
    }

    override fun snapshotDependencyTo(buildId: BuildId): BuildLocator {
        delegate.snapshotDependencyTo(buildId)
        return this
    }

    override fun includeFailed(): BuildLocator {
        delegate.includeFailed()
        return this
    }

    override fun includeRunning(): BuildLocator {
        delegate.includeRunning()
        return this
    }

    override fun onlyRunning(): BuildLocator {
        delegate.onlyRunning()
        return this
    }

    override fun includeCanceled(): BuildLocator {
        delegate.includeCanceled()
        return this
    }

    override fun onlyCanceled(): BuildLocator {
        delegate.onlyCanceled()
        return this
    }

    override fun withStatus(status: BuildStatus): BuildLocator {
        delegate.withStatus(status)
        return this
    }

    override fun withTag(tag: String): BuildLocator {
        delegate.withTag(tag)
        return this
    }

    override fun withBranch(branch: String): BuildLocator {
        delegate.withBranch(branch)
        return this
    }

    override fun withAllBranches(): BuildLocator {
        delegate.withAllBranches()
        return this
    }

    override fun pinnedOnly(): BuildLocator {
        delegate.pinnedOnly()
        return this
    }

    override fun includePersonal(): BuildLocator {
        delegate.includePersonal()
        return this
    }

    override fun onlyPersonal(): BuildLocator {
        delegate.onlyPersonal()
        return this
    }

    override fun limitResults(count: Int): BuildLocator {
        delegate.limitResults(count)
        return this
    }

    override fun pageSize(pageSize: Int): BuildLocator {
        delegate.pageSize(pageSize)
        return this
    }

    override fun since(date: Instant): BuildLocator {
        delegate.since(date)
        return this
    }

    override fun until(date: Instant): BuildLocator {
        delegate.until(date)
        return this
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun sinceDate(date: Date): BuildLocator {
        delegate.since(date.toInstant())
        return this
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun untilDate(date: Date): BuildLocator {
        delegate.until(date.toInstant())
        return this
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun withAnyStatus(): BuildLocator {
        delegate.includeFailed()
        return this
    }
}

private class InvestigationLocatorBridge(
    private val delegate: InvestigationLocatorEx
) : InvestigationLocator {
    override fun all(): Sequence<Investigation> =
        delegate.allSeq().map(::InvestigationBridge)

    override fun limitResults(count: Int): InvestigationLocator {
        delegate.limitResults(count)
        return this
    }

    override fun forProject(projectId: ProjectId): InvestigationLocator {
        delegate.forProject(projectId)
        return this
    }

    override fun withTargetType(targetType: InvestigationTargetType): InvestigationLocator {
        delegate.withTargetType(targetType)
        return this
    }
}

private class MuteLocatorBridge(
    private val delegate: MuteLocatorEx
) : MuteLocator {
    override fun all(): Sequence<Mute> = delegate.allSeq().map(::MuteBridge)

    override fun limitResults(count: Int): MuteLocator {
        delegate.limitResults(count)
        return this
    }

    override fun forProject(projectId: ProjectId): MuteLocator {
        delegate.forProject(projectId)
        return this
    }

    override fun byUser(userId: UserId): MuteLocator {
        delegate.byUser(userId)
        return this
    }

    override fun forTest(testId: TestId): MuteLocator {
        delegate.forTest(testId)
        return this
    }
}

private class TestLocatorBridge(
    private val delegate: TestLocatorEx
) : TestLocator {
    override fun all(): Sequence<Test> = delegate.allSeq().map(::TestBridge)

    override fun limitResults(count: Int): TestLocator {
        delegate.limitResults(count)
        return this
    }

    override fun byId(testId: TestId): TestLocator {
        delegate.byId(testId)
        return this
    }

    override fun byName(testName: String): TestLocator {
        delegate.byName(testName)
        return this
    }

    override fun currentlyMuted(muted: Boolean): TestLocator {
        delegate.currentlyMuted(muted)
        return this
    }

    override fun forProject(projectId: ProjectId): TestLocator {
        delegate.forProject(projectId)
        return this
    }
}

private class ProjectBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.Project
) : Project {
    override val id: ProjectId by lazy { delegate.id }
    override val name: String by lazyBlocking { delegate.getName() }
    override val archived: Boolean by lazyBlocking { delegate.isArchived() }
    override val parentProjectId: ProjectId? by lazyBlocking { delegate.getParentProjectId() }
    override val childProjects: List<Project> by lazyBlocking { delegate.getChildProjects().map(::ProjectBridge) }
    override val parameters: List<Parameter> by lazyBlocking { delegate.getParameters().map(::ParameterBridge) }
    override val buildConfigurations: List<BuildConfiguration> by lazyBlocking {
        delegate.getBuildConfigurations().map(::BuildConfigurationBridge)
    }

    override fun getHomeUrl(branch: String?): String = delegate.getHomeUrl(branch)
    override fun getTestHomeUrl(testId: TestId): String = delegate.getTestHomeUrl(testId)
    override fun setParameter(name: String, value: String) = runBlocking { delegate.setParameter(name, value) }

    override fun createVcsRoot(
        id: VcsRootId,
        name: String,
        type: VcsRootType,
        properties: Map<String, String>
    ): VcsRoot = VcsRootBridge(runBlocking { delegate.createVcsRoot(id, name, type, properties) })

    override fun createProject(id: ProjectId, name: String): Project =
        ProjectBridge(runBlocking { delegate.createProject(id, name) })

    override fun createBuildConfiguration(buildConfigurationDescriptionXml: String): BuildConfiguration =
        BuildConfigurationBridge(runBlocking { delegate.createBuildConfiguration(buildConfigurationDescriptionXml) })

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getWebUrl(branch: String?): String = getHomeUrl(branch)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchChildProjects(): List<Project> = childProjects

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchBuildConfigurations(): List<BuildConfiguration> = buildConfigurations

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchParameters(): List<Parameter> = parameters
    override fun toString(): String = delegate.toString()
}

private class ParameterBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.Parameter,
) : Parameter {
    override val name: String by lazy { delegate.name }
    override val value: String by lazy { delegate.value }
    override val own: Boolean by lazy { delegate.own }
    override fun toString(): String = delegate.toString()
}

private class BuildConfigurationBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildConfiguration
) : BuildConfiguration {
    override val id: BuildConfigurationId by lazy { delegate.id }
    override val name: String by lazyBlocking { delegate.getName() }
    override val projectId: ProjectId by lazyBlocking { delegate.getProjectId() }
    override val paused: Boolean by lazyBlocking { delegate.isPaused() }
    override val buildTags: List<String> by lazyBlocking { delegate.getBuildTags() }
    override val finishBuildTriggers: List<FinishBuildTrigger> by lazyBlocking {
        delegate.getFinishBuildTriggers().map(::FinishBuildTriggerBridge)
    }

    override val artifactDependencies: List<ArtifactDependency> by lazyBlocking {
        delegate.getArtifactDependencies().map(::ArtifactDependencyBridge)
    }

    override var buildCounter: Int
        get() = runBlocking { delegate.getBuildCounter() }
        set(value) = runBlocking { delegate.setBuildCounter(value) }

    override var buildNumberFormat: String
        get() = runBlocking { delegate.getBuildNumberFormat() }
        set(value) = runBlocking { delegate.setBuildNumberFormat(value) }

    override fun getHomeUrl(branch: String?): String = delegate.getHomeUrl(branch)

    override fun setParameter(name: String, value: String) = runBlocking { delegate.setParameter(name, value) }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun runBuild(
        parameters: Map<String, String>?,
        queueAtTop: Boolean,
        cleanSources: Boolean?,
        rebuildAllDependencies: Boolean,
        comment: String?,
        logicalBranchName: String?,
        personal: Boolean
    ): Build = runBuild(
        parameters,
        queueAtTop,
        cleanSources,
        rebuildAllDependencies,
        comment,
        logicalBranchName,
        null,
        personal,
        null,
        null
    )

    @Suppress("OVERRIDE_DEPRECATION")
    override fun runBuild(
        parameters: Map<String, String>?,
        queueAtTop: Boolean,
        cleanSources: Boolean?,
        rebuildAllDependencies: Boolean,
        comment: String?,
        logicalBranchName: String?,
        agentId: String?,
        personal: Boolean
    ): Build = runBuild(
        parameters,
        queueAtTop,
        cleanSources,
        rebuildAllDependencies,
        comment,
        logicalBranchName,
        agentId,
        personal,
        null,
        null
    )

    override fun runBuild(
        parameters: Map<String, String>?,
        queueAtTop: Boolean,
        cleanSources: Boolean?,
        rebuildAllDependencies: Boolean,
        comment: String?,
        logicalBranchName: String?,
        agentId: String?,
        personal: Boolean,
        revisions: List<SpecifiedRevision>?,
        dependencies: List<BuildId>?
    ): Build = BuildBridge(runBlocking {
        delegate.runBuild(
            parameters,
            queueAtTop,
            cleanSources,
            rebuildAllDependencies,
            comment,
            logicalBranchName,
            agentId,
            personal,
            revisions,
            dependencies
        )
    })

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getWebUrl(branch: String?): String = getHomeUrl(branch)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchBuildTags(): List<String> = buildTags

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchFinishBuildTriggers(): List<FinishBuildTrigger> = finishBuildTriggers

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchArtifactDependencies(): List<ArtifactDependency> = artifactDependencies

    override fun toString(): String = delegate.toString()
}

private class FinishBuildTriggerBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.FinishBuildTrigger
) : FinishBuildTrigger {
    override val initiatedBuildConfiguration: BuildConfigurationId = delegate.initiatedBuildConfiguration
    override val afterSuccessfulBuildOnly: Boolean = delegate.afterSuccessfulBuildOnly
    override val includedBranchPatterns: Set<String> = delegate.includedBranchPatterns
    override val excludedBranchPatterns: Set<String> = delegate.excludedBranchPatterns
    override fun toString(): String = delegate.toString()
}

private class ArtifactDependencyBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.ArtifactDependency
) : ArtifactDependency {
    override val dependsOnBuildConfiguration: BuildConfiguration =
        BuildConfigurationBridge(delegate.dependsOnBuildConfiguration)

    override val branch: String? by lazyBlocking { delegate.getBranch() }
    override val artifactRules: List<ArtifactRule> by lazyBlocking {
        delegate.getArtifactRules().map(::ArtifactRuleBridge)
    }
    override val cleanDestinationDirectory: Boolean by lazyBlocking { delegate.isCleanDestinationDirectory() }
    override fun toString(): String = delegate.toString()
}

private class ArtifactRuleBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.ArtifactRule
) : ArtifactRule {
    override val include: Boolean = delegate.include
    override val sourcePath: String = delegate.sourcePath
    override val archivePath: String? = delegate.archivePath
    override val destinationPath: String? = delegate.destinationPath
    override fun toString(): String = delegate.toString()
}

private class UserBridge(private val delegate: org.jetbrains.teamcity.rest.coroutines.User) : User {
    override val id: UserId by lazy { delegate.id }
    override val username: String by lazyBlocking { delegate.getUsername() }
    override val name: String? by lazyBlocking { delegate.getName() }
    override val email: String? by lazyBlocking { delegate.getEmail() }

    override fun getHomeUrl(): String = delegate.getHomeUrl()
    override fun toString(): String = delegate.toString()
}


private class BuildBridge(private val delegate: org.jetbrains.teamcity.rest.coroutines.Build) : Build {
    override val id: BuildId by lazy { delegate.id }
    override val buildConfigurationId: BuildConfigurationId by lazyBlocking { delegate.getBuildConfigurationId() }
    override val buildNumber: String? by lazyBlocking { delegate.getBuildNumber() }
    override val status: BuildStatus? by lazyBlocking { delegate.getStatus() }
    override val branch: Branch by lazyBlocking { BranchBridge(delegate.getBranch()) }
    override val state: BuildState by lazyBlocking { delegate.getState() }
    override val personal: Boolean by lazyBlocking { delegate.isPersonal() }
    override val name: String by lazyBlocking { delegate.getName() }
    override val canceledInfo: BuildCanceledInfo? by lazyBlocking {
        delegate.getCanceledInfo()?.let(::BuildCanceledInfoBridge)
    }
    override val comment: BuildCommentInfo? by lazyBlocking { delegate.getComment()?.let(::BuildCommentInfoBridge) }
    override val composite: Boolean? by lazyBlocking { delegate.isComposite() }
    override val statusText: String? by lazyBlocking { delegate.getStatusText() }
    override val queuedDateTime: ZonedDateTime by lazyBlocking { delegate.getQueuedDateTime() }
    override val startDateTime: ZonedDateTime? by lazyBlocking { delegate.getStartDateTime() }
    override val finishDateTime: ZonedDateTime? by lazyBlocking { delegate.getFinishDateTime() }
    override val runningInfo: BuildRunningInfo? by lazyBlocking {
        delegate.getRunningInfo()?.let(::BuildRunningInfoBridge)
    }
    override val parameters: List<Parameter> by lazyBlocking { delegate.getParameters().map(::ParameterBridge) }
    override val tags: List<String> by lazyBlocking { delegate.getTags() }
    override val revisions: List<Revision> by lazyBlocking { delegate.getRevisions().map(::RevisionBridge) }
    override val changes: List<Change> by lazyBlocking { delegate.getChanges().map(::ChangeBridge) }
    override val snapshotDependencies: List<Build> by lazyBlocking {
        delegate.getSnapshotDependencies().map(::BuildBridge)
    }
    override val pinInfo: PinInfo? by lazyBlocking { delegate.getPinInfo()?.let(::PinInfoBridge) }
    override val triggeredInfo: TriggeredInfo? by lazyBlocking {
        delegate.getTriggeredInfo()?.let(::TriggeredInfoBridge)
    }
    override val agent: BuildAgent? by lazyBlocking { delegate.getAgent()?.let(::BuildAgentBridge) }
    override val detachedFromAgent: Boolean by lazyBlocking { delegate.isDetachedFromAgent() }
    override val buildProblems: Sequence<BuildProblemOccurrence> by lazyBlocking {
        (delegate as BuildEx).getBuildProblemsSeq().map(::BuildProblemOccurrenceBridge)
    }

    override fun getHomeUrl(): String = delegate.getHomeUrl()


    @Suppress("OVERRIDE_DEPRECATION")
    override val buildTypeId: BuildConfigurationId by lazy { runBlocking { delegate.getBuildConfigurationId() } }

    @Suppress("OVERRIDE_DEPRECATION")
    override val queuedDate: Date by lazy { Date.from(queuedDateTime.toInstant()) }

    @Suppress("OVERRIDE_DEPRECATION")
    override val startDate: Date? by lazy { startDateTime?.let { Date.from(it.toInstant()) } }

    @Suppress("OVERRIDE_DEPRECATION")
    override val finishDate: Date? by lazy { finishDateTime?.let { Date.from(it.toInstant()) } }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun tests(status: TestStatus?): Sequence<TestOccurrence> = testRuns(status)

    override fun testRuns(status: TestStatus?): Sequence<TestRun> =
        (delegate as BuildEx).getTestRunsSeq(status).map(::TestRunBridge)

    override fun addTag(tag: String) = runBlocking {
        delegate.addTag(tag)
    }

    override fun setComment(comment: String) = runBlocking {
        delegate.setComment(comment)
    }

    override fun replaceTags(tags: List<String>) = runBlocking {
        delegate.replaceTags(tags)
    }

    override fun pin(comment: String) = runBlocking {
        delegate.pin(comment)
    }

    override fun unpin(comment: String) = runBlocking {
        delegate.unpin(comment)
    }

    override fun getArtifacts(parentPath: String, recursive: Boolean, hidden: Boolean): List<BuildArtifact> =
        runBlocking { delegate.getArtifacts(parentPath, recursive, hidden).map(::BuildArtifactBridge) }

    override fun findArtifact(pattern: String, parentPath: String): BuildArtifact = runBlocking {
        BuildArtifactBridge(runBlocking { delegate.findArtifact(pattern, parentPath) })
    }

    override fun findArtifact(pattern: String, parentPath: String, recursive: Boolean): BuildArtifact = runBlocking {
        BuildArtifactBridge(runBlocking { delegate.findArtifact(pattern, parentPath, recursive) })
    }

    override fun downloadArtifacts(pattern: String, outputDir: File) = runBlocking {
        delegate.downloadArtifacts(pattern, outputDir)
    }

    override fun downloadArtifact(artifactPath: String, output: OutputStream) = runBlocking {
        delegate.downloadArtifact(artifactPath, output)
    }

    override fun downloadArtifact(artifactPath: String, output: File) = runBlocking {
        delegate.downloadArtifact(artifactPath, output)
    }

    override fun openArtifactInputStream(artifactPath: String): InputStream = runBlocking {
        delegate.openArtifactInputStream(artifactPath)
    }

    override fun downloadBuildLog(output: File) = runBlocking {
        delegate.downloadBuildLog(output)
    }

    override fun cancel(comment: String, reAddIntoQueue: Boolean) = runBlocking {
        delegate.cancel(comment, reAddIntoQueue)
    }

    override fun getResultingParameters(): List<Parameter> =
        runBlocking { delegate.getResultingParameters().map(::ParameterBridge) }

    override fun finish() = runBlocking { delegate.finish() }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getWebUrl(): String = getHomeUrl()

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchStatusText(): String? = statusText

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun fetchQueuedDate(): Date = queuedDate

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun fetchStartDate(): Date? = startDate

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun fetchFinishDate(): Date? = finishDate

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchParameters(): List<Parameter> = parameters

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchRevisions(): List<Revision> = revisions

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchChanges(): List<Change> = changes

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchPinInfo(): PinInfo? = pinInfo

    @Suppress("OVERRIDE_DEPRECATION")
    override fun fetchTriggeredInfo(): TriggeredInfo? = triggeredInfo
    override fun toString(): String = delegate.toString()
}


private class BuildArtifactBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildArtifact
) : BuildArtifact {
    override val name: String by lazy { delegate.name }
    override val fullName: String by lazy { delegate.fullName }
    override val size: Long? by lazy { delegate.size }
    override val modificationDateTime: ZonedDateTime by lazy { delegate.modificationDateTime }
    override val build: Build = BuildBridge(delegate.build)

    override fun download(output: File) = runBlocking { delegate.download(output) }

    override fun download(output: OutputStream) = runBlocking { delegate.download(output) }

    override fun openArtifactInputStream(): InputStream = runBlocking { delegate.openArtifactInputStream() }

    @Suppress("OVERRIDE_DEPRECATION")
    override val modificationTime: Date = Date.from(modificationDateTime.toInstant())
    override fun toString(): String = delegate.toString()
}

private class BranchBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.Branch
) : Branch {
    override val name: String? by lazy { delegate.name }
    override val isDefault: Boolean by lazy { delegate.isDefault }
    override fun toString(): String = delegate.toString()
}

private class RevisionBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.Revision,
) : Revision {
    override val version: String by lazy { delegate.version }
    override val vcsBranchName: String by lazy { delegate.vcsBranchName }
    override val vcsRootInstance: VcsRootInstance = VcsRootInstanceBridge(delegate.vcsRootInstance)
    override fun toString(): String = delegate.toString()
}

private class ChangeBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.Change,
) : Change {
    override val id: ChangeId by lazy { delegate.id }
    override val version: String by lazyBlocking { delegate.getVersion() }
    override val username: String by lazyBlocking { delegate.getUsername() }
    override val user: User? by lazyBlocking { delegate.getUser()?.let(::UserBridge) }
    override val dateTime: ZonedDateTime by lazyBlocking { delegate.getDateTime() }
    override val comment: String by lazyBlocking { delegate.getComment() }
    override val vcsRootInstance: VcsRootInstance? by lazyBlocking {
        delegate.getVcsRootInstance()?.let(::VcsRootInstanceBridge)
    }
    override val files: List<ChangeFile> by lazyBlocking { delegate.getFiles().map(::ChangeFileBridge) }

    override fun getHomeUrl(
        specificBuildConfigurationId: BuildConfigurationId?,
        includePersonalBuilds: Boolean?
    ): String = delegate.getHomeUrl(specificBuildConfigurationId, includePersonalBuilds)

    override fun firstBuilds(): List<Build> = runBlocking { delegate.firstBuilds().map(::BuildBridge) }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getWebUrl(
        specificBuildConfigurationId: BuildConfigurationId?,
        includePersonalBuilds: Boolean?
    ): String = getHomeUrl(specificBuildConfigurationId, includePersonalBuilds)

    @Suppress("OVERRIDE_DEPRECATION")
    override val date: Date by lazy { Date.from(dateTime.toInstant()) }
    override fun toString(): String = delegate.toString()
}

private class ChangeFileBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.ChangeFile
) : ChangeFile {
    override val fileRevisionBeforeChange: String? by lazy { delegate.fileRevisionBeforeChange }
    override val fileRevisionAfterChange: String? by lazy { delegate.fileRevisionAfterChange }
    override val changeType: ChangeType by lazy { delegate.changeType }
    override val filePath: String? by lazy { delegate.filePath }
    override val relativeFilePath: String? by lazy { delegate.relativeFilePath }
    override fun toString(): String = delegate.toString()
}

private class BuildCanceledInfoBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildCanceledInfo,
) : BuildCanceledInfo {
    override val user: User? by lazy { delegate.user?.let(::UserBridge) }
    override val cancelDateTime: ZonedDateTime by lazy { delegate.cancelDateTime }
    override val text: String by lazy { delegate.text }

    @Suppress("OVERRIDE_DEPRECATION")
    override val cancelDate: Date = Date.from(delegate.cancelDateTime.toInstant())
    override fun toString(): String = delegate.toString()
}

private class BuildCommentInfoBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildCommentInfo,
) : BuildCommentInfo {
    override val user: User? by lazy { delegate.user?.let(::UserBridge) }
    override val timestamp: ZonedDateTime by lazy { delegate.timestamp }
    override val text: String by lazy { delegate.text }
    override fun toString(): String = delegate.toString()
}

private class BuildRunningInfoBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildRunningInfo,
) : BuildRunningInfo {
    override val percentageComplete: Int by lazy { delegate.percentageComplete }
    override val elapsedSeconds: Long by lazy { delegate.elapsedSeconds }
    override val estimatedTotalSeconds: Long by lazy { delegate.estimatedTotalSeconds }
    override val outdated: Boolean by lazy { delegate.outdated }
    override val probablyHanging: Boolean by lazy { delegate.probablyHanging }
    override fun toString(): String = delegate.toString()
}

private class PinInfoBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.PinInfo
) : PinInfo {
    override val user: User = UserBridge(delegate.user)
    override val dateTime: ZonedDateTime by lazy { delegate.dateTime }

    @Suppress("OVERRIDE_DEPRECATION")
    override val time: Date = Date.from(dateTime.toInstant())
    override fun toString(): String = delegate.toString()
}

private class TriggeredInfoBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.TriggeredInfo
) : TriggeredInfo {
    override val user: User? by lazy { delegate.user?.let(::UserBridge) }
    override val build: Build? by lazy { delegate.build?.let(::BuildBridge) }
    override fun toString(): String = delegate.toString()
}

private class BuildProblemOccurrenceBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildProblemOccurrence
) : BuildProblemOccurrence {
    override val buildProblem: BuildProblem = BuildProblemBridge(delegate.buildProblem)
    override val build: Build = BuildBridge(delegate.build)
    override val details: String by lazy { delegate.details }
    override val additionalData: String? by lazy { delegate.additionalData }
    override fun toString(): String = delegate.toString()
}

private class BuildProblemBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildProblem
) : BuildProblem {
    override val id: BuildProblemId by lazy { delegate.id }
    override val type: BuildProblemType by lazy { delegate.type }
    override val identity: String by lazy { delegate.identity }
    override fun toString(): String = delegate.toString()
}

private class BuildAgentPoolBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildAgentPool
) : BuildAgentPool {
    override val id: BuildAgentPoolId by lazy { delegate.id }
    override val name: String by lazyBlocking { delegate.getName() }
    override val projects: List<Project> by lazyBlocking { delegate.getProjects().map(::ProjectBridge) }
    override val agents: List<BuildAgent> by lazyBlocking { delegate.getAgents().map(::BuildAgentBridge) }
    override fun toString(): String = delegate.toString()
}

private class BuildAgentBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildAgent
) : BuildAgent {
    override val id: BuildAgentId by lazy { delegate.id }
    override val name: String by lazyBlocking { delegate.getName() }
    override val pool: BuildAgentPool by lazyBlocking { BuildAgentPoolBridge(delegate.getPool()) }
    override val connected: Boolean by lazyBlocking { delegate.isConnected() }
    override val enabled: Boolean by lazyBlocking { delegate.isEnabled() }
    override val authorized: Boolean by lazyBlocking { delegate.isAuthorized() }
    override val outdated: Boolean by lazyBlocking { delegate.isOutdated() }
    override val ipAddress: String by lazyBlocking { delegate.getIpAddress() }
    override val parameters: List<Parameter> by lazyBlocking { delegate.getParameters().map(::ParameterBridge) }
    override val enabledInfo: BuildAgentEnabledInfo? by lazyBlocking {
        delegate.getEnabledInfo()?.let(::BuildAgentEnabledInfoBridge)
    }
    override val authorizedInfo: BuildAgentAuthorizedInfo? by lazyBlocking {
        delegate.getAuthorizedInfo()?.let(::BuildAgentAuthorizedInfoInfoBridge)
    }
    override val currentBuild: Build? by lazyBlocking { delegate.getCurrentBuild()?.let(::BuildBridge) }

    override fun getHomeUrl(): String = delegate.getHomeUrl()
    override fun toString(): String = delegate.toString()
}

private class BuildAgentAuthorizedInfoInfoBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildAgentAuthorizedInfo
) : BuildAgentAuthorizedInfo {
    override val user: User? by lazy { delegate.user?.let(::UserBridge) }
    override val timestamp: ZonedDateTime by lazy { delegate.timestamp }
    override val text: String by lazy { delegate.text }
    override fun toString(): String = delegate.toString()
}

private class BuildAgentEnabledInfoBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.BuildAgentEnabledInfo
) : BuildAgentEnabledInfo {
    override val user: User? by lazy { delegate.user?.let(::UserBridge) }
    override val timestamp: ZonedDateTime by lazy { delegate.timestamp }
    override val text: String by lazy { delegate.text }
    override fun toString(): String = delegate.toString()
}

private class VcsRootBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.VcsRoot
) : VcsRoot {
    override val id: VcsRootId by lazy { delegate.id }
    override val name: String by lazyBlocking { delegate.getName() }
    override val url: String? by lazyBlocking { delegate.getUrl() }
    override val defaultBranch: String? by lazyBlocking { delegate.getDefaultBranch() }
    override fun toString(): String = delegate.toString()
}

private class VcsRootInstanceBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.VcsRootInstance
) : VcsRootInstance {
    override val vcsRootId: VcsRootId by lazy { delegate.vcsRootId }
    override val name: String by lazy { delegate.name }
    override fun toString(): String = delegate.toString()
}

private class InvestigationBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.Investigation
) : Investigation {
    override val id: InvestigationId by lazy { delegate.id }
    override val assignee: User = UserBridge(delegate.assignee)
    override val reporter: User? by lazy { delegate.reporter?.let(::UserBridge) }
    override val comment: String by lazy { delegate.comment }
    override val resolveMethod: InvestigationResolveMethod by lazy { delegate.resolveMethod }
    override val targetType: InvestigationTargetType by lazy { delegate.targetType }
    override val testIds: List<TestId>? by lazy { delegate.testIds }
    override val problemIds: List<BuildProblemId>? by lazy { delegate.problemIds }
    override val scope: InvestigationScope by lazy {
        when (val effectiveScope = delegate.scope) {
            is org.jetbrains.teamcity.rest.coroutines.InvestigationScope.InProject ->
                InvestigationScope.InProject(ProjectBridge(effectiveScope.project))

            is org.jetbrains.teamcity.rest.coroutines.InvestigationScope.InBuildConfiguration ->
                InvestigationScope.InBuildConfiguration(BuildConfigurationBridge(effectiveScope.configuration))
        }
    }
    override val state: InvestigationState by lazy { delegate.state }
    override fun toString(): String = delegate.toString()
}

private class MuteBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.Mute
) : Mute {
    override val id: InvestigationId by lazy { delegate.id }
    override val assignee: User? by lazy { delegate.assignee?.let(::UserBridge) }
    override val reporter: User? by lazy { delegate.assignee?.let(::UserBridge) }
    override val comment: String by lazy { delegate.comment }
    override val resolveMethod: InvestigationResolveMethod by lazy { delegate.resolveMethod }
    override val targetType: InvestigationTargetType by lazy { delegate.targetType }
    override val testIds: List<TestId>? by lazy { delegate.testIds }
    override val problemIds: List<BuildProblemId>? by lazy { delegate.problemIds }
    override val scope: InvestigationScope by lazy {
        when (val effectiveScope = delegate.scope) {
            is org.jetbrains.teamcity.rest.coroutines.InvestigationScope.InProject ->
                InvestigationScope.InProject(ProjectBridge(effectiveScope.project))

            is org.jetbrains.teamcity.rest.coroutines.InvestigationScope.InBuildConfiguration ->
                InvestigationScope.InBuildConfiguration(BuildConfigurationBridge(effectiveScope.configuration))
        }
    }
    override val tests: List<Test>? by lazy { delegate.tests?.map(::TestBridge) }
    override fun toString(): String = delegate.toString()
}

private class TestBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.Test
) : Test {
    override val id: TestId by lazy { delegate.id }
    override val name: String by lazyBlocking { delegate.getName() }
    override fun toString(): String = delegate.toString()
}

private class TestRunBridge(
    private val delegate: org.jetbrains.teamcity.rest.coroutines.TestRun
) : TestRun {
    override val testOccurrenceId: TestOccurrenceId by lazy { delegate.testOccurrenceId }

    override val name: String by lazyBlocking { delegate.getName() }
    override val status: TestStatus by lazyBlocking { delegate.getStatus() }
    override val duration: Duration by lazyBlocking { delegate.getDuration() }
    override val details: String by lazyBlocking { delegate.getDetails() }
    override val ignored: Boolean by lazyBlocking { delegate.isIgnored() }
    override val currentlyMuted: Boolean by lazyBlocking { delegate.isCurrentlyMuted() }
    override val muted: Boolean by lazyBlocking { delegate.isMutedAtRunningTime() }
    override val newFailure: Boolean by lazyBlocking { delegate.isNewFailure() }
    override val buildId: BuildId by lazyBlocking { delegate.getBuildId() }
    override val fixedIn: BuildId? by lazyBlocking { delegate.getFixedIn() }
    override val firstFailedIn: BuildId? by lazyBlocking { delegate.getFirstFailedIn() }
    override val testId: TestId by lazyBlocking { delegate.getTestId() }
    override val metadataValues: List<String>? by lazyBlocking { delegate.getMetadataValues() }
    override fun toString(): String = delegate.toString()
}
