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
                                    private val logResponses : Boolean) : TeamCityInstance() {
    override fun withLogResponses() = TeamCityInstanceImpl(serverUrl, authMethod, basicAuthHeader, true)

    private val RestLOG = LoggerFactory.getLogger(LOG.name + ".rest")

    private val service = RestAdapter.Builder()
            .setEndpoint("$serverUrl/$authMethod")
            .setLog({ RestLOG.debug(it) })
            .setLogLevel(if (logResponses) retrofit.RestAdapter.LogLevel.FULL else retrofit.RestAdapter.LogLevel.HEADERS_AND_ARGS)
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

    override fun build(buildType: BuildConfigurationId, number: String): Build = BuildImpl(service.build(buildType.stringId, number), true, service)

    override fun buildConfiguration(id: BuildConfigurationId): BuildConfiguration = BuildConfigurationImpl(service.buildConfiguration(id.stringId), service)

    override fun vcsRoots(): VcsRootLocator = VcsRootLocatorImpl(service)

    override fun vcsRoot(id: VcsRootId): VcsRoot = VcsRootImpl(service.vcsRoot(id.stringId))

    override fun project(id: ProjectId): Project = ProjectImpl(service.project(id.stringId), true, service)

    override fun rootProject(): Project = project(ProjectId("_Root"))

    override fun buildQueue(): BuildQueue = BuildQueueImpl(service)

    override fun buildResults(): BuildResults = BuildResultsImpl(service)
}

private class BuildLocatorImpl(private val service: TeamCityService, private val serverUrl: String): BuildLocator {
    private var buildConfigurationId: BuildConfigurationId? = null
    private var status: BuildStatus? = BuildStatus.SUCCESS
    private var tags = ArrayList<String>()
    private var count: Int? = null
    private var branch: String? = null
    private var includeAllBranches = false
    private var pinnedOnly = false

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

    override fun withAllBranches(): BuildLocator {
        if (branch != null) {
            LOG.warn("Branch is ignored because of #withAllBranches")
        }

        this.includeAllBranches = true
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
                if (pinnedOnly) "pinned:true" else null,
                count?.let {"count:$it"},

                if (!includeAllBranches)
                    branch?.let {"branch:$it"}
                else
                    "branch:default:any"
        ).filterNotNull()

        if (parameters.isEmpty()) {
            throw IllegalArgumentException("At least one parameter should be specified")
        }

        val buildLocator = parameters.joinToString(",")
        LOG.debug("Retrieving builds from $serverUrl using query '$buildLocator'")
        return service.builds(buildLocator).build.map { BuildImpl(it, false, service) }
    }

    override fun pinnedOnly(): BuildLocator {
        this.pinnedOnly = true
        return this
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

    override fun setParameter(name: String, value: String) {
        LOG.info("Setting parameter $name=$value in ${bean.id}")
        service.setProjectParameter(id.stringId, name, TypedString(value))
    }
}

private class BuildConfigurationImpl(private val bean: BuildTypeBean, private val service: TeamCityService) : BuildConfiguration {
    override val name: String
        get() = bean.name!!

    override val projectId: ProjectId
        get() = ProjectId(bean.projectId!!)

    override val id: BuildConfigurationId
        get() = BuildConfigurationId(bean.id!!)

    override val paused: Boolean
        get() = bean.paused

    override fun fetchBuildTags(): List<String> = service.buildTypeTags(id.stringId).tag!!.map { it.name!! }

    override fun setParameter(name: String, value: String) {
        LOG.info("Setting parameter $name=$value in ${bean.id}")
        service.setBuildTypeParameter(id.stringId, name, TypedString(value))
    }
}

private class VcsRootLocatorImpl(private val service: TeamCityService): VcsRootLocator {

    override fun list(): List<VcsRoot> {
        return service.vcsRoots().vcsRoot.map(::VcsRootImpl)
    }
}

private class ChangeImpl(private val bean: ChangeBean) : Change {
    override val id: ChangeId
        get() = ChangeId(bean.id!!)

    override val version: String
        get() = bean.version!!

    override val username: String
        get() = bean.username!!

    override val user: User?
        get() = bean.user?.let { UserImpl(it) }

    override val date: Date
        get() = teamCityServiceDateFormat.get().parse(bean.date!!)

    override val comment: String
        get() = bean.comment!!

    override fun toString() =
            "id=$id, version=$version, username=$username, user=$user, date=$date, comment=$comment"
}

private class UserImpl(private val bean: UserBean) : User {
    override val id: String
        get() = bean.id!!

    override val username: String
        get() = bean.username!!

    override val name: String
        get() = bean.name!!
}

private class PinInfoImpl(bean: PinInfoBean) : PinInfo {
    override val user = UserImpl(bean.user!!)
    override val time = teamCityServiceDateFormat.get().parse(bean.timestamp!!)
}

private class TriggeredImpl(private val bean: TriggeredBean, private val service: TeamCityService) : TriggeredInfo {
    override val user: User?
        get() = if (bean.user != null) UserImpl(bean.user!!) else null
    override val build: Build?
        get() = if (bean.build != null) BuildImpl(bean.build, false, service) else null
}

