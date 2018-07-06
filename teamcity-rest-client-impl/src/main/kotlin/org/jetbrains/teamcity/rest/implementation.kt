package org.jetbrains.teamcity.rest

import com.jakewharton.retrofit.Ok3Client
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import retrofit.RestAdapter
import retrofit.mime.TypedString
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit.SECONDS

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

private class RetryInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)

        var tryCount = 0
        while (!response.isSuccessful && tryCount < 3) {
            tryCount++
            LOG.warn("Request ${request.url()} is not successful, $tryCount sec waiting [$tryCount retry]")
            Thread.sleep((tryCount * 1000).toLong())
            response = chain.proceed(request)
        }

        return response
    }
}

internal class TeamCityInstanceImpl(internal val serverUrl: String,
                                    private val authMethod: String,
                                    private val basicAuthHeader: String?,
                                    logResponses: Boolean) : TeamCityInstance() {
    override fun withLogResponses() = TeamCityInstanceImpl(serverUrl, authMethod, basicAuthHeader, true)

    private val RestLOG = LoggerFactory.getLogger(LOG.name + ".rest")

    private var client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .also {
                it.readTimeout(60, SECONDS)
                it.writeTimeout(60, SECONDS)
            }
            .build()

    internal val service = RestAdapter.Builder()
            .setClient(Ok3Client(client))
            .setEndpoint("$serverUrl/$authMethod")
            .setLog { RestLOG.debug(if (basicAuthHeader != null) it.replace(basicAuthHeader, "[REDACTED]") else it) }
            .setLogLevel(if (logResponses) retrofit.RestAdapter.LogLevel.FULL else retrofit.RestAdapter.LogLevel.HEADERS_AND_ARGS)
            .setRequestInterceptor { request ->
                if (basicAuthHeader != null) {
                    request.addHeader("Authorization", "Basic $basicAuthHeader")
                }
            }
            .setErrorHandler { throw TeamCityConversationException("Failed to connect to ${it.url}: ${it.message}", it) }
            .build()
            .create(TeamCityService::class.java)

    override fun builds(): BuildLocator = BuildLocatorImpl(this)

    override fun queuedBuilds(projectId: ProjectId?): List<QueuedBuild> {
        val locator = if (projectId == null) null else "project:${projectId.stringId}"
        return service.queuedBuilds(locator).build.map { QueuedBuildImpl(it, this) }
    }

    override fun build(id: BuildId): Build = BuildImpl(service.build(id.stringId), true, this)

    override fun build(buildType: BuildConfigurationId, number: String): Build?
            = BuildLocatorImpl(this).fromConfiguration(buildType).withNumber(number).latest()

    override fun buildConfiguration(id: BuildConfigurationId):
            BuildConfiguration = BuildConfigurationImpl(service.buildConfiguration(id.stringId), this)

    override fun vcsRoots(): VcsRootLocator = VcsRootLocatorImpl(service, this)

    override fun vcsRoot(id: VcsRootId): VcsRoot = VcsRootImpl(service.vcsRoot(id.stringId), true, this)

    override fun project(id: ProjectId): Project = ProjectImpl(service.project(id.stringId), true, this)

    override fun rootProject(): Project = project(ProjectId("_Root"))

    override fun change(buildType: BuildConfigurationId, vcsRevision: String): Change = 
            ChangeImpl(service.change(buildType.stringId, vcsRevision), this)

    override fun getWebUrl(projectId: ProjectId, branch: String?): String =
        getUserUrlPage(serverUrl, "project.html", projectId = projectId, branch = branch)

    override fun getWebUrl(buildConfigurationId: BuildConfigurationId, branch: String?): String =
        getUserUrlPage(serverUrl, "viewType.html", buildTypeId = buildConfigurationId, branch = branch)

    override fun getWebUrl(buildId: BuildId): String =
        getUserUrlPage(
                serverUrl, "viewLog.html",
                buildId = buildId,
                tab = "buildResultsDiv"
        )

    override fun getWebUrl(queuedBuildId: QueuedBuildId): String =
        getUserUrlPage(serverUrl, "viewQueued.html", itemId = queuedBuildId)

    override fun getWebUrl(changeId: ChangeId, specificBuildConfigurationId: BuildConfigurationId?, includePersonalBuilds: Boolean?): String =
        getUserUrlPage(
                serverUrl, "viewModification.html",
                modId = changeId,
                buildTypeId = specificBuildConfigurationId,
                personal = includePersonalBuilds)
}

