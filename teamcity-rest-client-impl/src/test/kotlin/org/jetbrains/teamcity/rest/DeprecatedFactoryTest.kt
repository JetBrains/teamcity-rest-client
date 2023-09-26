package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.test.assertEquals


class DeprecatedFactoryTest {
  @Test
  fun guestAuth() {
    @Suppress("DEPRECATION")
    val instance = TeamCityInstance.guestAuth(publicInstanceUrl)
    val expected = TeamCityInstanceBuilder(publicInstanceUrl).withGuestAuth()
    val actual = (instance as TeamCityInstanceBlockingBridge).toBuilder()
    assertEquals(expected, actual)
  }

  @Test
  fun httpAuth() {
    @Suppress("DEPRECATION")
    val instance = TeamCityInstance.httpAuth(publicInstanceUrl, "jonnyzzz", "jonnyzzz")
    val expected = TeamCityInstanceBuilder(publicInstanceUrl).withHttpAuth("jonnyzzz", "jonnyzzz")
    val actual = (instance as TeamCityInstanceBlockingBridge).toBuilder()
    assertEquals(expected, actual)
  }


  @Test
  fun checkMethodIsStatic_guestAuth() {

    val method = TeamCityInstance::class.java
            .methods.single { it.name == "guestAuth" }

    Assert.assertTrue(Modifier.isStatic(method.modifiers))
    Assert.assertTrue(Modifier.isPublic(method.modifiers))
  }

  @Test
  fun checkMethodIsStatic_httpAuth() {

    val method = TeamCityInstance::class.java
            .methods.single { it.name == "httpAuth" }

    Assert.assertTrue(Modifier.isStatic(method.modifiers))
    Assert.assertTrue(Modifier.isPublic(method.modifiers))
  }
}
