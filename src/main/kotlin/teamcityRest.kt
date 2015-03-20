package org.jetbrains.teamcity.rest

import retrofit.http.GET
import retrofit.RestAdapter
import retrofit.http.Path
import retrofit.http.Query
import retrofit.RequestInterceptor
import java.util.ArrayList
import org.apache.commons.codec.binary.Base64
import retrofit.http.POST
import retrofit.http.Body
import retrofit.http.PUT
import retrofit.client.Response
import retrofit.mime.TypedString
import retrofit.http.Headers
import retrofit.http.Streaming
import java.io.File
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.regex.Pattern

/**
 * @author nik
 */
public class TeamCityInstance private(private val serverUrl: String, private val authMethod: String, private val basicAuthHeader: String?) {
    private val service = RestAdapter.Builder()
            .setEndpoint("$serverUrl/$authMethod")
            .setRequestInterceptor(object : RequestInterceptor {
                override fun intercept(request: RequestInterceptor.RequestFacade) {
                    if (basicAuthHeader != null) {
                        request.addHeader("Authorization", "Basic $basicAuthHeader")
                    }
                }
            })
            .build()
            .create(javaClass<TeamCityService>())

    fun builds(buildType: String, status: BuildStatus? = BuildStatus.SUCCESS, tag: String? = null): List<Build> {
        val locator = buildLocator(buildType, status, listOf(tag).filterNotNull())
        logLine("Retrieving builds from $serverUrl using query '$locator'")
        return service.builds(locator).build.map { Build(it.id!!, service, it.number!!, it.status!!) }
    }

    class object {
        fun guestAuth(serverUrl: String) = TeamCityInstance(serverUrl, "guestAuth", null)
        fun httpAuth(serverUrl: String, username: String, password: String): TeamCityInstance {
            val authorization = Base64.encodeBase64String("$username:$password".toByteArray())
            return TeamCityInstance(serverUrl, "httpAuth", authorization)
        }
    }
}

public class Build(private val id: String, private val service: TeamCityService, val buildNumber: String, val status: BuildStatus) {
    fun addTag(tag: String) {
        service.addTag(id, TypedString(tag))
        logLine("Added tag $tag to build $buildNumber")
    }

    fun pin(comment: String = "pinned via REST API") {
        service.pin(id, TypedString(comment))
        logLine("Pinned build $buildNumber")
    }

    fun getArtifacts(parentPath: String = ""): List<BuildArtifact> {
        return service.artifactChildren(id, parentPath).file.map { it.name }.filterNotNull().map { BuildArtifact(this, it) }
    }

    fun findArtifact(pattern: String, parentPath: String = ""): BuildArtifact {
        val list = getArtifacts(parentPath)
        val regexp = convertToJavaRegexp(pattern)
        val result = list.filter { regexp.matcher(it.fileName).matches() }
        if (result.isEmpty()) {
            val available = list.map { it.fileName }.joinToString(",")
            throw RuntimeException("Artifact $pattern not found in build $buildNumber. Available artifacts: $available.")
        }
        if (result.size() > 1) {
            val names = result.map { it.fileName }.joinToString(",")
            throw RuntimeException("Several artifacts matching $pattern are found in build $buildNumber: $names.")
        }
        return result.first()
    }

    fun downloadArtifacts(pattern: String, outputDir: File) {
        val list = getArtifacts()
        val regexp = convertToJavaRegexp(pattern)
        val matched = list.filter { regexp.matcher(it.fileName).matches() }
        if (matched.isEmpty()) {
            val available = list.map { it.fileName }.joinToString(",")
            throw RuntimeException("No artifacts matching $pattern are found in build $buildNumber. Available artifacts: $available.")
        }
        outputDir.mkdirs()
        matched.forEach {
            it.download(File(outputDir, it.fileName))
        }
    }

    fun downloadArtifact(artifactPath: String, output: File) {
        output.getParentFile().mkdirs()
        logLine("Downloading artifact '$artifactPath' from build $buildNumber...")
        val response = service.artifactContent(id, artifactPath)
        val input = response.getBody().`in`()
        BufferedOutputStream(FileOutputStream(output)).use {
            input.copyTo(it)
        }
        logLine("Artifact downloaded to $output")
    }
}

public class BuildArtifact(private val build: Build, val fileName: String) {
    fun download(output: File) {
        build.downloadArtifact(fileName, output)
    }
}

public enum class BuildStatus {
    SUCCESS
    FAILURE
    ERROR
}

fun logLine(s: String) {
    println(s)
}

private trait TeamCityService {
    Headers("Accept: application/json")
    GET("/app/rest/builds")
    fun builds(Query("locator") buildLocator: String): BuildListBean

    POST("/app/rest/builds/id:{id}/tags/")
    fun addTag(Path("id") buildId: String, Body tag: TypedString): Response

    PUT("/app/rest/builds/id:{id}/pin/")
    fun pin(Path("id") buildId: String, Body comment: TypedString): Response

    Streaming
    GET("/app/rest/builds/id:{id}/artifacts/content/{path}")
    fun artifactContent(Path("id") buildId: String, Path("path") artifactPath: String): Response

    Headers("Accept: application/json")
    GET("/app/rest/builds/id:{id}/artifacts/children/{path}")
    fun artifactChildren(Path("id") buildId: String, Path("path") artifactPath: String): ArtifactFileListBean
}

private class ArtifactFileListBean {
    var file: List<ArtifactFileBean> = ArrayList()
}

private class ArtifactFileBean {
    var name: String? = null
}

private class BuildListBean {
    var build: List<BuildInfoBean> = ArrayList()
}

private class BuildInfoBean {
    var id: String? = null
    var number: String? = null
    var status: BuildStatus? = null
}

private fun buildLocator(buildTypeId: String, status: BuildStatus? = BuildStatus.SUCCESS, tags: List<String>): String {
    val parameters = listOf(
            "buildType:(id:$buildTypeId)",
            if (status != null) "status:${status.name()}" else null,
            if (tags.isEmpty()) null else tags.joinToString(",", prefix = "tags:(", postfix = ")")
    )
    return parameters.filterNotNull().joinToString(",")
}

private fun convertToJavaRegexp(pattern: String): Pattern {
    return pattern.replaceAll("\\.", "\\.").replaceAll("\\*", ".*").replaceAll("\\?", ".").toRegex()
}
