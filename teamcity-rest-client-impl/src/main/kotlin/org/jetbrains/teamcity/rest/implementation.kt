@file:Suppress("RemoveRedundantBackticks", "OverridingDeprecatedMember")

package org.jetbrains.teamcity.rest

import com.google.gson.Gson
import com.jakewharton.retrofit.Ok3Client
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import retrofit.RestAdapter
import retrofit.converter.GsonConverter
import retrofit.mime.TypedString
import java.io.*
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.*
import java.util.concurrent.TimeUnit
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
                                    val authMethod: String,
                                    private val basicAuthHeader: String?,
                                    logResponses: Boolean) : TeamCityInstance() {
    override fun withLogResponses() = TeamCityInstanceImpl(serverUrl, authMethod, basicAuthHeader, true)

    private val restLog = LoggerFactory.getLogger(LOG.name + ".rest")

    private var client = OkHttpClient.Builder()
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .connectTimeout(2, TimeUnit.MINUTES)
            .addInterceptor(RetryInterceptor())
            .build()

    internal val service = RestAdapter.Builder()
            .setClient(Ok3Client(client))
            .setEndpoint("$serverUrl/$authMethod")
            .setLog { restLog.debug(if (basicAuthHeader != null) it.replace(basicAuthHeader, "[REDACTED]") else it) }
            .setLogLevel(if (logResponses) retrofit.RestAdapter.LogLevel.FULL else retrofit.RestAdapter.LogLevel.HEADERS_AND_ARGS)
            .setRequestInterceptor { request ->
                if (basicAuthHeader != null) {
                    request.addHeader("Authorization", "Basic $basicAuthHeader")
                }
            }
            .setErrorHandler {
                val responseText = try {
                    it.response.body.`in`().reader().use { it.readText() }
                } catch (t: Throwable) {
                    LOG.warn("Exception while reading error response text: ${t.message}", t)
                    ""
                }

                throw TeamCityConversationException("Failed to connect to ${it.url}: ${it.message} $responseText", it)
            }
            .build()
            .create(TeamCityService::class.java)

    override fun builds(): BuildLocator = BuildLocatorImpl(this)

    override fun build(id: BuildId): Build = BuildImpl(
            BuildBean().also { it.id = id.stringId }, false, this)

    override fun build(buildConfigurationId: BuildConfigurationId, number: String): Build? =
            BuildLocatorImpl(this).fromConfiguration(buildConfigurationId).withNumber(number).latest()

    override fun buildConfiguration(id: BuildConfigurationId): BuildConfiguration =
            BuildConfigurationImpl(BuildTypeBean().also { it.id = id.stringId }, false, this)

    override fun vcsRoots(): VcsRootLocator = VcsRootLocatorImpl(this)

    override fun vcsRoot(id: VcsRootId): VcsRoot = VcsRootImpl(service.vcsRoot(id.stringId), true, this)

    override fun project(id: ProjectId): Project = ProjectImpl(ProjectBean().let { it.id = id.stringId; it }, false, this)

    override fun rootProject(): Project = project(ProjectId("_Root"))

    override fun user(id: UserId): User =
            UserImpl(UserBean().also { it.id = id.stringId }, false, this)

    override fun user(userName: String): User {
        val bean = service.users("username:$userName")
        return UserImpl(bean, true, this)
    }

    override fun users(): UserLocator = UserLocatorImpl(this)

    override fun change(buildConfigurationId: BuildConfigurationId, vcsRevision: String): Change =
            ChangeImpl(service.change(
                    buildType = buildConfigurationId.stringId, version = vcsRevision), true, this)

    override fun change(id: ChangeId): Change =
            ChangeImpl(ChangeBean().also { it.id = id.stringId }, false, this)

    override fun buildQueue(): BuildQueue = BuildQueueImpl(this)

    override fun getWebUrl(projectId: ProjectId, branch: String?): String =
            project(projectId).getHomeUrl(branch = branch)

    override fun getWebUrl(buildConfigurationId: BuildConfigurationId, branch: String?): String =
            buildConfiguration(buildConfigurationId).getHomeUrl(branch = branch)

    override fun getWebUrl(buildId: BuildId): String =
            build(buildId).getHomeUrl()

    override fun getWebUrl(changeId: ChangeId, specificBuildConfigurationId: BuildConfigurationId?, includePersonalBuilds: Boolean?): String =
            change(changeId).getHomeUrl(
                    specificBuildConfigurationId = specificBuildConfigurationId,
                    includePersonalBuilds = includePersonalBuilds
            )

    override fun queuedBuilds(projectId: ProjectId?): List<Build> =
            buildQueue().queuedBuilds(projectId = projectId).toList()
}

