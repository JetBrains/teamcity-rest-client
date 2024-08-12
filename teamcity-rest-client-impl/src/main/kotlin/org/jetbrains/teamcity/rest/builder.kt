package org.jetbrains.teamcity.rest

import org.jetbrains.teamcity.rest.coroutines.TeamCityCoroutinesInstance
import org.jetbrains.teamcity.rest.coroutines.TeamCityCoroutinesInstanceEx
import org.jetbrains.teamcity.rest.coroutines.TeamCityCoroutinesInstanceImpl
import java.util.Base64
import java.util.concurrent.TimeUnit


/**
 * Builder object to create a new object of [TeamCityInstance] (blocking client implementation) 
 * or [TeamCityCoroutinesInstance] (coroutines-base client implementation)
 *
 * @see [TeamCityInstance] and [TeamCityCoroutinesInstance].
 */
class TeamCityInstanceBuilder(serverUrl: String) {
    private val serverUrl: String = serverUrl.trimEnd('/')
    private var urlBase: UrlBase = UrlBase.MISSING
    private var authHeader: String? = null
    private var logResponses: Boolean = false
    private var timeout: Long = 2
    private var timeoutTimeUnit: TimeUnit = TimeUnit.MINUTES
    private var maxConcurrentRequests: Int = 64
    private var maxConcurrentRequestsPerHost: Int = 5
    private var retryMaxAttempts: Int = 3
    private var retryInitialDelayMs: Long = 1000
    private var retryMaxDelayMs: Long = 1000
    private var nodeSelector: NodeSelector = NodeSelector.Unspecified

    /**
     * Creates guest authenticated accessor. Default setting.
     */
    fun withGuestAuth(): TeamCityInstanceBuilder {
        urlBase = UrlBase.GUEST_AUTH
        authHeader = null
        return this
    }

    /**
     * Creates username/password authenticated accessor
     *
     * @param username username
     * @param password password
     */
    fun withHttpAuth(username: String, password: String): TeamCityInstanceBuilder {
        val authorization = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        urlBase = UrlBase.HTTP_AUTH
        authHeader = "Basic $authorization"
        return this
    }

    /**
     * Creates token based connection.
     * TeamCity access token generated on My Settings & Tools | Access Tokens
     *
     * @param token token
     *
     * see https://www.jetbrains.com/help/teamcity/rest-api.html#RESTAPI-RESTAuthentication
     */
    fun withTokenAuth(token: String): TeamCityInstanceBuilder {
        urlBase = UrlBase.ROOT
        authHeader = "Bearer $token"
        return this
    }

    /**
     * Enables response verbose logging.
     */
    fun setResponsesLoggingEnabled(enabled: Boolean): TeamCityInstanceBuilder {
        logResponses = enabled
        return this
    }

    /**
     * Sets read, write and connect timeouts to [timeout], [timeUnit]
     */
    fun withTimeout(timeout: Long, timeUnit: TimeUnit): TeamCityInstanceBuilder {
        require(timeout > 0) { "Timeout must be positive" }

        this.timeout = timeout
        this.timeoutTimeUnit = timeUnit
        return this
    }

    fun withMaxConcurrentRequests(maxConcurrentRequests: Int): TeamCityInstanceBuilder {
        require(maxConcurrentRequests > 0) { "Max concurrent requests number must be positive" }
        this.maxConcurrentRequests = maxConcurrentRequests
        return this
    }

    fun withMaxConcurrentRequestsPerHost(maxConcurrentRequestsPerHost: Int): TeamCityInstanceBuilder {
        require(maxConcurrentRequestsPerHost > 0) { "Max concurrent requests per host number must be positive" }
        this.maxConcurrentRequestsPerHost = maxConcurrentRequestsPerHost
        return this
    }

