@file:Suppress("RemoveRedundantBackticks")

package org.jetbrains.teamcity.rest

import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import kotlin.test.assertTrue

class UserTest {
    private lateinit var instance: TeamCityInstance

    @Before
    fun setup() {
        Assume.assumeTrue(haveCustomInstance())

        setupLog4jDebug()

        // requires admin credentials to teamcity.jetbrains.com
        instance = customInstanceByConnectionFile()
    }

    @Test
    fun `user by id`() {
        val user = instance.user(UserId("1"))
        assertEquals("1", user.id.stringId)
        assertEquals("kir", user.username)
        assertEquals("Kirill Maximov", user.name)
        assertEquals("kir@jetbrains.com", user.email)
        assertEquals("${instance.serverUrl}/admin/editUser.html?userId=1", user.getHomeUrl())
    }

    @Test
    fun `user url from instance`() {
        val url = instance.user(UserId("1")).getHomeUrl()
        assertEquals("${instance.serverUrl}/admin/editUser.html?userId=1", url)
    }

    @Test
    fun `user by username`() {
        val user = instance.user("kir")
        assertEquals("1", user.id.stringId)
    }

    @Test
    fun `user list`() {
        val users = instance.users().all().toList()
        assertTrue { users.size > 1000 }
        assertEquals("kir", users.single { it.id.stringId == "1" }.username)
        assertEquals("kir@jetbrains.com", users.single { it.id.stringId == "1" }.email)
    }
}