@file:Suppress("RemoveRedundantBackticks", "OverridingDeprecatedMember")

package org.jetbrains.teamcity.rest

import com.google.gson.Gson
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.*
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.xml.stream.XMLOutputFactory
import javax.xml.stream.XMLStreamWriter
import kotlin.concurrent.thread
import kotlin.math.min

private val LOG = LoggerFactory.getLogger("teamcity-rest-client")

private val teamCityServiceDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssZ", Locale.ENGLISH)

internal fun createGuestAuthInstance(serverUrl: String): TeamCityInstanceImpl {
    return TeamCityInstanceImpl(serverUrl.trimEnd('/'), "/guestAuth/", null, false)
}

internal fun createHttpAuthInstance(serverUrl: String, username: String, password: String): TeamCityInstanceImpl {
    val authorization = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    return TeamCityInstanceImpl(serverUrl.trimEnd('/'), "/httpAuth/", "Basic $authorization", false)
}

internal fun createTokenAuthInstance(serverUrl: String, token: String): TeamCityInstanceImpl {
    return TeamCityInstanceImpl(serverUrl.trimEnd('/'), "/", "Bearer $token", false)
}

private class RetryInterceptor : Interceptor {
    private fun okhttp3.Response.retryRequired(): Boolean {
        val code = code
        if (code < 400) return false

        // Do not retry non-GET methods, their result may be not idempotent
        if (request.method != "GET") return false

        return  code == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
                code == HttpURLConnection.HTTP_INTERNAL_ERROR ||
                code == HttpURLConnection.HTTP_BAD_GATEWAY ||
                code == HttpURLConnection.HTTP_UNAVAILABLE ||
                code == HttpURLConnection.HTTP_GATEWAY_TIMEOUT
    }

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        var response = chain.proceed(request)

        var tryCount = 0
        while (response.retryRequired() && tryCount < 3) {
            tryCount++
            LOG.warn("Request ${request.url} is not successful, $tryCount sec waiting [$tryCount retry]")
            runCatching { response.close() }
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

private fun selectRestApiCountForPagedRequests(limitResults: Int?, pageSize: Int?): Int? {
    val reasonableMaxPageSize = 1024
    return pageSize ?: limitResults?.let { min(it, reasonableMaxPageSize) }
}

internal class TeamCityInstanceImpl(override val serverUrl: String,
                                    val serverUrlBase: String,
                                    private val authHeader: String?,
                                    logResponses: Boolean,
                                    timeout: Long = 2,
                                    unit: TimeUnit = TimeUnit.MINUTES
) : TeamCityInstance() {
    override fun withLogResponses() = TeamCityInstanceImpl(serverUrl, serverUrlBase, authHeader, true)
    override fun withTimeout(timeout: Long, unit: TimeUnit) = TeamCityInstanceImpl(serverUrl, serverUrlBase, authHeader, true, timeout, unit)

    private val restLog = LoggerFactory.getLogger(LOG.name + ".rest")

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        restLog.debug(if (authHeader != null) message.replace(authHeader, "[REDACTED]") else message)
    }.apply {
        level = if (logResponses) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.HEADERS
    }

    private var client = OkHttpClient.Builder()
        .readTimeout(timeout, unit)
        .writeTimeout(timeout, unit)
        .connectTimeout(timeout, unit)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().apply {
                if (authHeader != null) {
                    header("Authorization", authHeader)
                }
            }.build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .addInterceptor(RetryInterceptor())
        .dispatcher(Dispatcher(
            //by default non-daemon threads are used, and it blocks JVM from exit
            ThreadPoolExecutor(0, Int.MAX_VALUE, 60, TimeUnit.SECONDS,
                SynchronousQueue(),
                object: ThreadFactory {
                    private val count = AtomicInteger(0)
                    override fun newThread(r: Runnable) = thread(
                        block = { r.run() },
                        isDaemon = true,
                        start = false,
                        name = "TeamCity-Rest-Client - OkHttp Dispatcher - ${count.incrementAndGet()}"
                    )
                }
            )))
        .build()

    internal val service = Retrofit.Builder()
        .client(client)
        .baseUrl("$serverUrl$serverUrlBase")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TeamCityService::class.java)
        .blockingBridge()

    override fun close() {
        fun catchAll(action: () -> Unit): Unit = try {
            action()
        } catch (t: Throwable) {
            LOG.warn("Failed to close connection. ${t.message}", t)
        }

        catchAll {  client.dispatcher.cancelAll() }
        catchAll {  client.dispatcher.executorService.shutdown() }
        catchAll {  client.connectionPool.evictAll() }
        catchAll {  client.cache?.close() }
    }

    override fun builds(): BuildLocator = BuildLocatorImpl(this)

    override fun investigations(): InvestigationLocator = InvestigationLocatorImpl(this)

    override fun mutes(): MuteLocator = MuteLocatorImpl(this)

    override fun tests(): TestLocator = TestLocatorImpl(this)
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

    override fun buildAgents(): BuildAgentLocator = BuildAgentLocatorImpl(this)

    override fun buildAgentPools(): BuildAgentPoolLocator = BuildAgentPoolLocatorImpl(this)

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

    override fun testRuns(): TestRunsLocator = TestRunsLocatorImpl(this)
}

private fun <T> List<T>.toSequence(): Sequence<T> = object : Sequence<T> {
    override fun iterator(): Iterator<T> = this@toSequence.iterator()
}

private class BuildAgentLocatorImpl(private val instance: TeamCityInstanceImpl): BuildAgentLocator {
    private var compatibleConfigurationId: BuildConfigurationId? = null

