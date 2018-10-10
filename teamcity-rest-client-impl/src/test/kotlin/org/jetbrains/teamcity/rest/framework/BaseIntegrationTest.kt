package org.jetbrains.teamcity.rest.framework

import org.jetbrains.teamcity.rest.Build
import org.jetbrains.teamcity.rest.BuildConfiguration
import org.jetbrains.teamcity.rest.BuildConfigurationId
import org.jetbrains.teamcity.rest.BuildId
import org.jetbrains.teamcity.rest.BuildState
import org.jetbrains.teamcity.rest.BuildStatus
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.net.InetAddress
import java.util.*

abstract class BaseIntegrationTest {
    companion object {
        private val log = LoggerFactory.getLogger(BaseIntegrationTest::class.java)
    }

    @Rule
    @JvmField
    val testWatcher: TestWatcher = object : TestWatcher() {
        var success: Boolean = true

        override fun failed(e: Throwable, description: Description) {
            success = false
            super.failed(e, description)
        }

        override fun finished(description: Description?) {
            if (success) {
                teamcity.project.delete()
            }

            super.finished(description)
        }
    }

    val git by lazy {
        gitMultiRepo.repos().first()
    }

    val teamcity by lazy {
        TeamCityContext()
    }

    @Before
    open fun setUp() {
        gitMultiRepo.repos().forEach {
            it.branchCreate()
                    .setName(master)
                    .setStartPoint("origin/$master")
                    .setForce(true)
                    .call()
            it.checkout()
                    .setName(master)
                    .call()
        }
    }

    fun waitForFinishedBuild(id: BuildId): Build {
        while (true) {
            val build = teamcity.instance.build(id)
            log.debug("Build: $id state ${build.state} status ${build.status}: ${build.getHomeUrl()}")
            if (build.state != BuildState.QUEUED && build.state != BuildState.RUNNING) break

            Thread.sleep(1000)
        }

        val build = teamcity.instance.build(id)
        val buildWebUrl = teamcity.instance.build(id).getHomeUrl()

        if (build.canceledInfo != null) {
            log.info("Child build was cancelled: ${build.getHomeUrl()}")
        }

        return when (build.state) {
            BuildState.UNKNOWN -> {
                error("Child build has unknown state: $buildWebUrl")
            }

            BuildState.DELETED -> {
                error("Child build has deleted state: $buildWebUrl")
            }

            BuildState.QUEUED, BuildState.RUNNING -> error("Unexpected ${build.state} build state of $buildWebUrl")

            BuildState.FINISHED -> {
                when (build.status) {
                    null -> error("Unexpected NULL build status of $buildWebUrl")
                    BuildStatus.UNKNOWN -> {
                        if (build.canceledInfo == null) {
                            error("Unexpected ${build.status} build status of $buildWebUrl")
                        }

                        build
                    }
                    BuildStatus.ERROR, BuildStatus.FAILURE, BuildStatus.SUCCESS -> build
                }
            }
        }
    }

    fun commitFile(name: String, content: String) = git.commitFile(name, content)

    fun gitPushAll() {
        git.pushAll()
    }

    fun gitFetch() {
        git.fetch().call()
    }
}