package org.jetbrains.teamcity.rest.coroutines

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jetbrains.teamcity.rest.*
import org.jetbrains.teamcity.rest.BuildLocatorSettings.BuildField
import org.jetbrains.teamcity.rest.TestRunsLocatorSettings.TestRunField
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.*
import java.net.HttpURLConnection
import java.net.URI
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
import kotlin.math.roundToLong

private val LOG = LoggerFactory.getLogger("teamcity-rest-client")

private val teamCityServiceDateFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssZ", Locale.ENGLISH)
private const val reasonableMaxPageSize = 1024

private val textPlainMediaType = "text/plain".toMediaType()
private val applicationXmlMediaType = "application/xml".toMediaType()

private fun String.toTextPlainBody() = toRequestBody(textPlainMediaType)
private fun String.toApplicationXmlBody() = toRequestBody(applicationXmlMediaType)

private inline fun <reified T : Enum<T>> Set<T>.copyToEnumSet(): EnumSet<T> =
    if (isEmpty()) EnumSet.noneOf(T::class.java) else EnumSet.copyOf(this)

private class RetryInterceptor(
    private val maxAttempts: Int,
    private val initialDelayMs: Long,
    private val maxDelayMs: Long,
) : Interceptor {
    private val random = Random()
    private val expBackOffFactor: Int = 2
    private val expBackOffJitter: Double = 0.1

    private fun Response.retryRequired(): Boolean {
        val code = code
        if (code < 400) return false

        // Do not retry non-GET methods, their result may be not idempotent
        if (request.method != "GET") return false

        return code == HttpURLConnection.HTTP_CLIENT_TIMEOUT ||
                code == HttpURLConnection.HTTP_INTERNAL_ERROR ||
                code == HttpURLConnection.HTTP_BAD_GATEWAY ||
                code == HttpURLConnection.HTTP_UNAVAILABLE ||
                code == HttpURLConnection.HTTP_GATEWAY_TIMEOUT ||
                code == 429 // Too many requests == rate limited
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        var nextDelay = initialDelayMs
        var attempt = 1
        while (true) {
            var error: IOException? = null
            val response: Response? = try {
                chain.proceed(request)
            } catch (e: IOException) {
                error = e
                null
            }

            // response is OK
            if (response != null && !response.retryRequired()) {
                return response
            }

            // attempt limit exceeded
            if (attempt == maxAttempts) {
                if (response != null) {
                    return response
                }
                throw TeamCityConversationException("Request ${request.url} failed, tried $maxAttempts times", error)
            }

            // log error end retry
            if (response != null) {
                LOG.warn(
                    "Request ${request.method} ${request.url} failed: HTTP code ${response.code}, " +
                            "attempt=$attempt, will retry in $nextDelay ms"
                )
            } else {
                LOG.warn(
                    "Request ${request.method} ${request.url} failed, attempt=$attempt, will retry in $nextDelay ms",
                    error
                )
            }

            if (nextDelay != 0L) {
                Thread.sleep(nextDelay)
            }
            val nextRawDelay = minOf(nextDelay * expBackOffFactor, maxDelayMs)
            // (2 * random.nextDouble() - 1.0) -> between -1 and 1
            val jitter = ((2 * random.nextDouble() - 1) * nextRawDelay * expBackOffJitter).roundToLong()
            nextDelay = nextRawDelay + jitter

            // if not closed, next `chain.proceed(request)` will fail with error:
            // `cannot make a new request because the previous response is still open: please call response.close()`
            response?.close()

            attempt++
        }
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
    return pageSize ?: limitResults?.let { min(it, reasonableMaxPageSize) }
}

internal class TeamCityCoroutinesInstanceImpl(
    override val serverUrl: String,
    val serverUrlBase: String,
    private val authHeader: String?,
    private val nodeSelector: NodeSelector,
    private val logLevel: LoggingLevel,
    private val timeout: Long,
    private val unit: TimeUnit,
    private val maxConcurrentRequests: Int,
    private val maxConcurrentRequestsPerHost: Int,
    private val retryMaxAttempts: Int,
    private val retryInitialDelayMs: Long,
    private val retryMaxDelayMs: Long,
    private val userAgent: String?,

    ) : TeamCityCoroutinesInstanceEx {
    override fun toBuilder(): TeamCityInstanceBuilder = TeamCityInstanceBuilder(serverUrl)
        .setUrlBaseAndAuthHeader(serverUrlBase, authHeader)
        .withLoggingLevel(logLevel)
        .withTimeout(timeout, unit)
        .withMaxConcurrentRequests(maxConcurrentRequests)
        .withRetry(retryMaxAttempts, retryInitialDelayMs, retryMaxDelayMs, TimeUnit.MILLISECONDS)
        .withMaxConcurrentRequestsPerHost(maxConcurrentRequestsPerHost)
        .selectNode(nodeSelector)
        .apply { if (userAgent != null) withCustomUserAgent(userAgent) }

    private val restLog = LoggerFactory.getLogger(LOG.name + ".rest")

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        restLog.debug(if (authHeader != null) message.replace(authHeader, "[REDACTED]") else message)
    }.apply {
        level = when (logLevel) {
            LoggingLevel.BODY -> HttpLoggingInterceptor.Level.BODY
            LoggingLevel.HEADERS -> HttpLoggingInterceptor.Level.HEADERS
            LoggingLevel.BASIC -> HttpLoggingInterceptor.Level.BASIC
            LoggingLevel.NONE -> HttpLoggingInterceptor.Level.NONE
        }
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
                if (userAgent != null) {
                    header("User-Agent", userAgent)
                }
            }.build()
            chain.proceed(request)
        }
        .addInterceptor(loggingInterceptor)
        .addInterceptor(RetryInterceptor(retryMaxAttempts, retryInitialDelayMs, retryMaxDelayMs))
        .dispatcher(Dispatcher(
            //by default non-daemon threads are used, and it blocks JVM from exit
            ThreadPoolExecutor(0, Int.MAX_VALUE, 60, TimeUnit.SECONDS,
                SynchronousQueue(),
                object : ThreadFactory {
                    private val count = AtomicInteger(0)
                    override fun newThread(r: Runnable) = thread(
                        block = { r.run() },
                        isDaemon = true,
                        start = false,
                        name = "TeamCity-Rest-Client - OkHttp Dispatcher - ${count.incrementAndGet()}"
                    )
                }
            ))
            .apply {
                maxRequests = maxConcurrentRequests
                maxRequestsPerHost = maxConcurrentRequestsPerHost
            })
        .cookieJar(nodeSelector.toCookieJar())
        .build()

    internal val webLinks = WebLinks(serverUrl)

    internal val service = Retrofit.Builder()
        .client(client)
        .baseUrl("$serverUrl$serverUrlBase")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TeamCityService::class.java)
        .errorCatchingBridge()

    override fun close() {
        fun catchAll(action: () -> Unit): Unit = try {
            action()
        } catch (t: Throwable) {
            LOG.warn("Failed to close connection. ${t.message}", t)
        }

        catchAll { client.dispatcher.cancelAll() }
        catchAll { client.dispatcher.executorService.shutdown() }
        catchAll { client.connectionPool.evictAll() }
        catchAll { client.cache?.close() }
    }

    override fun builds(): BuildLocator = BuildLocatorImpl(this)

    override fun investigations(): InvestigationLocator = InvestigationLocatorImpl(this)
    override suspend fun createInvestigations(investigations: Collection<Investigation>) {
        val bean = InvestigationListBean().apply {
            investigation = investigations.map(Investigation::toInvestigationBean)
        }
        service.createInvestigations(bean)
    }

    override suspend fun deleteInvestigation(investigationId: InvestigationId) {
        // investigation id works as locator: `assignmentProject:(id:ijplatform),test:(id:5074875983533370049)`
        service.deleteInvestigations(investigationId.stringId)
    }

    override fun mutes(): MuteLocator = MuteLocatorImpl(this)

    override suspend fun createMutes(mutes: List<Mute>) {
        val bean = MuteListBean().apply {
            mute = mutes.map(Mute::toMuteBean)
        }
        service.createMutes(bean)
    }

    override suspend fun test(testId: TestId): Test = TestImpl(TestBean().apply { id = testId.stringId }, false, this)
    override fun tests(): TestLocator = TestLocatorImpl(this)
    override suspend fun build(id: BuildId): Build = BuildImpl(
        BuildBean().also { it.id = id.stringId }, emptySet(), this
    )

    override suspend fun build(buildConfigurationId: BuildConfigurationId, number: String): Build? =
        BuildLocatorImpl(this).fromConfiguration(buildConfigurationId).withNumber(number).latest()

    override suspend fun buildConfiguration(id: BuildConfigurationId): BuildConfiguration =
        BuildConfigurationImpl(BuildTypeBean().also { it.id = id.stringId }, false, this)

    override fun vcsRoots(): VcsRootLocator = VcsRootLocatorImpl(this)

    override suspend fun vcsRoot(id: VcsRootId): VcsRoot = VcsRootImpl(service.vcsRoot(id.stringId), true, this)

    override suspend fun project(id: ProjectId): Project =
        ProjectImpl(ProjectBean().let { it.id = id.stringId; it }, false, this)

    override suspend fun rootProject(): Project = project(ProjectId("_Root"))

    override suspend fun user(id: UserId): User =
        UserImpl(UserBean().also { it.id = id.stringId }, false, this)

    override suspend fun user(userName: String): User {
        val bean = service.users("username:$userName")
        return UserImpl(bean, true, this)
    }

    override fun users(): UserLocator = UserLocatorImpl(this)
    override suspend fun buildAgent(id: BuildAgentId): BuildAgent {
        return BuildAgentImpl(BuildAgentBean().also { it.id = id.stringId }, false, this)
    }

    override suspend fun buildAgent(typeId: BuildAgentTypeId): BuildAgent {
        val bean = service.agent("typeId:${typeId.stringId}")
        return BuildAgentImpl(bean, true, this)
    }

    override suspend fun change(buildConfigurationId: BuildConfigurationId, vcsRevision: String): Change =
        ChangeImpl(
            service.change(
                buildType = buildConfigurationId.stringId, version = vcsRevision
            ), true, this
        )

    override suspend fun change(id: ChangeId): Change =
        ChangeImpl(ChangeBean().also { it.id = id.stringId }, false, this)

    override fun buildQueue(): BuildQueue = BuildQueueImpl(this)

    override fun buildAgents(): BuildAgentLocator = BuildAgentLocatorImpl(this)

    override fun buildAgentPools(): BuildAgentPoolLocator = BuildAgentPoolLocatorImpl(this)

    override fun testRuns(): TestRunsLocator = TestRunsLocatorImpl(this)
}

private class BuildAgentLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) : BuildAgentLocatorEx {
    private var compatibleConfigurationId: BuildConfigurationId? = null

    override fun compatibleWith(buildConfigurationId: BuildConfigurationId): BuildAgentLocator {
        compatibleConfigurationId = buildConfigurationId
        return this
    }