private fun <T> List<T>.toSequence(): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> = this@toSequence.iterator()
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

    override fun all(): Sequence<User> {
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
            instance.service.users().user.map { UserImpl(it, false, instance) }.toSequence()
        } else {
            val bean = instance.service.users(locator)
            listOf(UserImpl(bean, true, instance)).toSequence()
        }
    }

    override fun list(): List<User> = all().toList()
}

private class BuildLocatorImpl(private val instance: TeamCityInstanceImpl) : BuildLocator {
    private var buildConfigurationId: BuildConfigurationId? = null
    private var snapshotDependencyTo: BuildId? = null
    private var number: String? = null
    private var vcsRevision: String? = null
    private var sinceDate: Date? = null
    private var status: BuildStatus? = BuildStatus.SUCCESS
    private var tags = ArrayList<String>()
    private var count: Int? = null
    private var branch: String? = null
    private var includeAllBranches = false
    private var pinnedOnly = false
    private var running: String? = null
    private var canceled: String? = null

    override fun fromConfiguration(buildConfigurationId: BuildConfigurationId): BuildLocatorImpl {
        this.buildConfigurationId = buildConfigurationId
        return this
    }

    override fun snapshotDependencyTo(buildId: BuildId): BuildLocator {
        this.snapshotDependencyTo = buildId
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

    override fun includeFailed(): BuildLocator {
        status = null
        return this
    }

    override fun withStatus(status: BuildStatus): BuildLocator {
        this.status = status
        return this
    }

    override fun includeRunning(): BuildLocator {
        running = "any"
        return this
    }

    override fun onlyRunning(): BuildLocator {
        running = "true"
        return this
    }

    override fun includeCanceled(): BuildLocator {
        canceled = "any"
        return this
    }

    override fun onlyCanceled(): BuildLocator {
        canceled = "true"
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

    override fun pinnedOnly(): BuildLocator {
        this.pinnedOnly = true
        return this
    }

    override fun limitResults(count: Int): BuildLocator {
        this.count = count
        return this
    }

    override fun latest(): Build? {
        return limitResults(1).all().firstOrNull()
    }

    override fun all(): Sequence<Build> {
        val count1 = count

        val parameters = listOfNotNull(
                buildConfigurationId?.stringId?.let { "buildType:$it" },
                snapshotDependencyTo?.stringId?.let { "snapshotDependency:(to:(id:$it))" },
                number?.let { "number:$it" },
                running?.let { "running:$it" },
                canceled?.let { "canceled:$it" },
                vcsRevision?.let { "revision:$it" },
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
                    "branch:default:any",

                // Always use default filter since sometimes TC automatically switches between
                // defaultFilter:true and defaultFilter:false
                // See BuildPromotionFinder.java in rest-api, setLocatorDefaults method
                "defaultFilter:true"
        )

        if (parameters.isEmpty()) {
            throw IllegalArgumentException("At least one parameter should be specified")
        }

        val sequence = lazyPaging(instance, {
            val buildLocator = parameters.joinToString(",")
            LOG.debug("Retrieving builds from ${instance.serverUrl} using query '$buildLocator'")
            return@lazyPaging instance.service.builds(buildLocator = buildLocator)
        }) { buildsBean ->
            Page(
                    data = buildsBean.build.map { BuildImpl(it, false, instance) },
                    nextHref = buildsBean.nextHref
            )
        }

        return if (count1 != null) sequence.take(count1) else sequence
    }

    override fun list(): List<Build> = all().toList()
    override fun withAnyStatus(): BuildLocator = includeFailed()
}

private abstract class BaseImpl<TBean : IdBean>(
        private val bean: TBean,
        private val isFullBean: Boolean,
        protected val instance: TeamCityInstanceImpl) {
    init {
        if (bean.id == null) {
            throw IllegalStateException("bean.id should not be null")
        }
    }

    protected inline val idString
        get() = bean.id!!

    protected inline fun <T> notNull(getter: (TBean) -> T?): T =
            getter(bean) ?: getter(fullBean)!!

    protected inline fun <T> nullable(getter: (TBean) -> T?): T? =
            getter(bean) ?: getter(fullBean)

    val fullBean: TBean by lazy {
        if (isFullBean) bean else fetchFullBean()
    }

    abstract fun fetchFullBean(): TBean
    abstract override fun toString(): String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return idString == (other as BaseImpl<*>).idString && instance === other.instance
    }

    override fun hashCode(): Int = idString.hashCode()
}

private class ProjectImpl(
        bean: ProjectBean,
        isFullProjectBean: Boolean,
        instance: TeamCityInstanceImpl) :
        BaseImpl<ProjectBean>(bean, isFullProjectBean, instance), Project {
    override fun fetchFullBean(): ProjectBean = instance.service.project(id.stringId)

    override fun toString(): String = "Project(id=$idString,name=$name)"

    override fun getHomeUrl(branch: String?): String =
            getUserUrlPage(instance.serverUrl, "project.html", projectId = id, branch = branch)

    override fun getTestHomeUrl(testId: TestId): String = getUserUrlPage(
            instance.serverUrl, "project.html",
            projectId = id,
            testNameId = testId,
            tab = "testDetails"
    )

    override val id: ProjectId
        get() = ProjectId(idString)

    override val name: String
        get() = notNull { it.name }

    override val archived: Boolean
        get() = nullable { it.archived } ?: false

    override val parentProjectId: ProjectId?
        get() = nullable { it.parentProjectId }?.let { ProjectId(it) }

    override val childProjects: List<Project> by lazy {
        fullBean.projects!!.project.map { ProjectImpl(it, false, instance) }
    }

    override val buildConfigurations: List<BuildConfiguration> by lazy {
        fullBean.buildTypes!!.buildType.map { BuildConfigurationImpl(it, false, instance) }
    }

    override val parameters: List<Parameter> by lazy {
        fullBean.parameters!!.property!!.map { ParameterImpl(it) }
    }

    override fun setParameter(name: String, value: String) {
        LOG.info("Setting parameter $name=$value in ProjectId=$idString")
        instance.service.setProjectParameter(id.stringId, name, TypedString(value))
    }

    override fun createProject(id: ProjectId, name: String): Project {
        val projectXmlDescription = xml {
            element("newProjectDescription") {
                attribute("name", name)
                attribute("id", id.stringId)
                element("parentProject") {
                    attribute("locator", "id:${this@ProjectImpl.id.stringId}")
                }
            }
        }

        val projectBean = instance.service.createProject(TypedString(projectXmlDescription))
        return ProjectImpl(projectBean, true, instance)
    }

    override fun createVcsRoot(id: VcsRootId, name: String, type: VcsRootType, properties: Map<String, String>): VcsRoot {
        val vcsRootDescriptionXml = xml {
            element("vcs-root") {
                attribute("name", name)
                attribute("id", id.stringId)
                attribute("vcsName", type.stringType)

                element("project") {
                    attribute("id", this@ProjectImpl.idString)
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

        val vcsRootBean = instance.service.createVcsRoot(TypedString(vcsRootDescriptionXml))
        return VcsRootImpl(vcsRootBean, true, instance)
    }

    override fun createBuildConfiguration(buildConfigurationDescriptionXml: String): BuildConfiguration {
        val bean = instance.service.createBuildType(TypedString(buildConfigurationDescriptionXml))
        return BuildConfigurationImpl(bean, false, instance)
    }

    override fun getWebUrl(branch: String?): String = getHomeUrl(branch = branch)
    override fun fetchChildProjects(): List<Project> = childProjects
    override fun fetchBuildConfigurations(): List<BuildConfiguration> = buildConfigurations
    override fun fetchParameters(): List<Parameter> = parameters
}

private class BuildConfigurationImpl(bean: BuildTypeBean,
                                     isFullBean: Boolean,
                                     instance: TeamCityInstanceImpl) :
        BaseImpl<BuildTypeBean>(bean, isFullBean, instance), BuildConfiguration {
    override fun fetchFullBean(): BuildTypeBean = instance.service.buildConfiguration(idString)

    override fun toString(): String = "BuildConfiguration(id=$idString,name=$name)"

    override fun getHomeUrl(branch: String?): String = getUserUrlPage(
            instance.serverUrl, "viewType.html", buildTypeId = id, branch = branch)

    override val name: String
        get() = notNull { it.name }

    override val projectId: ProjectId
        get() = ProjectId(notNull { it.projectId })

    override val id: BuildConfigurationId
        get() = BuildConfigurationId(idString)

    override val paused: Boolean
        get() = nullable { it.paused } ?: false // TC won't return paused:false field

    override val buildTags: List<String> by lazy {
        instance.service.buildTypeTags(idString).tag!!.map { it.name!! }
    }

    override val finishBuildTriggers: List<FinishBuildTrigger> by lazy {
        instance.service.buildTypeTriggers(idString)
                .trigger
                ?.filter { it.type == "buildDependencyTrigger" }
                ?.map { FinishBuildTriggerImpl(it) }.orEmpty()
    }

    override val artifactDependencies: List<ArtifactDependency> by lazy {
        instance.service
                .buildTypeArtifactDependencies(idString)
                .`artifact-dependency`
                ?.filter { it.disabled == false }
                ?.map { ArtifactDependencyImpl(it, true, instance) }.orEmpty()
    }

    override fun setParameter(name: String, value: String) {
        LOG.info("Setting parameter $name=$value in BuildConfigurationId=$idString")
        instance.service.setBuildTypeParameter(idString, name, TypedString(value))
    }

    override var buildCounter: Int
        get() = getSetting("buildNumberCounter")?.toIntOrNull()
                ?: throw TeamCityQueryException("Cannot get 'buildNumberCounter' setting for $idString")
        set(value) {
            LOG.info("Setting build counter to '$value' in BuildConfigurationId=$idString")
            instance.service.setBuildTypeSettings(idString, "buildNumberCounter", TypedString(value.toString()))
        }

    override var buildNumberFormat: String
        get() = getSetting("buildNumberPattern")
                ?: throw TeamCityQueryException("Cannot get 'buildNumberPattern' setting for $idString")
        set(value) {
            LOG.info("Setting build number format to '$value' in BuildConfigurationId=$idString")
            instance.service.setBuildTypeSettings(idString, "buildNumberPattern", TypedString(value))
        }

    private fun getSetting(settingName: String) =
            nullable { it.settings }?.property?.firstOrNull { it.name == settingName }?.value

    override fun runBuild(parameters: Map<String, String>?,
                          queueAtTop: Boolean,
                          cleanSources: Boolean,
                          rebuildAllDependencies: Boolean,
                          comment: String?,
                          logicalBranchName: String?,
                          personal: Boolean): Build {
        val request = TriggerBuildRequestBean()

        request.buildType = BuildTypeBean().apply { id = this@BuildConfigurationImpl.idString }
        request.branchName = logicalBranchName
        comment?.let { commentText -> request.comment = CommentBean().apply { text = commentText } }
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
        return instance.build(BuildId(triggeredBuildBean.id!!.toString()))
    }

    override fun getWebUrl(branch: String?): String = getHomeUrl(branch = branch)
    override fun fetchBuildTags(): List<String> = buildTags
    override fun fetchFinishBuildTriggers(): List<FinishBuildTrigger> = finishBuildTriggers
    override fun fetchArtifactDependencies(): List<ArtifactDependency> = artifactDependencies
}

private class VcsRootLocatorImpl(private val instance: TeamCityInstanceImpl) : VcsRootLocator {
    override fun all(): Sequence<VcsRoot> {
        return lazyPaging(instance, {
            LOG.debug("Retrieving vcs roots from ${instance.serverUrl}")
            return@lazyPaging instance.service.vcsRoots()
        }) { vcsRootsBean ->
            Page(
                    data = vcsRootsBean.`vcs-root`.map { VcsRootImpl(it, false, instance) },
                    nextHref = vcsRootsBean.nextHref
            )
        }
    }

    override fun list(): List<VcsRoot> = all().toList()
}

private class ChangeImpl(bean: ChangeBean,
                         isFullBean: Boolean,
                         instance: TeamCityInstanceImpl) :
        BaseImpl<ChangeBean>(bean, isFullBean, instance), Change {
    override fun fetchFullBean(): ChangeBean = instance.service.change(changeId = idString)

    override fun getHomeUrl(specificBuildConfigurationId: BuildConfigurationId?, includePersonalBuilds: Boolean?): String = getUserUrlPage(
            instance.serverUrl, "viewModification.html",
            modId = id,
            buildTypeId = specificBuildConfigurationId,
            personal = includePersonalBuilds)

    override fun firstBuilds(): List<Build> =
            instance.service
                    .changeFirstBuilds(id.stringId)
                    .build
                    .map { BuildImpl(it, false, instance) }

    override val id: ChangeId
        get() = ChangeId(idString)

    override val version: String
        get() = notNull { it.version }

    override val username: String
        get() = notNull { it.username }

    override val user: User?
        get() = nullable { it.user }?.let { UserImpl(it, false, instance) }

    override val date: Date
        get() = teamCityServiceDateFormat.get().parse(notNull { it.date })

    override val comment: String
        get() = notNull { it.comment }

    override fun toString() =
            "Change(id=$id, version=$version, username=$username, user=$user, date=$date, comment=$comment)"

    override fun getWebUrl(specificBuildConfigurationId: BuildConfigurationId?, includePersonalBuilds: Boolean?): String =
            getHomeUrl(
                    specificBuildConfigurationId = specificBuildConfigurationId,
                    includePersonalBuilds = includePersonalBuilds
            )
}

private class UserImpl(bean: UserBean,
                       isFullBuildBean: Boolean,
                       instance: TeamCityInstanceImpl) :
        BaseImpl<UserBean>(bean, isFullBuildBean, instance), User {

    override fun fetchFullBean(): UserBean = instance.service.users("id:${id.stringId}")

    override val email: String?
        get() = nullable { it.email }

    override val id: UserId
        get() = UserId(idString)

    override val username: String
        get() = notNull { it.username }

    override val name: String?
        get() = nullable { it.name }

    override fun getHomeUrl(): String = getUserUrlPage(
            instance.serverUrl, "admin/editUser.html",
            userId = id
    )

    override fun toString(): String {
        return "User(id=${id.stringId}, username=$username)"
    }
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

private class ArtifactDependencyImpl(bean: ArtifactDependencyBean,
                                     isFullBean: Boolean,
                                     instance: TeamCityInstanceImpl) :
        BaseImpl<ArtifactDependencyBean>(bean, isFullBean, instance), ArtifactDependency {

    override fun fetchFullBean(): ArtifactDependencyBean {
        error("Not supported, ArtifactDependencyImpl should be created with full bean")
    }

    override fun toString(): String = "ArtifactDependency(buildConf=${dependsOnBuildConfiguration.id.stringId})"

    override val dependsOnBuildConfiguration: BuildConfiguration
        get() = BuildConfigurationImpl(notNull { it.`source-buildType` }, false, instance)

    override val branch: String?
        get () = findPropertyByName("revisionBranch")

    override val artifactRules: List<ArtifactRule>
        get() = findPropertyByName("pathRules")!!.split(' ').map { ArtifactRuleImpl(it) }

    override val cleanDestinationDirectory: Boolean
        get() = findPropertyByName("cleanDestinationDirectory")!!.toBoolean()

    private fun findPropertyByName(name: String): String? {
        return fullBean.properties?.property?.find { it.name == name }?.value
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

    override val vcsRootInstance: VcsRootInstance
        get() = VcsRootInstanceImpl(bean.`vcs-root-instance`!!)
}

private data class BranchImpl(
        override val name: String?,
        override val isDefault: Boolean) : Branch

private data class Page<out T>(val data: List<T>, val nextHref: String?)

private val CONVERTER = GsonConverter(Gson())

private inline fun <reified Bean, T> lazyPaging(instance: TeamCityInstanceImpl,
                                                crossinline getFirstBean: () -> Bean,
                                                crossinline convertToPage: (Bean) -> Page<T>): Sequence<T> {
    val initialValue = Page<T>(listOf(), null)
    return generateSequence(initialValue) { prev ->
        return@generateSequence when {
            prev === initialValue -> convertToPage(getFirstBean())
            prev.nextHref == null || prev.nextHref.isBlank() -> return@generateSequence null
            else -> {
                val path = prev.nextHref.trimStart(*"/${instance.authMethod}/".toCharArray())
                val response = instance.service.root(path)
                val body = response.body ?: return@generateSequence null
                val bean = CONVERTER.fromBody(body, Bean::class.java) as Bean
                convertToPage(bean)
            }
        }
    }.mapNotNull { it.data }.flatten()
}

private class BuildImpl(bean: BuildBean,
                        isFullBean: Boolean,
                        instance: TeamCityInstanceImpl) :
        BaseImpl<BuildBean>(bean, isFullBean, instance), Build {
    override fun fetchFullBean(): BuildBean = instance.service.build(id.stringId)

    override fun getHomeUrl(): String = getUserUrlPage(
            instance.serverUrl, "viewLog.html",
            buildId = id
    )

    override val id: BuildId
        get() = BuildId(idString)

    override val buildConfigurationId: BuildConfigurationId
        get() = BuildConfigurationId(notNull { it.buildTypeId })

    override val buildNumber: String?
        get() = nullable { it.number }

    override val status: BuildStatus?
        get() = nullable { it.status }

    override val state: BuildState
        get() = try {
            val state = notNull { it.state }
            BuildState.valueOf(state.toUpperCase())
        } catch (e: IllegalArgumentException) {
            BuildState.UNKNOWN
        }

    override val branch: Branch
        get() {
            val branchName = nullable { it.branchName }
            val isDefaultBranch = nullable { it.defaultBranch }
            return BranchImpl(
                    name = branchName,
                    isDefault = isDefaultBranch ?: (branchName == null)
            )
        }

    override val name: String by lazy {
        bean.buildType?.name ?: instance.buildConfiguration(buildConfigurationId).name
    }

    override fun toString(): String {
        return "Build{id=$id, buildConfigurationId=$buildConfigurationId, buildNumber=$buildNumber, status=$status, branch=$branch}"
    }

    override val canceledInfo: BuildCanceledInfo?
        get() = fullBean.canceledInfo?.let { BuildCanceledInfoImpl(it, instance) }
    override val statusText: String?
        get() = fullBean.statusText
    override val queuedDate: Date
        get() = teamCityServiceDateFormat.get().parse(fullBean.queuedDate!!)
    override val startDate: Date?
        get() = fullBean.startDate?.let { teamCityServiceDateFormat.get().parse(it) }
    override val finishDate: Date?
        get() = fullBean.finishDate?.let { teamCityServiceDateFormat.get().parse(it) }

    override val runningInfo: BuildRunningInfo?
        get() = fullBean.`running-info`?.let { BuildRunningInfoImpl(it) }

    override val pinInfo get() = fullBean.pinInfo?.let { PinInfoImpl(it, instance) }
    override val triggeredInfo get() = fullBean.triggered?.let { TriggeredImpl(it, instance) }
    override val snapshotDependencies: List<Build> get() =
        fullBean.`snapshot-dependencies`?.build?.map { BuildImpl(it, false, instance) } ?: emptyList()

    override fun tests(status: TestStatus?): Sequence<TestOccurrence> = lazyPaging(instance, {
        val statusLocator = when (status) {
            null -> ""
            TestStatus.FAILED -> ",status:FAILURE"
            TestStatus.SUCCESSFUL -> ",status:SUCCESS"
            TestStatus.IGNORED -> ",ignored:true"
            TestStatus.UNKNOWN -> error("Unsupported filter by test status UNKNOWN")
        }

        return@lazyPaging instance.service.tests(
                locator = "build:(id:${id.stringId})$statusLocator",
                fields = TestOccurrenceBean.filter)
    }) { occurrencesBean ->
        Page(
                data = occurrencesBean.testOccurrence.map { TestOccurrenceImpl(it) },
                nextHref = occurrencesBean.nextHref
        )
    }

    override val buildProblems: Sequence<BuildProblemOccurrence>
        get() = lazyPaging(instance, {
            return@lazyPaging instance.service.problemOccurrences(
                    locator = "build:(id:${id.stringId})",
                    fields = "\$long,problemOccurrence(\$long)")
        }) { occurrencesBean ->
            Page(
                    data = occurrencesBean.problemOccurrence.map { BuildProblemOccurrenceImpl(it, instance) },
                    nextHref = occurrencesBean.nextHref
            )
        }

    override val parameters: List<Parameter>
        get() = fullBean.properties!!.property!!.map { ParameterImpl(it) }
    override val tags: List<String>
        get() = fullBean.tags?.tag?.map { it.name!! } ?: emptyList()
    override val revisions: List<Revision>
        get() = fullBean.revisions!!.revision!!.map { RevisionImpl(it) }

    override val changes: List<Change>
        get() = instance.service.changes(
                "build:$idString",
                "change(id,version,username,user,date,comment)")
                .change!!.map { ChangeImpl(it, true, instance) }

    override fun addTag(tag: String) {
        LOG.info("Adding tag $tag to build $buildNumber (id:$idString)")
        instance.service.addTag(idString, TypedString(tag))
    }

    override fun pin(comment: String) {
        LOG.info("Pinning build $buildNumber (id:$idString)")
        instance.service.pin(idString, TypedString(comment))
    }

    override fun unpin(comment: String) {
        LOG.info("Unpinning build $buildNumber (id:$idString)")
        instance.service.unpin(idString, TypedString(comment))
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

    override fun downloadBuildLog(output: File) {
        LOG.info("Downloading build log from build $buildNumber (id:${id.stringId}) to $output")

        val response = instance.service.buildLog(id.stringId)
        saveToFile(response, output)

        LOG.debug("Build log from build $buildNumber (id:${id.stringId}) downloaded to $output")
    }

    override fun cancel(comment: String, reAddIntoQueue: Boolean) {
        val request = BuildCancelRequestBean()
        request.comment = comment
        request.readdIntoQueue = reAddIntoQueue
        instance.service.cancelBuild(id.stringId, request)
    }

    override fun getWebUrl(): String = getHomeUrl()
    override fun fetchStatusText(): String? = statusText
    override fun fetchQueuedDate(): Date = queuedDate
    override fun fetchStartDate(): Date? = startDate
    override fun fetchFinishDate(): Date? = finishDate
    override fun fetchParameters(): List<Parameter> = parameters
    override fun fetchRevisions(): List<Revision> = revisions
    override fun fetchChanges(): List<Change> = changes
    override fun fetchPinInfo(): PinInfo? = pinInfo
    override fun fetchTriggeredInfo(): TriggeredInfo? = triggeredInfo
    override val buildTypeId: BuildConfigurationId = buildConfigurationId
}

private class BuildRunningInfoImpl(private val bean: BuildRunningInfoBean): BuildRunningInfo {
    override val percentageComplete: Int
        get() = bean.percentageComplete
    override val elapsedSeconds: Long
        get() = bean.elapsedSeconds
    override val estimatedTotalSeconds: Long
        get() = bean.estimatedTotalSeconds
    override val outdated: Boolean
        get() = bean.outdated
    override val probablyHanging: Boolean
        get() = bean.probablyHanging
}

private class VcsRootImpl(bean: VcsRootBean,
                          isFullVcsRootBean: Boolean,
                          instance: TeamCityInstanceImpl) :
        BaseImpl<VcsRootBean>(bean, isFullVcsRootBean, instance), VcsRoot {

    override fun fetchFullBean(): VcsRootBean = instance.service.vcsRoot(idString)

    override fun toString(): String = "VcsRoot(id=$id, name=$name, url=$url"

    override val id: VcsRootId
        get() = VcsRootId(idString)

    override val name: String
        get() = notNull { it.name }

    val properties: List<NameValueProperty> by lazy {
        fullBean.properties!!.property!!.map { NameValueProperty(it) }
    }

    override val url: String?
        get() = getNameValueProperty(properties, "url")

    override val defaultBranch: String?
        get() = getNameValueProperty(properties, "branch")
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

private class BuildQueueImpl(private val instance: TeamCityInstanceImpl): BuildQueue {
    override fun removeBuild(id: BuildId, comment: String, reAddIntoQueue: Boolean) {
        val request = BuildCancelRequestBean()
        request.comment = comment
        request.readdIntoQueue = reAddIntoQueue
        instance.service.removeQueuedBuild(id.stringId, request)
    }

    override fun queuedBuilds(projectId: ProjectId?): Sequence<Build> {
        val parameters = if (projectId == null) emptyList() else listOf("project:${projectId.stringId}")

        return lazyPaging(instance, {
            val buildLocator = if (parameters.isNotEmpty()) parameters.joinToString(",") else null
            LOG.debug("Retrieving queued builds from ${instance.serverUrl} using query '$buildLocator'")
            return@lazyPaging instance.service.queuedBuilds(locator = buildLocator)
        }) { buildsBean ->
            Page(
                    data = buildsBean.build.map { BuildImpl(it, false, instance) },
                    nextHref = buildsBean.nextHref
            )
        }
    }
}

private fun getNameValueProperty(properties: List<NameValueProperty>, name: String): String? = properties.singleOrNull { it.name == name}?.value

private class TestOccurrenceImpl(bean: TestOccurrenceBean): TestOccurrence {
    override val name = bean.name!!

    override val status = when {
        bean.ignored == true -> TestStatus.IGNORED
        bean.status == "FAILURE" -> TestStatus.FAILED
        bean.status == "SUCCESS" -> TestStatus.SUCCESSFUL
        else -> TestStatus.UNKNOWN
    }

    override val duration = Duration.ofMillis(bean.duration ?: 0L)!!

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
    personal?.let { params.add("personal=" + if (personal) "true" else "false") }
    buildTypeId?.let { params.add("buildTypeId=" + buildTypeId.stringId.urlencode()) }
    branch?.let { params.add("branch=" + branch.urlencode()) }

    return "$serverUrl/$pageName" +
            if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
}

private fun saveToFile(response: retrofit.client.Response, file: File) {
    file.parentFile.mkdirs()
    val input = response.body.`in`()
    BufferedOutputStream(FileOutputStream(file)).use {
        input.copyTo(it)
    }
}
