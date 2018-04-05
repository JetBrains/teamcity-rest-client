@file:Suppress("RemoveRedundantBackticks")

package org.jetbrains.teamcity.rest

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
    @GET("/app/rest/testOccurrences/")
    fun tests(@Query("locator") locator: String, @Query("fields") fields: String?): TestOccurrencesBean

    @Headers("Accept: application/json")
    @GET("/app/rest/vcs-roots")
    fun vcsRoots(@Query("locator") locator: String): VcsRootListBean

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
                         @Query("locator") locator: String): ArtifactFileListBean

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

    @Headers("Accept: application/json")
    @POST("/app/rest/buildQueue")
    fun triggerBuild(@Body value: TriggerBuildRequestBean): TriggeredBuildBean

    @Headers("Accept: application/json")
    @POST("/app/rest/builds/id:{id}")
    fun cancelBuild(@Path("id") buildId: String, @Body value: BuildCancelRequestBean): Response

    @GET("/app/rest/testOccurrences")
    fun tests(@Query("locator") locator: String): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/users")
    fun users(): UserListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/users/{userLocator}")
    fun users(@Path("userLocator") userLocator: String): UserBean

    @Headers("Accept: application/json")
    @GET("/app/rest/problemOccurrences")
    fun problemOccurrences(@Query("locator") locator: String, @Query("fields") fields: String): BuildProblemOccurrencesBean

    @POST("/app/rest/projects")
    @Headers("Accept: application/json", "Content-Type: application/xml")
    fun createProject(@Body projectDescriptionXml: TypedString): ProjectBean

    @POST("/app/rest/vcs-roots")
    @Headers("Accept: application/json", "Content-Type: application/xml")
    fun createVcsRoot(@Body vcsRootXml: TypedString): VcsRootBean

    @POST("/app/rest/buildTypes")
    @Headers("Accept: application/json", "Content-Type: application/xml")
    fun createBuildType(@Body buildTypeXml: TypedString): BuildTypeBean
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

internal class VcsRootListBean {
    var nextHref: String? = null
    var `vcs-root`: List<VcsRootBean> = ArrayList()
}

internal open class VcsRootBean {
    var id: String? = null
    var name: String? = null
}

internal class BuildListBean {
    var nextHref: String? = null
    var build: List<BuildBean> = ArrayList()
}

internal class UserListBean {
    var user: List<UserBean> = ArrayList()
}

internal open class BuildBean {
    var id: String? = null
    var buildTypeId: String? = null
    var canceledInfo: BuildCanceledBean? = null
    var number: String? = null
    var status: BuildStatus? = null
    var state: String? = null
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
    var webUrl: String? = null
    var buildType: BuildTypeBean? = BuildTypeBean()
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
    var paused: Boolean? = null
}

internal class BuildProblemBean {
    var id: String? = null
    var type: String? = null
    var identity: String? = null
}

internal class BuildProblemOccurrencesBean {
    var nextHref: String? = null
    var problemOccurrence: List<BuildProblemOccurrenceBean> = ArrayList()
}

internal class BuildProblemOccurrenceBean {
    var details: String? = null
    var additionalData: String? = null
    var problem: BuildProblemBean? = null
    var build: BuildBean? = null
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

internal open class TriggerBuildRequestBean {
    var branchName: String? = null
    var personal: Boolean? = null
    var triggeringOptions: TriggeringOptionsBean? = null

    var properties: ParametersBean? = null
    var buildType: BuildTypeBean? = null
    var comment: CommentBean? = null

//  TODO: lastChanges
//    <lastChanges>
//      <change id="modificationId"/>
//    </lastChanges>
}

internal class TriggeringOptionsBean {
    var cleanSources: Boolean? = null
    var rebuildAllDependencies: Boolean? = null
    var queueAtTop: Boolean? = null
}

internal class CommentBean {
    var text: String? = null
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
    var archived: Boolean? = null

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
    var email: String? = null
}

internal class ParametersBean() {
    var property: List<ParameterBean>? = ArrayList()

    constructor(properties: List<ParameterBean>) : this() {
        property = properties
    }
}

internal class ParameterBean() {
    var name: String? = null
    var value: String? = null
    var own: Boolean? = null

    constructor(name: String, value: String) : this() {
        this.name = name
        this.value = value
    }
}

internal class PinInfoBean {
    var user: UserBean? = null
    var timestamp: String? = null
}

internal class TriggeredBean {
    var user: UserBean? = null
    val build: BuildBean? = null
}

internal class BuildCanceledBean {
    var user: UserBean? = null
    val timestamp: String? = null
}

internal class TriggeredBuildBean {
    val id: Int? = null
    val buildTypeId: String? = null
}

internal class RevisionsBean {
    var revision: List<RevisionBean>? = ArrayList()
}

internal class RevisionBean {
    var version: String? = null
    var vcsBranchName: String? = null
    var `vcs-root-instance`: VcsRootBean? = null
}

internal class BuildCancelRequestBean {
    var comment: String = ""
    var readdIntoQueue = false
}

internal open class TestOccurrencesBean {
    var nextHref: String? = null
    var testOccurrence: List<TestOccurrenceBean> = ArrayList()
}

internal open class TestBean {
    var id: String? = null
}

internal open class TestOccurrenceBean {
    var name: String? = null
    var status: String? = null
    var ignored: Boolean? = null
    var duration: Long? = null
    var ignoreDetails: String? = null
    var details: String? = null
    val currentlyMuted: Boolean? = null
    val muted: Boolean? = null

    var build: BuildBean? = null
    var test: TestBean? = null

    companion object {
        val filter = "testOccurrence(name,status,ignored,muted,currentlyMuted,duration,ignoreDetails,details,build(id),test(id))"
    }
}
