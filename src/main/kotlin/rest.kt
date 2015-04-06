package org.jetbrains.teamcity.rest

import retrofit.client.Response
import retrofit.http.*
import retrofit.mime.TypedString
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Locale

private trait TeamCityService {
    Headers("Accept: application/json")
    GET("/app/rest/builds")
    fun builds(Query("locator") buildLocator: String): BuildInfoListBean

    Headers("Accept: application/json")
    GET("/app/rest/builds/id:{id}")
    fun build(Path("id") id: String): BuildBean

    POST("/app/rest/builds/id:{id}/tags/")
    fun addTag(Path("id") buildId: String, Body tag: TypedString): Response

    PUT("/app/rest/builds/id:{id}/pin/")
    fun pin(Path("id") buildId: String, Body comment: TypedString): Response

    Streaming
    GET("/app/rest/builds/id:{id}/artifacts/content/{path}")
    fun artifactContent(Path("id") buildId: String, Path("path") artifactPath: String): Response

    Headers("Accept: application/json")
    GET("/app/rest/builds/id:{id}/artifacts/children/{path}")
    fun artifactChildren(Path("id") buildId: String, Path("path") artifactPath: String): ArtifactFileListBean

    Headers("Accept: application/json")
    GET("/app/rest/projects/id:{id}")
    fun project(Path("id") id: String): ProjectBean

    Headers("Accept: application/json")
    GET("/app/rest/buildTypes/id:{id}/buildTags")
    fun buildTypeTags(Path("id") buildTypeId: String): TagsBean
}

private class ProjectsBean {
    var project: List<ProjectBean> = ArrayList()
}

private class ArtifactFileListBean {
    var file: List<ArtifactFileBean> = ArrayList()
}

private class ArtifactFileBean {
    var name: String? = null
}

private class BuildInfoListBean {
    var build: List<BuildInfoBean> = ArrayList()
}

private open class BuildInfoBean {
    var id: String? = null
    var number: String? = null
    var status: BuildStatus? = null

    fun toBuild(service: TeamCityService): BuildImpl = BuildImpl(
            id = BuildId(id!!),
            service = service,
            buildNumber = number!!,
            status = status!!)
}

private class BuildBean: BuildInfoBean() {
    var queuedDate: String? = null
    var startDate: String? = null
    var finishDate: String? = null
}

private class BuildTypeBean {
    var id: String? = null
    var name: String? = null
    var projectId: String? = null

    fun toBuildType(service: TeamCityService): BuildType =
            BuildTypeImpl(BuildTypeId(id!!), name!!, ProjectId(projectId!!), service)
}

private class BuildTypesBean {
    var buildType: List<BuildTypeBean> = ArrayList()
}

private class TagBean {
    var name: String? = null
}

private class TagsBean {
    var tag: List<TagBean>? = ArrayList()
}

private class ProjectBean {
    var id: String? = null
    var name: String? = null
    var parentProjectId: String? = null
    var archived: Boolean = false

    var projects: ProjectsBean? = ProjectsBean()
    var parameters: ParametersBean? = ParametersBean()
    var buildTypes: BuildTypesBean? = BuildTypesBean()
}

private class ParametersBean {
    var property: List<ParameterBean>? = ArrayList()
}

private class ParameterBean {
    var name: String? = null
    var value: String? = null
    var own: Boolean = false

    fun toParameter(): Parameter = ParameterImpl(name!!, value, own)
}

