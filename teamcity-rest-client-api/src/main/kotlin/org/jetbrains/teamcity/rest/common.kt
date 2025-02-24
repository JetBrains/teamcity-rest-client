package org.jetbrains.teamcity.rest

import org.jetbrains.teamcity.rest.TestRunsLocatorSettings.TestRunField
import java.time.Instant

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

    /**
     * Filters [buildId] build dependencies, including transitive
     */
    fun snapshotDependencyTo(buildId: BuildId): Self

    /**
     * Filters builds dependant to [buildId] build, including transitive
     */
    fun snapshotDependencyFrom(buildId: BuildId): Self

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

    fun withAgent(agentName: String): Self

    /**
     * If true, applies default filter which returns only "normal" builds
     * (finished builds which are not canceled, not failed-to-start, not personal,
     * and on default branch (in branched build configurations)).
     * True by default.
     */
    fun defaultFilter(enable: Boolean) : Self

    /**
     * Use this method to manually select Build fields to prefetch. By default [BuildField.defaultFields] are fetched/
     */
    fun prefetchFields(vararg fields: BuildField): Self

    enum class BuildField {
        NAME,
        BUILD_CONFIGURATION_ID,
        PROJECT_ID,
        PROJECT_NAME,
        BUILD_NUMBER,
        STATUS,
        STATUS_TEXT,
        STATE,
        BRANCH,
        IS_PERSONAL,
        CANCELED_INFO,
        COMMENT,
        IS_COMPOSITE,
        QUEUED_DATETIME,
        START_DATETIME,
        FINISH_DATETIME,
        RUNNING_INFO,
        PARAMETERS,
        RESULTING_PARAMETERS,
        TAGS,
        REVISIONS,
        SNAPSHOT_DEPENDENCIES,
        PIN_INFO,
        TRIGGERED_INFO,
        AGENT,
        IS_DETACHED_FROM_AGENT,
        QUEUED_WAIT_REASONS,
        IS_FAILED_TO_START,
        HISTORY,
        ;

        companion object {
            val size = BuildField.values().size

            val essentialFields = setOf(
                BuildLocatorSettings.BuildField.BUILD_CONFIGURATION_ID,
                BuildLocatorSettings.BuildField.BUILD_NUMBER,
                BuildLocatorSettings.BuildField.STATUS,
                BuildLocatorSettings.BuildField.BRANCH,
            )

            val defaultFields = setOf(
                BuildLocatorSettings.BuildField.BUILD_CONFIGURATION_ID,
                BuildLocatorSettings.BuildField.BUILD_NUMBER,
                BuildLocatorSettings.BuildField.STATUS,
                BuildLocatorSettings.BuildField.STATE,
                BuildLocatorSettings.BuildField.IS_PERSONAL,
                BuildLocatorSettings.BuildField.BRANCH,
                BuildLocatorSettings.BuildField.IS_COMPOSITE,
                BuildLocatorSettings.BuildField.QUEUED_DATETIME,
                BuildLocatorSettings.BuildField.START_DATETIME,
                BuildLocatorSettings.BuildField.FINISH_DATETIME,
                BuildLocatorSettings.BuildField.CANCELED_INFO,
                BuildLocatorSettings.BuildField.TRIGGERED_INFO,
                BuildLocatorSettings.BuildField.REVISIONS,
                BuildLocatorSettings.BuildField.AGENT,
                BuildLocatorSettings.BuildField.PARAMETERS,
                BuildLocatorSettings.BuildField.SNAPSHOT_DEPENDENCIES,
                BuildLocatorSettings.BuildField.IS_FAILED_TO_START,
            )
        }
    }
}

interface InvestigationLocatorSettings<Self : InvestigationLocatorSettings<Self>> {
    fun limitResults(count: Int): Self
    fun forProject(projectId: ProjectId): Self
    fun forBuildConfiguration(buildConfigurationId: BuildConfigurationId): Self
    fun forTest(testId: TestId): Self
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

    /**
     * Use this method to manually select Test fields to prefetch.
     */
    fun prefetchFields(vararg fields: TestField): Self

    fun excludePrefetchFields(vararg fields: TestField): Self

    enum class TestField {
        NAME,
        PARSED_NAME_PACKAGE,
        PARSED_NAME_SUITE,
        PARSED_NAME_CLASS,
        PARSED_SHORT_NAME,
        PARSED_NAME_WITHOUT_PREFIX,
        PARSED_METHOD_NAME,
        PARSED_NAME_WITH_PARAMETERS,
        ;

        companion object {
            val size = TestField.values().size
        }
    }

}

interface TestRunsLocatorSettings<Self : TestRunsLocatorSettings<Self>> {
    fun limitResults(count: Int): Self
    fun pageSize(pageSize: Int): Self
    fun forBuild(buildId: BuildId): Self
    fun forTest(testId: TestId): Self
    fun forProject(projectId: ProjectId): Self
    fun withStatus(testStatus: TestStatus): Self

    @Deprecated(
        message = "Deprecated, please use withoutFields instead",
        replaceWith = ReplaceWith("excludePrefetchFields(TestRunsLocatorSettings.Fields.DETAILS)")
    )
    fun withoutDetailsField(): Self

    /**
     * If expandMultipleInvocations is enabled, individual runs of tests, which were executed several
     * times in same build, are returned as separate entries.
     * By default such runs are aggregated into a single value, duration property will be the sum of durations
     * of individual runs, and status will be SUCCESSFUL if and only if all runs are successful.
     */
    fun expandMultipleInvocations(): Self

    fun muted(muted: Boolean): Self

    fun currentlyMuted(currentlyMuted: Boolean): Self


    /**
     * Use this method to manually select TestRun fields to prefetch.
     */
    fun prefetchFields(vararg fields: TestRunField): Self

    /**
     * By default, REST client will fetch all TestRun fields listed in [TestRunField].
     * You can unselect some of them using this method.
     */
    fun excludePrefetchFields(vararg fields: TestRunField): Self

    fun prefetchTestFields(vararg fields: TestLocatorSettings.TestField): Self

    enum class TestRunField {
        NAME,
        STATUS,
        DURATION,
        DETAILS ,
        IGNORED,
        IS_CURRENTLY_MUTED,
        IS_MUTED,
        IS_NEW_FAILURE,
        BUILD_ID,
        FIXED_IN_BUILD_ID,
        FIRST_FAILED_IN_BUILD_ID,
        TEST_ID,
        METADATA_VALUES,
        LOG_ANCHOR,
        ;

        companion object {
            val size = TestRunField.values().size
        }
    }
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

data class BuildAgentTypeId(val stringId: String) {
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

data class RoleScope(val descriptor: String) {
    override fun toString(): String = descriptor
}

data class RoleId(val stringId: String) {
    override fun toString(): String = stringId
}

data class ArtifactDependencyId(val stringId: String) {
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

enum class InvestigationResolveMethod(val value: String) {
    MANUALLY("manually"),
    WHEN_FIXED("whenFixed"),
    AT_TIME("atTime")
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

enum class CompatibleBuildConfigurationsPolicy {
    ANY,
    SELECTED,
    UNKNOWN,
}

enum class BuildConfigurationType(val value: String) {
    REGULAR("regular"),
    COMPOSITE("composite"),
    DEPLOYMENT("deployment"),
}
