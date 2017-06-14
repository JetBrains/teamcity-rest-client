package org.jetbrains.teamcity.rest

import org.junit.Assert
import org.junit.Test
import java.lang.reflect.Modifier


class DeprecatedFactoryTest {
  @Test
  fun guestAuth() {
    @Suppress("DEPRECATION")
    TeamCityInstance.guestAuth(publicInstanceUrl)
  }

  @Test
  fun httpAuth() {
    @Suppress("DEPRECATION")
    TeamCityInstance.httpAuth(publicInstanceUrl, "jonnyzzz", "jonnyzzz")
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
