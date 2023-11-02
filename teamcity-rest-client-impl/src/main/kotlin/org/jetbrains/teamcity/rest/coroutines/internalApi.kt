/**
 * Extended API required for `blocking -> coroutines` API bridges
 *
 * Why: `Flow<T>` cannot be easily converted to `Sequence<T>`,
 *       so we cannot reuse `fun all(): Flow<T>` by `runBlocking { }` wrapper
 *
 * Solution: All locators, in addition to `fun all(): Flow<T>`
 *           have `fun allSeq(): Sequence<T>` method to retrieve data blocking, but lazily
 */
package org.jetbrains.teamcity.rest.coroutines

import org.jetbrains.teamcity.rest.*

internal interface TeamCityCoroutinesInstanceEx : TeamCityCoroutinesInstance {
    fun toBuilder(): TeamCityInstanceBuilder
}

internal interface ProjectEx : Project {
    suspend fun createMutes(mutes: List<Mute>)
    fun getMutesSeq(): Sequence<Mute>
}

internal interface BuildEx : Build {
    fun getTestRunsSeq(status: TestStatus? = null): Sequence<TestRun>
    fun getBuildProblemsSeq(): Sequence<BuildProblemOccurrence>
}

internal interface VcsRootLocatorEx : VcsRootLocator {
    fun allSeq(): Sequence<VcsRoot>
}

internal interface BuildAgentLocatorEx : BuildAgentLocator {
    fun allSeq(): Sequence<BuildAgent>
}

internal interface BuildAgentPoolLocatorEx : BuildAgentPoolLocator {
    fun allSeq(): Sequence<BuildAgentPool>
}

internal interface BuildLocatorEx : BuildLocator {
    fun allSeq(): Sequence<Build>
}


internal interface InvestigationLocatorEx : InvestigationLocator {
    fun allSeq(): Sequence<Investigation>
}


internal interface MuteLocatorEx : MuteLocator {
    fun allSeq(): Sequence<Mute>
}

internal interface TestLocatorEx : TestLocator {
    fun allSeq(): Sequence<Test>
}

internal interface TestRunsLocatorEx : TestRunsLocator {
    fun allSeq(): Sequence<TestRun>
}

internal interface UserLocatorEx : UserLocator {
    fun allSeq(): Sequence<User>
}

internal interface BuildQueueEx : BuildQueue {
    fun queuedBuildsSeq(
        projectId: ProjectId? = null,
        prefetchFields: Set<BuildLocatorSettings.BuildField>
    ): Sequence<Build>

    fun queuedBuildsSeq(
        buildConfigurationId: BuildConfigurationId,
        prefetchFields: Set<BuildLocatorSettings.BuildField>
    ): Sequence<Build>
}

internal interface IssueEx : Issue {
    val tcInstance: TeamCityCoroutinesInstance
}

internal interface BuildAgentEx : BuildAgent {
    val tcInstance: TeamCityCoroutinesInstance
}