private class TriggeredBuildImpl(private val bean: TriggeredBuildBean): TriggeredBuild {
    override val id: Int
        get() = bean.id!!
    override val buildTypeId: String
        get() = bean.buildTypeId!!
    override fun toString() =
            "TriggeredBuildImpl{id=$id, buildTypeId=$buildTypeId}"
}

private class ParameterImpl(private val bean: ParameterBean) : Parameter {
    override val name: String
        get() = bean.name!!

    override val value: String?
        get() = bean.value!!

    override val own: Boolean
        get() = bean.own
}

private class RevisionImpl(private val bean: RevisionBean) : Revision {
    override val version: String
        get() = bean.version!!

    override val vcsBranchName: String
        get() = bean.vcsBranchName!!

    override val vcsRoot: VcsRoot
        get() = VcsRootImpl(bean.`vcs-root-instance`!!)
}

private data class BranchImpl(
        override val name: String?,
        override val isDefault: Boolean): Branch

private class BuildImpl(private val bean: BuildBean,
                        private val isFullBuildBean: Boolean,
                        private val service: TeamCityService) : Build {
    override val id: BuildId
        get() = BuildId(bean.id!!)

    override val buildTypeId: String
        get() = bean.buildTypeId!!

    override val buildNumber: String
        get() = bean.number!!

    override val status: BuildStatus
        get() = bean.status!!

    override val state: String
        get() = bean.state!!

    override val webUrl: String
        get() = bean.webUrl!!

    override val branch: Branch
        get() = BranchImpl(bean.branchName, bean.isDefaultBranch ?: (bean.branchName == null))

    val fullBuildBean: BuildBean by lazy {
        if (isFullBuildBean) bean else service.build(id.stringId)
    }

    override fun toString(): String {
        return "Build{id=$id, buildTypeId=$buildTypeId, buildNumber=$buildNumber, status=$status, branch=$branch}"
    }

    override fun fetchStatusText(): String = fullBuildBean.statusText!!
    override fun fetchQueuedDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.queuedDate!!)
    override fun fetchStartDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.startDate!!)
    override fun fetchFinishDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.finishDate!!)
    override fun fetchPinInfo() = fullBuildBean.pinInfo?.let {PinInfoImpl(it)}
    override fun fetchTriggeredInfo() = fullBuildBean.triggered?.let {TriggeredImpl(it, service)}

    override fun fetchTests() = service.tests(locator = "build:(id:${id.stringId})", fields = TestOccurrence.filter).testOccurrence.map {
        object : TestInfo {
            override val name = it.name!!
            override val status = when {
                it.ignored == true -> TestStatus.IGNORED
                it.status == "FAILURE" -> TestStatus.FAILED
                it.status == "SUCCESS" -> TestStatus.SUCCESSFUL
                else -> TestStatus.UNKNOWN
            }
            override val duration = it.duration ?: 1L

            override val details = when (status) {
                TestStatus.IGNORED -> it.ignoreDetails
                TestStatus.FAILED -> it.details
                else -> null
            } ?: ""

            override fun toString() = "Test{name=$name, status=$status, duration=$duration, details=$details}"
        }
    }

    override fun fetchParameters(): List<Parameter> = fullBuildBean.properties!!.property!!.map { ParameterImpl(it) }

    override fun fetchRevisions(): List<Revision> = fullBuildBean.revisions!!.revision!!.map { RevisionImpl(it) }

    override fun fetchChanges(): List<Change> = service.changes(
            "build:${id.stringId}",
            "change(id,version,username,user,date,comment)")
            .change!!.map { ChangeImpl(it) }

    override fun addTag(tag: String) {
        LOG.info("Adding tag $tag to build $buildNumber (id:${id.stringId})")
        service.addTag(id.stringId, TypedString(tag))
    }

    override fun pin(comment: String) {
        LOG.info("Pinning build $buildNumber (id:${id.stringId})")
        service.pin(id.stringId, TypedString(comment))
    }

    override fun unpin(comment: String) {
        LOG.info("Unpinning build $buildNumber (id:${id.stringId})")
        service.unpin(id.stringId, TypedString(comment))
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

private class VcsRootImpl(private val bean: VcsRootBean) : VcsRoot {

    override val id: VcsRootId
        get() = VcsRootId(bean.id!!)

    override val name: String
        get() = bean.name!!
}

private class BuildArtifactImpl(private val build: Build, override val fileName: String, override val size: Long?, override val modificationTime: Date) : BuildArtifact {
    override fun download(output: File) {
        build.downloadArtifact(fileName, output)
    }
}

private class BuildQueueImpl(private val service: TeamCityService): BuildQueue {
    override fun triggerBuild(triggerRequest: TriggerRequest): TriggeredBuild {
          return TriggeredBuildImpl(service.triggerBuild(triggerRequest))
    }
}

private class BuildResultsImpl(private val service: TeamCityService): BuildResults {
    override fun tests(id: BuildId) {
        service.tests("build:${id.stringId}")
    }
}

private fun convertToJavaRegexp(pattern: String): Regex {
    return pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".").toRegex()
}
