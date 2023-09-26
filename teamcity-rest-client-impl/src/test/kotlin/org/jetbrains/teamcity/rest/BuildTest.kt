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
import java.security.MessageDigest
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
    fun test_download_artifact() {
        val build = customInstanceByConnectionFile().build(BuildId("422"))
        val nonHiddenArtifacts = build.getArtifacts()
        assertTrue(nonHiddenArtifacts.isEmpty())

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
}