    private fun getLocator(): String {
        val compatibleConfigurationIdCopy = compatibleConfigurationId
        val parameters = listOfNotNull(
            compatibleConfigurationIdCopy?.let { "compatible:(buildType:(id:${compatibleConfigurationIdCopy.stringId}))" }
        )
        val locator = parameters.joinToString(",")
        LOG.debug("Retrieving agents from ${instance.serverUrl} using query '$locator'")
        return locator
    }

    private suspend fun allAgents(): List<BuildAgentImpl> =
        instance.service.agents().agent.map { BuildAgentImpl(it, false, instance) }

    override fun all(): Flow<BuildAgent> {
        val locator = getLocator()
        return if (locator.isNotEmpty()) {
            lazyPagingFlow(instance,
                getFirstBean = { instance.service.agents(getLocator(), BuildAgentBean.fields) },
                convertToPage = { bean -> Page(bean.agent.map { BuildAgentImpl(it, false, instance) }, bean.nextHref) })
        } else {
            flow { allAgents().forEach { emit(it) } }
        }
    }

    override fun allSeq(): Sequence<BuildAgent> {
        val locator = getLocator()
        return if (locator.isNotEmpty())
            lazyPagingSequence(instance,
                getFirstBean = { instance.service.agents(getLocator(), BuildAgentBean.fields) },
                convertToPage = { bean -> Page(bean.agent.map { BuildAgentImpl(it, false, instance) }, bean.nextHref) })
        else
            runBlocking { allAgents() }.asSequence()
    }
}

private class BuildAgentPoolLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) :
    BuildAgentPoolLocatorEx {
    private suspend fun allPools(): List<BuildAgentPool> = instance.service.agentPools().agentPool
        .map { BuildAgentPoolImpl(it, false, instance) }

    override fun all(): Flow<BuildAgentPool> = flow { allPools().forEach { emit(it) } }

    override fun allSeq(): Sequence<BuildAgentPool> = runBlocking { allPools() }.asSequence()
}

private class UserLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) : UserLocatorEx {
    private suspend fun allUsers(): List<User> = instance.service.users().user.map { UserImpl(it, false, instance) }

    override fun all(): Flow<User> = flow { allUsers().forEach { emit(it) } }

    override fun allSeq(): Sequence<User> = runBlocking { allUsers() }.asSequence()
}

private class BuildLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) : BuildLocatorEx {
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
    private var agentName: String? = null
    private var defaultFilter: Boolean = true
    private val buildFields: MutableSet<BuildField> = BuildField.defaultFields.copyToEnumSet()


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

    override fun withAgent(agentName: String): BuildLocator {
        this.agentName = agentName
        return this
    }

    override fun defaultFilter(enable: Boolean): BuildLocator {
        this.defaultFilter = enable
        return this
    }

    override fun prefetchFields(vararg fields: BuildField): BuildLocator {
        buildFields.clear()
        buildFields.addAll(fields)
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

    override suspend fun latest(): Build? {
        return limitResults(1).all().firstOrNull()
    }

    private fun getLocator(): String {
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
            agentName?.let { "agentName:$it" },
            if (tags.isNotEmpty())
                tags.joinToString("),tag:(", prefix = "tag:(", postfix = ")")
            else null,
            if (pinnedOnly) "pinned:true" else null,
            count?.let { "count:$it" },

            since?.let { "sinceDate:${teamCityServiceDateFormat.withZone(ZoneOffset.UTC).format(it)}" },
            until?.let { "untilDate:${teamCityServiceDateFormat.withZone(ZoneOffset.UTC).format(it)}" },

            if (!includeAllBranches)
                branch?.let { "branch:$it" }
            else
                "branch:default:any",

            personal?.let { "personal:$it" },

            // Always use default filter since sometimes TC automatically switches between
            // defaultFilter:true and defaultFilter:false
            // See BuildPromotionFinder.java in rest-api, setLocatorDefaults method
            "defaultFilter:$defaultFilter"
        )
        check(parameters.isNotEmpty()) { "At least one parameter should be specified" }
        val locator = parameters.joinToString(",")
        LOG.debug("Retrieving builds from ${instance.serverUrl} using query '$locator'")
        return locator
    }

    override fun all(): Flow<Build> {
        val buildLocator = getLocator()
        val buildFieldsCopy = buildFields.copyToEnumSet()
        val fields = BuildBean.buildCustomFieldsFilter(buildFieldsCopy, wrap = true)
        val flow = lazyPagingFlow(instance,
            getFirstBean = { instance.service.builds(buildLocator = buildLocator, fields = fields) },
            convertToPage = { buildsBean ->
                Page(data = buildsBean.build.map { BuildImpl(it, buildFieldsCopy, instance) }, nextHref = buildsBean.nextHref)
            })
        return limitResults?.let(flow::take) ?: flow
    }

    override fun allSeq(): Sequence<Build> {
        val buildLocator = getLocator()
        val buildFieldsCopy = buildFields.copyToEnumSet()
        val fields = BuildBean.buildCustomFieldsFilter(buildFieldsCopy, wrap = true)
        val sequence = lazyPagingSequence(instance,
            getFirstBean = { instance.service.builds(buildLocator = buildLocator, fields = fields) },
            convertToPage = { buildsBean ->
                Page(data = buildsBean.build.map { BuildImpl(it, buildFieldsCopy, instance) }, nextHref = buildsBean.nextHref)
            })
        return limitResults?.let(sequence::take) ?: sequence
    }
}

private class InvestigationLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) : InvestigationLocatorEx {
    private var limitResults: Int? = null
    private var targetType: InvestigationTargetType? = null
    private var affectedProjectId: ProjectId? = null
    private var buildConfigurationId: BuildConfigurationId? = null
    private var testId: TestId? = null

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

    override fun forBuildConfiguration(buildConfigurationId: BuildConfigurationId): InvestigationLocator {
        this.buildConfigurationId = buildConfigurationId
        return this
    }

    override fun forTest(testId: TestId): InvestigationLocator {
        this.testId = testId
        return this
    }

    private fun getLocator(): String? {
        var locator: String? = null

        val parameters = listOfNotNull(
            (limitResults ?: reasonableMaxPageSize).let { "count:$it" },
            affectedProjectId?.let { "affectedProject:$it" },
            targetType?.let { "type:${it.value}" },
            testId?.let { "test:(id:${it.stringId})" },
            buildConfigurationId?.let { "buildType:(id:${it.stringId})" }
        )

        if (parameters.isNotEmpty()) {
            locator = parameters.joinToString(",")
            LOG.debug("Retrieving investigations from ${instance.serverUrl} using query '$locator'")
        }
        return locator
    }

    override fun all(): Flow<Investigation> {
        val locator = getLocator()
        val flow = lazyPagingFlow(instance,
            getFirstBean = { instance.service.investigations(locator) },
            convertToPage = { investigationsBean ->
                Page(
                    data = investigationsBean.investigation.map { InvestigationImpl(it, true, instance) },
                    nextHref = investigationsBean.nextHref
                )
            })
        return limitResults?.let(flow::take) ?: flow
    }

    override fun allSeq(): Sequence<Investigation> {
        val locator = getLocator()
        val flow = lazyPagingSequence(instance,
            getFirstBean = { instance.service.investigations(locator) },
            convertToPage = { investigationsBean ->
                Page(
                    data = investigationsBean.investigation.map { InvestigationImpl(it, true, instance) },
                    nextHref = investigationsBean.nextHref
                )
            })
        return limitResults?.let(flow::take) ?: flow
    }
}

private class MuteLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) : MuteLocatorEx {
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

    private fun getLocator(): String? {
        var muteLocator: String? = null

        val parameters = listOfNotNull(
            (limitResults ?: reasonableMaxPageSize).let { "count:$it" },
            affectedProjectId?.let { "affectedProject:$it" },
            reporter?.let { "reporter:$it" },
            test?.let { "test:$it" }
        )

        if (parameters.isNotEmpty()) {
            muteLocator = parameters.joinToString(",")
            LOG.debug("Retrieving mutes from ${instance.serverUrl} using query '$muteLocator'")
        }

        return muteLocator
    }

    override fun all(): Flow<Mute> {
        val locator = getLocator()
        val flow = lazyPagingFlow(instance,
            getFirstBean = { instance.service.mutes(locator) },
            convertToPage = { mutesBean ->
                Page(
                    data = mutesBean.mute.map { MuteImpl(it, true, instance) },
                    nextHref = mutesBean.nextHref
                )
            })
        return limitResults?.let(flow::take) ?: flow
    }

    override fun allSeq(): Sequence<Mute> {
        val locator = getLocator()
        val flow = lazyPagingSequence(instance,
            getFirstBean = { instance.service.mutes(locator) },
            convertToPage = { mutesBean ->
                Page(
                    data = mutesBean.mute.map { MuteImpl(it, true, instance) },
                    nextHref = mutesBean.nextHref
                )
            })
        return limitResults?.let(flow::take) ?: flow
    }
}

private class TestLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) : TestLocatorEx {
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

    override fun byName(testName: String): TestLocator {
        this.name = testName
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

    private suspend fun getTests(): List<TestImpl> {
        require(name != null || id != null || !(affectedProjectId == null || currentlyMuted == null)) {
            "TestLocator needs name or id, or affectedProjectID with e.g. currentlyMuted specified"
        }
        var testLocator: String? = null

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
    }

    override fun all(): Flow<Test> = flow { getTests().forEach { emit(it) } }

    override fun allSeq(): Sequence<Test> = runBlocking { getTests() }.asSequence()
}

private class TestRunsLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) : TestRunsLocatorEx {
    private var limitResults: Int? = null
    private var pageSize: Int? = null
    private var buildId: BuildId? = null
    private var testId: TestId? = null
    private var affectedProjectId: ProjectId? = null
    private var testStatus: TestStatus? = null
    private var expandMultipleInvocations = false
    private var muted: Boolean? = null
    private var currentlyMuted: Boolean? = null
    private val testRunFields = EnumSet.allOf(TestRunField::class.java)

    override fun limitResults(count: Int): TestRunsLocator {
        this.limitResults = count
        return this
    }

    override fun pageSize(pageSize: Int): TestRunsLocator {
        this.pageSize = pageSize
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

    @Suppress("OVERRIDE_DEPRECATION")
    override fun withoutDetailsField(): TestRunsLocator {
        this.testRunFields.remove(TestRunField.DETAILS)
        return this
    }

    override fun expandMultipleInvocations(): TestRunsLocator {
        this.expandMultipleInvocations = true
        return this
    }

    override fun muted(muted: Boolean): TestRunsLocator {
        this.muted = muted
        return this
    }

    override fun currentlyMuted(currentlyMuted: Boolean): TestRunsLocator {
        this.currentlyMuted = currentlyMuted
        return this
    }

    override fun prefetchFields(vararg fields: TestRunField): TestRunsLocator {
        this.testRunFields.clear()
        this.testRunFields.addAll(fields)
        return this
    }

    override fun excludePrefetchFields(vararg fields: TestRunField): TestRunsLocator {
        this.testRunFields.removeAll(fields.toSet())
        return this
    }

    private data class Locator(val testOccurrencesLocator: String, val fields: String, val fieldsSet: Set<TestRunField>)

    private fun getLocator(): Locator {
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
            muted?.let { "muted:$it" },
            currentlyMuted?.let { "currentlyMuted:$it" },
            expandMultipleInvocations.let { "expandInvocations:$it" },
            statusLocator
        )
        require(parameters.isNotEmpty()) { "At least one parameter should be specified" }
        val testOccurrencesLocator = parameters.joinToString(",")

        val fields = TestOccurrenceBean.buildCustomFieldsFilter(testRunFields, wrap = true)
        LOG.debug("Retrieving test occurrences from ${instance.serverUrl} using query '$testOccurrencesLocator'")
        return Locator(testOccurrencesLocator, fields, testRunFields.clone())
    }