    override fun compatibleWith(buildConfigurationId: BuildConfigurationId): BuildAgentLocator {
        compatibleConfigurationId = buildConfigurationId
        return this
    }

    override fun all(): Sequence<BuildAgent> {
        val compatibleConfigurationIdCopy = compatibleConfigurationId

        val parameters = listOfNotNull(
            compatibleConfigurationIdCopy?.let { "compatible:(buildType:(id:${compatibleConfigurationIdCopy.stringId}))" }
        )
        val locator = parameters.joinToString(",")

        return if (locator.isNotEmpty()) {
            lazyPaging(instance, {
                LOG.debug("Retrieving agents from ${instance.serverUrl} using query '$locator'")
                return@lazyPaging instance.service.agents(locator, BuildAgentBean.fields)
            }) { agentsBean ->
                Page(
                    data = agentsBean.agent.map { BuildAgentImpl(it, false, instance) },
                    nextHref = agentsBean.nextHref
                )
            }
        } else {
            instance.service.agents().agent.map { BuildAgentImpl(it, false, instance) }.toSequence()
        }
    }
}

private class BuildAgentPoolLocatorImpl(private val instance: TeamCityInstanceImpl): BuildAgentPoolLocator {
    override fun all(): Sequence<BuildAgentPool> =
        instance.service.agentPools().agentPool.map { BuildAgentPoolImpl(it, false, instance) }.toSequence()
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
    private var affectedProjectId: ProjectId? = null
    private var buildConfigurationId: BuildConfigurationId? = null
    private var snapshotDependencyTo: BuildId? = null
    private var number: String? = null
    private var vcsRevision: String? = null
    private var since: Instant? = null
    private var until: Instant? = null
    private var status: BuildStatus? = BuildStatus.SUCCESS
    private var tags = ArrayList<String>()
    private var limitResults: Int? = null
    private var pageSize: Int? = null
    private var branch: String? = null
    private var includeAllBranches = false
    private var pinnedOnly = false
    private var personal: String? = null
    private var running: String? = null
    private var canceled: String? = null

    override fun forProject(projectId: ProjectId): BuildLocator {
        this.affectedProjectId = projectId
        return this
    }

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

    override fun since(date: Instant): BuildLocator {
        this.since = date
        return this
    }

