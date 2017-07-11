package org.jetbrains.teamcity.rest


/**
 * Factory object to create new object of [TeamCityInstance] interface
 *
 * @see TeamCityInstance
 */
object TeamCityInstanceFactory {
  /**
   * Creates guest authenticated accessor
   * @param serverUrl HTTP or HTTPS URL to TeamCity server
   *
   * Used via reflection for backward compatibility for deprecated methods
   */
  @JvmStatic
  fun guestAuth(serverUrl: String): TeamCityInstance
          = createGuestAuthInstance(serverUrl)

  /**
   * Creates username/password authenticated accessor
   *
   * @param serverUrl HTTP or HTTPS URL to TeamCity server
   * @param username username
   * @param password password
   *
   * Used via reflection for backward compatibility for deprecated methods
   */
  @JvmStatic
  fun httpAuth(serverUrl: String, username: String, password: String): TeamCityInstance
          = createHttpAuthInstance(serverUrl, username, password)
}