    override fun all(): Flow<TestRun> {
        val (testOccurrencesLocator, fields, fieldsSet) = getLocator()
        val flow = lazyPagingFlow(instance,
            getFirstBean = { instance.service.testOccurrences(testOccurrencesLocator, fields) },
            convertToPage = { testOccurrencesBean ->
                Page(
                    data = testOccurrencesBean.testOccurrence.map { TestRunImpl(it, fieldsSet, instance) },
                    nextHref = testOccurrencesBean.nextHref
                )
            })
        return limitResults?.let(flow::take) ?: flow
    }


    override fun allSeq(): Sequence<TestRun> {
        val (testOccurrencesLocator, fields, fieldsSet) = getLocator()
        val sequence = lazyPagingSequence(instance,
            getFirstBean = { instance.service.testOccurrences(testOccurrencesLocator, fields) },
            convertToPage = { testOccurrencesBean ->
                Page(
                    data = testOccurrencesBean.testOccurrence.map { TestRunImpl(it, fieldsSet, instance) },
                    nextHref = testOccurrencesBean.nextHref
                )
            })
        return limitResults?.let(sequence::take) ?: sequence
    }
}

private abstract class BaseImpl<TBean : IdBean>(
    protected var bean: TBean,
    protected var isFullBean: Boolean,
    protected val instance: TeamCityCoroutinesInstanceImpl
) {
    init {
        if (bean.id == null) {
            throw IllegalStateException("bean.id should not be null")
        }
    }

    protected inline val idString
        get() = bean.id!!

    protected suspend inline fun <T> notnull(getter: (TBean) -> T?): T =
        getter(bean) ?: getter(fullBean.getValue())!!

    protected suspend inline fun <T> nullable(getter: (TBean) -> T?): T? =
        getter(bean) ?: getter(fullBean.getValue())

    protected suspend fun <T> fromFullBeanIf(check: Boolean, getter: (TBean) -> T?): T? {
        val maybeValue = getter(bean)
        if (maybeValue != null || !check) {
            return maybeValue
        }
        return getter(fullBean.getValue())
    }

    val fullBean = SuspendingLazy {
        if (!isFullBean) {
            val full = fetchFullBean()
            check(isFullBeanIdValid(bean.id, full.id)) {
                "Incorrect full bean fetched: current '${bean.id}' != fetched '${full.id}'"
            }
            bean = full
            isFullBean = true
        }
        bean
    }

    open fun isFullBeanIdValid(beanId: String?, fullBeanId: String?): Boolean = beanId == fullBeanId
    abstract suspend fun fetchFullBean(): TBean
    abstract override fun toString(): String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return idString == (other as BaseImpl<*>).idString && instance.serverUrl === other.instance.serverUrl
    }

    override fun hashCode(): Int = idString.hashCode()
}

private abstract class InvestigationMuteBaseImpl<TBean : InvestigationMuteBaseBean>(
    bean: TBean,
    isFullProjectBean: Boolean,
    override val tcInstance: TeamCityCoroutinesInstanceImpl
) : BaseImpl<TBean>(bean, isFullProjectBean, tcInstance), IssueEx {
    val id: InvestigationId
        get() = InvestigationId(idString)

    override val reporter: UserId? by lazy { bean.assignment?.user?.id?.let(::UserId) }
    override val reportedAt: ZonedDateTime? by lazy { bean.assignment?.timestamp?.let { time -> ZonedDateTime.parse(time, teamCityServiceDateFormat) } }

    override val comment: String
        get() = bean.assignment?.text ?: ""
    override val resolveMethod: InvestigationResolveMethod
        get() {
            val asString = bean.resolution?.type ?: error("Unexpected null investigation resolve method")
            return InvestigationResolveMethod.values().firstOrNull {
                it.value == asString
            } ?: error("Unexpected resolve method type: $asString")
        }
    override val targetType: InvestigationTargetType
        get() {
            val target = bean.target!!
            if (target.tests != null) return InvestigationTargetType.TEST
            if (target.problems != null) return InvestigationTargetType.BUILD_PROBLEM
            return InvestigationTargetType.BUILD_CONFIGURATION
        }

    override val testIds: List<TestId>?
        get() = bean.target?.tests?.test?.map { x -> TestId(x.id!!) }

    override val problemIds: List<BuildProblemId>?
        get() = bean.target?.problems?.problem?.map { x -> BuildProblemId(x.id!!) }

    override val scope: InvestigationScope
        get() {
            val scope = bean.scope!!
            val projectId = scope.project?.id
            if (projectId != null) {
                return InvestigationScope.InProject(ProjectId(projectId))
            }

            if (!scope.buildTypes?.buildType.isNullOrEmpty()) {
                val buildConfigurationIds = scope.buildTypes!!.buildType
                    .mapNotNull(BuildTypeBean::id)
                    .map(::BuildConfigurationId)

                return if (buildConfigurationIds.count() == 1) {
                    InvestigationScope.InBuildConfiguration(buildConfigurationIds.single())
                } else {
                    InvestigationScope.InBuildConfigurations(buildConfigurationIds)
                }
            }
            error("scope is missed in the bean")
        }
    override val resolutionTime: ZonedDateTime?
        get() = bean.resolution?.time?.let { time -> ZonedDateTime.parse(time, teamCityServiceDateFormat) }
}

