/**
 * Extended API required for `blocking -> coroutines` API bridges
 *
 * Why: `Flow<T>` cannot be easily converted to `Sequence<T>`,
 *       so we cannot reuse `fun all(): Flow<T>` by `runBlocking { }` wrapper
 *
 * Solution: All locators, in addition to `fun all(): Flow<T>`
 *           have `fun allSeq(): Sequence<T>` method to retrieve data blocking, but lazily
 */
package org.jetbrains.teamcity.rest

import org.jetbrains.teamcity.rest.coroutines.Build
import org.jetbrains.teamcity.rest.coroutines.BuildAgent
import org.jetbrains.teamcity.rest.coroutines.BuildAgentLocator
import org.jetbrains.teamcity.rest.coroutines.BuildAgentPool
import org.jetbrains.teamcity.rest.coroutines.BuildAgentPoolLocator
import org.jetbrains.teamcity.rest.coroutines.BuildLocator
import org.jetbrains.teamcity.rest.coroutines.BuildProblemOccurrence
import org.jetbrains.teamcity.rest.coroutines.BuildQueue
import org.jetbrains.teamcity.rest.coroutines.Investigation
import org.jetbrains.teamcity.rest.coroutines.InvestigationLocator
import org.jetbrains.teamcity.rest.coroutines.Mute
import org.jetbrains.teamcity.rest.coroutines.MuteLocator
import org.jetbrains.teamcity.rest.coroutines.TeamCityCoroutinesInstance
import org.jetbrains.teamcity.rest.coroutines.Test
import org.jetbrains.teamcity.rest.coroutines.TestLocator
import org.jetbrains.teamcity.rest.coroutines.TestRun
import org.jetbrains.teamcity.rest.coroutines.TestRunsLocator
import org.jetbrains.teamcity.rest.coroutines.User
import org.jetbrains.teamcity.rest.coroutines.UserLocator
import org.jetbrains.teamcity.rest.coroutines.VcsRoot
import org.jetbrains.teamcity.rest.coroutines.VcsRootLocator

internal interface TeamCityCoroutinesInstanceEx : TeamCityCoroutinesInstance {
    fun toBuilder(): TeamCityInstanceBuilder
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
    fun queuedBuildsSeq(projectId: ProjectId? = null): Sequence<Build>
    fun queuedBuildsSeq(buildConfigurationId: BuildConfigurationId): Sequence<Build>
}
