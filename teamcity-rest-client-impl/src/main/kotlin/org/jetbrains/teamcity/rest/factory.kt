package org.jetbrains.teamcity.rest

@Deprecated("Use TeamCityInstanceBuilder", replaceWith = ReplaceWith("TeamCityInstanceBuilder"))
object TeamCityInstanceFactory {
  /**
   * Deprecated.
   * Use [TeamCityInstanceBuilder.withGuestAuth]
   *
   * Creates guest authenticated accessor
   * @param serverUrl HTTP or HTTPS URL to TeamCity server
   */

  @JvmStatic
  @Deprecated("Use TeamCityInstanceBuilder", replaceWith = ReplaceWith("TeamCityInstanceBuilder(serverUrl).withGuestAuth().buildBlockingInstance()"))
  fun guestAuth(serverUrl: String): TeamCityInstance =
    TeamCityInstanceBuilder(serverUrl).withGuestAuth().buildBlockingInstance()

  /**
   * Deprecated.
   * Use [TeamCityInstanceBuilder.withHttpAuth]
   *
   * Creates username/password authenticated accessor
   *
   * @param serverUrl HTTP or HTTPS URL to TeamCity server
   * @param username username
   * @param password password
   */
  @Deprecated("Use TeamCityInstanceBuilder", replaceWith = ReplaceWith("TeamCityInstanceBuilder(serverUrl).withHttpAuth(username, password).buildBlockingInstance()"))
  @JvmStatic
  fun httpAuth(serverUrl: String, username: String, password: String): TeamCityInstance =
    TeamCityInstanceBuilder(serverUrl).withHttpAuth(username, password).buildBlockingInstance()

  /**
   * Deprecated.
   * Use [TeamCityInstanceBuilder.withTokenAuth]
   *
   * Creates token based connection.
   * TeamCity access token generated on My Settings & Tools | Access Tokens
   *
   * @param serverUrl HTTP or HTTPS URL to TeamCity server
   * @param token token
   *
   * see https://www.jetbrains.com/help/teamcity/rest-api.html#RESTAPI-RESTAuthentication
   */
  @Deprecated("Use TeamCityInstanceBuilder", replaceWith = ReplaceWith("TeamCityInstanceBuilder(serverUrl).withTokenAuth(token).buildBlockingInstance()"))
  @JvmStatic
  fun tokenAuth(serverUrl: String, token: String): TeamCityInstance =
    TeamCityInstanceBuilder(serverUrl).withTokenAuth(token).buildBlockingInstance()
}
