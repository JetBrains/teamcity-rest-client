package org.jetbrains.teamcity.rest

import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import retrofit.RestAdapter
import retrofit.mime.TypedString
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private val LOG = LoggerFactory.getLogger("teamcity-rest-client")

private val teamCityServiceDateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.ENGLISH)

internal fun createGuestAuthInstance(serverUrl: String): TeamCityInstanceImpl {
    return TeamCityInstanceImpl(serverUrl, "guestAuth", null)
}

internal fun createHttpAuthInstance(serverUrl: String, username: String, password: String): TeamCityInstanceImpl {
    val authorization = Base64.encodeBase64String("$username:$password".toByteArray())
    return TeamCityInstanceImpl(serverUrl, "httpAuth", authorization)
}

internal class TeamCityInstanceImpl(private val serverUrl: String,
                                   private val authMethod: String,
                                   private val basicAuthHeader: String?) : TeamCityInstance {
    private val RestLOG = LoggerFactory.getLogger(LOG.name + ".rest")

    private val service = RestAdapter.Builder()
            .setEndpoint("$serverUrl/$authMethod")
            .setLog({ RestLOG.debug(it) })
            .setLogLevel(retrofit.RestAdapter.LogLevel.HEADERS_AND_ARGS)
            .setRequestInterceptor({ request ->
                if (basicAuthHeader != null) {
                    request.addHeader("Authorization", "Basic $basicAuthHeader")
                }
            })
            .setErrorHandler({ throw Error("Failed to connect to ${it.url}: ${it.message}", it) })
            .build()
            .create(TeamCityService::class.java)

    override fun builds(): BuildLocator = BuildLocatorImpl(service, serverUrl)

    override fun build(id: BuildId): Build = BuildImpl(service.build(id.stringId), true, service)

    override fun project(id: ProjectId): Project = ProjectImpl(service.project(id.stringId), true, service)

    override fun rootProject(): Project = project(ProjectId("_Root"))
}

private class BuildLocatorImpl(private val service: TeamCityService, private val serverUrl: String): BuildLocator {
    private var buildConfigurationId: BuildConfigurationId? = null
    private var status: BuildStatus? = BuildStatus.SUCCESS
    private var tags = ArrayList<String>()
    private var count: Int? = null
    private var branch: String? = null

    override fun fromConfiguration(buildConfigurationId: BuildConfigurationId): BuildLocator {
        this.buildConfigurationId = buildConfigurationId
        return this
    }

    override fun withAnyStatus(): BuildLocator {
        status = null
        return this
    }

    override fun withStatus(status: BuildStatus): BuildLocator {
        this.status = status
        return this
    }

    override fun withTag(tag: String): BuildLocator {
        tags.add(tag)
        return this
    }

    override fun withBranch(branch: String): BuildLocator {
        this.branch = branch
        return this
    }

    override fun limitResults(count: Int): BuildLocator {
        this.count = count
        return this
    }

    override fun latest(): Build? {
        return limitResults(1).list().firstOrNull()
    }

    override fun list(): List<Build> {
        val parameters = listOf(
                buildConfigurationId?.stringId?.let {"buildType:$it"},
                status?.name?.let {"status:$it"},
                if (!tags.isEmpty())
                    tags.joinToString(",", prefix = "tags:(", postfix = ")")
                else null,
                count?.let {"count:$it"},
                branch?.let {"branch:$it"}
        ).filterNotNull()

        if (parameters.isEmpty()) {
            throw IllegalArgumentException("At least one parameter should be specified")
        }

        val buildLocator = parameters.joinToString(",")
        LOG.debug("Retrieving builds from $serverUrl using query '$buildLocator'")
        return service.builds(buildLocator).build.map { BuildImpl(it, false, service) }
    }
}