private class BuildLocatorImpl(private val instance: TeamCityInstanceImpl) : BuildLocator {
    private var buildConfigurationId: BuildConfigurationId? = null
    private var number: String? = null
    private var vcsRevision: String? = null
    private var sinceDate: Date? = null
    private var status: BuildStatus? = BuildStatus.SUCCESS
    private var tags = ArrayList<String>()
    private var count: Int? = null
    private var branch: String? = null
    private var includeAllBranches = false
    private var pinnedOnly = false

    override fun fromConfiguration(buildConfigurationId: BuildConfigurationId): BuildLocatorImpl {
        this.buildConfigurationId = buildConfigurationId
        return this
    }

    override fun withNumber(buildNumber: String): BuildLocator {
        this.number = buildNumber
        return this
    }

    override fun withVcsRevision(vcsRevision: String): BuildLocator {
        this.vcsRevision = vcsRevision
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

    override fun sinceDate(date: Date): BuildLocator {
        this.sinceDate = date
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
        val parameters = listOfNotNull(
                buildConfigurationId?.stringId?.let { "buildType:$it" },
                number?.let { "number:$it" },
                vcsRevision?.let { "revision:$it" },
                status?.name?.let { "status:$it" },
                if (!tags.isEmpty())
                    tags.joinToString(",", prefix = "tags:(", postfix = ")")
                else null,
                if (pinnedOnly) "pinned:true" else null,
                count?.let { "count:$it" },

                sinceDate?.let {"sinceDate:${teamCityServiceDateFormat.get().format(sinceDate)}"},

                if (!includeAllBranches)
                    branch?.let { "branch:$it" }
                else
                    "branch:default:any"
        )

        if (parameters.isEmpty()) {
            throw IllegalArgumentException("At least one parameter should be specified")
        }

        val buildLocator = parameters.joinToString(",")
        LOG.debug("Retrieving builds from ${instance.serverUrl} using query '$buildLocator'")
        return instance.service.builds(buildLocator).build.map { BuildImpl(it, false, instance) }
    }

    override fun pinnedOnly(): BuildLocator {
        this.pinnedOnly = true
        return this
    }
}

private class ProjectImpl(
        private val bean: ProjectBean,
        private val isFullProjectBean: Boolean,
        private val instance: TeamCityInstanceImpl) : Project {

    override fun getWebUrl(branch: String?): String = instance.getWebUrl(id, branch = branch)

    override val id: ProjectId
        get() = ProjectId(bean.id!!)

    override val name: String
        get() = bean.name!!

    override val archived: Boolean
        get() = bean.archived

    override val parentProjectId: ProjectId
        get() = ProjectId(bean.parentProjectId!!)

    val fullProjectBean: ProjectBean by lazy {
        if (isFullProjectBean) bean else instance.service.project(id.stringId)
    }

    override fun fetchChildProjects(): List<Project> = fullProjectBean.projects!!.project.map { ProjectImpl(it, false, instance) }
    override fun fetchBuildConfigurations(): List<BuildConfiguration> = fullProjectBean.buildTypes!!.buildType.map { BuildConfigurationImpl(it, instance) }
    override fun fetchParameters(): List<Parameter> = fullProjectBean.parameters!!.property!!.map { ParameterImpl(it) }

    override fun setParameter(name: String, value: String) {
        LOG.info("Setting parameter $name=$value in ${bean.id}")
        instance.service.setProjectParameter(id.stringId, name, TypedString(value))
    }
}

private class BuildConfigurationImpl(private val bean: BuildTypeBean,
                                     private val instance: TeamCityInstanceImpl) : BuildConfiguration {
    override fun getWebUrl(branch: String?): String = instance.getWebUrl(id, branch = branch)

    override val name: String
        get() = bean.name!!

    override val projectId: ProjectId
        get() = ProjectId(bean.projectId!!)

    override val id: BuildConfigurationId
        get() = BuildConfigurationId(bean.id!!)

    override val paused: Boolean
        get() = bean.paused

    override fun fetchBuildTags(): List<String> = instance.service.buildTypeTags(id.stringId).tag!!.map { it.name!! }

    override fun fetchFinishBuildTriggers(): List<FinishBuildTrigger> =
            instance.service.buildTypeTriggers(id.stringId)
                    .trigger
                    ?.filter { it.type == "buildDependencyTrigger" }
                    ?.map { FinishBuildTriggerImpl(it) }.orEmpty()

    override fun fetchArtifactDependencies(): List<ArtifactDependency> =
            instance.service
                    .buildTypeArtifactDependencies(id.stringId)
                    .`artifact-dependency`
                    ?.filter { it.disabled == false }
                    ?.map { ArtifactDependencyImpl(it, instance) }.orEmpty()

    override fun setParameter(name: String, value: String) {
        LOG.info("Setting parameter $name=$value in ${bean.id}")
        instance.service.setBuildTypeParameter(id.stringId, name, TypedString(value))
    }
}

private class VcsRootLocatorImpl(private val service: TeamCityService,
                                 private val instance: TeamCityInstanceImpl) : VcsRootLocator {

    override fun list(): List<VcsRoot> {
        return service.vcsRoots().vcsRoot.map {VcsRootImpl(it, false, instance)}
    }
}

private class ChangeImpl(private val bean: ChangeBean,
                         private val instance: TeamCityInstanceImpl) : Change {
    override fun getWebUrl(specificBuildConfigurationId: BuildConfigurationId?, includePersonalBuilds: Boolean?): String = instance.getWebUrl(
            id, specificBuildConfigurationId = specificBuildConfigurationId,
            includePersonalBuilds = includePersonalBuilds)

    override fun firstBuilds(): List<Build> =
            instance.service
                    .changeFirstBuilds(id.stringId)
                    .build
                    .map { BuildImpl(it, false, instance) }

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
    override val time = teamCityServiceDateFormat.get().parse(bean.timestamp!!)!!
}

private class TriggeredImpl(private val bean: TriggeredBean,
                            private val instance: TeamCityInstanceImpl) : TriggeredInfo {
    override val user: User?
        get() = if (bean.user != null) UserImpl(bean.user!!) else null
    override val build: Build?
        get() = if (bean.build != null) BuildImpl(bean.build, false, instance) else null
}

private class ParameterImpl(private val bean: ParameterBean) : Parameter {
    override val name: String
        get() = bean.name!!

