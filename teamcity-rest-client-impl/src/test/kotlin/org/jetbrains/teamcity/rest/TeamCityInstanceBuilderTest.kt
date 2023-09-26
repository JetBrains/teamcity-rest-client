package org.jetbrains.teamcity.rest

import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.TimeUnit


class TeamCityInstanceBuilderTest {
    private fun getBuilder() = TeamCityInstanceBuilder("https://myserver.local")

    @Test
    fun test_with_retry_validation() {
        getBuilder().withRetry(1, 1000, 1000, TimeUnit.MILLISECONDS)
        getBuilder().withRetry(10, 1000, 1000, TimeUnit.MILLISECONDS)

        assertThrows("At least one attempt must be configured", IllegalArgumentException::class.java) {
            getBuilder().withRetry(0, 1000, 1000, TimeUnit.MILLISECONDS)
        }
        assertThrows("At least one attempt must be configured", IllegalArgumentException::class.java) {
            getBuilder().withRetry(-1, 1000, 1000, TimeUnit.MILLISECONDS)
        }

        assertThrows("Retry delay cannot be negative", IllegalArgumentException::class.java) {
            getBuilder().withRetry(1, -1, 1000, TimeUnit.MILLISECONDS)
        }
        assertThrows("Retry delay cannot be negative", IllegalArgumentException::class.java) {
            getBuilder().withRetry(1, -10, 1000, TimeUnit.MILLISECONDS)
        }

        assertThrows(
            "Delay limit (maxDelay) bust be greater or equal to the initial delay",
            IllegalArgumentException::class.java
        ) {
            getBuilder().withRetry(10, 1000, 999, TimeUnit.MILLISECONDS)
        }

        assertThrows(
            "Delay limit (maxDelay) bust be greater or equal to the initial delay",
            IllegalArgumentException::class.java
        ) {
            getBuilder().withRetry(10, 1000, 100, TimeUnit.MILLISECONDS)
        }
    }

    @Test
    fun test_with_auth_validation() {
        getBuilder().withGuestAuth().build()
        getBuilder().withTokenAuth("token").build()
        getBuilder().withHttpAuth("username", "password").build()

        assertThrows(
            "Authentication settings are not specified. Please specify authentication settings using " +
                    ".withGuestAuth(), .withHttpAuth(username, password) or .withTokenAuth(token)",
            IllegalStateException::class.java
        ) {
            getBuilder().build()
        }
    }

    @Test
    fun test_with_timeout_validation() {
        getBuilder().withTimeout(10, TimeUnit.SECONDS)

        assertThrows("Timeout must be positive", IllegalArgumentException::class.java) {
            getBuilder().withTimeout(0, TimeUnit.SECONDS)
        }

        assertThrows("Timeout must be positive", IllegalArgumentException::class.java) {
            getBuilder().withTimeout(-1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun test_with_max_concurrent_requests_validation() {
        getBuilder().withMaxConcurrentRequests(10)

        assertThrows("Max concurrent requests number must be positive", IllegalArgumentException::class.java) {
            getBuilder().withMaxConcurrentRequests(0)
        }

        assertThrows("Max concurrent requests number must be positive", IllegalArgumentException::class.java) {
            getBuilder().withMaxConcurrentRequests(-1)
        }
    }

    @Test
    fun test_with_max_concurrent_requests_per_host_validation() {
        getBuilder().withMaxConcurrentRequestsPerHost(10)

        assertThrows("Max concurrent requests per host number must be positive", IllegalArgumentException::class.java) {
            getBuilder().withMaxConcurrentRequestsPerHost(0)
        }

        assertThrows("Max concurrent requests per host number must be positive", IllegalArgumentException::class.java) {
            getBuilder().withMaxConcurrentRequestsPerHost(-1)
        }
    }
}

