package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Before
import org.junit.Test

class InvestigationTest {
    @Before
    fun setupLog4j() {
        setupLog4jDebug()
    }

    @Test
    fun test_limit() {
        val investigations = publicInstance().investigations().limitResults(3).all()
        Assert.assertEquals(investigations.count(), 3)
        investigations.forEach {
            callPublicPropertiesAndFetchMethods(it)
        }
    }

    @Test
    fun test_all() {
        val investigations = publicInstance().investigations().all()
        Assert.assertTrue(investigations.count() > 0)
    }
}
