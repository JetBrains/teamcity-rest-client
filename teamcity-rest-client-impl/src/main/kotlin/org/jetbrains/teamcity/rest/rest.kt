package org.jetbrains.teamcity.rest

import com.google.gson.annotations.SerializedName
import retrofit.client.Response
import retrofit.http.*
import retrofit.mime.TypedString
import java.util.*

internal interface TeamCityService {
    @Headers("Accept: application/json")
    @GET("/app/rest/builds")
    fun builds(@Query("locator") buildLocator: String): BuildListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/buildQueue")
    fun queuedBuilds(@Query("locator") locator: String?): QueuedBuildListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/builds/id:{id}")
    fun build(@Path("id") id: String): BuildBean

    @Headers("Accept: application/json")
    @GET("/app/rest/changes")
    fun changes(@Query("locator") locator: String, @Query("fields") fields: String): ChangesBean

    @Headers("Accept: application/json")
    @GET("/app/rest/vcs-roots")
    fun vcsRoots(): VcsRootListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/vcs-roots/id:{id}")
    fun vcsRoot(@Path("id") id: String): VcsRootBean

    @POST("/app/rest/builds/id:{id}/tags/")
    fun addTag(@Path("id") buildId: String, @Body tag: TypedString): Response

    @PUT("/app/rest/builds/id:{id}/pin/")
    fun pin(@Path("id") buildId: String, @Body comment: TypedString): Response

    //The standard DELETE annotation doesn't allow to include a body, so we need to use our own.
    //Probably it would be better to change Rest API here (https://youtrack.jetbrains.com/issue/TW-49178).
    @DELETE_WITH_BODY("/app/rest/builds/id:{id}/pin/")
    fun unpin(@Path("id") buildId: String, @Body comment: TypedString): Response

    @Streaming
    @GET("/app/rest/builds/id:{id}/artifacts/content/{path}")
    fun artifactContent(@Path("id") buildId: String, @Path("path", encode = false) artifactPath: String): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/builds/id:{id}/artifacts/children/{path}")
    fun artifactChildren(@Path("id") buildId: String,
                         @Path("path", encode = false) artifactPath: String,
                         @Query("locator") locator: String,
                         @Query("fields") fields: String): ArtifactFileListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/projects/id:{id}")
    fun project(@Path("id") id: String): ProjectBean

    @Headers("Accept: application/json")
    @GET("/app/rest/buildTypes/id:{id}")
    fun buildConfiguration(@Path("id") buildTypeId: String): BuildTypeBean

    @Headers("Accept: application/json")
    @GET("/app/rest/buildTypes/id:{id}/buildTags")
    fun buildTypeTags(@Path("id") buildTypeId: String): TagsBean

    @Headers("Accept: application/json")
    @GET("/app/rest/buildTypes/id:{id}/triggers")
    fun buildTypeTriggers(@Path("id") buildTypeId: String): TriggersBean

    @Headers("Accept: application/json")
    @GET("/app/rest/buildTypes/id:{id}/artifact-dependencies")
    fun buildTypeArtifactDependencies(@Path("id") buildTypeId: String): ArtifactDependenciesBean

    @PUT("/app/rest/projects/id:{id}/parameters/{name}")
    fun setProjectParameter(@Path("id") projectId: String, @Path("name") name: String, @Body value: TypedString): Response

    @PUT("/app/rest/buildTypes/id:{id}/parameters/{name}")
    fun setBuildTypeParameter(@Path("id") buildTypeId: String, @Path("name") name: String, @Body value: TypedString): Response

    @Streaming
    @GET("/downloadBuildLog.html")
    fun buildLog(@Query ("buildId") id: String): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/changes/buildType:{id},version:{version}")
    fun change(@Path("id") buildType: String, @Path("version") version: String): ChangeBean
}

internal class ProjectsBean {
    var project: List<ProjectBean> = ArrayList()
}

internal class ArtifactFileListBean {
    var file: List<ArtifactFileBean> = ArrayList()
}

internal class ArtifactFileBean {
    var name: String? = null
    var fullName: String? = null
    var size: Long? = null
    var modificationTime: String? = null

    companion object {
        val FIELDS = "${ArtifactFileBean::fullName.name},${ArtifactFileBean::name.name},${ArtifactFileBean::size.name},${ArtifactFileBean::modificationTime.name}"
    }
}

internal class VcsRootListBean {
    @SerializedName("vcs-root")
    var vcsRoot: List<VcsRootBean> = ArrayList()
}

internal open class VcsRootBean {
    var id: String? = null
    var name: String? = null

    var properties: NameValuePropertiesBean? = null
}

internal open class VcsRootInstanceBean {
    var `vcs-root-id`: String? = null
    var name: String? = null
}

internal class BuildListBean {
    var build: List<BuildBean> = ArrayList()
}

internal open class BuildBean {
    var id: String? = null
    var buildTypeId: String? = null
    var number: String? = null
    var status: BuildStatus? = null
    var branchName: String? = null
    var isDefaultBranch: Boolean? = null

    var statusText: String? = null
    var queuedDate: String? = null
    var startDate: String? = null
    var finishDate: String? = null

    var revisions: RevisionsBean? = null

    var pinInfo: PinInfoBean? = null

    var triggered: TriggeredBean? = null

    var properties: ParametersBean? = ParametersBean()
}

internal class QueuedBuildListBean {
    var build: List<QueuedBuildBean> = ArrayList()
}

internal open class QueuedBuildBean {
    var id: String? = null
    var buildTypeId: String? = null
    var state: String? = null
    var branchName: String? = null
    var defaultBranch: Boolean? = null

    var href: String? = null
    var webUrl: String? = null
}

internal class BuildTypeBean {
    var id: String? = null
    var name: String? = null
    var projectId: String? = null
    var paused: Boolean = false
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

internal class TriggerBean {
    var id: String? = null
    var type: String? = null
    var properties: ParametersBean? = ParametersBean()
}

internal class TriggersBean {
    var trigger: List<TriggerBean>? = ArrayList()
}

internal class ArtifactDependencyBean {
    var id: String? = null
    var type: String? = null
    var disabled: Boolean? = false
    var inherited: Boolean? = false
    var properties: ParametersBean? = ParametersBean()
    var `source-buildType`: BuildTypeBean = BuildTypeBean()
}

internal class ArtifactDependenciesBean {
    var `artifact-dependency`: List<ArtifactDependencyBean>? = ArrayList()
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
    var username: String? = null
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

internal class PinInfoBean {
    var user: UserBean? = null
    var timestamp: String? = null
}

internal class TriggeredBean {
    var user: UserBean? = null
    val build: BuildBean? = null
}

internal class RevisionsBean {
    var revision: List<RevisionBean>? = ArrayList()
}

internal class RevisionBean {
    var version: String? = null
    var vcsBranchName: String? = null
    var `vcs-root-instance`: VcsRootInstanceBean? = null
}

internal class NameValuePropertiesBean {
    var property: List<NameValuePropertyBean>? = ArrayList()
}

internal class NameValuePropertyBean {
    var name: String? = null
    var value: String? = null
}