private class InvestigationImpl(
    bean: InvestigationBean,
    isFullProjectBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    InvestigationMuteBaseImpl<InvestigationBean>(bean, isFullProjectBean, instance), Investigation {
    override suspend fun fetchFullBean(): InvestigationBean = instance.service.investigation(id.stringId)

    override fun toString(): String = "Investigation(id=$idString,state=$state)"

    override val assignee: UserId by lazy { UserId(bean.assignee!!.id!!) }

    override val state: InvestigationState
        get() = bean.state!!

}

private class MuteImpl(
    bean: MuteBean,
    isFullProjectBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    InvestigationMuteBaseImpl<MuteBean>(bean, isFullProjectBean, instance), Mute {

    override val assignee: UserId? by lazy { bean.assignment?.user?.id?.let(::UserId) }

    override suspend fun fetchFullBean(): MuteBean = instance.service.mute(id.stringId)

    override fun toString(): String = "Investigation(id=$idString)"
}

private class ProjectImpl(
    bean: ProjectBean,
    isFullProjectBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<ProjectBean>(bean, isFullProjectBean, instance), ProjectEx {
    override suspend fun fetchFullBean(): ProjectBean = instance.service.project(id.stringId)

    override fun toString(): String =
        if (isFullBean) runBlocking { "Project(id=$idString,name=${getName()})" } else "Project(id=$idString)"

    override fun getHomeUrl(branch: String?): String =
        instance.webLinks.projectPage(id, branch = branch)

    override fun getTestHomeUrl(testId: TestId): String =
        instance.webLinks.testHistoryPage(testId, id)

    override fun getMutes(): Flow<Mute> = lazyPagingFlow(
        instance = instance,
        getFirstBean = {
            instance.service.mutes("project:(id:${this@ProjectImpl.id.stringId}),count:$reasonableMaxPageSize")
        },
        convertToPage = { bean ->
            Page(bean.mute.map { MuteImpl(it, true, instance) }, bean.nextHref)
        }
    )

    override suspend fun createMutes(mutes: List<Mute>) = instance.createMutes(mutes)

    override fun getMutesSeq(): Sequence<Mute> = lazyPagingSequence(
        instance = instance,
        getFirstBean = {
            instance.service.mutes("project:(id:${this@ProjectImpl.id.stringId}),count:$reasonableMaxPageSize")
        },
        convertToPage = { bean ->
            Page(bean.mute.map { MuteImpl(it, true, instance) }, bean.nextHref)
        }
    )

    override suspend fun assignToAgentPool(agentPoolId: BuildAgentPoolId) {
        instance.service.assignProjectToAgentPool(
            agentPoolId.stringId,
            ProjectBean().apply { id = this@ProjectImpl.id.stringId })
    }

    override val id = ProjectId(idString)
    private val name = SuspendingLazy { notnull { it.name } }
    private val archived = SuspendingLazy { nullable { it.archived } ?: false }
    private val parentProjectId = SuspendingLazy { nullable { it.parentProjectId }?.let { ProjectId(it) } }
    private val parameters = SuspendingLazy { fullBean.getValue().parameters!!.property!!.map { ParameterImpl(it) } }

    private val childProjects = SuspendingLazy {
        fullBean.getValue().projects!!.project.map { ProjectImpl(it, false, instance) }
    }

    private val buildConfigurations = SuspendingLazy {
        fullBean.getValue().buildTypes!!.buildType.map { BuildConfigurationImpl(it, false, instance) }
    }

    override suspend fun getName(): String = name.getValue()
    override suspend fun isArchived(): Boolean = archived.getValue()
    override suspend fun getParentProjectId(): ProjectId? = parentProjectId.getValue()
    override suspend fun getChildProjects(): List<Project> = childProjects.getValue()
    override suspend fun getBuildConfigurations(): List<BuildConfiguration> = buildConfigurations.getValue()
    override suspend fun getParameters(): List<Parameter> = parameters.getValue()

    override suspend fun setParameter(name: String, value: String) {
        LOG.info("Setting parameter $name=$value in ProjectId=$idString")
        instance.service.setProjectParameter(id.stringId, name, value.toTextPlainBody())
    }

    override suspend fun removeParameter(name: String) {
        LOG.info("Unsetting parameter $name in ProjectId=$idString")
        instance.service.removeProjectParameter(id.stringId, name)
    }

    override suspend fun createProject(id: ProjectId, name: String): Project {
        val projectXmlDescription = xml {
            element("newProjectDescription") {
                attribute("name", name)
                attribute("id", id.stringId)
                element("parentProject") {
                    attribute("locator", "id:${this@ProjectImpl.id.stringId}")
                }
            }
        }

        val projectBean = instance.service.createProject(projectXmlDescription.toApplicationXmlBody())
        return ProjectImpl(projectBean, true, instance)
    }

    override suspend fun createVcsRoot(
        id: VcsRootId,
        name: String,
        type: VcsRootType,
        properties: Map<String, String>
    ): VcsRoot {
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

        val vcsRootBean = instance.service.createVcsRoot(vcsRootDescriptionXml.toApplicationXmlBody())
        return VcsRootImpl(vcsRootBean, true, instance)
    }

    override suspend fun createBuildConfiguration(buildConfigurationDescriptionXml: String): BuildConfiguration {
        val bean = instance.service.createBuildType(buildConfigurationDescriptionXml.toApplicationXmlBody())
        return BuildConfigurationImpl(bean, false, instance)
    }
}

private class BuildConfigurationImpl(
    bean: BuildTypeBean,
    isFullBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<BuildTypeBean>(bean, isFullBean, instance), BuildConfiguration {
    override suspend fun fetchFullBean(): BuildTypeBean =
        instance.service.buildConfiguration(idString, BuildTypeBean.fields)

    override fun toString(): String =
        if (isFullBean) runBlocking { "BuildConfiguration(id=$idString,name=${getName()})" } else "BuildConfiguration(id=$idString)"

    override fun getHomeUrl(branch: String?): String =
        instance.webLinks.buildConfigurationPage(id, branch = branch)

    override val id = BuildConfigurationId(idString)
    private val name = SuspendingLazy { notnull { it.name } }
    private val projectId = SuspendingLazy { ProjectId(notnull { it.projectId }) }
    private val paused = SuspendingLazy { nullable { it.paused } ?: false } // TC won't return paused:false field
    private val type = SuspendingLazy { BuildConfigurationType.valueOf(notnull { it.type }) }

    private val projectName = SuspendingLazy { notnull { it.projectName } }

    private val buildCounter = SuspendingLazy {
        getSetting("buildNumberCounter")?.toIntOrNull()
            ?: throw TeamCityQueryException("Cannot get 'buildNumberCounter' setting for $idString")
    }

    private val buildNumberFormat = SuspendingLazy {
        getSetting("buildNumberPattern")
            ?: throw TeamCityQueryException("Cannot get 'buildNumberPattern' setting for $idString")
    }

    override suspend fun getName(): String = name.getValue()
    override suspend fun getProjectId(): ProjectId = projectId.getValue()
    override suspend fun getProjectName(): String = projectName.getValue()

    override suspend fun isPaused(): Boolean = paused.getValue()

    override suspend fun getType(): BuildConfigurationType = type.getValue()

    override suspend fun getBuildTags(): List<String> {
        return instance.service.buildTypeTags(idString).tag!!.map { it.name!! }
    }

    override suspend fun getFinishBuildTriggers(): List<FinishBuildTrigger> {
        return instance.service.buildTypeTriggers(idString)
            .trigger
            ?.filter { it.type == "buildDependencyTrigger" }
            ?.map { FinishBuildTriggerImpl(it) }.orEmpty()
    }

    override suspend fun getArtifactDependencies(): List<ArtifactDependency> {
        return instance.service
            .buildTypeArtifactDependencies(idString)
            .`artifact-dependency`
            ?.filter { it.disabled == false }
            ?.map { ArtifactDependencyImpl(it, true, instance) }.orEmpty()
    }

    override suspend fun getSnapshotDependencies(): List<SnapshotDependency> {
        return instance.service
            .buildTypeSnapshotDependencies(idString)
            .`snapshot-dependency`
            ?.filter { it.disabled == false }
            ?.map { SnapshotDependencyImpl(it, true, instance) }.orEmpty()
    }

    override suspend fun getBuildCounter(): Int = buildCounter.getValue()

    override suspend fun setBuildCounter(value: Int) {
        LOG.info("Setting build counter to '$value' in BuildConfigurationId=$idString")
        instance.service.setBuildTypeSettings(idString, "buildNumberCounter", value.toString().toTextPlainBody())
    }

    override suspend fun getBuildNumberFormat(): String = buildNumberFormat.getValue()

    override suspend fun setBuildNumberFormat(format: String) {
        LOG.info("Setting build number format to '$format' in BuildConfigurationId=$idString")
        instance.service.setBuildTypeSettings(idString, "buildNumberPattern", format.toTextPlainBody())
    }

    override suspend fun setParameter(name: String, value: String) {
        LOG.info("Setting parameter $name=$value in BuildConfigurationId=$idString")
        instance.service.setBuildTypeParameter(idString, name, value.toTextPlainBody())
    }
    override suspend fun removeParameter(name: String) {
        LOG.info("Remove parameter $name in BuildConfigurationId=$idString")
        instance.service.removeBuildTypeParameter(idString, name)
    }

    override suspend fun getParameters(): List<Parameter> =
        instance.service.getBuildTypeParameters(idString).property.map(::ParameterImpl)

    private suspend fun getSetting(settingName: String) =
        nullable { it.settings }?.property?.firstOrNull { it.name == settingName }?.value

    override suspend fun runBuild(
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
}

private class VcsRootLocatorImpl(private val instance: TeamCityCoroutinesInstanceImpl) : VcsRootLocatorEx {
    override fun all(): Flow<VcsRoot> = lazyPagingFlow(instance,
        getFirstBean = {
            LOG.debug("Retrieving vcs roots from ${instance.serverUrl}")
            instance.service.vcsRoots()
        },
        convertToPage = { bean -> Page(bean.`vcs-root`.map { VcsRootImpl(it, false, instance) }, bean.nextHref) })

    override fun allSeq(): Sequence<VcsRoot> = lazyPagingSequence(instance,
        getFirstBean = {
            LOG.debug("Retrieving sequentially vcs roots from ${instance.serverUrl}")
            instance.service.vcsRoots()
        },
        convertToPage = { bean -> Page(bean.`vcs-root`.map { VcsRootImpl(it, false, instance) }, bean.nextHref) })
}

private class ChangeImpl(
    bean: ChangeBean,
    isFullBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<ChangeBean>(bean, isFullBean, instance), Change {
    override suspend fun fetchFullBean(): ChangeBean = instance.service.change(changeId = idString)

    override fun getHomeUrl(
        specificBuildConfigurationId: BuildConfigurationId?,
        includePersonalBuilds: Boolean?
    ): String = instance.webLinks.changePage(id, specificBuildConfigurationId, includePersonalBuilds)

    override suspend fun firstBuilds(): List<Build> =
        instance.service
            .changeFirstBuilds(id.stringId)
            .build
            .map { BuildImpl(it, emptySet(), instance) }

    override val id: ChangeId = ChangeId(idString)
    private val version = SuspendingLazy { notnull { it.version } }
    private val username = SuspendingLazy { notnull { it.username } }
    private val user = SuspendingLazy { nullable { it.user }?.let { UserImpl(it, false, instance) } }
    private val dateTime = SuspendingLazy { ZonedDateTime.parse(notnull { it.date }, teamCityServiceDateFormat) }
    private val comment = SuspendingLazy { notnull { it.comment } }
    private val vcsRootInstance = SuspendingLazy { nullable { it.vcsRootInstance }?.let { VcsRootInstanceImpl(it) } }

    private val registrationDate = SuspendingLazy {
        ZonedDateTime.parse(notnull { it.registrationDate }, teamCityServiceDateFormat)
    }

    override suspend fun getVersion(): String = version.getValue()
    override suspend fun getUsername(): String = username.getValue()
    override suspend fun getUser(): User? = user.getValue()
    override suspend fun getDateTime(): ZonedDateTime = dateTime.getValue()
    override suspend fun getRegistrationDate(): ZonedDateTime = registrationDate.getValue()
    override suspend fun getComment(): String = comment.getValue()
    override suspend fun getVcsRootInstance(): VcsRootInstance? = vcsRootInstance.getValue()


    override suspend fun getFiles(): List<ChangeFile> {
        return instance.service.changeFiles(id.stringId).files?.file?.map { ChangeFileImpl(it) } ?: emptyList()
    }

    override fun toString(): String {
        return if (isFullBean) {
            runBlocking {
                "Change(id=$id, version=${getVersion()}, username=${getUsername()}, user=${getUser()}, date=${getDateTime()}, comment=${getComment()}, vcsRootInstance=${getVcsRootInstance()})"
            }
        } else {
            "Change(id=$id)"
        }
    }
}

private class ChangeFileImpl(private val bean: ChangeFileBean) : ChangeFile {
    override val fileRevisionBeforeChange: String?
        get() = bean.`before-revision`
    override val fileRevisionAfterChange: String?
        get() = bean.`after-revision`
    override val changeType: ChangeType by lazy {
        try {
            bean.changeType?.let { ChangeType.valueOf(it.uppercase()) } ?: ChangeType.UNKNOWN
        } catch (e: IllegalArgumentException) {
            ChangeType.UNKNOWN
        }
    }
    override val filePath: String?
        get() = bean.file
    override val relativeFilePath: String?
        get() = bean.`relative-file`

    override fun toString(): String {
        return "ChangeFile(fileRevisionBeforeChange=$fileRevisionBeforeChange, fileRevisionAfterChange=$fileRevisionAfterChange, changeType=$changeType, filePath=$filePath, relativeFilePath=$relativeFilePath)"
    }
}

private class UserImpl(
    bean: UserBean,
    isFullBuildBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<UserBean>(bean, isFullBuildBean, instance), User {

    override suspend fun fetchFullBean(): UserBean {
        return instance.service.users(locator)
    }

    override val id = UserId(idString)
    private val locator = "id:${id.stringId}"
    private val email = SuspendingLazy { nullable { it.email } }
    private val name = SuspendingLazy { nullable { it.name } }
    private val username = SuspendingLazy { notnull { it.username } }
    private val roles = SuspendingLazy { fullBean.getValue().roles?.role?.map(::AssignedRoleImpl) ?: emptyList() }

    override suspend fun getEmail(): String? = email.getValue()
    override suspend fun getRoles(): List<AssignedRole> = roles.getValue()

    override suspend fun addRole(roleId: RoleId, roleScope: RoleScope) {
        instance.service.addUserRole(locator, roleId.stringId, roleScope.descriptor)
    }

    override suspend fun deleteRole(roleId: RoleId, roleScope: RoleScope) {
        instance.service.deleteUserRole(locator, roleId.stringId, roleScope.descriptor)
    }

    override suspend fun getUsername(): String = username.getValue()

    override suspend fun getName(): String? = name.getValue()

    override fun getHomeUrl(): String = instance.webLinks.userPage(id)

    override fun toString(): String =
        if (isFullBean) runBlocking { "User(id=${id.stringId}, username=${getUsername()})" } else "User(id=${id.stringId})"
}

private class AssignedRoleImpl(roleBean: RoleBean) : AssignedRole {
    override val id = RoleId(roleBean.roleId!!)
    override val scope = RoleScope(roleBean.scope!!)
    override fun toString() = "RoleImpl(id=$id,scope=$scope)"
}

private class PinInfoImpl(bean: PinInfoBean, instance: TeamCityCoroutinesInstanceImpl) : PinInfo {
    override val user = UserImpl(bean.user!!, false, instance)
    override val dateTime: ZonedDateTime = ZonedDateTime.parse(bean.timestamp!!, teamCityServiceDateFormat)
}

private class TriggeredImpl(
    private val bean: TriggeredBean,
    private val instance: TeamCityCoroutinesInstanceImpl
) : TriggeredInfo {
    override val user: User?
        get() = if (bean.user != null) UserImpl(bean.user!!, false, instance) else null
    override val build: Build?
        get() = if (bean.build != null) BuildImpl(bean.build, emptySet(), instance) else null
    override val type: String by lazy { bean.type!! }
}

private class BuildCanceledInfoImpl(
    private val bean: BuildCanceledBean,
    private val instance: TeamCityCoroutinesInstanceImpl
) : BuildCanceledInfo {
    override val cancelDateTime: ZonedDateTime
        get() = ZonedDateTime.parse(bean.timestamp!!, teamCityServiceDateFormat)
    override val user: User?
        get() = if (bean.user != null) UserImpl(bean.user!!, false, instance) else null
    override val text: String
        get() = bean.text ?: ""
}

private class ParameterImpl(
    override val name: String,
    override val value: String?,
    override val own: Boolean,
) : Parameter {
    constructor(bean: ParameterBean) : this(bean.name!!, bean.value, bean.own == true)
    constructor(bean: BuildTypeParameterBean) : this(bean.name!!, bean.value, bean.inherited == false)
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

private class ArtifactDependencyImpl(
    bean: ArtifactDependencyBean,
    isFullBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<ArtifactDependencyBean>(bean, isFullBean, instance), ArtifactDependency {

    override val id = ArtifactDependencyId(bean.id!!)
    private val branch = SuspendingLazy { findPropertyByName("revisionBranch") }
    private val isClearDestDir = SuspendingLazy { findPropertyByName("cleanDestinationDirectory")!!.toBoolean() }

    private val dependsOnBuildConfiguration = SuspendingLazy {
        BuildConfigurationImpl(bean.`source-buildType`, false, instance)
    }

    private val artifactRules = SuspendingLazy {
        findPropertyByName("pathRules")!!.split(' ').map { ArtifactRuleImpl(it) }
    }

    override suspend fun fetchFullBean(): ArtifactDependencyBean {
        error("Not supported, ArtifactDependencyImpl should be created with full bean")
    }

    override fun toString(): String = if (isFullBean) {
        "ArtifactDependency(buildConf=${runBlocking { getDependsOnBuildConfiguration() }.id.stringId})"
    } else {
        "ArtifactDependency(id=$id)"
    }

    override suspend fun getDependsOnBuildConfiguration(): BuildConfiguration = dependsOnBuildConfiguration.getValue()

    override suspend fun getBranch(): String? = branch.getValue()

    override suspend fun getArtifactRules(): List<ArtifactRule> = artifactRules.getValue()

    override suspend fun isCleanDestinationDirectory(): Boolean = isClearDestDir.getValue()

    private suspend fun findPropertyByName(name: String): String? {
        return fullBean.getValue().properties?.property?.find { it.name == name }?.value
    }
}

private class SnapshotDependencyImpl(
    bean: SnapshotDependencyBean,
    isFullBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<SnapshotDependencyBean>(bean, isFullBean, instance), SnapshotDependency {

    override val id = BuildConfigurationId(bean.id!!)

    private val buildConfiguration = SuspendingLazy {
        BuildConfigurationImpl(bean.`source-buildType`, false, instance)
    }

    override suspend fun getBuildConfiguration(): BuildConfiguration = buildConfiguration.getValue()

    override suspend fun fetchFullBean(): SnapshotDependencyBean {
        error("Not supported, ArtifactDependencyImpl should be created with full bean")
    }

    override fun toString(): String = if (isFullBean) {
        "SnapshotDependency(buildConf=${runBlocking { getBuildConfiguration() }.id.stringId})"
    } else {
        "SnapshotDependency(id=$id)"
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

private class BuildProblemOccurrenceImpl(
    private val bean: BuildProblemOccurrenceBean,
    private val instance: TeamCityCoroutinesInstanceImpl
) : BuildProblemOccurrence {
    override val buildProblem: BuildProblem
        get() = BuildProblemImpl(bean.problem!!)
    override val build: Build
        get() = BuildImpl(bean.build!!, emptySet(), instance)
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

    override val vcsBranchName: String?
        get() = bean.vcsBranchName

    override val vcsRootInstance: VcsRootInstance
        get() = VcsRootInstanceImpl(bean.`vcs-root-instance`!!)
}

private data class BranchImpl(
    override val name: String?,
    override val isDefault: Boolean
) : Branch

private data class Page<out T>(val data: List<T>, val nextHref: String?)

private val GSON = Gson()

private inline fun <reified BeanType> ResponseBody.toBean(): BeanType = GSON.fromJson(string(), BeanType::class.java)

private fun String.splitToPathAndParams(): Pair<String, Map<String, String>> {
    val uri = URI(this)
    val path = uri.path
    val fullParams = uri.query
    val paramsMap = fullParams.split("&").associate {
        val (key, value) = it.split("=")
        key to value
    }
    return path to paramsMap
}

private suspend inline fun <reified Bean, T> extractNextPage(
    currentPage: Page<T>,
    instance: TeamCityCoroutinesInstanceImpl,
    crossinline convertToPage: suspend (Bean) -> Page<T>
): Page<T>? {
    val nextHref = currentPage.nextHref ?: return null
    val hrefSuffix = nextHref.removePrefix(instance.serverUrlBase)
    val (path, params) = hrefSuffix.splitToPathAndParams()
    val body = instance.service.root(path, params)
    return convertToPage(body.toBean<Bean>())
}

private inline fun <reified Bean, T> lazyPagingFlow(
    instance: TeamCityCoroutinesInstanceImpl,
    crossinline getFirstBean: suspend () -> Bean,
    crossinline convertToPage: suspend (Bean) -> Page<T>
): Flow<T> = flow {
    var page = convertToPage(getFirstBean())
    while (true) {
        page.data.forEach { emit(it) }
        page = extractNextPage(page, instance, convertToPage) ?: break
    }
}

private inline fun <reified Bean, T> lazyPagingSequence(
    instance: TeamCityCoroutinesInstanceImpl,
    crossinline getFirstBean: suspend () -> Bean,
    crossinline convertToPage: suspend (Bean) -> Page<T>
): Sequence<T> = sequence {
    var page = runBlocking { convertToPage(getFirstBean()) }
    while (true) {
        page.data.forEach { yield(it) }
        page = runBlocking { extractNextPage(page, instance, convertToPage) } ?: break
    }
}

private class BuildImpl(
    bean: BuildBean,
    private val prefetchedFields: Set<BuildField>,
    instance: TeamCityCoroutinesInstanceImpl
) : BaseImpl<BuildBean>(bean, isFullBean = prefetchedFields.size == BuildField.size, instance),
    BuildEx {
    override val id: BuildId = BuildId(idString)

    /**
     * Build id may change because of build reuse. No easy way to verify.
     * https://www.jetbrains.com/help/teamcity/build-dependencies-setup.html#Reusing+builds
     */
    override fun isFullBeanIdValid(beanId: String?, fullBeanId: String?): Boolean = true

    override suspend fun fetchFullBean(): BuildBean = instance.service.build(id.stringId, BuildBean.fullFieldsFilter)

    private val statusText = SuspendingLazy {
        fromFullBeanIf(BuildField.STATUS_TEXT !in prefetchedFields, BuildBean::statusText)
    }

    private val runningInfo = SuspendingLazy {
        val info = fromFullBeanIf(BuildField.RUNNING_INFO !in prefetchedFields, BuildBean::`running-info`)
        info?.let { BuildRunningInfoImpl(it) }
    }

    private val parameters = SuspendingLazy {
        val properties = fromFullBeanIf(BuildField.PARAMETERS !in prefetchedFields, BuildBean::properties)
        properties?.property?.map(::ParameterImpl) ?: emptyList()
    }

    private val tags = SuspendingLazy {
        val tags = fromFullBeanIf(BuildField.TAGS !in prefetchedFields, BuildBean::tags)
        tags?.tag?.map { it.name!! } ?: emptyList()
    }

    private val revisions = SuspendingLazy {
        val revisionsBean = fromFullBeanIf(BuildField.REVISIONS !in prefetchedFields, BuildBean::revisions)
        revisionsBean?.revision?.map { RevisionImpl(it) } ?: emptyList()
    }

    private val pinInfo = SuspendingLazy {
        val pinInfoBean = fromFullBeanIf(BuildField.PIN_INFO !in prefetchedFields, BuildBean::pinInfo)
        pinInfoBean?.let { PinInfoImpl(it, instance) }
    }

    private val triggeredInfo = SuspendingLazy {
        val triggeredInfoBean = fromFullBeanIf(BuildField.TRIGGERED_INFO !in prefetchedFields, BuildBean::triggered)
        triggeredInfoBean?.let { TriggeredImpl(it, instance) }
    }

    private val buildConfigurationId = SuspendingLazy {
        val buildTypeId = fromFullBeanIf(BuildField.BUILD_CONFIGURATION_ID !in prefetchedFields, BuildBean::buildTypeId)
        BuildConfigurationId(checkNotNull(buildTypeId))
    }

    private val buildNumber = SuspendingLazy {
        fromFullBeanIf(BuildField.BUILD_NUMBER !in prefetchedFields, BuildBean::number)
    }

    private val status = SuspendingLazy { fromFullBeanIf(BuildField.STATUS !in prefetchedFields, BuildBean::status) }

    private val personal = SuspendingLazy {
        fromFullBeanIf(BuildField.IS_PERSONAL !in prefetchedFields, BuildBean::personal) ?: false
    }

    private val comment = SuspendingLazy {
        fromFullBeanIf(BuildField.COMMENT !in prefetchedFields, BuildBean::comment)
            ?.let { BuildCommentInfoImpl(it, instance) }
    }

    private val composite = SuspendingLazy {
        fromFullBeanIf(BuildField.IS_COMPOSITE !in prefetchedFields, BuildBean::composite)
    }

    private val queuedDateTime = SuspendingLazy {
        fromFullBeanIf(BuildField.QUEUED_DATETIME !in prefetchedFields, BuildBean::queuedDate)
            ?.let { ZonedDateTime.parse(it, teamCityServiceDateFormat) }
    }

    private val startDateTime = SuspendingLazy {
        fromFullBeanIf(BuildField.START_DATETIME !in prefetchedFields, BuildBean::startDate)
            ?.let { ZonedDateTime.parse(it, teamCityServiceDateFormat) }
    }

    private val finishDateTime = SuspendingLazy {
        fromFullBeanIf(BuildField.FINISH_DATETIME !in prefetchedFields, BuildBean::finishDate)
            ?.let { ZonedDateTime.parse(it, teamCityServiceDateFormat) }
    }

    private val changes = SuspendingLazy {
        instance.service.changes(
            "build:$idString",
            "change(id,version,username,user,date,comment,vcsRootInstance)"
        ).change!!.map { ChangeImpl(it, true, instance) }
    }

    private val snapshotDependencies = SuspendingLazy {
        val snapshotDepsBean = fromFullBeanIf(BuildField.SNAPSHOT_DEPENDENCIES !in prefetchedFields, BuildBean::`snapshot-dependencies`)
        snapshotDepsBean?.build?.map { BuildImpl(it, BuildField.essentialFields, instance) } ?: emptyList()
    }

    private val agent = SuspendingLazy {
        fromFullBeanIf(BuildField.AGENT !in prefetchedFields, BuildBean::agent)
            ?.takeIf { it.id != null }
            ?.let { BuildAgentImpl(it, false, instance) }
    }

    private val state = SuspendingLazy {
        try {
            val state = checkNotNull(fromFullBeanIf(BuildField.STATE !in prefetchedFields, BuildBean::state))
            BuildState.valueOf(state.uppercase())
        } catch (e: IllegalArgumentException) {
            BuildState.UNKNOWN
        }
    }

    private val name = SuspendingLazy {
        val name = fromFullBeanIf(BuildField.NAME !in prefetchedFields) { it.buildType?.name }
        name ?: instance.buildConfiguration(getBuildConfigurationId()).getName()
    }

    private val canceledInfo = SuspendingLazy {
        fromFullBeanIf(BuildField.CANCELED_INFO !in prefetchedFields, BuildBean::canceledInfo)
            ?.let { BuildCanceledInfoImpl(it, instance) }
    }

    private val branch = SuspendingLazy {
        val (branchName, isDefaultBranch) = fromFullBeanIf(BuildField.BRANCH !in prefetchedFields) {
            when {
                it.branchName == null && it.defaultBranch == null -> null
                else -> it.branchName to it.defaultBranch
            }
        } ?: (null to null)
        BranchImpl(
            name = branchName,
            isDefault = isDefaultBranch ?: (branchName == null)
        )
    }

    private val detachedFromAgent = SuspendingLazy {
        fromFullBeanIf(BuildField.IS_DETACHED_FROM_AGENT !in prefetchedFields, BuildBean::detachedFromAgent) ?: false
    }

    private val failedToStart = SuspendingLazy {
        fromFullBeanIf(BuildField.IS_FAILED_TO_START !in prefetchedFields, BuildBean::failedToStart) ?: false
    }

    private val projectId = SuspendingLazy {
        val stringId = fromFullBeanIf(BuildField.PROJECT_ID !in prefetchedFields) { it.buildType?.projectId }
        ProjectId(checkNotNull(stringId))
    }

    private val projectName = SuspendingLazy {
        val stringId = fromFullBeanIf(BuildField.PROJECT_NAME !in prefetchedFields) { it.buildType?.projectName }
        checkNotNull(stringId)
    }

    private val queuedWaitReasons = SuspendingLazy {
        fromFullBeanIf(BuildField.QUEUED_WAIT_REASONS !in prefetchedFields, BuildBean::queuedWaitReasons)?.property
            ?.map(::PropertyImpl) ?: emptyList()
    }

    override fun getHomeUrl(): String = instance.webLinks.buildPage(id)

    override suspend fun getStatusText(): String? = statusText.getValue()

    override suspend fun getQueuedDateTime(): ZonedDateTime? = queuedDateTime.getValue()

    override suspend fun getStartDateTime(): ZonedDateTime? = startDateTime.getValue()

    override suspend fun getFinishDateTime(): ZonedDateTime? = finishDateTime.getValue()

    override suspend fun getRunningInfo(): BuildRunningInfo? = runningInfo.getValue()

    override suspend fun getParameters(): List<Parameter> = parameters.getValue()

    override suspend fun getTags(): List<String> = tags.getValue()

    override suspend fun getRevisions(): List<Revision> = revisions.getValue()

    override suspend fun getChanges(): List<Change> = changes.getValue()

    override suspend fun getSnapshotDependencies(): List<Build> = snapshotDependencies.getValue()

    override suspend fun getPinInfo(): PinInfo? = pinInfo.getValue()

    override suspend fun getTriggeredInfo(): TriggeredInfo? = triggeredInfo.getValue()

    override suspend fun getAgent(): BuildAgent? = agent.getValue()


    override suspend fun getBuildConfigurationId(): BuildConfigurationId = buildConfigurationId.getValue()

    override suspend fun getBuildNumber(): String? = buildNumber.getValue()

    override suspend fun getStatus(): BuildStatus? = status.getValue()

    override suspend fun getState(): BuildState = state.getValue()

    override suspend fun isPersonal(): Boolean = personal.getValue()

    override suspend fun getName(): String = name.getValue()

    override suspend fun getProjectName(): String = projectName.getValue()

    override suspend fun getCanceledInfo(): BuildCanceledInfo? = canceledInfo.getValue()

    override suspend fun getComment(): BuildCommentInfo? = comment.getValue()

    override suspend fun isComposite(): Boolean? = composite.getValue()

    override suspend fun getBranch(): Branch = branch.getValue()

    override fun toString(): String =
        if (isFullBean) runBlocking { "Build{id=$id, buildConfigurationId=${getBuildConfigurationId()}, buildNumber=${getBuildNumber()}, status=${getStatus()}, branch=${getBranch()}}" }
        else "Build{id=$id}"

    override suspend fun isDetachedFromAgent(): Boolean = detachedFromAgent.getValue()

    override suspend fun isFailedToStart(): Boolean = failedToStart.getValue()

    override fun getTestRuns(status: TestStatus?): Flow<TestRun> = instance
        .testRuns()
        .forBuild(id)
        .let { if (status == null) it else it.withStatus(status) }
        .all()

    override suspend fun getProjectId(): ProjectId = projectId.getValue()

    override fun getTestRunsSeq(status: TestStatus?): Sequence<TestRun> = (instance
            .testRuns()
            .forBuild(id)
            .let { if (status == null) it else it.withStatus(status) } as TestRunsLocatorEx)
            .allSeq()

    override fun getBuildProblems(): Flow<BuildProblemOccurrence> = lazyPagingFlow(instance,
        getFirstBean = {
            instance.service.problemOccurrences(
                locator = "build:(id:${id.stringId})",
                fields = "\$long,problemOccurrence(\$long)"
            )
        }, convertToPage = { bean ->
            Page(
                data = bean.problemOccurrence.map { BuildProblemOccurrenceImpl(it, instance) },
                nextHref = bean.nextHref
            )
        })

    override fun getBuildProblemsSeq(): Sequence<BuildProblemOccurrence> = lazyPagingSequence(instance,
        getFirstBean = {
            instance.service.problemOccurrences(
                locator = "build:(id:${id.stringId})",
                fields = "\$long,problemOccurrence(\$long)"
            )
        }, convertToPage = { bean ->
            Page(
                data = bean.problemOccurrence.map { BuildProblemOccurrenceImpl(it, instance) },
                nextHref = bean.nextHref
            )
        })

    override suspend fun addTag(tag: String) {
        LOG.info("Adding tag $tag to build ${getHomeUrl()}")
        instance.service.addTag(idString, tag.toTextPlainBody())
    }

    override suspend fun setComment(comment: String) {
        LOG.info("Adding comment $comment to build ${getHomeUrl()}")
        instance.service.setComment(idString, comment.toTextPlainBody())
    }

    override suspend fun replaceTags(tags: List<String>) {
        LOG.info("Replacing tags of build ${getHomeUrl()} with ${tags.joinToString(", ")}")
        val tagBeans = tags.map { tag -> TagBean().apply { name = tag } }
        instance.service.replaceTags(idString, TagsBean().apply { tag = tagBeans })
    }

    override suspend fun pin(comment: String) {
        LOG.info("Pinning build ${getHomeUrl()}")
        instance.service.pin(idString, comment.toTextPlainBody())
    }

    override suspend fun unpin(comment: String) {
        LOG.info("Unpinning build ${getHomeUrl()}")
        instance.service.unpin(idString, comment.toTextPlainBody())
    }

    override suspend fun getArtifacts(parentPath: String, recursive: Boolean, hidden: Boolean): List<BuildArtifact> {
        val locator = "recursive:$recursive,hidden:$hidden"
        val fields = "file(${ArtifactFileBean.FIELDS})"
        return instance.service.artifactChildren(id.stringId, parentPath, locator, fields).file
            .filter { it.fullName != null && it.modificationTime != null }
            .map {
                BuildArtifactImpl(
                    this,
                    it.name!!,
                    it.fullName!!,
                    it.size,
                    ZonedDateTime.parse(it.modificationTime!!, teamCityServiceDateFormat)
                )
            }
    }

    override suspend fun findArtifact(pattern: String, parentPath: String): BuildArtifact {
        return findArtifact(pattern, parentPath, false)
    }

    override suspend fun findArtifact(pattern: String, parentPath: String, recursive: Boolean): BuildArtifact {
        val list = getArtifacts(parentPath, recursive)
        val regexp = convertToJavaRegexp(pattern)
        val result = list.filter { regexp.matches(it.name) }
        if (result.isEmpty()) {
            val available = list.joinToString(",") { it.name }
            throw TeamCityQueryException("Artifact $pattern not found in build ${getBuildNumber()}. Available artifacts: $available.")
        }
        if (result.size > 1) {
            val names = result.joinToString(",") { it.name }
            throw TeamCityQueryException("Several artifacts matching $pattern are found in build ${getBuildNumber()}: $names.")
        }
        return result.first()
    }

    override suspend fun downloadArtifacts(pattern: String, outputDir: File) {
        val list = getArtifacts(recursive = true)
        val regexp = convertToJavaRegexp(pattern)
        val matched = list.filter { regexp.matches(it.fullName) }
        if (matched.isEmpty()) {
            val available = list.joinToString(",") { it.fullName }
            throw TeamCityQueryException("No artifacts matching $pattern are found in build ${getBuildNumber()}. Available artifacts: $available.")
        }
        outputDir.mkdirs()
        matched.forEach {
            it.download(File(outputDir, it.name))
        }
    }

    override suspend fun openArtifactInputStream(artifactPath: String): InputStream {
        LOG.info("Opening artifact '$artifactPath' stream from build ${getHomeUrl()}")
        return openArtifactInputStreamImpl(artifactPath)
    }

    override suspend fun downloadArtifact(artifactPath: String, output: File) {
        LOG.info("Downloading artifact '$artifactPath' from build ${getHomeUrl()} to $output")
        try {
            output.parentFile?.mkdirs()
            withContext(Dispatchers.IO) {
                FileOutputStream(output).use {
                    downloadArtifactImpl(artifactPath, it)
                }
            }
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            LOG.debug("Artifact '{}' from build {} downloaded to {}", artifactPath, getHomeUrl(), output)
        }
    }

    override suspend fun downloadArtifact(artifactPath: String, output: OutputStream) {
        LOG.info("Downloading artifact '$artifactPath' from build ${getHomeUrl()}")
        try {
            downloadArtifactImpl(artifactPath, output)
        } finally {
            LOG.debug("Artifact '$artifactPath' from build ${getHomeUrl()} downloaded")
        }
    }

    private suspend fun openArtifactInputStreamImpl(artifactPath: String): InputStream {
        return instance.service.artifactContent(id.stringId, artifactPath).byteStream()
    }

    private suspend fun downloadArtifactImpl(artifactPath: String, output: OutputStream) {
        openArtifactInputStreamImpl(artifactPath).use { input ->
            output.use {
                input.copyTo(output, bufferSize = 512 * 1024)
            }
        }
    }

    override suspend fun downloadBuildLog(output: File) {
        LOG.info("Downloading build log from build ${getHomeUrl()} to $output")

        val response = instance.service.buildLog(id.stringId)
        saveToFile(response, output)

        LOG.debug("Build log from build {} downloaded to {}", getHomeUrl(), output)
    }

    override suspend fun cancel(comment: String, reAddIntoQueue: Boolean) {
        val request = BuildCancelRequestBean()
        request.comment = comment
        request.readdIntoQueue = reAddIntoQueue
        instance.service.cancelBuild(id.stringId, request)
    }

    override suspend fun getResultingParameters(): List<Parameter> {
        return instance.service.resultingProperties(id.stringId).property!!.map { ParameterImpl(it) }
    }

    override suspend fun finish() {
        instance.service.finishBuild(id.stringId)
    }

    override suspend fun log(message: String) {
        instance.service.log(id.stringId, message)
    }

    override suspend fun getStatistics(): List<Property> =
        instance.service.buildStatistics(id.stringId).property?.map(::PropertyImpl) ?: emptyList()

    override suspend fun getQueuedWaitReasons(): List<Property> = queuedWaitReasons.getValue()

    override fun testRunsLocator(status: TestStatus?) = instance.testRuns()
        .forBuild(id)
        .let { if (status == null) it else it.withStatus(status) }

    override suspend fun markAsSuccessful(comment: String) {
        markAs(BuildStatus.SUCCESS, comment)
    }

    override suspend fun markAsFailed(comment: String) {
        markAs(BuildStatus.FAILURE, comment)
    }

    private suspend fun markAs(status: BuildStatus, comment: String) {
        val update = BuildStatusUpdateBean()
        update.status = "$status"
        update.comment = comment
        instance.service.updateBuildStatus("id:$idString", update)
    }
}

private class PropertyImpl(private val bean: PropertyBean) : Property {
    override val name: String
        get() = bean.name!!

    override val value: String
        get() = bean.value!!
}

private class TestImpl(
    bean: TestBean,
    isFullBuildBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<TestBean>(bean, isFullBuildBean, instance), Test {

    override suspend fun fetchFullBean(): TestBean = instance.service.test(idString)

    override val id = TestId(idString)

    override suspend fun getName(): String = notnull { it.name }

    override fun toString(): String =
        if (isFullBean) runBlocking { "Test(id=${id.stringId}, name=${getName()})" }
        else "Test(id=${id.stringId})"
}

private class BuildRunningInfoImpl(private val bean: BuildRunningInfoBean) : BuildRunningInfo {
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

private class BuildCommentInfoImpl(
    private val bean: BuildCommentBean,
    private val instance: TeamCityCoroutinesInstanceImpl
) : BuildCommentInfo {
    override val timestamp: ZonedDateTime by lazy { ZonedDateTime.parse(bean.timestamp!!, teamCityServiceDateFormat) }
    override val user: User? by lazy {
        if (bean.user != null) UserImpl(bean.user!!, false, instance) else null
    }
    override val text: String = bean.text ?: ""

    override fun toString(): String {
        return "BuildCommentInfo(timestamp=$timestamp,userId=${user?.id},text=$text)"
    }
}

private class VcsRootImpl(
    bean: VcsRootBean,
    isFullVcsRootBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<VcsRootBean>(bean, isFullVcsRootBean, instance), VcsRoot {

    override suspend fun fetchFullBean(): VcsRootBean = instance.service.vcsRoot(idString)

    override fun toString(): String =
        if (isFullBean) runBlocking { "VcsRoot(id=$id, name=${getName()})" }
        else "VcsRoot(id=$id)"

    override val id = VcsRootId(idString)
    private val name = SuspendingLazy { notnull { it.name } }
    private val url = SuspendingLazy { getNameValueProperty(properties.getValue(), "url") }
    private val defaultBranch = SuspendingLazy { getNameValueProperty(properties.getValue(), "branch") }

    private val properties: SuspendingLazy<List<NameValueProperty>> = SuspendingLazy {
        fullBean.getValue().properties!!.property!!.map { NameValueProperty(it) }
    }

    override suspend fun getName(): String = name.getValue()

    override suspend fun getUrl(): String? = url.getValue()

    override suspend fun getDefaultBranch(): String? = defaultBranch.getValue()
}

private class BuildAgentPoolImpl(
    bean: BuildAgentPoolBean,
    isFullBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<BuildAgentPoolBean>(bean, isFullBean, instance), BuildAgentPool {

    override suspend fun fetchFullBean(): BuildAgentPoolBean = instance.service.agentPools("id:$idString")

    override fun toString(): String =
        if (isFullBean) runBlocking { "BuildAgentPool(id=$id, name=${getName()})" }
        else "BuildAgentPool(id=$id)"

    override val id = BuildAgentPoolId(idString)
    private val name = SuspendingLazy { notnull { it.name } }

    private val projects = SuspendingLazy {
        fullBean.getValue().projects?.project?.map { ProjectImpl(it, false, instance) } ?: emptyList()
    }

    private val agents = SuspendingLazy {
        fullBean.getValue().agents?.agent?.map { BuildAgentImpl(it, false, instance) } ?: emptyList()
    }

    override suspend fun getName(): String = name.getValue()

    override suspend fun getProjects(): List<Project> = projects.getValue()

    override suspend fun getAgents(): List<BuildAgent> = agents.getValue()
}

private class BuildAgentImpl(
    bean: BuildAgentBean,
    isFullBean: Boolean,
    instance: TeamCityCoroutinesInstanceImpl
) :
    BaseImpl<BuildAgentBean>(bean, isFullBean, instance), BuildAgentEx {

    override val tcInstance = instance

    override suspend fun fetchFullBean(): BuildAgentBean = instance.service.agent("id:$idString")

    override fun toString(): String =
        if (isFullBean) runBlocking { "BuildAgent(id=$id, name=${getName()})" }
        else "BuildAgent(id=$id)"

    override val id = BuildAgentId(idString)
    private val typeId = SuspendingLazy { BuildAgentTypeId(notnull { it.typeId }) }
    private val name = SuspendingLazy { notnull { it.name } }
    private val pool = SuspendingLazy { BuildAgentPoolImpl(fullBean.getValue().pool!!, false, instance) }
    private val connected = SuspendingLazy { notnull { it.connected } }
    private val enabled = SuspendingLazy { notnull { it.enabled } }
    private val authorized = SuspendingLazy { notnull { it.authorized } }
    private val outdated = SuspendingLazy { !notnull { it.uptodate } }
    private val ip = SuspendingLazy { notnull { it.ip } }
    private val parameters = SuspendingLazy { fullBean.getValue().properties!!.property!!.map { ParameterImpl(it) } }

    private val enabledInfo = SuspendingLazy {
        fullBean.getValue().enabledInfo?.let { info ->
            info.comment?.let { comment ->
                BuildAgentEnabledInfoImpl(
                    user = comment.user?.let { UserImpl(it, false, instance) },
                    timestamp = ZonedDateTime.parse(comment.timestamp!!, teamCityServiceDateFormat),
                    text = comment.text ?: ""
                )
            }
        }
    }

    private val authorizedInfo = SuspendingLazy {
        fullBean.getValue().authorizedInfo?.let { info ->
            info.comment?.let { comment ->
                BuildAgentAuthorizedInfoImpl(
                    user = comment.user?.let { UserImpl(it, false, instance) },
                    timestamp = ZonedDateTime.parse(comment.timestamp!!, teamCityServiceDateFormat),
                    text = comment.text ?: ""
                )
            }
        }
    }

    private val currentBuild = SuspendingLazy {
        fullBean.getValue().build?.let {
            // API may return an empty build bean, pass it as null
            if (it.id == null) null else BuildImpl(it, emptySet(), instance)
        }
    }

    override suspend fun getTypeId(): BuildAgentTypeId = typeId.getValue()

    override suspend fun getName(): String = name.getValue()

    override suspend fun getPool(): BuildAgentPool = pool.getValue()

    override suspend fun isConnected(): Boolean = connected.getValue()

    override suspend fun isEnabled(): Boolean = enabled.getValue()

    override suspend fun isAuthorized(): Boolean = authorized.getValue()
    override suspend fun isOutdated(): Boolean = outdated.getValue()
    override suspend fun getIpAddress(): String = ip.getValue()

    override suspend fun getParameters(): List<Parameter> = parameters.getValue()

    override suspend fun getEnabledInfo(): BuildAgentEnabledInfo? = enabledInfo.getValue()

    override suspend fun getAuthorizedInfo(): BuildAgentAuthorizedInfo? = authorizedInfo.getValue()

    override suspend fun getCurrentBuild(): Build? = currentBuild.getValue()


    override fun getHomeUrl(): String = "${instance.serverUrl}/agentDetails.html?id=${id.stringId}"

    override suspend fun getCompatibleBuildConfigurations(): CompatibleBuildConfigurations {
        val bean = instance.service.agentCompatibilityPolicy("id:${id.stringId}")
        return CompatibleBuildConfigurationsImpl(
            buildConfigurationIds = bean.buildTypes?.buildType?.map { buildTypeBean ->
                BuildConfigurationId(buildTypeBean.id!!)
            } ?: emptyList(),
            policy = CompatibleBuildConfigurationsPolicy.values().firstOrNull {
                it.name.equals(bean.policy, ignoreCase = true)
            } ?: CompatibleBuildConfigurationsPolicy.UNKNOWN)
    }

    override suspend fun setCompatibleBuildConfigurations(value: CompatibleBuildConfigurations) {
        val bean = CompatibilityPolicyBean()
        bean.policy = value.policy.toString()
        bean.buildTypes = BuildTypesBean().apply {
            buildType = value.buildConfigurationIds.map {
                BuildTypeBean().apply { id = it.stringId }
            }
        }
        instance.service.updateAgentCompatibilityPolicy("id:${id.stringId}", bean)
    }

    private class BuildAgentAuthorizedInfoImpl(
        override val user: User?,
        override val timestamp: ZonedDateTime,
        override val text: String
    ) : BuildAgentAuthorizedInfo

    private class BuildAgentEnabledInfoImpl(
        override val user: User?,
        override val timestamp: ZonedDateTime,
        override val text: String
    ) : BuildAgentEnabledInfo

    private class CompatibleBuildConfigurationsImpl(
        override val buildConfigurationIds: List<BuildConfigurationId>,
        override val policy: CompatibleBuildConfigurationsPolicy
    ) : CompatibleBuildConfigurations
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
    override val modificationDateTime: ZonedDateTime
) : BuildArtifact {
    override suspend fun download(output: File) {
        build.downloadArtifact(fullName, output)
    }

    override suspend fun download(output: OutputStream) {
        build.downloadArtifact(fullName, output)
    }

    override suspend fun openArtifactInputStream(): InputStream {
        return build.openArtifactInputStream(fullName)
    }

    override fun toString(): String {
        return "BuildArtifact(build=$build, name='$name', fullName='$fullName', size=$size, modificationDateTime=$modificationDateTime)"
    }
}

private class BuildQueueImpl(private val instance: TeamCityCoroutinesInstanceImpl) : BuildQueueEx {
    override suspend fun removeBuild(id: BuildId, comment: String, reAddIntoQueue: Boolean) {
        val request = BuildCancelRequestBean()
        request.comment = comment
        request.readdIntoQueue = reAddIntoQueue
        instance.service.removeQueuedBuild(id.stringId, request)
    }

    override fun queuedBuilds(projectId: ProjectId?, prefetchFields: Set<BuildField>): Flow<Build> {
        val parameters = if (projectId == null) emptyList() else listOf("project:${projectId.stringId}")
        return queuedBuilds(parameters, prefetchFields)
    }

    override fun queuedBuilds(
        buildConfigurationId: BuildConfigurationId,
        prefetchFields: Set<BuildField>
    ): Flow<Build> {
        val parameters = listOf("buildType:${buildConfigurationId.stringId}")
        return queuedBuilds(parameters, prefetchFields)
    }

    override fun queuedBuildsSeq(projectId: ProjectId?, prefetchFields: Set<BuildField>): Sequence<Build> {
        val parameters = if (projectId == null) emptyList() else listOf("project:${projectId.stringId}")
        return queuedBuildsSeq(parameters, prefetchFields)
    }

    override fun queuedBuildsSeq(
        buildConfigurationId: BuildConfigurationId,
        prefetchFields: Set<BuildField>
    ): Sequence<Build> {
        val parameters = listOf("buildType:${buildConfigurationId.stringId}")
        return queuedBuildsSeq(parameters, prefetchFields)
    }

    private fun queuedBuilds(parameters: List<String>, prefetchFields: Set<BuildField>): Flow<BuildImpl> {
        val prefetchFieldsCopy = prefetchFields.copyToEnumSet()
        return lazyPagingFlow(
            instance,
            getFirstBean = {
                val buildLocator = if (parameters.isNotEmpty()) parameters.joinToString(",") else null
                LOG.debug("Retrieving queued builds from ${instance.serverUrl} using query '$buildLocator'")
                instance.service.queuedBuilds(
                    locator = buildLocator,
                    fields = BuildBean.buildCustomFieldsFilter(prefetchFieldsCopy, wrap = true)
                )
            },
            convertToPage = { buildsBean ->
                Page(
                    data = buildsBean.build.map { BuildImpl(it, prefetchFieldsCopy, instance) },
                    nextHref = buildsBean.nextHref
                )
            })
    }

    private fun queuedBuildsSeq(parameters: List<String>, prefetchFields: Set<BuildField>): Sequence<BuildImpl> {
        val prefetchFieldsCopy = prefetchFields.copyToEnumSet()
        return lazyPagingSequence(
            instance,
            getFirstBean = {
                val buildLocator = if (parameters.isNotEmpty()) parameters.joinToString(",") else null
                LOG.debug("Retrieving sequentially queued builds from ${instance.serverUrl} using query '$buildLocator'")
                instance.service.queuedBuilds(
                    locator = buildLocator,
                    fields = BuildBean.buildCustomFieldsFilter(prefetchFieldsCopy, wrap = true)
                )
            },
            convertToPage = { buildsBean ->
                Page(
                    data = buildsBean.build.map { BuildImpl(it, prefetchFieldsCopy, instance) },
                    nextHref = buildsBean.nextHref
                )
            })
    }
}

private fun getNameValueProperty(properties: List<NameValueProperty>, name: String): String? =
    properties.singleOrNull { it.name == name }?.value

private open class TestRunImpl(
    bean: TestOccurrenceBean,
    private val prefetchedFields: Set<TestRunField>,
    instance: TeamCityCoroutinesInstanceImpl
) : TestRun, BaseImpl<TestOccurrenceBean>(bean, isFullBean = prefetchedFields.size == TestRunField.size, instance) {

    override val testOccurrenceId = TestOccurrenceId(bean.id!!)
    private val name = SuspendingLazy {
        checkNotNull(fromFullBeanIf(TestRunField.NAME !in prefetchedFields, TestOccurrenceBean::name))
    }

    private val duration = SuspendingLazy<Duration> {
        val duration = fromFullBeanIf(TestRunField.DURATION !in prefetchedFields, TestOccurrenceBean::duration) ?: 0L
        Duration.ofMillis(duration)
    }

    private val ignored = SuspendingLazy {
        fromFullBeanIf(TestRunField.IGNORED !in prefetchedFields, TestOccurrenceBean::ignored) ?: false
    }

    private val currentlyMuted = SuspendingLazy {
        fromFullBeanIf(TestRunField.IS_CURRENTLY_MUTED !in prefetchedFields, TestOccurrenceBean::currentlyMuted)
            ?: false
    }

    private val mutedAtRunningTime = SuspendingLazy {
        fromFullBeanIf(TestRunField.IS_MUTED !in prefetchedFields, TestOccurrenceBean::muted) ?: false
    }

    private val newFailure = SuspendingLazy {
        fromFullBeanIf(TestRunField.IS_NEW_FAILURE !in prefetchedFields, TestOccurrenceBean::newFailure) ?: false
    }

    private val buildId = SuspendingLazy {
        val buildId = fromFullBeanIf(TestRunField.BUILD_ID !in prefetchedFields, TestOccurrenceBean::build)?.id
        BuildId(checkNotNull(buildId))
    }

    private val metadataValues = SuspendingLazy {
        fromFullBeanIf(TestRunField.METADATA_VALUES !in prefetchedFields, TestOccurrenceBean::metadata)
            ?.typedValues?.map { it.value.toString() }
    }

    private val testId = SuspendingLazy {
        val stringId = fromFullBeanIf(TestRunField.TEST_ID !in prefetchedFields, TestOccurrenceBean::test)?.id
        TestId(checkNotNull(stringId))
    }

    private val logAnchor = SuspendingLazy {
        checkNotNull(fromFullBeanIf(TestRunField.LOG_ANCHOR !in prefetchedFields, TestOccurrenceBean::logAnchor))
    }

    private val fixedIn = SuspendingLazy {
        fromFullBeanIf(TestRunField.FIXED_IN_BUILD_ID !in prefetchedFields, TestOccurrenceBean::nextFixed)
            ?.id?.let { BuildId(it) }
    }

    private val firstFailedIn = SuspendingLazy {
        fromFullBeanIf(TestRunField.FIRST_FAILED_IN_BUILD_ID !in prefetchedFields, TestOccurrenceBean::firstFailed)
            ?.id?.let { BuildId(it) }
    }

    private val status = SuspendingLazy {
        val ignored = fromFullBeanIf(TestRunField.IGNORED !in prefetchedFields, TestOccurrenceBean::ignored)
        if (ignored == true) return@SuspendingLazy TestStatus.IGNORED

        val status = fromFullBeanIf(TestRunField.STATUS !in prefetchedFields, TestOccurrenceBean::status)
        when (status) {
            "FAILURE" -> TestStatus.FAILED
            "SUCCESS" -> TestStatus.SUCCESSFUL
            else -> TestStatus.UNKNOWN
        }
    }

    private val details = SuspendingLazy {
        when (getStatus()) {
            TestStatus.IGNORED -> fromFullBeanIf(
                TestRunField.DETAILS !in prefetchedFields,
                TestOccurrenceBean::ignoreDetails
            )

            TestStatus.FAILED -> fromFullBeanIf(
                TestRunField.DETAILS !in prefetchedFields,
                TestOccurrenceBean::details
            )

            else -> null
        } ?: ""
    }

    override suspend fun getName(): String = name.getValue()

    final override suspend fun getStatus() = status.getValue()

    override suspend fun getDuration(): Duration = duration.getValue()

    override suspend fun getDetails(): String = details.getValue()

    override suspend fun isIgnored(): Boolean = ignored.getValue()

    override suspend fun isCurrentlyMuted(): Boolean = currentlyMuted.getValue()

    override suspend fun isMutedAtRunningTime(): Boolean = mutedAtRunningTime.getValue()

    override suspend fun isNewFailure(): Boolean = newFailure.getValue()

    override suspend fun getBuildId(): BuildId = buildId.getValue()

    override suspend fun getFixedIn(): BuildId? = fixedIn.getValue()

    override suspend fun getFirstFailedIn(): BuildId? = firstFailedIn.getValue()

    override suspend fun getMetadataValues(): List<String>? = metadataValues.getValue()

    override suspend fun getTestId(): TestId = testId.getValue()

    override suspend fun getLogAnchor(): String = logAnchor.getValue()

    override suspend fun fetchFullBean(): TestOccurrenceBean = instance.service.testOccurrence(idString, TestOccurrenceBean.fullFieldsFilter)

    override fun toString() =
        if (isFullBean) runBlocking { "Test(id=${bean.id},name=${getName()}, status=${getStatus()}, duration=${getDuration()}, details=${getDetails()})" }
        else "Test(id=${bean.id})"
}

private fun convertToJavaRegexp(pattern: String): Regex {
    return pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".").toRegex()
}

private fun saveToFile(body: ResponseBody, file: File) {
    file.parentFile?.mkdirs()
    body.byteStream().use { input ->
        file.outputStream().use { output ->
            input.copyTo(output, bufferSize = 512 * 1024)
        }
    }
}


private class SuspendingLazy<T>(private val producer: suspend () -> T) {
    @Volatile
    private var value: Any? = NotInitialized
    private val mutex = Mutex()

    suspend fun getValue(): T {
        if (value === NotInitialized) {
            mutex.withLock {
                if (value === NotInitialized) {
                    value = producer()
                }
            }
        }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private object NotInitialized
}

internal fun Issue.toInvestigationMuteBaseBean(assignmentBean: AssignmentBean) = InvestigationMuteBaseBean(
    assignment = assignmentBean,
    resolution = InvestigationResolutionBean(
        type = resolveMethod.value,
        time = resolutionTime?.format(teamCityServiceDateFormat)
    ),
    scope = when (val scope = scope) {
        is InvestigationScope.InBuildConfiguration -> InvestigationScopeBean(buildTypes = BuildTypesBean().apply {
            buildType = listOf(BuildTypeBean().apply { id = scope.configurationId.stringId })
        })

        is InvestigationScope.InBuildConfigurations -> InvestigationScopeBean(buildTypes = BuildTypesBean().apply {
            buildType = scope.configurationIds.map { configurationId ->
                BuildTypeBean().apply { id = configurationId.stringId }
            }
        })

        is InvestigationScope.InProject -> InvestigationScopeBean(project = ProjectBean().apply {
            id = scope.projectId.stringId
        })
    },
    target = when (targetType) {
        InvestigationTargetType.TEST -> InvestigationTargetBean(
            tests = TestUnderInvestigationListBean().apply {
                test = testIds?.map { TestBean().apply { id = it.stringId } } ?: emptyList()
            }
        )

        InvestigationTargetType.BUILD_PROBLEM -> InvestigationTargetBean(
            problems = ProblemUnderInvestigationListBean().apply {
                problem = problemIds?.map {
                    BuildProblemBean().apply {
                        id = it.stringId
                    }
                } ?: emptyList()
            }
        )

        InvestigationTargetType.BUILD_CONFIGURATION -> InvestigationTargetBean(anyProblem = true)
    }
)

internal fun Mute.toMuteBean(): MuteBean {
    val assignmentBean = AssignmentBean(
        text = this@toMuteBean.comment,
        user = reporter?.let { userId -> UserBean().apply { id = userId.stringId } }
    )
    val baseBean = toInvestigationMuteBaseBean(assignmentBean)
    return MuteBean(baseBean)
}

internal fun Investigation.toInvestigationBean(): InvestigationBean {
    val assignee = UserBean().apply { id = assignee.stringId }
    val assignmentBean = AssignmentBean(
        text = this@toInvestigationBean.comment,
        user = reporter?.let { userId -> UserBean().apply { id = userId.stringId } }
    )
    val baseBean = toInvestigationMuteBaseBean(assignmentBean)
    return InvestigationBean(baseBean, assignee, state)
}