    override fun until(date: Instant): BuildLocator {
        this.until = date
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

    override fun includePersonal(): BuildLocator {
        this.personal = "any"
        return this
    }

    override fun onlyPersonal(): BuildLocator {
        this.personal = "true"
        return this
    }

    override fun limitResults(count: Int): BuildLocator {
        this.limitResults = count
        return this
    }

    override fun pageSize(pageSize: Int): BuildLocator {
        this.pageSize = pageSize
        return this
    }

    override fun latest(): Build? {
        return limitResults(1).all().firstOrNull()
    }

    override fun all(): Sequence<Build> {
        val count = selectRestApiCountForPagedRequests(limitResults = limitResults, pageSize = pageSize)

        val parameters = listOfNotNull(
                affectedProjectId?.stringId?.let { "affectedProject:(id:$it)" },
                buildConfigurationId?.stringId?.let { "buildType:$it" },
                snapshotDependencyTo?.stringId?.let { "snapshotDependency:(to:(id:$it))" },
                number?.let { "number:$it" },
                running?.let { "running:$it" },
                canceled?.let { "canceled:$it" },
                vcsRevision?.let { "revision:$it" },
                status?.name?.let { "status:$it" },
                if (tags.isNotEmpty())
                    tags.joinToString(",", prefix = "tags:(", postfix = ")")
                else null,
                if (pinnedOnly) "pinned:true" else null,
                count?.let { "count:$it" },

                since?.let {"sinceDate:${teamCityServiceDateFormat.withZone(ZoneOffset.UTC).format(it)}"},
                until?.let {"untilDate:${teamCityServiceDateFormat.withZone(ZoneOffset.UTC).format(it)}"},

                if (!includeAllBranches)
                    branch?.let { "branch:$it" }
                else
                    "branch:default:any",

                personal?.let { "personal:$it" },

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

        val limitResults1 = limitResults
        return if (limitResults1 != null) sequence.take(limitResults1) else sequence
    }

    override fun list(): List<Build> = all().toList()
    override fun withAnyStatus(): BuildLocator = includeFailed()
    override fun sinceDate(date: Date): BuildLocator = since(date.toInstant())
    override fun untilDate(date: Date): BuildLocator = until(date.toInstant())
}

private class InvestigationLocatorImpl(private val instance: TeamCityInstanceImpl) : InvestigationLocator {
    private var limitResults: Int? = null
    private var targetType: InvestigationTargetType? = null
    private var affectedProjectId: ProjectId? = null

    override fun limitResults(count: Int): InvestigationLocator {
        this.limitResults = count
        return this
    }

    override fun withTargetType(targetType: InvestigationTargetType): InvestigationLocator {
        this.targetType = targetType
        return this
    }

    override fun forProject(projectId: ProjectId): InvestigationLocator {
        this.affectedProjectId = projectId
        return this
    }

    override fun all(): Sequence<Investigation> {
        var investigationLocator : String? = null

        val parameters = listOfNotNull(
                limitResults?.let { "count:$it" },
                affectedProjectId?.let { "affectedProject:$it" },
                targetType?.let { "type:${it.value}" }
        )

        if (parameters.isNotEmpty()) {
            investigationLocator = parameters.joinToString(",")
            LOG.debug("Retrieving investigations from ${instance.serverUrl} using query '$investigationLocator'")
        }

        return instance.service
                .investigations(investigationLocator = investigationLocator)
                .investigation.map { InvestigationImpl(it, true, instance) }
                .toSequence()
    }

}

private class MuteLocatorImpl(private val instance: TeamCityInstanceImpl) : MuteLocator {
    private var limitResults: Int? = null
    private var reporter: UserId? = null
    private var test: TestId? = null
    private var affectedProjectId: ProjectId? = null

    override fun limitResults(count: Int): MuteLocator {
        this.limitResults = count
        return this
    }
    override fun forProject(projectId: ProjectId): MuteLocator {
        this.affectedProjectId = projectId
        return this
    }

    override fun byUser(userId: UserId): MuteLocator {
        this.reporter = userId
        return this
    }

    override fun forTest(testId: TestId): MuteLocator {
        this.test = testId
        return this
    }

    override fun all(): Sequence<Mute> {
        var muteLocator : String? = null

        val parameters = listOfNotNull(
            limitResults?.let { "count:$it" },
            affectedProjectId?.let { "affectedProject:$it" },
            reporter?.let { "reporter:$it" },
            test?.let { "test:$it" }
        )

        if (parameters.isNotEmpty()) {
            muteLocator = parameters.joinToString(",")
            LOG.debug("Retrieving mutes from ${instance.serverUrl} using query '$muteLocator'")
        }

        return instance.service
            .mutes(muteLocator = muteLocator)
            .mute.map { MuteImpl(it, true, instance) }
            .toSequence()
    }

}

private class TestLocatorImpl(private val instance: TeamCityInstanceImpl) : TestLocator {
    private var id: TestId? = null
    private var count: Int? = null
    private var name: String? = null
    private var affectedProjectId: ProjectId? = null
    private var currentlyMuted: Boolean? = null

    override fun limitResults(count: Int): TestLocator {
        this.count = count
        return this
    }
    override fun byId(testId: TestId): TestLocator {
        this.id = testId
        return this
    }

    override fun byName(name: String): TestLocator {
        this.name = name
        return this
    }

    override fun currentlyMuted(muted: Boolean): TestLocator {
        this.currentlyMuted = muted
        return this
    }
    override fun forProject(projectId: ProjectId): TestLocator {
        this.affectedProjectId = projectId
        return this
    }
    override fun all(): Sequence<Test> {
        if (name == null && id == null && (affectedProjectId == null || currentlyMuted == null)) {
            throw IllegalArgumentException("TestLocator needs name or id, or affectedProjectID with e.g. currentlyMuted specified")
        }

        var testLocator : String? = null

        val parameters = listOfNotNull(
            name?.let { "name:$it" },
            id?.let { "id:$it" },
            affectedProjectId?.let { "affectedProject:$it" },
            currentlyMuted?.let { "currentlyMuted:$it" },
            count?.let { "count:$it" }
        )

        if (parameters.isNotEmpty()) {
            testLocator = parameters.joinToString(",")
            LOG.debug("Retrieving test from ${instance.serverUrl} using query '$testLocator'")
        }

        return instance.service
            .tests(testLocator)
            .test.map { TestImpl(it, true, instance) }
            .toSequence()
    }
}

private class TestRunsLocatorImpl(private val instance: TeamCityInstanceImpl) : TestRunsLocator {
    private var limitResults: Int? = null
    private var pageSize: Int? = null
    private var buildId: BuildId? = null
    private var testId: TestId? = null
    private var affectedProjectId: ProjectId? = null
    private var testStatus: TestStatus? = null
    private var expandMultipleInvocations = false
    private var includeDetailsField = true

    override fun limitResults(count: Int): TestRunsLocator {
        this.limitResults = count
        return this
    }

    override fun pageSize(pageSize: Int): TestRunsLocator {
        this.pageSize= pageSize
        return this
    }

    override fun forProject(projectId: ProjectId): TestRunsLocator {
        this.affectedProjectId = projectId
        return this
    }

    override fun forBuild(buildId: BuildId): TestRunsLocator {
        this.buildId = buildId
        return this
    }

    override fun forTest(testId: TestId): TestRunsLocator {
        this.testId = testId
        return this
    }

    override fun withStatus(testStatus: TestStatus): TestRunsLocator {
        this.testStatus = testStatus
        return this
    }

    override fun withoutDetailsField(): TestRunsLocator {
        this.includeDetailsField = false
        return this
    }

    override fun expandMultipleInvocations(): TestRunsLocator {
        this.expandMultipleInvocations = true
        return this
    }

    override fun all(): Sequence<TestRun> {
        val statusLocator = when (testStatus) {
            null -> null
            TestStatus.FAILED -> "status:FAILURE"
            TestStatus.SUCCESSFUL -> "status:SUCCESS"
            TestStatus.IGNORED -> "ignored:true"
            TestStatus.UNKNOWN -> error("Unsupported filter by test status UNKNOWN")
        }

        val count = selectRestApiCountForPagedRequests(limitResults = limitResults, pageSize = pageSize)
        val parameters = listOfNotNull(
                count?.let { "count:$it" },
                affectedProjectId?.let { "affectedProject:$it" },
                buildId?.let { "build:$it" },
                testId?.let { "test:$it" },
                expandMultipleInvocations.let { "expandInvocations:$it" },
                statusLocator
        )

        if (parameters.isEmpty()) {
            throw IllegalArgumentException("At least one parameter should be specified")
        }

        val fields = TestOccurrenceBean.getFieldsFilter(includeDetailsField)
        val isFullBean = fields == TestOccurrenceBean.fullFieldsFilter
        val sequence = lazyPaging(instance, {
            val testOccurrencesLocator = parameters.joinToString(",")
            LOG.debug("Retrieving test occurrences from ${instance.serverUrl} using query '$testOccurrencesLocator'")

            return@lazyPaging instance.service.testOccurrences(locator = testOccurrencesLocator, fields = fields)
        }) { testOccurrencesBean ->
            Page(
                    data = testOccurrencesBean.testOccurrence.map { TestRunImpl(it, isFullBean, instance) },
                    nextHref = testOccurrencesBean.nextHref
            )
        }

        val limitResults1 = limitResults
        return if (limitResults1 != null) sequence.take(limitResults1) else sequence
    }
}

private abstract class BaseImpl<TBean : IdBean>(
        private var bean: TBean,
        private var isFullBean: Boolean,
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
        if (!isFullBean) {
            bean = fetchFullBean()
            isFullBean = true
        }
        bean
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

private abstract class InvestigationMuteBaseImpl<TBean : InvestigationMuteBaseBean>(
    bean: TBean,
    isFullProjectBean: Boolean,
    instance: TeamCityInstanceImpl) :
    BaseImpl<TBean>(bean, isFullProjectBean, instance) {
    val id: InvestigationId
        get() = InvestigationId(idString)

    val reporter: User?
        get() {
            val assignment = nullable { it.assignment } ?: return null
            val userBean = assignment.user ?: return null
            return UserImpl( userBean, false, instance)
        }
    val comment: String
        get() = notNull { it.assignment?.text ?: "" }
    val resolveMethod: InvestigationResolveMethod
        get() {
            val asString = notNull { it.resolution?.type }
            if (asString == "whenFixed") {
                return InvestigationResolveMethod.WHEN_FIXED
            } else if (asString == "manually") {
                return InvestigationResolveMethod.MANUALLY
            }

            throw IllegalStateException("Properties are invalid")
        }
    val targetType: InvestigationTargetType
        get() {
            val target = notNull { it.target}
            if (target.tests != null) return InvestigationTargetType.TEST
            if (target.problems != null) return InvestigationTargetType.BUILD_PROBLEM
            return InvestigationTargetType.BUILD_CONFIGURATION
        }

    val testIds: List<TestId>?
        get() = nullable { it.target?.tests?.test?.map { x -> TestId(notNull { x.id })} }

    val problemIds: List<BuildProblemId>?
        get() = nullable { it.target?.problems?.problem?.map { x -> BuildProblemId(notNull { x.id })} }

    val scope: InvestigationScope
        get() {
            val scope = notNull { it.scope }
            val project = scope.project?.let { bean -> ProjectImpl(bean, false, instance) }
            if (project != null) {
                return InvestigationScope.InProject(project)
            }

            /* neither teamcity.jetbrains nor buildserver contain more then one assignment build type */
            if (scope.buildTypes?.buildType != null && scope.buildTypes.buildType.size > 1) {
                throw IllegalStateException("more then one buildType")
            }
            val buildConfiguration = scope.buildTypes?.let { bean -> BuildConfigurationImpl(bean.buildType[0], false, instance) }
            if (buildConfiguration != null) {
                return InvestigationScope.InBuildConfiguration(buildConfiguration)
            }

            throw IllegalStateException("scope is missed in the bean")
        }
}

private class InvestigationImpl(
        bean: InvestigationBean,
        isFullProjectBean: Boolean,
        instance: TeamCityInstanceImpl) :
        InvestigationMuteBaseImpl<InvestigationBean>(bean, isFullProjectBean, instance), Investigation {
    override fun fetchFullBean(): InvestigationBean = instance.service.investigation(id.stringId)

    override fun toString(): String = "Investigation(id=$idString,state=$state)"

    override val assignee: User
        get() = UserImpl( notNull { it.assignee }, false, instance)

    override val state: InvestigationState
        get() = notNull { it.state }

}

private class MuteImpl(
    bean: MuteBean,
    isFullProjectBean: Boolean,
    instance: TeamCityInstanceImpl) :
    InvestigationMuteBaseImpl<MuteBean>(bean, isFullProjectBean, instance), Mute {

    override val tests: List<Test>?
        get() = nullable { it.target?.tests?.test?.map { testBean -> TestImpl(testBean, false, instance) } }

    override val assignee: User?
        get() {
            val assignment = nullable { it.assignment } ?: return null
            val userBean = assignment.user ?: return null
            return UserImpl( userBean, false, instance)
        }

    override fun fetchFullBean(): MuteBean = instance.service.mute(id.stringId)

    override fun toString(): String = "Investigation(id=$idString)"
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
        instance.service.setProjectParameter(id.stringId, name, value.toRequestBody())
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

        val projectBean = instance.service.createProject(projectXmlDescription.toRequestBody())
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

        val vcsRootBean = instance.service.createVcsRoot(vcsRootDescriptionXml.toRequestBody())
        return VcsRootImpl(vcsRootBean, true, instance)
    }

    override fun createBuildConfiguration(buildConfigurationDescriptionXml: String): BuildConfiguration {
        val bean = instance.service.createBuildType(buildConfigurationDescriptionXml.toRequestBody())
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
        instance.service.setBuildTypeParameter(idString, name, value.toRequestBody())
    }

    override var buildCounter: Int
        get() = getSetting("buildNumberCounter")?.toIntOrNull()
                ?: throw TeamCityQueryException("Cannot get 'buildNumberCounter' setting for $idString")
        set(value) {
            LOG.info("Setting build counter to '$value' in BuildConfigurationId=$idString")
            instance.service.setBuildTypeSettings(idString, "buildNumberCounter", value.toString().toRequestBody())
        }

    override var buildNumberFormat: String
        get() = getSetting("buildNumberPattern")
                ?: throw TeamCityQueryException("Cannot get 'buildNumberPattern' setting for $idString")
        set(value) {
            LOG.info("Setting build number format to '$value' in BuildConfigurationId=$idString")
            instance.service.setBuildTypeSettings(idString, "buildNumberPattern", value.toRequestBody())
        }

    private fun getSetting(settingName: String) =
            nullable { it.settings }?.property?.firstOrNull { it.name == settingName }?.value

    override fun runBuild(
        parameters: Map<String, String>?,
        queueAtTop: Boolean,
        cleanSources: Boolean?,
        rebuildAllDependencies: Boolean,
        comment: String?,
        logicalBranchName: String?,
        personal: Boolean
    ): Build {
        return runBuild(parameters, queueAtTop, cleanSources, rebuildAllDependencies,
            comment, logicalBranchName, null, personal, null, null)
    }

    override fun runBuild(parameters: Map<String, String>?,
                          queueAtTop: Boolean,
                          cleanSources: Boolean?,
                          rebuildAllDependencies: Boolean,
                          comment: String?,
                          logicalBranchName: String?,
                          agentId: String?,
                          personal: Boolean): Build {
        return runBuild(parameters, queueAtTop, cleanSources, rebuildAllDependencies,
            comment, logicalBranchName, agentId, personal, null, null)
    }

    override fun runBuild(
        parameters: Map<String, String>?,
        queueAtTop: Boolean,
        cleanSources: Boolean?,
        rebuildAllDependencies: Boolean,
        comment: String?,
        logicalBranchName: String?,
        agentId: String?,
        personal: Boolean,
        revisions: List<SpecifiedRevision>?,
        dependencies: List<BuildId>?
    ): Build {
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
        parameters?.let { parametersMap ->
            val parametersBean = ParametersBean(parametersMap.map { ParameterBean(it.key, it.value) })
            request.properties = parametersBean
        }
        if (!agentId.isNullOrEmpty())
            request.agent = BuildAgentBean().apply { id = agentId }
        request.`snapshot-dependencies` = dependencies?.let { deps ->
            BuildListBean().apply {
                build = deps.map {
                    BuildBean().apply { id = it.stringId }
                }
            }
        }
        request.revisions = revisions?.let { r ->
            RevisionsBean().apply {
                revision = r.map {
                    RevisionBean().apply {
                        version = it.version
                        vcsBranchName = it.vcsBranchName
                        `vcs-root-instance` = VcsRootInstanceBean().apply {
                            `vcs-root-id` = it.vcsRootId.stringId
                        }
                    }
                }
            }
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

    override val dateTime: ZonedDateTime
        get() = ZonedDateTime.parse(notNull { it.date }, teamCityServiceDateFormat)

    override val comment: String
        get() = notNull { it.comment }

    override val vcsRootInstance: VcsRootInstance?
        get() = nullable { it.vcsRootInstance }?.let { VcsRootInstanceImpl(it) }

    override val files: List<ChangeFile> by lazy {
        instance.service.changeFiles(id.stringId).files?.file?.map { ChangeFileImpl(it) } ?: emptyList()
    }

    override fun toString() =
            "Change(id=$id, version=$version, username=$username, user=$user, date=$dateTime, comment=$comment, " +
                    "vcsRootInstance=$vcsRootInstance)"

    override fun getWebUrl(specificBuildConfigurationId: BuildConfigurationId?, includePersonalBuilds: Boolean?): String =
            getHomeUrl(
                    specificBuildConfigurationId = specificBuildConfigurationId,
                    includePersonalBuilds = includePersonalBuilds
            )
    override val date: Date
        get() = Date.from(dateTime.toInstant())
}

private class ChangeFileImpl(private val bean: ChangeFileBean) : ChangeFile {
    override val fileRevisionBeforeChange: String?
        get() = bean.`before-revision`
    override val fileRevisionAfterChange: String?
        get() = bean.`after-revision`
    override val changeType: ChangeType
        get() = try {
            bean.changeType?.let { ChangeType.valueOf(it.toUpperCase()) } ?: ChangeType.UNKNOWN
        } catch (e: IllegalArgumentException) {
            ChangeType.UNKNOWN
        }
    override val filePath: String?
        get() = bean.file
    override val relativeFilePath: String?
        get() = bean.`relative-file`

    override fun toString(): String {
        return "ChangeFile(fileRevisionBeforeChange=$fileRevisionBeforeChange, fileRevisionAfterChange=$fileRevisionAfterChange, changeType=$changeType, filePath=$filePath, relativeFilePath=$relativeFilePath)"
    }
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
    override val dateTime: ZonedDateTime = ZonedDateTime.parse(bean.timestamp!!, teamCityServiceDateFormat)
    override val time: Date = Date.from(dateTime.toInstant())
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
    override val cancelDateTime: ZonedDateTime
        get() = ZonedDateTime.parse(bean.timestamp!!, teamCityServiceDateFormat)
    override val user: User?
        get() = if (bean.user != null) UserImpl(bean.user!!, false, instance) else null
    override val cancelDate: Date
        get() = Date.from(cancelDateTime.toInstant())
    override val text: String
        get() = bean.text ?: ""
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

private val GSON = Gson()

private inline fun <reified BeanType> ResponseBody.toBean(): BeanType =  GSON.fromJson(string(), BeanType::class.java)

private fun String.splitToPathAndParams() : Pair<String, Map<String, String>> {
    val uri = URI(this)
    val path = uri.path
    val fullParams = uri.query
    val paramsMap = fullParams.split("&").associate {
        val (key, value) = it.split("=")
        key to value
    }
    return path to paramsMap
}

private inline fun <reified Bean, T> lazyPaging(instance: TeamCityInstanceImpl,
                                                crossinline getFirstBean: () -> Bean,
                                                crossinline convertToPage: (Bean) -> Page<T>): Sequence<T> {
    val initialValue = Page<T>(listOf(), null)
    return generateSequence(initialValue) { prev ->
        when {
            prev === initialValue -> convertToPage(getFirstBean())
            prev.nextHref.isNullOrBlank() -> null
            else -> {
                val hrefSuffix = prev.nextHref.removePrefix(instance.serverUrlBase)
                val (path, params) = hrefSuffix.splitToPathAndParams()
                val body = instance.service.root(path, params)
                val bean = body.toBean<Bean>()
                bean?.let(convertToPage)
            }
        }
    }.map(Page<T>::data).flatten()
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

    override val personal: Boolean
        get() = nullable { it.personal } ?: false

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

    override val composite: Boolean?
        get() = fullBean.composite

    override val canceledInfo: BuildCanceledInfo?
        get() = fullBean.canceledInfo?.let { BuildCanceledInfoImpl(it, instance) }
    override val statusText: String?
        get() = fullBean.statusText
    override val queuedDateTime: ZonedDateTime
        get() = fullBean.queuedDate!!.let { ZonedDateTime.parse(it, teamCityServiceDateFormat) }
    override val startDateTime: ZonedDateTime?
        get() = fullBean.startDate?.let { ZonedDateTime.parse(it, teamCityServiceDateFormat) }
    override val finishDateTime: ZonedDateTime?
        get() = fullBean.finishDate?.let { ZonedDateTime.parse(it, teamCityServiceDateFormat) }

    override val runningInfo: BuildRunningInfo?
        get() = fullBean.`running-info`?.let { BuildRunningInfoImpl(it) }
    override val comment: BuildCommentInfo?
        get() = fullBean.comment?.let { BuildCommentInfoImpl(it, instance) }
    override val agent: BuildAgent?
        get() {
            val agentBean = fullBean.agent

            if (agentBean?.id == null)
                return null

            return BuildAgentImpl(agentBean, false, instance)
        }

    override val detachedFromAgent: Boolean
        get() = nullable { it.detachedFromAgent } ?: false

    override val pinInfo get() = fullBean.pinInfo?.let { PinInfoImpl(it, instance) }
    override val triggeredInfo get() = fullBean.triggered?.let { TriggeredImpl(it, instance) }
    override val snapshotDependencies: List<Build> get() =
        fullBean.`snapshot-dependencies`?.build?.map { BuildImpl(it, false, instance) } ?: emptyList()

    override fun tests(status: TestStatus?): Sequence<TestRun> = testRuns(status)

    override fun testRuns(status: TestStatus?): Sequence<TestRun> = instance
            .testRuns()
            .forBuild(id)
            .let { if (status == null) it else it.withStatus(status) }
            .all()

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
                "change(id,version,username,user,date,comment,vcsRootInstance)")
                .change!!.map { ChangeImpl(it, true, instance) }

    override fun addTag(tag: String) {
        LOG.info("Adding tag $tag to build ${getHomeUrl()}")
        instance.service.addTag(idString, tag.toRequestBody())
    }

    override fun setComment(comment: String) {
        LOG.info("Adding comment $comment to build ${getHomeUrl()}")
        instance.service.setComment(idString, comment.toRequestBody())
    }

    override fun replaceTags(tags: List<String>) {
        LOG.info("Replacing tags of build ${getHomeUrl()} with ${tags.joinToString(", ")}")
        val tagBeans = tags.map { tag -> TagBean().apply { name = tag } }
        instance.service.replaceTags(idString, TagsBean().apply { tag = tagBeans })
    }

    override fun pin(comment: String) {
        LOG.info("Pinning build ${getHomeUrl()}")
        instance.service.pin(idString, comment.toRequestBody())
    }

    override fun unpin(comment: String) {
        LOG.info("Unpinning build ${getHomeUrl()}")
        instance.service.unpin(idString, comment.toRequestBody())
    }

    override fun getArtifacts(parentPath: String, recursive: Boolean, hidden: Boolean): List<BuildArtifact> {
        val locator = "recursive:$recursive,hidden:$hidden"
        val fields = "file(${ArtifactFileBean.FIELDS})"
        return instance.service.artifactChildren(id.stringId, parentPath, locator, fields).file
                .filter { it.fullName != null && it.modificationTime != null }
                .map { BuildArtifactImpl(this, it.name!!, it.fullName!!, it.size, ZonedDateTime.parse(it.modificationTime!!, teamCityServiceDateFormat)) }
    }

    override fun findArtifact(pattern: String, parentPath: String): BuildArtifact {
        return findArtifact(pattern, parentPath, false)
    }

    override fun findArtifact(pattern: String, parentPath: String, recursive: Boolean): BuildArtifact {
        val list = getArtifacts(parentPath, recursive)
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

    override fun openArtifactInputStream(artifactPath: String): InputStream {
        LOG.info("Opening artifact '$artifactPath' stream from build ${getHomeUrl()}")
        return openArtifactInputStreamImpl(artifactPath)
    }

    override fun downloadArtifact(artifactPath: String, output: File) {
        LOG.info("Downloading artifact '$artifactPath' from build ${getHomeUrl()} to $output")
        try {
            output.parentFile?.mkdirs()
            FileOutputStream(output).use {
                downloadArtifactImpl(artifactPath, it)
            }
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            LOG.debug("Artifact '$artifactPath' from build ${getHomeUrl()} downloaded to $output")
        }
    }

    override fun downloadArtifact(artifactPath: String, output: OutputStream) {
        LOG.info("Downloading artifact '$artifactPath' from build ${getHomeUrl()}")
        try {
            downloadArtifactImpl(artifactPath, output)
        } finally {
            LOG.debug("Artifact '$artifactPath' from build ${getHomeUrl()} downloaded")
        }
    }

    private fun openArtifactInputStreamImpl(artifactPath: String) : InputStream {
        return instance.service.artifactContent(id.stringId, artifactPath).byteStream()
    }

    private fun downloadArtifactImpl(artifactPath: String, output: OutputStream) {
        openArtifactInputStreamImpl(artifactPath).use { input ->
            output.use {
                input.copyTo(output, bufferSize = 512 * 1024)
            }
        }
    }

    override fun downloadBuildLog(output: File) {
        LOG.info("Downloading build log from build ${getHomeUrl()} to $output")

        val response = instance.service.buildLog(id.stringId)
        saveToFile(response, output)

        LOG.debug("Build log from build ${getHomeUrl()} downloaded to $output")
    }

    override fun cancel(comment: String, reAddIntoQueue: Boolean) {
        val request = BuildCancelRequestBean()
        request.comment = comment
        request.readdIntoQueue = reAddIntoQueue
        instance.service.cancelBuild(id.stringId, request)
    }

    override fun getResultingParameters(): List<Parameter> {
        return instance.service.resultingProperties(id.stringId).property!!.map { ParameterImpl(it) }
    }

    override fun finish() {
        instance.service.finishBuild(id.stringId)
    }

    override fun getWebUrl(): String = getHomeUrl()
    override fun fetchStatusText(): String? = statusText
    override fun fetchQueuedDate(): Date = Date.from(queuedDateTime.toInstant())
    override fun fetchStartDate(): Date? = startDateTime?.let { Date.from(it.toInstant()) }
    override fun fetchFinishDate(): Date? = finishDateTime?.let { Date.from(it.toInstant()) }
    override fun fetchParameters(): List<Parameter> = parameters
    override fun fetchRevisions(): List<Revision> = revisions
    override fun fetchChanges(): List<Change> = changes
    override fun fetchPinInfo(): PinInfo? = pinInfo
    override fun fetchTriggeredInfo(): TriggeredInfo? = triggeredInfo
    override val buildTypeId: BuildConfigurationId
        get() = buildConfigurationId
    override val queuedDate: Date
        get() = Date.from(queuedDateTime.toInstant())
    override val startDate: Date?
        get() = startDateTime?.let { Date.from(it.toInstant()) }
    override val finishDate: Date?
        get() = finishDateTime?.let { Date.from(it.toInstant()) }
}

private class TestImpl(bean: TestBean,
                       isFullBuildBean: Boolean,
                       instance: TeamCityInstanceImpl) :
    BaseImpl<TestBean>(bean, isFullBuildBean, instance), Test {

    override fun fetchFullBean(): TestBean = instance.service.test(idString)

    override val id: TestId
        get() = TestId(idString)

    override val name: String
        get() = notNull { it.name }

    override fun toString(): String {
        return "Test(id=${id.stringId}, name=$name)"
    }
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

private class BuildCommentInfoImpl(private val bean: BuildCommentBean,
                                   private val instance: TeamCityInstanceImpl): BuildCommentInfo {
    override val timestamp: ZonedDateTime
        get() = ZonedDateTime.parse(bean.timestamp!!, teamCityServiceDateFormat)
    override val user: User?
        get() = if (bean.user != null) UserImpl(bean.user!!, false, instance) else null
    override val text: String
        get() = bean.text ?: ""

    override fun toString(): String {
        return "BuildCommentInfo(timestamp=$timestamp,user=${user?.username},text=$text)"
    }
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

private class BuildAgentPoolImpl(bean: BuildAgentPoolBean,
                                 isFullBean: Boolean,
                                 instance: TeamCityInstanceImpl) :
        BaseImpl<BuildAgentPoolBean>(bean, isFullBean, instance), BuildAgentPool {

    override fun fetchFullBean(): BuildAgentPoolBean = instance.service.agentPools("id:$idString")

    override fun toString(): String = "BuildAgentPool(id=$id, name=$name)"

    override val id: BuildAgentPoolId
        get() = BuildAgentPoolId(idString)

    override val name: String
        get() = notNull { it.name }

    override val projects: List<Project>
        get() = fullBean.projects?.project?.map { ProjectImpl(it, false, instance) } ?: emptyList()

    override val agents: List<BuildAgent>
        get() = fullBean.agents?.agent?.map { BuildAgentImpl(it, false, instance) } ?: emptyList()
}

private class BuildAgentImpl(bean: BuildAgentBean,
                                 isFullBean: Boolean,
                                 instance: TeamCityInstanceImpl) :
        BaseImpl<BuildAgentBean>(bean, isFullBean, instance), BuildAgent {

    override fun fetchFullBean(): BuildAgentBean = instance.service.agent("id:$idString")

    override fun toString(): String = "BuildAgent(id=$id, name=$name)"

    override val id: BuildAgentId
        get() = BuildAgentId(idString)

    override val name: String
        get() = notNull { it.name }

    override val pool: BuildAgentPool
        get() = BuildAgentPoolImpl(fullBean.pool!!, false, instance)

    override val connected: Boolean
        get() = notNull { it.connected }

    override val enabled: Boolean
        get() = notNull { it.enabled }
    override val authorized: Boolean
        get() = notNull { it.authorized }
    override val outdated: Boolean
        get() = !notNull { it.uptodate }
    override val ipAddress: String
        get() = notNull { it.ip }

    override val parameters: List<Parameter>
        get() = fullBean.properties!!.property!!.map { ParameterImpl(it) }

    override val enabledInfo: BuildAgentEnabledInfo?
        get() = fullBean.enabledInfo?.let { info ->
            info.comment?.let { comment ->
                BuildAgentEnabledInfoImpl(
                        user = comment.user?.let { UserImpl(it, false, instance) },
                        timestamp = ZonedDateTime.parse(comment.timestamp!!, teamCityServiceDateFormat),
                        text = comment.text ?: ""
                )
            }
        }

    override val authorizedInfo: BuildAgentAuthorizedInfo?
        get() = fullBean.authorizedInfo?.let { info ->
            info.comment?.let { comment ->
                BuildAgentAuthorizedInfoImpl(
                        user = comment.user?.let { UserImpl(it, false, instance) },
                        timestamp = ZonedDateTime.parse(comment.timestamp!!, teamCityServiceDateFormat),
                        text = comment.text ?: ""
                )
            }
        }

    override val currentBuild: Build?
        get() = fullBean.build?.let {
            // API may return an empty build bean, pass it as null
            if (it.id == null) null else BuildImpl(it, false, instance)
        }

    override fun getHomeUrl(): String = "${instance.serverUrl}/agentDetails.html?id=${id.stringId}"

    private class BuildAgentAuthorizedInfoImpl(
            override val user: User?,
            override val timestamp: ZonedDateTime,
            override val text: String) : BuildAgentAuthorizedInfo

    private class BuildAgentEnabledInfoImpl(
            override val user: User?,
            override val timestamp: ZonedDateTime,
            override val text: String) : BuildAgentEnabledInfo
}

private class VcsRootInstanceImpl(private val bean: VcsRootInstanceBean) : VcsRootInstance {
    override val vcsRootId: VcsRootId
        get() = VcsRootId(bean.`vcs-root-id`!!)

    override val name: String
        get() = bean.name!!

    override fun toString(): String {
        return "VcsRootInstanceImpl(id=$vcsRootId, name=$name)"
    }


}

private class NameValueProperty(private val bean: NameValuePropertyBean) {
    val name: String
        get() = bean.name!!

    val value: String?
        get() = bean.value
}

private class BuildArtifactImpl(
        override val build: Build,
        override val name: String,
        override val fullName: String,
        override val size: Long?,
        override val modificationDateTime: ZonedDateTime) : BuildArtifact {

    override val modificationTime: Date
        get() = Date.from(modificationDateTime.toInstant())

    override fun download(output: File) {
        build.downloadArtifact(fullName, output)
    }

    override fun download(output: OutputStream) {
        build.downloadArtifact(fullName, output)
    }

    override fun openArtifactInputStream(): InputStream {
        return build.openArtifactInputStream(fullName)
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

        return queuedBuilds(parameters)
    }

    override fun queuedBuilds(buildConfigurationId: BuildConfigurationId): Sequence<Build> {
        val parameters = listOf("buildType:${buildConfigurationId.stringId}")
        return queuedBuilds(parameters)
    }

    private fun queuedBuilds(parameters: List<String>): Sequence<BuildImpl> {
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

@Suppress("DEPRECATION")
private open class TestOccurrenceImpl(private val bean: TestOccurrenceBean,
                                      isFullBean: Boolean,
                                      instance: TeamCityInstanceImpl): TestOccurrence, BaseImpl<TestOccurrenceBean>(bean, isFullBean, instance) {
    override val name: String
        get() = notNull { it.name }

    override val testOccurrenceId: TestOccurrenceId
        get() = TestOccurrenceId(notNull { it.id })

    final override val status
        get() = when {
            nullable { it.ignored } == true -> TestStatus.IGNORED
            nullable { it.status } == "FAILURE" -> TestStatus.FAILED
            nullable { it.status } == "SUCCESS" -> TestStatus.SUCCESSFUL
            else -> TestStatus.UNKNOWN
        }

    override val duration
        get() = Duration.ofMillis(nullable { it.duration } ?: 0L)!!

    override val details: String
        get() = when (status) {
            TestStatus.IGNORED -> nullable { it.ignoreDetails }
            TestStatus.FAILED -> nullable { it.details }
            else -> null
        } ?: ""

    override val ignored: Boolean
        get() = nullable { it.ignored } ?: false

    override val currentlyMuted: Boolean
        get() = nullable { it.currentlyMuted } ?: false

    override val muted: Boolean
        get() = nullable { it.muted } ?: false

    override val newFailure: Boolean
        get() = nullable { it.newFailure } ?: false

    override val buildId: BuildId
        get() = BuildId(notNull { it.build }.id!!)

    override val fixedIn: BuildId?
        get() {
            if (nullable { it.nextFixed }?.id == null)
                return null

            return BuildId(notNull { it.nextFixed }.id!!)
        }

    override val firstFailedIn : BuildId?
        get() {
            if (nullable { it.firstFailed }?.id == null)
                return null

            return BuildId(notNull { it.firstFailed }.id!!)
        }

    override val metadataValues: List<String>?
        get() {
            return nullable { it.metadata }?.typedValues?.map { it.value.toString()  }
        }

    override val testId: TestId
        get() = TestId(notNull { it.test }.id!!)

    override fun fetchFullBean(): TestOccurrenceBean = instance.service.testOccurrence(notNull { it.id }, TestOccurrenceBean.fullFieldsFilter)

    override fun toString() = "Test(name=$name, status=$status, duration=$duration, details=$details)"
}

private class TestRunImpl(bean: TestOccurrenceBean, isFullBean: Boolean, instance: TeamCityInstanceImpl) : TestRun,
    TestOccurrenceImpl(bean, isFullBean, instance)

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

private fun saveToFile(body: ResponseBody, file: File) {
    file.parentFile?.mkdirs()
    body.byteStream() .use { input ->
        file.outputStream().use { output ->
            input.copyTo(output, bufferSize = 512 * 1024)
        }
    }
}