    override val value: String?
        get() = bean.value!!

    override val own: Boolean
        get() = bean.own
}

private class FinishBuildTriggerImpl(private val bean: TriggerBean) : FinishBuildTrigger {
    override val initiatedBuildConfiguration: BuildConfigurationId
        get() = BuildConfigurationId(bean.properties?.property?.find { it.name == "dependsOn" }?.value!!)

    override val afterSuccessfulBuildOnly: Boolean
        get() = bean.properties?.property?.find { it.name == "afterSuccessfulBuildOnly" }?.value?.toBoolean() ?: false

    private val branchPatterns: List<String>
        get() = bean.properties
                    ?.property
                    ?.find { it.name == "branchFilter" }
                    ?.value
                    ?.split(" ").orEmpty()

    override val includedBranchPatterns: Set<String>
        get() = branchPatterns.filter { !it.startsWith("-:") }.mapTo(HashSet()) { it.substringAfter(":") }

    override val excludedBranchPatterns: Set<String>
        get() = branchPatterns.filter { it.startsWith("-:") }.mapTo(HashSet()) { it.substringAfter(":") }
}

private class ArtifactDependencyImpl(private val bean: ArtifactDependencyBean,
                                     private val instance: TeamCityInstanceImpl) : ArtifactDependency {

    override val dependsOnBuildConfiguration: BuildConfiguration
        get() = BuildConfigurationImpl(bean.`source-buildType`, instance)

    override val branch: String?
        get () = findPropertyByName("revisionBranch")

    override val artifactRules: List<ArtifactRule>
        get() = findPropertyByName("pathRules")!!.split(' ').map { ArtifactRuleImpl(it) }

    override val cleanDestinationDirectory: Boolean
        get() = findPropertyByName("cleanDestinationDirectory")!!.toBoolean()

    private fun findPropertyByName(name: String): String? {
        return bean.properties?.property?.find { it.name == name }?.value
    }

}

internal class ArtifactRuleImpl(private val pathRule: String) : ArtifactRule {
    override val include: Boolean
        get() = !pathRule.startsWith("-:")

    override val sourcePath: String
        get() = pathRule.substringBefore("=>").substringBefore("!").substringAfter(":")

    override val archivePath: String?
        get() = pathRule.substringBefore("=>").substringAfter("!", "").let { if (it != "") it else null }

    override val destinationPath: String?
        get() = pathRule.substringAfter("=>", "").let { if (it != "") it else null }
}

private class RevisionImpl(private val bean: RevisionBean) : Revision {
    override val version: String
        get() = bean.version!!

    override val vcsBranchName: String
        get() = bean.vcsBranchName!!

