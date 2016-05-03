package org.jetbrains.teamcity.rest

import retrofit.client.Response
import retrofit.http.*
import retrofit.mime.TypedString
import java.util.*

internal interface TeamCityService {
    @Headers("Accept: application/json")
    @GET("/app/rest/builds")
    fun builds(@Query("locator") buildLocator: String?): BuildListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/builds/id:{id}")
    fun build(@Path("id") id: String): BuildBean

    @Headers("Accept: application/json")
    @GET("/app/rest/changes")
    fun changes(@Query("locator") locator: String, @Query("fields") fields: String): ChangesBean

    @POST("/app/rest/builds/id:{id}/tags/")
    fun addTag(@Path("id") buildId: String, @Body tag: TypedString): Response

    @PUT("/app/rest/builds/id:{id}/pin/")
    fun pin(@Path("id") buildId: String, @Body comment: TypedString): Response

    @Streaming
    @GET("/app/rest/builds/id:{id}/artifacts/content/{path}")
    fun artifactContent(@Path("id") buildId: String, @Path("path") artifactPath: String): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/builds/id:{id}/artifacts/children/{path}")
    fun artifactChildren(@Path("id") buildId: String, @Path("path") artifactPath: String): ArtifactFileListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/projects/id:{id}")
    fun project(@Path("id") id: String): ProjectBean

    @Headers("Accept: application/json")
    @GET("/app/rest/buildTypes/id:{id}/buildTags")
    fun buildTypeTags(@Path("id") buildTypeId: String): TagsBean

    @Headers("Accept: application/json")
    @GET("/app/rest/buildQueue")
    fun queuedBuilds(@Query("locator") buildLocator: String?): QueuedBuildListBean
}

internal class ProjectsBean {
    var project: List<ProjectBean> = ArrayList()
}

internal class ArtifactFileListBean {
    var file: List<ArtifactFileBean> = ArrayList()
}

internal class ArtifactFileBean {
    var name: String? = null
    var size: Long? = null
    var modificationTime: String? = null
}

internal class BuildListBean {
    var build: List<BuildBean> = ArrayList()
}

internal open class BuildBean {
    var id: String? = null
    var number: String? = null
    var status: BuildStatus? = null
    var buildTypeId: String? = null
    var branchName : String? = null
    var isDefaultBranch : Boolean? = null

    var queuedDate: String? = null
    var startDate: String? = null
    var finishDate: String? = null

    var properties: ParametersBean? = ParametersBean()
}

internal class QueuedBuildListBean {
    var build: List<QueuedBuildBean> = ArrayList()
}

internal open class QueuedBuildBean {
    var id: String? = null
    var buildTypeId: String? = null
    var state: QueuedBuildStatus? = null
    var branchName : String? = null
    var isDefaultBranch : Boolean? = null

    var href: String? = null
    var webUrl: String? = null
}

internal class BuildTypeBean {
    var id: String? = null
    var name: String? = null
    var projectId: String? = null
}

internal class BuildTypesBean {
    var buildType: List<BuildTypeBean> = ArrayList()
}

internal class TagBean {
    var name: String? = null
}

internal class TagsBean {
    var tag: List<TagBean>? = ArrayList()
}

internal class ProjectBean {
    var id: String? = null
    var name: String? = null
    var parentProjectId: String? = null
    var archived: Boolean = false

    var projects: ProjectsBean? = ProjectsBean()
    var parameters: ParametersBean? = ParametersBean()
    var buildTypes: BuildTypesBean? = BuildTypesBean()
}

internal class ChangesBean {
    var change: List<ChangeBean>? = ArrayList()
}

internal class ChangeBean {
    var id: String? = null
    var version: String? = null
    var user: UserBean? = null
    var date: String? = null
    var comment: String? = null
}

internal class UserBean {
    var id: String? = null
    var username: String? = null
    var name: String? = null
}

internal class ParametersBean {
    var property: List<ParameterBean>? = ArrayList()
}

internal class ParameterBean {
    var name: String? = null
    var value: String? = null
    var own: Boolean = false
}