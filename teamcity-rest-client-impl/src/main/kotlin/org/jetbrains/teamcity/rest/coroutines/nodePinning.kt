package org.jetbrains.teamcity.rest.coroutines

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.jetbrains.teamcity.rest.NodeSelector

fun NodeSelector.toCookieJar(): CookieJar {
    return when (this) {
        is NodeSelector.PinToNode -> PinToNodeCookieJar(this.nodeId)
        is NodeSelector.ServerControlled -> ServerControlledRouting()
        is NodeSelector.Unspecified -> CookieJar.NO_COOKIES
    }
}

private const val TEAMCITY_NODE_ID_COOKIE_NAME = "X-TeamCity-Node-Id-Cookie"

private class ServerControlledRouting : CookieJar {
    @Volatile
    private var serverProvidedNodeId: String? = null
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val targetNodeId = serverProvidedNodeId ?: return emptyList()

        val domain = url.topPrivateDomain() ?: return emptyList()

        return listOf(
            Cookie.Builder()
                .domain(domain)
                .name(TEAMCITY_NODE_ID_COOKIE_NAME)
                .value(targetNodeId)
                .build()
        )
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies
            .find { it.name == TEAMCITY_NODE_ID_COOKIE_NAME }
            ?.let { serverProvidedNodeId = it.value }
    }
}

private class PinToNodeCookieJar(val nodeId: String) : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val domain = url.topPrivateDomain() ?: return emptyList()

        return listOf(
            Cookie.Builder()
                .domain(domain)
                .name(TEAMCITY_NODE_ID_COOKIE_NAME)
                .value(nodeId)
                .build()
        )
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // noop, same as CookieJar.NO_COOKIES
    }
}