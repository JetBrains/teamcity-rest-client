package org.jetbrains.teamcity.rest

import java.time.Instant
import java.util.concurrent.TimeUnit

interface TeamCityInstanceSettings<Self : TeamCityInstanceSettings<Self>> {
    val serverUrl: String
}

interface BuildAgentLocatorSettings<Self : BuildAgentLocatorSettings<Self>> {
    fun compatibleWith(buildConfigurationId: BuildConfigurationId): Self
}

interface BuildAgentPoolLocatorSettings<Self : BuildAgentPoolLocatorSettings<Self>>

interface BuildLocatorSettings<Self : BuildLocatorSettings<Self>> {
    fun forProject(projectId: ProjectId): Self

    fun fromConfiguration(buildConfigurationId: BuildConfigurationId): Self

    fun withNumber(buildNumber: String): Self

    /**
     * Filters builds to include only ones which are built on top of the specified revision.
     */
    fun withVcsRevision(vcsRevision: String): Self

    fun snapshotDependencyTo(buildId: BuildId): Self

    /**
     * By default only successful builds are returned, call this method to include failed builds as well.
     */
    fun includeFailed(): Self

    /**
     * By default only finished builds are returned
     */
    fun includeRunning(): Self
    fun onlyRunning(): Self

    /**
     * By default canceled builds are not returned
     */
    fun includeCanceled(): Self
    fun onlyCanceled(): Self

    fun withStatus(status: BuildStatus): Self
    fun withTag(tag: String): Self

    fun withBranch(branch: String): Self

    /**
     * By default only builds from the default branch are returned, call this method to include builds from all branches.
     */
    fun withAllBranches(): Self

    fun pinnedOnly(): Self

    fun includePersonal(): Self
    fun onlyPersonal(): Self

    fun limitResults(count: Int): Self
    fun pageSize(pageSize: Int): Self

    fun since(date: Instant): Self
    fun until(date: Instant): Self
}

interface InvestigationLocatorSettings<Self : InvestigationLocatorSettings<Self>> {
    fun limitResults(count: Int): Self
    fun forProject(projectId: ProjectId): Self
    fun withTargetType(targetType: InvestigationTargetType): Self
}


interface MuteLocatorSettings<Self : MuteLocatorSettings<Self>> {
    fun limitResults(count: Int): Self
    fun forProject(projectId: ProjectId): Self
    fun byUser(userId: UserId): Self
    fun forTest(testId: TestId): Self
}

interface TestLocatorSettings<Self : TestLocatorSettings<Self>> {
    fun limitResults(count: Int): Self
    fun byId(testId: TestId): Self
    fun byName(testName: String): Self
    fun currentlyMuted(muted: Boolean): Self
    fun forProject(projectId: ProjectId): Self
}

interface TestRunsLocatorSettings<Self : TestRunsLocatorSettings<Self>> {
    fun limitResults(count: Int): Self
    fun pageSize(pageSize: Int): Self
    fun forBuild(buildId: BuildId): Self
    fun forTest(testId: TestId): Self
    fun forProject(projectId: ProjectId): Self
    fun withStatus(testStatus: TestStatus): Self
    fun withoutDetailsField(): Self

    /**
     * If expandMultipleInvocations is enabled, individual runs of tests, which were executed several
     * times in same build, are returned as separate entries.
     * By default such runs are aggregated into a single value, duration property will be the sum of durations
     * of individual runs, and status will be SUCCESSFUL if and only if all runs are successful.
     */
    fun expandMultipleInvocations(): Self
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

data class TestOccurrenceId(val stringId: String) {
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

data class VcsRootType(val stringType: String) {
    companion object {
        val GIT = VcsRootType("jetbrains.git")
    }
}


data class SpecifiedRevision(val version: String, val vcsBranchName: String, val vcsRootId: VcsRootId)

enum class ChangeType {
    EDITED,
    ADDED,
    REMOVED,

    /**
     * This type is used when directory or subdirectory is copied and/or modified, and individual files there
     * are not included separately in the [Change].
     */
    COPIED,
    UNKNOWN
}

data class UserId(val stringId: String) {
    override fun toString(): String = stringId
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

enum class TestStatus {
    SUCCESSFUL,
    IGNORED,
    FAILED,
    UNKNOWN
}