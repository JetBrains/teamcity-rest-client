package org.jetbrains.teamcity.rest

import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.mime.TypedString
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.regex.Pattern

private val LOG = LoggerFactory.getLogger(TeamCityInstance.javaClass)

private class TeamCityInstanceBuilderImpl(private val serverUrl: String): TeamCityInstanceBuilder {
    private var debug = false
    private var username: String? = null
    private var password: String? = null

    override fun withDebugLogging(): TeamCityInstanceBuilder {
        debug = true
        return this
    }

    override fun httpAuth(username: String, password: String): TeamCityInstanceBuilder {
        this.username = username
        this.password = password
        return this
    }

    override fun build(): TeamCityInstance {
        if (username != null && password != null) {
            val authorization = Base64.encodeBase64String("$username:$password".toByteArray())
            return TeamCityInstanceImpl(serverUrl, "httpAuth", authorization, debug)
        } else {
            return TeamCityInstanceImpl(serverUrl, "guestAuth", null, debug)
        }
    }
}

private class TeamCityInstanceImpl(private val serverUrl: String,
                                   private val authMethod: String,
                                   private val basicAuthHeader: String?,
                                   private val debug: Boolean) : TeamCityInstance {
    private val RestLOG = LoggerFactory.getLogger(LOG.getName() + ".rest")

    private val service = RestAdapter.Builder()
            .setEndpoint("$serverUrl/$authMethod")
            .setLog({ RestLOG.debug(it) })
            .setLogLevel(if (debug) retrofit.RestAdapter.LogLevel.FULL else RestAdapter.LogLevel.NONE)
            .setRequestInterceptor(object : RequestInterceptor {
                override fun intercept(request: RequestInterceptor.RequestFacade) {
                    if (basicAuthHeader != null) {
                        request.addHeader("Authorization", "Basic $basicAuthHeader")
                    }
                }
            })
            .build()
            .create(javaClass<TeamCityService>())

    override fun builds(buildTypeId: BuildTypeId?, buildId: BuildId?, status: BuildStatus?, tags: List<String>?): List<BuildInfo> {
        val parameters = listOf(
                if (buildTypeId != null) "buildType:${buildTypeId.stringId}" else null,
                if (buildId != null) "id:${buildId.stringId}" else null,
                if (status != null) "status:${status.name()}" else null,
                if (tags != null && !tags.isEmpty())
                    tags.joinToString(",", prefix = "tags:(", postfix = ")")
                else null
        ).filterNotNull()

        if (parameters.isEmpty()) {
            throw IllegalArgumentException("At least one parameter should be specified")
        }

        val buildLocator = parameters.joinToString(",")

        LOG.debug("Retrieving builds from $serverUrl using query '$buildLocator'")
        return service.builds(buildLocator).build.map { it.toBuildInfo(service) }
    }

    override fun build(id: BuildId): Build = service.build(id.stringId).toBuild(service)

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
        override val id: ProjectId,
        override val name: String,
        override val archived: Boolean,
        override val parentProjectId: ProjectId,

        override val childProjects: List<ProjectInfo>,
        override val buildTypes: List<BuildTypeInfo>,
        override val parameters: List<Parameter>) : Project {
    override fun project(): Project = this
}

public class ParameterImpl(override val name: String,
                              override val value: String?,
                              override val own: Boolean) : Parameter

private class BuildInfoImpl(override val id: BuildId,
                        private val service: TeamCityService,
                        override val buildNumber: String,
                        override val status: BuildStatus) : BuildInfo {
    override fun build(): Build = service.build(id.stringId).toBuild(service)

    override fun addTag(tag: String) {
        LOG.info("Adding tag $tag to build $buildNumber (id:${id.stringId})")
        service.addTag(id.stringId, TypedString(tag))
    }

    override fun pin(comment: String) {
        LOG.info("Pinning build $buildNumber (id:${id.stringId})")
        service.pin(id.stringId, TypedString(comment))
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
        LOG.info("Downloading artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) to ${output}")

        output.getParentFile().mkdirs()
        val response = service.artifactContent(id.stringId, artifactPath)
        val input = response.getBody().`in`()
        BufferedOutputStream(FileOutputStream(output)).use {
            input.copyTo(it)
        }

        LOG.debug("Artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) downloaded to ${output}")
    }
}

private class BuildImpl(val buildInfo: BuildInfoImpl,
                            override val queuedDate: Date,
                            override val startDate: Date,
                            override val finishDate: Date) : Build, BuildInfo by buildInfo

private class BuildArtifactImpl(private val build: BuildInfo, override val fileName: String) : BuildArtifact {
    fun download(output: File) {
        build.downloadArtifact(fileName, output)
    }
}

private fun convertToJavaRegexp(pattern: String): Pattern {
    return pattern.replaceAll("\\.", "\\.").replaceAll("\\*", ".*").replaceAll("\\?", ".").toRegex()
}
