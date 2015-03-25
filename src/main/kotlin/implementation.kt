package org.jetbrains.teamcity.rest

import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.mime.TypedString
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

private class TeamCityInstanceImpl(private val serverUrl: String, private val authMethod: String, private val basicAuthHeader: String?) : TeamCityInstance {
    private val service = RestAdapter.Builder()
            .setEndpoint("$serverUrl/$authMethod")
            //.setLogLevel(RestAdapter.LogLevel.BASIC)
            .setRequestInterceptor(object : RequestInterceptor {
                override fun intercept(request: RequestInterceptor.RequestFacade) {
                    if (basicAuthHeader != null) {
                        request.addHeader("Authorization", "Basic $basicAuthHeader")
                    }
                }
            })
            .build()
            .create(javaClass<TeamCityService>())

    override fun builds(buildTypeId: BuildTypeId, status: BuildStatus?, tag: String?): List<Build> {
        val locator = buildLocator(buildTypeId, status, listOf(tag).filterNotNull())
        logLine("Retrieving builds from $serverUrl using query '$locator'")
        return service.builds(locator).build.map { BuildImpl(BuildId(it.id!!), service, it.number!!, it.status!!) }
    }

    override fun project(id: ProjectId): Project = service.project(id.stringId).toProject(service)

    override fun rootProject(): Project = project(ProjectId("_Root"))
}

public class ProjectInfoImpl(
        override val id: ProjectId,
        override val name: String,
        override val archived: Boolean,
        override val parentProjectId: ProjectId,
        private val service: TeamCityService): ProjectInfo {
    override fun project(): Project = service.project(id.stringId).toProject(service)
}

public class BuildTypeInfoImpl(
        override val id: BuildTypeId,
        override val name: String,
        override val projectId: ProjectId,
        private val service: TeamCityService) : BuildTypeInfo {
    override fun buildTags(): List<String> = service.buildTypeTags(id.stringId).tag!!.map { it.name!! }
}

public class ProjectImpl(
        override val info: ProjectInfo,
        override val childProjects: List<ProjectInfo>,
        override val buildTypes: List<BuildTypeInfo>,
        override val parameters: List<PropertyInfo>) : Project

public class PropertyInfoImpl(override val name: String,
                              override val value: String?,
                              override val own: Boolean) : PropertyInfo

private class BuildImpl(private val id: BuildId,
                        private val service: TeamCityService,
                        override val buildNumber: String,
                        override val status: BuildStatus) : Build {
    override fun addTag(tag: String) {
        service.addTag(id.stringId, TypedString(tag))
        logLine("Added tag $tag to build $buildNumber")
    }

    override fun pin(comment: String) {
        service.pin(id.stringId, TypedString(comment))
        logLine("Pinned build $buildNumber")
    }

    override fun getArtifacts(parentPath: String): List<BuildArtifactImpl> {
        return service.artifactChildren(id.stringId, parentPath).file.map { it.name }.filterNotNull().map { BuildArtifactImpl(this, it) }
    }

    override fun findArtifact(pattern: String, parentPath: String): BuildArtifact {
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

    override fun downloadArtifacts(pattern: String, outputDir: File) {
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

    override fun downloadArtifact(artifactPath: String, output: File) {
        output.getParentFile().mkdirs()
        logLine("Downloading artifact '$artifactPath' from build $buildNumber...")
        val response = service.artifactContent(id.stringId, artifactPath)
        val input = response.getBody().`in`()
        BufferedOutputStream(FileOutputStream(output)).use {
            input.copyTo(it)
        }
        logLine("Artifact downloaded to $output")
    }
}

private class BuildArtifactImpl(private val build: Build, override val fileName: String) : BuildArtifact {
    fun download(output: File) {
        build.downloadArtifact(fileName, output)
    }
}

private fun buildLocator(buildTypeId: BuildTypeId, status: BuildStatus? = BuildStatus.SUCCESS, tags: List<String>): String {
    val parameters = listOf(
            if (status != null) "status:${status.name()}" else null,
            if (tags.isEmpty()) null else tags.joinToString(",", prefix = "tags:(", postfix = ")")
    )

    return "buildType:${buildTypeId.stringId}," + parameters.filterNotNull().joinToString(",")
}

private fun convertToJavaRegexp(pattern: String): Pattern {
    return pattern.replaceAll("\\.", "\\.").replaceAll("\\*", ".*").replaceAll("\\?", ".").toRegex()
}

fun logLine(s: String) {
    println(s)
}
