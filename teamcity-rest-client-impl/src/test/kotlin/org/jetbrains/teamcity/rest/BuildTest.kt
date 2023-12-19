package org.jetbrains.teamcity.rest

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_to_string() {
        val builds = customInstanceByConnectionFile().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(3)
                .all()

        builds.forEach {
            it.getArtifacts()
            callPublicPropertiesAndFetchMethods(it)
        }
    }

    @Test
    fun test_equals_hashcode() {
        val id = publicInstance().builds().all().first().id

        val firstBlocking = publicInstance().build(id)
        val secondBlocking = publicInstance().build(id)
        assertEquals(firstBlocking, secondBlocking)

        runBlocking {
            val first = publicCoroutinesInstance().build(id)
            val second = publicCoroutinesInstance().build(id)
            assertEquals(first, second)
        }
    }

    @Test
    fun since_date_and_until_date() {
        val monthAgo = GregorianCalendar()
        monthAgo.add(Calendar.MONTH, -1)
        val weekAgo = GregorianCalendar()
        monthAgo.add(Calendar.DAY_OF_MONTH, -7)

        val builds = publicInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(3)
                .since(monthAgo.toInstant())
                .until(weekAgo.toInstant())
                .all()

        for (build in builds) {
            assert(build.startDateTime!! >= monthAgo.toZonedDateTime() && build.startDateTime!! <= weekAgo.toZonedDateTime())
        }
    }

    @Test
    fun test_build_fetch_revisions() {
        publicInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(10)
                .all()
                .forEach {
                    val revisions = it.revisions
                    Assert.assertTrue(revisions.isNotEmpty())
                }
    }

    @Test
    fun test_fetch_status() {
        val build = publicInstance().builds()
                .fromConfiguration(runTestsBuildConfiguration)
                .limitResults(1)
                .all().first()

        build.statusText
    }

    @Test
    fun test_get_artifacts() {
        val build = customInstanceByConnectionFile().builds()
                .fromConfiguration(dependantBuildConfiguration)
                .limitResults(15)
                .all()
                .first { it.getArtifacts().isNotEmpty() }

        val artifacts = build.getArtifacts("internal")
        Assert.assertTrue(artifacts.any {
            it.fullName == "internal/gradle_test_report.zip" && it.name == "gradle_test_report.zip" && it.size != null
        })

        val artifactsRecursive = build.getArtifacts("internal", recursive = true)
        Assert.assertTrue(artifactsRecursive.size == artifacts.size)
    }

    @Test
    fun `should be able to find artifact recursively`() {
        val build = publicInstance().builds()
                .fromConfiguration(dependantBuildConfiguration)
                .withNumber("5")
                .includeFailed()
                .limitResults(1)
                .all()
                .first()

        val thrownException = catchThrowable { build.findArtifact("gradle_test_*.zip", "org/jetbrains", false) }

        assertThat(thrownException).isInstanceOf(TeamCityQueryException::class.java)
                .hasMessageContaining("gradle_test_*.zip not found in build 5")

        val existingArtifact = build.findArtifact("gradle_test_*.zip", "org/jetbrains", true)

        assertThat(existingArtifact.name).isEqualTo("gradle_test_report.zip")
    }

    @Test
    fun test_get_webUrl() {
        val build = publicInstance().builds()
                .fromConfiguration(changesBuildConfiguration)
                .limitResults(1)
                .all().first()

        assertEquals("$publicInstanceUrl/viewLog.html?buildId=${build.id.stringId}", build.getHomeUrl())
    }

    @Test
    fun test_snapshot_dependencies() {
        val build = publicInstance().builds()
                .fromConfiguration(dependantBuildConfiguration)
                .limitResults(1)
                .all().first()

        assertTrue(build.snapshotDependencies.isNotEmpty())
    }

    @Test
    fun test_get_tags() {
        val build = publicInstance().build(BuildId("241"))

        assertTrue(build.tags.isNotEmpty())
        assertTrue(build.tags.contains("1.0"))
    }

    @Test
    fun pagination() {
        val builds = publicInstance().builds()
                .fromConfiguration(manyTestsBuildConfiguration)
                // Default reasonableMaxPageSize=1024, but we have only 100 builds in test data
                // Paging will never happen if we don't set page size explicitly
                .pageSize(5)
                .all()
                .toList()
        assertThat(builds.size).isGreaterThanOrEqualTo(100)

        val buildsAsync = runBlocking {
            publicCoroutinesInstance().builds()
                .fromConfiguration(manyTestsBuildConfiguration)
                // Default reasonableMaxPageSize=1024, but we have only 100 builds in test data
                // Paging will never happen if we don't set page size explicitly
                .pageSize(5)
                .all()
                .toList()
        }
        assertThat(buildsAsync.size).isGreaterThanOrEqualTo(100)
        assertEquals(builds.size, buildsAsync.size)
        assertEqualsAnyOrder(builds.map { it.id.stringId }, buildsAsync.map { it.id.stringId })
    }

    /**
     * Test ensures that `nextHref` returned by TeamCity is correctly
     * decoded and sent in next page request.
     */
    @Test
    fun paginationWithLocator() {
        val since = ZonedDateTime.of(2000, 10, 15, 12, 30, 15, 1042, ZoneOffset.UTC).toInstant()
        val builds = publicInstance().builds()
                .fromConfiguration(manyTestsBuildConfiguration)
                // Default reasonableMaxPageSize=1024, but we have only 100 builds in test data
                // Paging will never happen if we don't set page size explicitly
                .pageSize(5)
                .since(since)
                .all()
                .toList()
        assertThat(builds.size).isGreaterThanOrEqualTo(100)

        val buildsAsync = runBlocking {
            publicCoroutinesInstance().builds()
                .fromConfiguration(manyTestsBuildConfiguration)
                // Default reasonableMaxPageSize=1024, but we have only 100 builds in test data
                // Paging will never happen if we don't set page size explicitly
                .pageSize(5)
                .since(since)
                .all()
                .toList()
        }
        assertThat(buildsAsync.size).isGreaterThanOrEqualTo(100)
        assertEquals(builds.size, buildsAsync.size)
        assertEqualsAnyOrder(builds.map { it.id.stringId }, buildsAsync.map { it.id.stringId })
    }

    @Test
    fun test_parameters() {
        val build = customInstanceByConnectionFile().build(BuildId("241"))
        val parameters = build.parameters

        assertTrue(parameters.isNotEmpty())
        assertEquals(4, parameters.count())
        assertEquals("0", parameters.first { it.name == "system.FAILED_TESTS_PERCENTAGE" }.value)
    }

    @Test
    fun test_resulting_parameters() {
        val build = customInstanceByConnectionFile().build(BuildId("17"))
        val resultingParameters = build.getResultingParameters()

        assertTrue(resultingParameters.isNotEmpty())
        assertEquals(147, resultingParameters.count())
        assertEquals("1", resultingParameters.first { it.name == "build.counter" }.value)
    }

    @Test
    fun test_download_build_log() {
        val build = customInstanceByConnectionFile().build(BuildId("422"))

        val logWithTCVersion = Files.createTempFile("test_download_buildlog_", ".txt")
        val logWithReplacedTCVersion = Files.createTempFile("test_download_buildlog_without_version_", ".txt")

        try {
            build.downloadBuildLog(logWithTCVersion.toFile())

            // Log file contains TeamCity version of the *current* server, not the one where the build was run at.
            // To avoid changing this expected sha256 after each server upgrade, we make it stable by removing
            // server version from the file.
            replaceTeamCityVersionInLog(logWithTCVersion, logWithReplacedTCVersion)

            val expectedSha256 = "4d6a6a1125e09694d5fe01b251d6474571eaf2ee39b4dfd3fb436708c9d6cc45"
            val actualDownloadFileSha256 = calculateSha256Hash(logWithReplacedTCVersion.inputStream())
            assertEquals(expectedSha256, actualDownloadFileSha256)
        } finally {
            logWithReplacedTCVersion.deleteIfExists()
            logWithReplacedTCVersion.deleteIfExists()
        }
    }

    @Test
    fun test_download_artifact() {
        val build = customInstanceByConnectionFile().build(BuildId("422"))
        val nonHiddenArtifacts = build.getArtifacts()
        println(nonHiddenArtifacts)

        // For some reason, TeamCity may respond with hidden artifact instead of an empty list even if
        // the artifact locator is configured with `hidden=false`.
        // In this case, artifact name is prefixed with "._":
        // {"file":[{"name":"._.teamcity","fullName":"._.teamcity","size":163,"modificationTime":"20230815T095841+0000"}]}
        //
        // assertTrue(nonHiddenArtifacts.isEmpty(), nonHiddenArtifacts.toString())

        val hiddenArtifacts = build.getArtifacts(hidden = true, recursive = true)
        assertTrue(hiddenArtifacts.isNotEmpty())

        val artifact = hiddenArtifacts.firstOrNull { it.fullName == ".teamcity/settings/buildSettings.xml" }
        assertNotNull(artifact)

        val expectedSha256 = "ffcba858d36f9344866cc644fe01b2ac20ba1a35229b4fc51ddd3eb46b450480"
        
        val actualInputStreamSha256 = artifact.openArtifactInputStream().use(::calculateSha256Hash)
        assertEquals(expectedSha256, actualInputStreamSha256)

        Files.createTempFile("test_download_artifact_", ".xml").apply {
            try {
                artifact.download(toFile())
                val actualDownloadFileSha256 = calculateSha256Hash(inputStream())
                assertEquals(expectedSha256, actualDownloadFileSha256)

                artifact.download(outputStream())
                val actualOutputStreamSha256 = calculateSha256Hash(inputStream())
                assertEquals(expectedSha256, actualOutputStreamSha256)
            } finally {
                deleteIfExists() // cleanup
            }
        }
    }

    private fun calculateSha256Hash(stream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(2048)

        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            digest.update(buffer, 0, bytesRead)
        }

        return digest.digest().joinToString(separator = "") { byte ->
            String.format("%02x", byte)
        }
    }

    private fun replaceTeamCityVersionInLog(before: Path, after: Path) {
        val versionText = Regex("TeamCity server version is [\\d.]+ \\(build \\d+\\), server timezone: .*")
        val replacementText = "TeamCity server version is REPLACED (build REPLACED), server timezone: REPLACED"

        Files.newBufferedWriter(after, StandardOpenOption.WRITE).use { writer ->
            Files.lines(before)
                .map { it.replace(versionText, replacementText) }
                .forEach {
                    writer.write(it)
                    writer.newLine()
                }
        }
    }
}