    /**
     * Setup failed requests retry with growing delaying with exponential backoff.
     * Retry is enabled by default: Three attempts are performed with fixed 1000-millisecond delay.
     */
    fun withRetry(
        maxAttempts: Int,
        initialDelay: Long,
        maxDelay: Long,
        delayTimeUnit: TimeUnit
    ): TeamCityInstanceBuilder {
        require(maxAttempts > 0) { "At least one attempt must be configured" }
        require(initialDelay >= 0) { "Retry delay cannot be negative" }
        require(maxDelay >= initialDelay) { "Delay limit (maxDelay) bust be greater or equal to the initial delay" }

        retryMaxAttempts = maxAttempts
        retryInitialDelayMs = delayTimeUnit.toMillis(initialDelay)
        retryMaxDelayMs = delayTimeUnit.toMillis(maxDelay)
        return this
    }

    /**
     * Specifies how to route requests to different TeamCity nodes in a multinode TeamCity setup.
     * Does not affect a single node setup.
     */
    fun selectNode(selector: NodeSelector): TeamCityInstanceBuilder {
        this.nodeSelector = selector
        return this
    }

    /**
     * Build instance over coroutines
     */
    fun build(): TeamCityCoroutinesInstance = TeamCityCoroutinesInstanceImpl(
        serverUrl,
        urlBase.value,
        authHeader,
        nodeSelector,
        logResponses,
        timeout,
        timeoutTimeUnit,
        maxConcurrentRequests,
        maxConcurrentRequestsPerHost,
        retryMaxAttempts,
        retryInitialDelayMs,
        retryMaxDelayMs
    )
    
    fun buildBlockingInstance(): TeamCityInstance = TeamCityInstanceBlockingBridge(build() as TeamCityCoroutinesInstanceEx)

    internal fun setUrlBaseAndAuthHeader(urlBase: String, authHeader: String?): TeamCityInstanceBuilder {
        this.urlBase = UrlBase.values().first { it.value == urlBase }
        this.authHeader = authHeader
        return this
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TeamCityInstanceBuilder

        if (serverUrl != other.serverUrl) return false
        if (urlBase != other.urlBase) return false
        if (authHeader != other.authHeader) return false
        if (logResponses != other.logResponses) return false
        if (timeout != other.timeout) return false
        if (timeoutTimeUnit != other.timeoutTimeUnit) return false
        if (maxConcurrentRequests != other.maxConcurrentRequests) return false
        if (maxConcurrentRequestsPerHost != other.maxConcurrentRequestsPerHost) return false

        return true
    }

    override fun hashCode(): Int {
        var result = serverUrl.hashCode()
        result = 31 * result + urlBase.hashCode()
        result = 31 * result + (authHeader?.hashCode() ?: 0)
        result = 31 * result + logResponses.hashCode()
        result = 31 * result + timeout.hashCode()
        result = 31 * result + timeoutTimeUnit.hashCode()
        result = 31 * result + maxConcurrentRequests.hashCode()
        result = 31 * result + maxConcurrentRequestsPerHost.hashCode()
        return result
    }

    private enum class UrlBase(private val urlBaseValue: String) {
        GUEST_AUTH("/guestAuth/"),
        HTTP_AUTH("/httpAuth/"),
        ROOT("/"),
        MISSING("_missing_");

        val value: String
            get() {
                check(this != MISSING) {
                    "Authentication settings are not specified. " +
                            "Please specify authentication settings using .withGuestAuth(), " +
                            ".withHttpAuth(username, password) or .withTokenAuth(token)"
                }
                return urlBaseValue
            }
    }
}


/**
 * Controls to which node requests of this client are routed In multinode TeamCity setup. In a case of
 * single node setup this setting is essentially a no-op.
 *
 * Pinning is done by setting `X-TeamCity-Node-Id-Cookie` cookie.
 * Routing relies on a proxy in front of TeamCity server which should be set up in accordance to
 * [TeamCity Multinode Setup for High Availability](https://www.jetbrains.com/help/teamcity/multinode-setup.html) documentation.
 */
sealed interface NodeSelector {
    /**
     * Does not use cookie to pin to a specific node, even if given by the server.
     * Relies on a proxy routing decision, and usually is routed to first healthy node.
     */
    object Unspecified : NodeSelector

    /**
     * Respects server's load balancing decision and pins requests to the node provided by the server in the `Set-Cookie` header.
     */
    object ServerControlled : NodeSelector

    /**
     * Pins requests to the node with id=[nodeId].
     */
    data class PinToNode(val nodeId: String) : NodeSelector
}