@file:Suppress("RemoveRedundantBackticks")

package org.jetbrains.teamcity.rest

import com.jakewharton.retrofit.Ok3Client
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import retrofit.RestAdapter
import retrofit.mime.TypedString
import java.io.*
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter

private val LOG = LoggerFactory.getLogger("teamcity-rest-client")

private val teamCityServiceDateFormat =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat? = SimpleDateFormat("yyyyMMdd'T'HHmmssZ", Locale.ENGLISH)
        }

internal fun createGuestAuthInstance(serverUrl: String): TeamCityInstanceImpl {
    return TeamCityInstanceImpl(serverUrl.trimEnd('/'), "guestAuth", null, false)
}

internal fun createHttpAuthInstance(serverUrl: String, username: String, password: String): TeamCityInstanceImpl {
    val authorization = Base64.encodeBase64String("$username:$password".toByteArray())
    return TeamCityInstanceImpl(serverUrl.trimEnd('/'), "httpAuth", authorization, false)
}

private class RetryInterceptor : Interceptor {
    private fun Response.retryRequired(): Boolean {
        val code = code()
        if (code < 400) return false

        // Do not retry non-GET methods, their result may be not idempotent
        if (request().method() != "GET") return false

        return  code == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
                code == HttpURLConnection.HTTP_INTERNAL_ERROR ||
                code == HttpURLConnection.HTTP_BAD_GATEWAY ||
                code == HttpURLConnection.HTTP_UNAVAILABLE ||
                code == HttpURLConnection.HTTP_GATEWAY_TIMEOUT
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)

        var tryCount = 0
        while (response.retryRequired() && tryCount < 3) {
            tryCount++
            LOG.warn("Request ${request.url()} is not successful, $tryCount sec waiting [$tryCount retry]")
            Thread.sleep((tryCount * 1000).toLong())
            response = chain.proceed(request)
        }

        return response
    }
}

private fun xml(init: XMLStreamWriter.() -> Unit): String {
    val stringWriter = StringWriter()
    val xmlStreamWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(stringWriter)
    init(xmlStreamWriter)
    xmlStreamWriter.flush()
    return stringWriter.toString()
}

private fun XMLStreamWriter.element(name: String, init: XMLStreamWriter.() -> Unit): XMLStreamWriter {
    this.writeStartElement(name)
    this.init()
    this.writeEndElement()
    return this
}

private fun XMLStreamWriter.attribute(name: String, value: String) = writeAttribute(name, value)

