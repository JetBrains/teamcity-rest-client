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

private val teamCityServiceDateFormat =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat? = SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.ENGLISH)
        }

internal fun createGuestAuthInstance(serverUrl: String): TeamCityInstanceImpl {
    return TeamCityInstanceImpl(serverUrl, "guestAuth", null, false)
}

internal fun createHttpAuthInstance(serverUrl: String, username: String, password: String): TeamCityInstanceImpl {
    val authorization = Base64.encodeBase64String("$username:$password".toByteArray())
    return TeamCityInstanceImpl(serverUrl, "httpAuth", authorization, false)
}

internal class TeamCityInstanceImpl(private val serverUrl: String,
                                    private val authMethod: String,
                                    private val basicAuthHeader: String?,
                                    private val logResponces : Boolean) : TeamCityInstance {
    override fun withLogResponses() = TeamCityInstanceImpl(serverUrl, authMethod, basicAuthHeader, true)

    private val RestLOG = LoggerFactory.getLogger(LOG.name + ".rest")

    private val service = RestAdapter.Builder()
            .setEndpoint("$serverUrl/$authMethod")
            .setLog({ RestLOG.debug(it) })
            .setLogLevel(if (logResponces) retrofit.RestAdapter.LogLevel.FULL else retrofit.RestAdapter.LogLevel.HEADERS_AND_ARGS)
            .setRequestInterceptor({ request ->
                if (basicAuthHeader != null) {
                    request.addHeader("Authorization", "Basic $basicAuthHeader")
                }
            })
            .setErrorHandler({ throw Error("Failed to connect to ${it.url}: ${it.message}", it) })
            .build()
            .create(TeamCityService::class.java)

    override fun queuedBuilds(): QueuedBuildLocator = QueuedBuildsLocatorImpl(service, serverUrl)

    override fun builds(): BuildLocator = BuildLocatorImpl(service, serverUrl)

    override fun build(id: BuildId): Build = BuildImpl(service.build(id.stringId), true, service)

    override fun project(id: ProjectId): Project = ProjectImpl(service.project(id.stringId), true, service)

    override fun rootProject(): Project = project(ProjectId("_Root"))
}

private open class LocatorImpl(): Locator {
    private var buildConfigurationId: BuildConfigurationId? = null
    private var status: BuildStatus? = null
    private var tags = ArrayList<String>()
    private var count: Int? = null
    private var branch: String? = null
    private var includeAllBranches = false
    private var sinceDate: String? = null

    override fun fromConfiguration(buildConfigurationId: BuildConfigurationId): Locator {
        this.buildConfigurationId = buildConfigurationId
        return this
    }

    override fun withAnyStatus(): Locator {
        status = null
        return this
    }

    override fun withStatus(status: BuildStatus): Locator {
        this.status = status
        return this
    }

    override fun withTag(tag: String): Locator {
        tags.add(tag)
        return this
    }

    override fun withBranch(branch: String): Locator {
        this.branch = branch
        return this
    }

    override fun withAllBranches(): Locator {
        if (branch != null) {
            LOG.warn("Branch is ignored because of #withAllBranches")
        }

        this.includeAllBranches = true
        return this
    }

    override fun limitResults(count: Int): Locator {
        this.count = count
        return this
    }

    override fun sinceDate(sinceDate: Date): Locator {
        // ISO8601-sorta date needs to be passed to REST API
        val tz = TimeZone.getTimeZone("UTC")
        val df = SimpleDateFormat("yyyyMMdd'T'HHmmssZ")
        df.setTimeZone(tz)
        this.sinceDate = df.format(sinceDate)
        return this
    }

    override fun build() : String? {
        val parameters = listOf(
                buildConfigurationId?.stringId?.let {"buildType:$it"},
                status?.name?.let {"status:$it"},
                if (!tags.isEmpty())
                    tags.joinToString(",", prefix = "tags:(", postfix = ")")
                else null,
                count?.let {"count:$it"},
                sinceDate?.let {"sinceDate:$it"},

                if (!includeAllBranches)
                    branch?.let {"branch:$it"}
                else
                    "branch:default:any"
        ).filterNotNull()

        if (parameters.isEmpty()) {
            return null;
        }

        return parameters.joinToString(",")
    }
}

private class BuildLocatorImpl(private val service: TeamCityService, private val serverUrl: String)
        : BuildLocator, LocatorImpl() {
    override fun latest(): Build? {
        limitResults(1)
        return list().firstOrNull()
    }

    override fun list(): List<Build> {
        val buildLocator = build();
        LOG.debug("Retrieving builds from $serverUrl using query '$buildLocator'")
        return service.builds(buildLocator).build.map { BuildImpl(it, false, service) }
    }
}

private class QueuedBuildsLocatorImpl(private val service: TeamCityService, private val serverUrl: String)
        : QueuedBuildLocator, LocatorImpl() {
    override fun latest(): QueuedBuild? {
        limitResults(1)
        return list().firstOrNull()
    }

    override fun list(): List<QueuedBuild> {
        val buildLocator = build();
        LOG.debug("Retrieving builds from $serverUrl using query '$buildLocator'")
        return service.queuedBuilds(buildLocator).build.map { QueuedBuildImpl(it, false, service) }
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
        get() = teamCityServiceDateFormat.get().parse(bean.date!!)

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

    override val buildConfigurationId: String
        get() = bean.buildTypeId!!

    override val branch: Branch
        get() = object:Branch {
            override val isDefault: Boolean
                get() = bean.isDefaultBranch ?: name == null

            override val name: String?
                get() = bean.branchName
        }

    val fullBuildBean: BuildBean by lazy {
        if (isFullBuildBean) bean else service.build(id.stringId)
    }

    override fun toString(): String {
        return "Build{id=${bean.id}, number=${bean.number}, state=${bean.status}, typeId=${bean.buildTypeId}, branch=${bean.branchName}}"
    }

    override fun fetchQueuedDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.queuedDate!!)
    override fun fetchStartDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.startDate!!)
    override fun fetchFinishDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.finishDate!!)

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
        return service.artifactChildren(id.stringId, parentPath).file.filter { it.name != null && it.modificationTime != null }.map {
            BuildArtifactImpl(this, it.name!!, it.size, teamCityServiceDateFormat.get().parse(it.modificationTime!!))
        }
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

private class QueuedBuildImpl(private val bean: QueuedBuildBean,
                              private val isFullBean: Boolean,
                              private val service: TeamCityService) : QueuedBuild {
    override val id: BuildId
        get() = BuildId(bean.id!!)

    override val buildConfigurationId: String
        get() = bean.buildTypeId!!

    override val status: QueuedBuildStatus
        get() = bean.state!!

    override val branch: Branch
        get() = object:Branch {
            override val isDefault: Boolean
                get() = bean.isDefaultBranch ?: name == null

            override val name: String?
                get() = bean.branchName
        }

    override fun toString(): String {
        return "QueuedBuild{id=${bean.id}, typeId=${bean.buildTypeId}, state=${bean.state}, branch=${bean.branchName}}"
    }
}

private class BuildArtifactImpl(private val build: Build, override val fileName: String, override val size: Long?, override val modificationTime: Date) : BuildArtifact {
    override fun download(output: File) {
        build.downloadArtifact(fileName, output)
    }
}

private fun convertToJavaRegexp(pattern: String): Regex {
    return pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".").toRegex()
}