package org.jetbrains.teamcity.rest

import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.TimeUnit


class TeamCityInstanceBuilderTest {
    private fun getBuilder() =  TeamCityInstanceBuilder("https://myserver.local")

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
}