internal class TeamCityInstanceImpl(override val serverUrl: String,
                                    private val authMethod: String,
                                    private val basicAuthHeader: String?,
                                    logResponses: Boolean) : TeamCityInstance(), Experimental {
    override fun withLogResponses() = TeamCityInstanceImpl(serverUrl, authMethod, basicAuthHeader, true)

    private val restLog = LoggerFactory.getLogger(LOG.name + ".rest")

    private var client = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor())
            .build()

    internal val service = RestAdapter.Builder()
            .setClient(Ok3Client(client))
            .setEndpoint("$serverUrl/$authMethod")
            .setLog({ restLog.debug(if (basicAuthHeader != null) it.replace(basicAuthHeader, "[REDACTED]") else it) })
            .setLogLevel(if (logResponses) retrofit.RestAdapter.LogLevel.FULL else retrofit.RestAdapter.LogLevel.HEADERS_AND_ARGS)
            .setRequestInterceptor({ request ->
                if (basicAuthHeader != null) {
                    request.addHeader("Authorization", "Basic $basicAuthHeader")
                }
            })
            .setErrorHandler({
                val responseText = try {
                    it.response.body.`in`().reader().use { it.readText() }
                } catch (t: Throwable) {
                    LOG.warn("Exception while reading error response text: ${t.message}", t)
                    ""
                }

                throw Error("Failed to connect to ${it.url}: ${it.message} $responseText", it)
            })
            .build()
            .create(TeamCityService::class.java)

    override fun builds(): BuildLocator = BuildLocatorImpl(this)

    override fun build(id: BuildId): Build = BuildImpl(service.build(id.stringId), true, this)

    override fun build(buildType: BuildConfigurationId, number: String): Build?
            = BuildLocatorImpl(this).fromConfiguration(buildType).withNumber(number).latest()

    override fun buildConfiguration(id: BuildConfigurationId):
            BuildConfiguration = BuildConfigurationImpl(service.buildConfiguration(id.stringId), this)

    override fun vcsRoots(): VcsRootLocator = VcsRootLocatorImpl(this)

    override fun vcsRoot(id: VcsRootId): VcsRoot = VcsRootImpl(service.vcsRoot(id.stringId))

    override fun project(id: ProjectId): Project = ProjectImpl(ProjectBean().let { it.id = id.stringId; it }, false, this)

    override fun rootProject(): Project = project(ProjectId("_Root"))

    override fun user(id: UserId): User = UserImpl(service.users("id:${id.stringId}"), true, this)

    override fun users(): UserLocator = UserLocatorImpl(this)

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

    override fun getWebUrl(userId: UserId): String =
        getUserUrlPage(
                serverUrl, "admin/editUser.html",
                userId = userId
        )

    override fun getWebUrl(projectId: ProjectId, testId: TestId): String =
        getUserUrlPage(
                serverUrl, "project.html",
                projectId = projectId,
                testNameId = testId,
                tab = "testDetails"
        )

    override fun getWebUrl(queuedBuildId: QueuedBuildId): String =
        getUserUrlPage(serverUrl, "viewQueued.html", itemId = queuedBuildId)

    override fun getWebUrl(changeId: ChangeId, specificBuildConfigurationId: BuildConfigurationId?, includePersonalBuilds: Boolean?): String =
        getUserUrlPage(
                serverUrl, "viewModification.html",
                modId = changeId,
                buildTypeId = specificBuildConfigurationId,
                personal = includePersonalBuilds)

    override fun buildQueue(): BuildQueue = BuildQueueImpl(this)

    override fun buildResults(): BuildResults = BuildResultsImpl(service)

    override val experimental: Experimental
        get() = this

    override fun createProject(id: ProjectId, name: String, parentProjectId: ProjectId): Project {
        val projectXmlDescription = xml {
            element("newProjectDescription") {
                attribute("name", name)
                attribute("id", id.stringId)
                element("parentProject") {
                    attribute("locator", "id:${parentProjectId.stringId}")
                }
            }
        }

        val projectBean = service.createProject(TypedString(projectXmlDescription))
        return ProjectImpl(projectBean, true, this)
    }

    override fun createVcsRoot(id: VcsRootId, name: String, type: VcsRootType, parentProjectId: ProjectId, properties: Map<String, String>): VcsRoot {
        val vcsRootDescriptionXml = xml {
            element("vcs-root") {
                attribute("name", name)
                attribute("id", id.stringId)
                attribute("vcsName", type.stringType)

                element("project") {
                    attribute("id", parentProjectId.stringId)
                }

                element("properties") {
                    properties.entries.sortedBy { it.key }.forEach {
                        element("property") {
                            attribute("name", it.key)
                            attribute("value", it.value)
                        }
                    }
                }
            }
        }

        return createVcsRoot(vcsRootDescriptionXml)
    }

    override fun createVcsRoot(vcsRootDescriptionXml: String): VcsRoot {
        val vcsRootBean = service.createVcsRoot(TypedString(vcsRootDescriptionXml))
        return VcsRootImpl(vcsRootBean)
    }

    override fun createBuildType(buildTypeDescriptionXml: String): BuildConfiguration {
        val bean = service.createBuildType(TypedString(buildTypeDescriptionXml))
        return BuildConfigurationImpl(bean, this)
    }
}

private class UserLocatorImpl(private val instance: TeamCityInstanceImpl): UserLocator {
    private var id: UserId? = null
    private var username: String? = null

    override fun withId(id: UserId): UserLocator {
        this.id = id
        return this
    }

    override fun withUsername(name: String): UserLocator {
        this.username = name
        return this
    }

    override fun list(): List<User> {
        val idCopy = id
        val usernameCopy = username

        if (idCopy != null && usernameCopy != null) {
            throw IllegalArgumentException("UserLocator accepts only id or username, not both")
        }

        val locator = when {
            idCopy != null -> "id:${idCopy.stringId}"
            usernameCopy != null -> "username:$usernameCopy"
            else -> ""
        }

        return if (idCopy == null && usernameCopy == null) {
            instance.service.users().user.map { UserImpl(it, false, instance) }
        } else {
            val bean = instance.service.users(locator)
            listOf(UserImpl(bean, true, instance))
        }
    }
}

