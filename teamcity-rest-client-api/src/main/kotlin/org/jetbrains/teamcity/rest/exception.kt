package org.jetbrains.teamcity.rest


open class TeamCityRestException(message: String?, cause: Throwable?) : RuntimeException(message, cause)

open class TeamCityQueryException(message: String?, cause: Throwable? = null) : TeamCityRestException(message, cause)

open class TeamCityConversationException(message: String?, cause: Throwable? = null) : TeamCityRestException(message, cause)