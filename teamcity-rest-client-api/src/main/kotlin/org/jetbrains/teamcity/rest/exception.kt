package org.jetbrains.teamcity.rest


open class TeamCityRestException(message: String?, cause: Throwable?) : RuntimeException(message, cause)

open class TeamCityQueryException(message: String?, cause: Throwable? = null) : TeamCityRestException(message, cause)

open class TeamCityConversationException(
    message: String?,
    cause: Throwable? = null,
    val httpCode: Int? = null,
    val requestUrl: String? = null,
    val responseErrorBody: String? = null,
) : TeamCityRestException(message, cause)