private class BuildLocatorImpl(private val instance: TeamCityInstanceImpl) : BuildLocator {
    private var buildConfigurationId: BuildConfigurationId? = null
    private var snapshotDependencyTo: BuildId? = null
    private var number: String? = null
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

    override fun snapshotDependencyTo(buildId: BuildId): BuildLocator {
        this.snapshotDependencyTo = buildId
        return this
    }

    fun withNumber(buildNumber: String): BuildLocator {
        this.number = buildNumber
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

    override fun list(): Sequence<Build> {
        val count1 = count

        val parameters = listOfNotNull(
                buildTypeId?.stringId?.let { "buildType:$it" },
                snapshotDependencyTo?.stringId?.let { "snapshotDependency:(to:(id:$it))" },
                number?.let { "number:$it" },
                status?.name?.let { "status:$it" },
                if (!tags.isEmpty())
                    tags.joinToString(",", prefix = "tags:(", postfix = ")")
                else null,
                if (pinnedOnly) "pinned:true" else null,
                count1?.let { "count:$it" },

                sinceDate?.let {"sinceDate:${teamCityServiceDateFormat.get().format(sinceDate)}"},

                if (!includeAllBranches)
                    branch?.let { "branch:$it" }
                else
                    "branch:default:any"
        )

        if (parameters.isEmpty()) {
            throw IllegalArgumentException("At least one parameter should be specified")
        }

        val sequence = lazyPaging { start ->
            val buildLocator = parameters.plus("start:$start").joinToString(",")

            LOG.debug("Retrieving builds from ${instance.serverUrl} using query '$buildLocator'")
            val buildsBean = instance.service.builds(buildLocator = buildLocator)

            return@lazyPaging Page(
                    data = buildsBean.build.map { BuildImpl(it, false, instance) },
                    hasNextPage = buildsBean.nextHref.isNotBlank()
            )
        }

        return if (count1 != null) sequence.take(count1) else sequence
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
        get() = bean.name ?: fullProjectBean.name!!

    override val archived: Boolean
        get() = bean.archived ?: fullProjectBean.archived ?: false

    override val parentProjectId: ProjectId
        get() = ProjectId(bean.parentProjectId ?: fullProjectBean.parentProjectId!!)

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
        get() = bean.paused ?: false // TC won't return paused:false field

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

private class VcsRootLocatorImpl(private val instance: TeamCityInstanceImpl) : VcsRootLocator {
    override fun list(): Sequence<VcsRoot> {
        return lazyPaging { start ->
            val locator = "start:$start"
            LOG.debug("Retrieving vcs roots from ${instance.serverUrl} using locator '$locator'")

            val vcsRootsBean = instance.service.vcsRoots(locator = locator)
            return@lazyPaging Page(
                    data = vcsRootsBean.`vcs-root`.map { VcsRootImpl(it) },
                    hasNextPage = vcsRootsBean.nextHref.isNotBlank()
            )
        }
    }
}

private class ChangeImpl(private val bean: ChangeBean,
                         private val instance: TeamCityInstanceImpl) : Change {
    override fun getWebUrl(specificBuildConfigurationId: BuildConfigurationId?, includePersonalBuilds: Boolean?): String = instance.getWebUrl(
            id, specificBuildConfigurationId = specificBuildConfigurationId,
            includePersonalBuilds = includePersonalBuilds)

    override val id: ChangeId
        get() = ChangeId(bean.id!!)

    override val version: String
        get() = bean.version!!

    override val username: String
        get() = bean.username!!

    override val user: User?
        get() = bean.user?.let { UserImpl(it, false, instance) }

    override val date: Date
        get() = teamCityServiceDateFormat.get().parse(bean.date!!)

    override val comment: String
        get() = bean.comment!!

    override fun toString() =
            "id=$id, version=$version, username=$username, user=$user, date=$date, comment=$comment"
}

private class UserImpl(private val bean: UserBean,
                       private val isFullBuildBean: Boolean,
                       private val instance: TeamCityInstanceImpl) : User {
    override val email: String
        get() = bean.email ?: fullUserBean.email!!

    override val id: UserId
        get() = UserId(bean.id!!)

    override val username: String
        get() = bean.username ?: fullUserBean.username!!

    override val name: String
        get() = bean.name ?: fullUserBean.name!!

    override fun getWebUrl(): String = instance.getWebUrl(id)

    val fullUserBean: UserBean by lazy {
        if (isFullBuildBean) bean else instance.service.users("id:${id.stringId}")
    }

    override fun toString(): String {
        return "User(id=${id.stringId}, username=$username)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return id == (other as UserImpl).id && instance == other.instance
    }

    override fun hashCode(): Int = id.stringId.hashCode()
}

private class PinInfoImpl(bean: PinInfoBean, instance: TeamCityInstanceImpl) : PinInfo {
    override val user = UserImpl(bean.user!!, false, instance)
    override val time = teamCityServiceDateFormat.get().parse(bean.timestamp!!)!!
}

private class TriggeredImpl(private val bean: TriggeredBean,
                            private val instance: TeamCityInstanceImpl) : TriggeredInfo {
    override val user: User?
        get() = if (bean.user != null) UserImpl(bean.user!!, false, instance) else null
    override val build: Build?
        get() = if (bean.build != null) BuildImpl(bean.build, false, instance) else null
}

private class BuildCanceledInfoImpl(private val bean: BuildCanceledBean,
                                    private val instance: TeamCityInstanceImpl) : BuildCanceledInfo {
    override val user: User?
        get() = if (bean.user != null) UserImpl(bean.user!!, false, instance) else null
    override val cancelDate: Date
        get() = teamCityServiceDateFormat.get().parse(bean.timestamp!!)!!
}

private class ParameterImpl(private val bean: ParameterBean) : Parameter {
    override val name: String
        get() = bean.name!!

    override val value: String
        get() = bean.value!!

    override val own: Boolean
        get() = bean.own!!
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

private class BuildProblemImpl(private val bean: BuildProblemBean) : BuildProblem {
    override val id: BuildProblemId
        get() = BuildProblemId(bean.id!!)
    override val type: BuildProblemType
        get() = BuildProblemType(bean.type!!)
    override val identity: String
        get() = bean.identity!!

    override fun toString(): String =
        "BuildProblem(id=${id.stringId},type=$type,identity=$identity)"
}

private class BuildProblemOccurrenceImpl(private val bean: BuildProblemOccurrenceBean,
                                         private val instance: TeamCityInstanceImpl) : BuildProblemOccurrence {
    override val buildProblem: BuildProblem
        get() = BuildProblemImpl(bean.problem!!)
    override val build: Build
        get() = BuildImpl(bean.build!!, false, instance)
    override val details: String
        get() = bean.details ?: ""
    override val additionalData: String?
        get() = bean.additionalData

    override fun toString(): String =
        "BuildProblemOccurrence(build=${build.id},problem=$buildProblem,details=$details,additionalData=$additionalData)"
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

    override val vcsRoot: VcsRoot
        get() = VcsRootImpl(bean.`vcs-root-instance`!!)
}

private data class BranchImpl(
        override val name: String?,
        override val isDefault: Boolean) : Branch

private data class Page<out T>(val data: List<T>, val hasNextPage: Boolean)

private fun <T> lazyPaging(nextPage: (Int) -> Page<T>): Sequence<T> {
    data class PageSeq(val nextStart: Int, val hasNext: Boolean, val data: List<T>?)

    return generateSequence(PageSeq(0, true, null), { prev ->
        if (!prev.hasNext) return@generateSequence null

        val data = nextPage(prev.nextStart)
        return@generateSequence PageSeq(
                nextStart = prev.nextStart + data.data.size,
                hasNext = data.hasNextPage,
                data = data.data)
    }).mapNotNull { it.data }.flatten()
}

private fun String?.isNotBlank(): Boolean = this != null && !this.isBlank()

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

    override val status: BuildStatus?
        get() = bean.status

    override val state: BuildState
        get() = try {
            val state = bean.state ?: fullBuildBean.state!!
            BuildState.valueOf(state.toUpperCase())
        } catch (e: IllegalArgumentException) {
            BuildState.UNKNOWN
        }

    override val branch: Branch
        get() = BranchImpl(bean.branchName, bean.isDefaultBranch ?: (bean.branchName == null))

    override val name: String by lazy {
        if (isFullBuildBean) bean.buildType!!.name!! else instance.buildConfiguration(buildTypeId).name
    }

    val fullBuildBean: BuildBean by lazy {
        if (isFullBuildBean) bean else instance.service.build(id.stringId)
    }

    override fun toString(): String {
        return "Build{id=$id, buildTypeId=$buildTypeId, buildNumber=$buildNumber, status=$status, branch=$branch}"
    }

    override val canceledInfo: BuildCanceledInfo?
        get() = fullBuildBean.canceledInfo?.let { BuildCanceledInfoImpl(it, instance) }

    override fun fetchStatusText(): String = fullBuildBean.statusText!!
    override fun fetchQueuedDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.queuedDate!!)
    override fun fetchStartDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.startDate!!)
    override fun fetchFinishDate(): Date = teamCityServiceDateFormat.get().parse(fullBuildBean.finishDate!!)
    override fun fetchPinInfo() = fullBuildBean.pinInfo?.let { PinInfoImpl(it, instance) }
    override fun fetchTriggeredInfo() = fullBuildBean.triggered?.let { TriggeredImpl(it, instance) }

    override fun fetchTests(status: TestStatus?): Sequence<TestOccurrence> = lazyPaging { start ->
        val statusLocator = when (status) {
            null -> ""
            TestStatus.FAILED -> ",status:FAILURE"
            TestStatus.SUCCESSFUL -> ",status:SUCCESS"
            TestStatus.IGNORED -> ",ignored:true"
            TestStatus.UNKNOWN -> error("Unsupported filter by test status UNKNOWN")
        }

        val occurrencesBean = instance.service.tests(
                locator = "build:(id:${id.stringId}),start:$start$statusLocator",
                fields = TestOccurrenceBean.filter)

        return@lazyPaging Page(
                data = occurrencesBean.testOccurrence.map { TestOccurrenceImpl(it) },
                hasNextPage = occurrencesBean.nextHref.isNotBlank()
        )
    }

    override fun fetchBuildProblems(): Sequence<BuildProblemOccurrence> = lazyPaging { start ->
        val occurrencesBean = instance.service.problemOccurrences(
                locator = "build:(id:${id.stringId}),start:$start",
                fields = "\$long,problemOccurrence(\$long)")
        return@lazyPaging Page(
                data = occurrencesBean.problemOccurrence.map { BuildProblemOccurrenceImpl(it, instance) },
                hasNextPage = occurrencesBean.nextHref.isNotBlank()
        )
    }

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

    override fun getArtifacts(parentPath: String): List<BuildArtifactImpl> {
        return instance.service.artifactChildren(id.stringId, parentPath).file.filter { it.name != null && it.modificationTime != null }.map {
            BuildArtifactImpl(this, it.name!!, it.size, teamCityServiceDateFormat.get().parse(it.modificationTime!!))
        }
    }

    override fun findArtifact(pattern: String, parentPath: String): BuildArtifact {
        val list = getArtifacts(parentPath)
        val regexp = convertToJavaRegexp(pattern)
        val result = list.filter { regexp.matches(it.fileName) }
        if (result.isEmpty()) {
            val available = list.joinToString(",") { it.fileName }
            throw RuntimeException("Artifact $pattern not found in build $buildNumber. Available artifacts: $available.")
        }
        if (result.size > 1) {
            val names = result.joinToString(",") { it.fileName }
            throw RuntimeException("Several artifacts matching $pattern are found in build $buildNumber: $names.")
        }
        return result.first()
    }

    override fun downloadArtifacts(pattern: String, outputDir: File) {
        val list = getArtifacts()
        val regexp = convertToJavaRegexp(pattern)
        val matched = list.filter { regexp.matches(it.fileName) }
        if (matched.isEmpty()) {
            val available = list.joinToString(",") { it.fileName }
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
        FileOutputStream(output).use {
            downloadArtifactImpl(artifactPath, it)
        }

        LOG.debug("Artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) downloaded to $output")
    }

    override fun downloadArtifact(artifactPath: String, output: OutputStream) {
        LOG.info("Downloading artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) to $output")

        downloadArtifactImpl(artifactPath, output)

        LOG.debug("Artifact '$artifactPath' from build $buildNumber (id:${id.stringId}) downloaded to $output")
    }

    private fun downloadArtifactImpl(artifactPath: String, output: OutputStream) {
        val response = instance.service.artifactContent(id.stringId, artifactPath)
        val input = response.body.`in`()
        BufferedOutputStream(output).use {
            input.copyTo(it)
        }
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

private class BuildQueueImpl(private val instance: TeamCityInstanceImpl): BuildQueue {
    override fun triggerBuild(buildTypeId: BuildConfigurationId,
                     parameters: Map<String, String>?,
                     queueAtTop: Boolean?,
                     cleanSources: Boolean?,
                     rebuildAllDependencies: Boolean?,
                     comment: String?,
                     logicalBranchName: String?,
                     personal: Boolean?): BuildId {
        val request = TriggerBuildRequestBean()

        request.buildType = BuildTypeBean().apply { id = buildTypeId.stringId }
        request.branchName = logicalBranchName
        comment.let { commentText -> request.comment = CommentBean().apply { text = commentText } }
        request.personal = personal
        request.triggeringOptions = TriggeringOptionsBean().apply {
            this.cleanSources = cleanSources
            this.rebuildAllDependencies = rebuildAllDependencies
            this.queueAtTop = queueAtTop
        }
        parameters?.let {
            val parametersBean = ParametersBean(it.map { ParameterBean(it.key, it.value) })
            request.properties = parametersBean
        }

        val triggeredBuildBean = instance.service.triggerBuild(request)
        return BuildId(triggeredBuildBean.id!!.toString())
    }

    override fun cancelBuild(id: BuildId, comment: String, reAddIntoQueue: Boolean) {
        val request = BuildCancelRequestBean()
        request.comment = comment
        request.readdIntoQueue = reAddIntoQueue
        instance.service.cancelBuild(id.stringId, request)
    }

    override fun queuedBuilds(projectId: ProjectId?): List<QueuedBuild> {
        val locator = if (projectId == null) null else "project:${projectId.stringId}"
        return instance.service.queuedBuilds(locator).build.map { QueuedBuildImpl(it, instance) }
    }
}

private class BuildResultsImpl(private val service: TeamCityService): BuildResults {
    override fun tests(id: BuildId) {
        service.tests("build:${id.stringId}")
    }
}

private class TestOccurrenceImpl(bean: TestOccurrenceBean): TestOccurrence {
    override val name = bean.name!!

    override val status = when {
        bean.ignored == true -> TestStatus.IGNORED
        bean.status == "FAILURE" -> TestStatus.FAILED
        bean.status == "SUCCESS" -> TestStatus.SUCCESSFUL
        else -> TestStatus.UNKNOWN
    }

    override val duration = bean.duration ?: 1L

    override val details = when (status) {
        TestStatus.IGNORED -> bean.ignoreDetails
        TestStatus.FAILED -> bean.details
        else -> null
    } ?: ""

    override val ignored: Boolean = bean.ignored ?: false

    override val currentlyMuted: Boolean = bean.currentlyMuted ?: false

    override val muted: Boolean = bean.muted ?: false

    override val buildId: BuildId = BuildId(bean.build!!.id!!)

    override val testId: TestId = TestId(bean.test!!.id!!)

    override fun toString() = "Test(name=$name, status=$status, duration=$duration, details=$details)"
}

private fun convertToJavaRegexp(pattern: String): Regex {
    return pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".").toRegex()
}

private fun String.urlencode(): String = URLEncoder.encode(this, "UTF-8")

private fun getUserUrlPage(serverUrl: String,
                           pageName: String,
                           tab: String? = null,
                           projectId: ProjectId? = null,
                           buildId: BuildId? = null,
                           testNameId: TestId? = null,
                           userId: UserId? = null,
                           itemId: QueuedBuildId? = null,
                           modId: ChangeId? = null,
                           personal: Boolean? = null,
                           buildTypeId: BuildConfigurationId? = null,
                           branch: String? = null): String {
    val params = mutableListOf<String>()

    tab?.let { params.add("tab=" + tab.urlencode()) }
    projectId?.let { params.add("projectId=" + projectId.stringId.urlencode()) }
    buildId?.let { params.add("buildId=" + buildId.stringId.urlencode()) }
    testNameId?.let { params.add("testNameId=" + testNameId.stringId.urlencode()) }
    userId?.let { params.add("userId=" + userId.stringId.urlencode()) }
    modId?.let { params.add("modId=" + modId.stringId.urlencode()) }
    itemId?.let { params.add("itemId=" + itemId.stringId.urlencode()) }
    personal?.let { params.add("personal=" + if (personal) "true" else "false") }
    buildTypeId?.let { params.add("buildTypeId=" + buildTypeId.stringId.urlencode()) }
    branch?.let { params.add("branch=" + branch.urlencode()) }

    return "$serverUrl/$pageName" +
            if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
}