    override val vcsRootInstance: VcsRootInstance
        get() = VcsRootInstanceImpl(bean.`vcs-root-instance`!!)
}

private data class BranchImpl(
        override val name: String?,
        override val isDefault: Boolean) : Branch

private class BuildImpl(private val bean: BuildBean,
                        private val isFullBuildBean: Boolean,
                        private val instance: TeamCityInstanceImpl) : Build {
    override fun getWebUrl(): String = instance.getWebUrl(id)

    override val id: BuildId
        get() = BuildId(bean.id!!)

    override val buildTypeId: BuildConfigurationId
        get() = BuildConfigurationId(bean.buildTypeId!!)

    override val buildNumber: String
        get() = bean.number!!

    override val status: BuildStatus
        get() = bean.status!!

    override val branch: Branch
        get() = BranchImpl(bean.branchName, bean.isDefaultBranch ?: (bean.branchName == null))

    val fullBuildBean: BuildBean by lazy {
        if (isFullBuildBean) bean else instance.service.build(id.stringId)
    }

    override fun toString(): String {
        return "Build{id=$id, buildTypeId=$buildTypeId, buildNumber=$buildNumber, status=$status, branch=$branch}"
    }

    override fun fetchStatusText(): String = fullBuildBean.statusText!!
    override fun fetchQueuedDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.queuedDate!!)
    override fun fetchStartDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.startDate!!)
    override fun fetchFinishDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.finishDate!!)
    override fun fetchPinInfo() = fullBuildBean.pinInfo?.let { PinInfoImpl(it) }
    override fun fetchTriggeredInfo() = fullBuildBean.triggered?.let { TriggeredImpl(it, instance) }

    override fun fetchParameters(): List<Parameter> = fullBuildBean.properties!!.property!!.map { ParameterImpl(it) }

    override fun fetchRevisions(): List<Revision> = fullBuildBean.revisions!!.revision!!.map { RevisionImpl(it) }

    override fun fetchChanges(): List<Change> = instance.service.changes(
            "build:${id.stringId}",
            "change(id,version,username,user,date,comment)")
            .change!!.map { ChangeImpl(it, instance) }

    override fun addTag(tag: String) {
        LOG.info("Adding tag $tag to build $buildNumber (id:${id.stringId})")
        instance.service.addTag(id.stringId, TypedString(tag))
    }

    override fun pin(comment: String) {
        LOG.info("Pinning build $buildNumber (id:${id.stringId})")
        instance.service.pin(id.stringId, TypedString(comment))
    }

    override fun unpin(comment: String) {
        LOG.info("Unpinning build $buildNumber (id:${id.stringId})")
        instance.service.unpin(id.stringId, TypedString(comment))
    }

    override fun getArtifacts(parentPath: String, recursive: Boolean, hidden: Boolean): List<BuildArtifact> {
        val locator = "recursive:$recursive,hidden:$hidden"
        val fields = "file(${ArtifactFileBean.FIELDS})"
        return instance.service.artifactChildren(id.stringId, parentPath, locator, fields).file
                .filter { it.fullName != null && it.modificationTime != null }
                .map { BuildArtifactImpl(this, it.name!!, it.fullName!!, it.size, teamCityServiceDateFormat.get().parse(it.modificationTime!!)) }
    }

    override fun findArtifact(pattern: String, parentPath: String): BuildArtifact {
        val list = getArtifacts(parentPath)
        val regexp = convertToJavaRegexp(pattern)
        val result = list.filter { regexp.matches(it.name) }
        if (result.isEmpty()) {
            val available = list.joinToString(",") { it.name }
            throw TeamCityQueryException("Artifact $pattern not found in build $buildNumber. Available artifacts: $available.")
        }
        if (result.size > 1) {
            val names = result.joinToString(",") { it.name }
            throw TeamCityQueryException("Several artifacts matching $pattern are found in build $buildNumber: $names.")
        }
        return result.first()
    }

    override fun downloadArtifacts(pattern: String, outputDir: File) {
        val list = getArtifacts(recursive = true)
        val regexp = convertToJavaRegexp(pattern)
        val matched = list.filter { regexp.matches(it.fullName) }
        if (matched.isEmpty()) {
            val available = list.joinToString(",") { it.fullName }
            throw TeamCityQueryException("No artifacts matching $pattern are found in build $buildNumber. Available artifacts: $available.")
        }
        outputDir.mkdirs()
        matched.forEach {
            it.download(File(outputDir, it.name))
        }
    }

    override fun downloadArtifact(artifactPath: String, output: File) {
        LOG.info("Downloading artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) to $output")

        val response = instance.service.artifactContent(id.stringId, artifactPath)
        saveToFile(response, output)

        LOG.debug("Artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) downloaded to $output")
    }

    override fun downloadBuildLog(output: File) {
        LOG.info("Downloading build log from build $buildNumber (id:${id.stringId}) to $output")

        val response = instance.service.buildLog(id.stringId)
        saveToFile(response, output)

        LOG.debug("Build log from build $buildNumber (id:${id.stringId}) downloaded to $output")
    }
}

