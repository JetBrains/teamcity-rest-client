package org.jetbrains.teamcity.rest.coroutines

import org.jetbrains.teamcity.rest.*
import java.net.URLEncoder

class WebLinks(private val serverUrl: String) {
    fun buildConfigurationPage(id: BuildConfigurationId, branch: String? = null) =
        "$serverUrl/buildConfiguration/$id" + if (branch != null) "?${branch.urlencode()}" else ""

    fun buildPage(id: BuildId, configurationId: BuildConfigurationId? = null) =
        if (configurationId != null) {
            "$serverUrl/buildConfiguration/$configurationId/$id"
        } else {
            "$serverUrl/build/$id"
        }

    fun changePage(id: ChangeId, configurationId: BuildConfigurationId? = null, personal: Boolean? = null): String {
        val params = mutableListOf<String>()
        if (configurationId != null)
            params.add("buildTypeId=$configurationId")
        if (personal != null)
            params.add("personal=$personal")

        return "$serverUrl/change/$id" +
                if (params.isNotEmpty()) params.joinToString("&", prefix = "?") else ""
    }

    fun projectPage(id: ProjectId, branch: String? = null) =
        "$serverUrl/project/$id" + if (branch != null) "?${branch.urlencode()}" else ""

    fun testHistoryPage(id: TestId, projectId: ProjectId) =
        "$serverUrl/test/$id?currentProjectId=$projectId"

    fun userPage(id: UserId) =
        "$serverUrl/admin/editUser.html?userId=$id"
}

private fun String.urlencode(): String = URLEncoder.encode(this, "UTF-8")