private class ProjectImpl(
        private val bean: ProjectBean,
        private val isFullProjectBean: Boolean,
        private val service: TeamCityService) : Project {

    override val id: ProjectId
        get() = ProjectId(bean.id!!)

    override val name: String
        get() = bean.name!!

    override val archived: Boolean
        get() = bean.archived

    override val parentProjectId: ProjectId
        get() = ProjectId(bean.parentProjectId!!)

    val fullProjectBean: ProjectBean by lazy {
        if (isFullProjectBean) bean else service.project(id.stringId)
    }

    override fun fetchChildProjects(): List<Project> = fullProjectBean.projects!!.project.map { ProjectImpl(it, false, service) }
    override fun fetchBuildConfigurations(): List<BuildConfiguration> = fullProjectBean.buildTypes!!.buildType.map { BuildConfigurationImpl(it, service) }
    override fun fetchParameters(): List<Parameter> = fullProjectBean.parameters!!.property!!.map { ParameterImpl(it) }
}

private class BuildConfigurationImpl(private val bean: BuildTypeBean, private val service: TeamCityService) : BuildConfiguration {
    override val name: String
        get() = bean.name!!

    override val projectId: ProjectId
        get() = ProjectId(bean.projectId!!)

    override val id: BuildConfigurationId
        get() = BuildConfigurationId(bean.id!!)

    override fun fetchBuildTags(): List<String> = service.buildTypeTags(id.stringId).tag!!.map { it.name!! }
}

private class ChangeImpl(private val bean: ChangeBean) : Change {
    override val id: ChangeId
        get() = ChangeId(bean.id!!)

    override val version: String
        get() = bean.version!!

    override val user: User
        get() = UserImpl(bean.user!!)

    override val date: Date
        get() = teamCityServiceDateFormat.parse(bean.date!!)

    override val comment: String
        get() = bean.comment!!
}

private class UserImpl(private val bean: UserBean) : User {
    override val id: String
        get() = bean.id!!

    override val username: String
        get() = bean.username!!

    override val name: String
        get() = bean.name!!
}

private class ParameterImpl(private val bean: ParameterBean) : Parameter {
    override val name: String
        get() = bean.name!!

    override val value: String?
        get() = bean.value!!

    override val own: Boolean
        get() = bean.own
}

private class BuildImpl(private val bean: BuildBean,
                        private val isFullBuildBean: Boolean,
                        private val service: TeamCityService) : Build {
    override val id: BuildId
        get() = BuildId(bean.id!!)

    override val buildNumber: String
        get() = bean.number!!

    override val status: BuildStatus
        get() = bean.status!!

    val fullBuildBean: BuildBean by lazy {
        if (isFullBuildBean) bean else service.build(id.stringId)
    }

    override fun fetchQueuedDate(): Date = teamCityServiceDateFormat.parse(fullBuildBean.queuedDate!!)
    override fun fetchStartDate(): Date = teamCityServiceDateFormat.parse(fullBuildBean.startDate!!)
    override fun fetchFinishDate(): Date = teamCityServiceDateFormat.parse(fullBuildBean.finishDate!!)

    override fun fetchParameters(): List<Parameter> = fullBuildBean.properties!!.property!!.map { ParameterImpl(it) }

    override fun fetchChanges(): List<Change> = service.changes("build:${id.stringId}", "change(id,version,user,date,comment)").change!!.map { ChangeImpl(it) }

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
        val result = list.filter { regexp.matches(it.fileName) }
        if (result.isEmpty()) {
            val available = list.map { it.fileName }.joinToString(",")
            throw RuntimeException("Artifact $pattern not found in build $buildNumber. Available artifacts: $available.")
        }
        if (result.size > 1) {
            val names = result.map { it.fileName }.joinToString(",")
            throw RuntimeException("Several artifacts matching $pattern are found in build $buildNumber: $names.")
        }
        return result.first()
    }

    override fun downloadArtifacts(pattern: String, outputDir: File) {
        val list = getArtifacts()
        val regexp = convertToJavaRegexp(pattern)
        val matched = list.filter { regexp.matches(it.fileName) }
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
        LOG.info("Downloading artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) to $output")

        output.parentFile.mkdirs()
        val response = service.artifactContent(id.stringId, artifactPath)
        val input = response.body.`in`()
        BufferedOutputStream(FileOutputStream(output)).use {
            input.copyTo(it)
        }

        LOG.debug("Artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) downloaded to $output")
    }
}

private class BuildArtifactImpl(private val build: Build, override val fileName: String) : BuildArtifact {
    override fun download(output: File) {
        build.downloadArtifact(fileName, output)
    }
}

private fun convertToJavaRegexp(pattern: String): Regex {
    return pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".").toRegex()
}