private class QueuedBuildImpl(private val bean: QueuedBuildBean, private val instance: TeamCityInstanceImpl) : QueuedBuild {
    override val id: QueuedBuildId
        get() = QueuedBuildId(bean.id!!)

    override val buildTypeId: BuildConfigurationId
        get() = BuildConfigurationId(bean.buildTypeId!!)

    override val status: QueuedBuildStatus
        get() = when (bean.state!!) {
            "queued" -> QueuedBuildStatus.QUEUED
            "finished" -> QueuedBuildStatus.FINISHED
            else -> error("Unknown queued build status: " + bean.state)
        }

    override val branch: Branch
        get() = object : Branch {
            override val isDefault: Boolean
                get() = bean.defaultBranch ?: name == null

            override val name: String?
                get() = bean.branchName
        }

    override fun getWebUrl(): String = instance.getWebUrl(id)

    override fun toString(): String {
        return "QueuedBuild{id=${id.stringId}, typeId=${buildTypeId.stringId}, state=$status, branch=${branch.name}, branchIsDefault=${branch.isDefault}"
    }
}

private class VcsRootImpl(private val bean: VcsRootBean,
                          private val isFullVcsRootBean: Boolean,
                          private val instance: TeamCityInstanceImpl) : VcsRoot {

    override val id: VcsRootId
        get() = VcsRootId(bean.id!!)

    override val name: String
        get() = bean.name!!

    val fullVcsRootBean: VcsRootBean by lazy {
        if (isFullVcsRootBean) bean else instance.service.vcsRoot(id.stringId)
    }

    fun fetchNameValueProperties(): List<NameValueProperty> = fullVcsRootBean.properties!!.property!!.map { NameValueProperty(it) }

    override fun getUrl(): String? = getNameValueProperty(fetchNameValueProperties(), "url")
    override fun getDefaultBranch(): String? = getNameValueProperty(fetchNameValueProperties(), "branch")
}

private class VcsRootInstanceImpl(private val bean: VcsRootInstanceBean) : VcsRootInstance {
    override val vcsRootId: VcsRootId
        get() = VcsRootId(bean.`vcs-root-id`!!)

    override val name: String
        get() = bean.name!!
}

private class NameValueProperty(private val bean: NameValuePropertyBean) {
    val name: String
        get() = bean.name!!

    val value: String?
        get() = bean.value
}

private class BuildArtifactImpl(
        private val build: Build,
        override val name: String,
        override val fullName: String,
        override val size: Long?,
        override val modificationTime: Date) : BuildArtifact {
    override fun download(output: File) {
        build.downloadArtifact(fullName, output)
    }
}

private fun getNameValueProperty(properties: List<NameValueProperty>, name: String): String? = properties.single { it.name == name}.value

private fun convertToJavaRegexp(pattern: String): Regex {
    return pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".").toRegex()
}

private fun String.urlencode(): String = URLEncoder.encode(this, "UTF-8")

private fun getUserUrlPage(serverUrl: String,
                           pageName: String,
                           tab: String? = null,
                           projectId: ProjectId? = null,
                           buildId: BuildId? = null,
                           itemId: QueuedBuildId? = null,
                           modId: ChangeId? = null,
                           personal: Boolean? = null,
                           buildTypeId: BuildConfigurationId? = null,
                           branch: String? = null): String {
    val params = mutableListOf<String>()

    tab?.let { params.add("tab=" + tab.urlencode()) }
    projectId?.let { params.add("projectId=" + projectId.stringId.urlencode()) }
    buildId?.let { params.add("buildId=" + buildId.stringId.urlencode()) }
    modId?.let { params.add("modId=" + modId.stringId.urlencode()) }
    itemId?.let { params.add("itemId=" + itemId.stringId.urlencode()) }
    personal?.let { params.add("personal=" + if (personal) "true" else "false") }
    buildTypeId?.let { params.add("buildTypeId=" + buildTypeId.stringId.urlencode()) }
    branch?.let { params.add("branch=" + branch.urlencode()) }

    return "${serverUrl.trimEnd('/')}/$pageName" +
            if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
}

private fun saveToFile(response: retrofit.client.Response, file: File) {
    file.parentFile.mkdirs()
    val input = response.body.`in`()
    BufferedOutputStream(FileOutputStream(file)).use {
        input.copyTo(it)
    }
}
