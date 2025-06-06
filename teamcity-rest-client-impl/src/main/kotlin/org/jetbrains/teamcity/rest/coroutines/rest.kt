@file:Suppress("RemoveRedundantBackticks")

package org.jetbrains.teamcity.rest.coroutines

import okhttp3.RequestBody
import okhttp3.ResponseBody
import org.jetbrains.teamcity.rest.*
import retrofit2.Response
import retrofit2.http.*
import java.util.*
import kotlin.coroutines.cancellation.CancellationException

internal interface TeamCityService {
    // Even with `@Path(encoded = true)` retrofit2 will encode special characters like [?,=,&]
    // So caller must specify path part and query params explicit
    @Streaming
    @Headers("Accept: application/json")
    @GET("{path}")
    suspend fun root(@Path("path", encoded = true) path: String, @QueryMap(encoded = false) encodedParams: Map<String, String>): Response<ResponseBody>

    @Headers("Accept: application/json")
    @GET("app/rest/builds")
    suspend fun builds(@Query("locator") buildLocator: String, @Query("fields") fields: String?): Response<BuildListBean>

    @Headers("Accept: application/json")
    @GET("app/rest/buildQueue")
    suspend fun queuedBuilds(@Query("locator") locator: String?, @Query("fields") fields: String?): Response<BuildListBean>

    @Headers("Accept: application/json")
    @GET("app/rest/builds/id:{id}")
    suspend fun build(@Path("id") id: String, @Query("fields") fields: String?): Response<BuildBean>

    @Headers("Accept: application/json")
    @GET("app/rest/investigations")
    suspend fun investigations(@Query("locator") investigationLocator: String?): Response<InvestigationListBean>

    @Headers("Accept: application/json")
    @POST("app/rest/investigations/multiple")
    suspend fun createInvestigations(@Body investigations: InvestigationListBean): Response<ResponseBody>

    @Headers("Accept: application/json")
    @DELETE("app/rest/investigations/{locator}")
    suspend fun deleteInvestigations(@Path("locator") locator: String): Response<ResponseBody>

    @Headers("Accept: application/json")
    @POST("app/rest/mutes/multiple")
    suspend fun createMutes(@Body mutes: MuteListBean): Response<ResponseBody>

    @Headers("Accept: application/json")
    @GET("app/rest/tests")
    suspend fun tests(@Query("locator") locator: String?, @Query("fields") fields: String): Response<TestListBean>

    @Headers("Accept: application/json")
    @GET("app/rest/tests/id:{id}")
    suspend fun test(@Path("id") id: String, @Query("fields") fields: String): Response<TestBean>

    @Headers("Accept: application/json")
    @GET("app/rest/investigations/id:{id}")
    suspend fun investigation(@Path("id") id: String): Response<InvestigationBean>

    @Headers("Accept: application/json")
    @GET("app/rest/mutes")
    suspend fun mutes(@Query("locator") muteLocator: String?): Response<MuteListBean>

    @Headers("Accept: application/json")
    @GET("app/rest/mutes/id:{id}")
    suspend fun mute(@Path("id") id: String): Response<MuteBean>

    @Headers("Accept: application/json")
    @GET("app/rest/changes")
    suspend fun changes(@Query("locator") locator: String, @Query("fields") fields: String): Response<ChangesBean>

    @Headers("Accept: application/json")
    @GET("app/rest/testOccurrences/")
    suspend fun testOccurrences(@Query("locator") locator: String, @Query("fields") fields: String?): Response<TestOccurrencesBean>

    @Headers("Accept: application/json")
    @GET("app/rest/testOccurrences/{id}")
    suspend fun testOccurrence(@Path("id") id: String, @Query("fields") fields: String?): Response<TestOccurrenceBean>

    @Headers("Accept: application/json")
    @GET("app/rest/vcs-roots")
    suspend fun vcsRoots(@Query("locator") locator: String? = null): Response<VcsRootListBean>

    @Headers("Accept: application/json")
    @GET("app/rest/vcs-roots/id:{id}")
    suspend fun vcsRoot(@Path("id") id: String): Response<VcsRootBean>

    @POST("app/rest/builds/id:{id}/tags/")
    suspend fun addTag(@Path("id") buildId: String, @Body tag: RequestBody): Response<ResponseBody>

    @PUT("app/rest/builds/id:{id}/comment/")
    suspend fun setComment(@Path("id") buildId: String, @Body comment: RequestBody): Response<ResponseBody>

    @PUT("app/rest/builds/id:{id}/tags/")
    suspend fun replaceTags(@Path("id") buildId: String, @Body tags: TagsBean): Response<ResponseBody>

    @PUT("app/rest/builds/id:{id}/pin/")
    suspend fun pin(@Path("id") buildId: String, @Body comment: RequestBody): Response<ResponseBody>

    //The standard @DELETE method doesn't allow including a body, so we need to use our own.
    //Probably it would be better to change TeamCity REST API here (https://youtrack.jetbrains.com/issue/TW-49178).
    @HTTP(method = "DELETE", path = "app/rest/builds/id:{id}/pin/", hasBody = true)
    suspend fun unpin(@Path("id") buildId: String, @Body comment: RequestBody): Response<ResponseBody>

    @Streaming
    @GET("app/rest/builds/id:{id}/artifacts/content/{path}")
    suspend fun artifactContent(@Path("id") buildId: String, @Path("path", encoded = true) artifactPath: String): Response<ResponseBody>

    @Headers("Accept: application/json")
    @GET("app/rest/builds/id:{id}/artifacts/children/{path}")
    suspend fun artifactChildren(@Path("id") buildId: String,
                         @Path("path", encoded = true) artifactPath: String,
                         @Query("locator") locator: String,
                         @Query("fields") fields: String): Response<ArtifactFileListBean>

    @Headers("Accept: application/json")
    @GET("app/rest/builds/id:{id}/resulting-properties")
    suspend fun resultingProperties(@Path("id") buildId: String): Response<ParametersBean>

    @Headers("Accept: application/json")
    @GET("app/rest/projects/id:{id}")
    suspend fun project(@Path("id") id: String): Response<ProjectBean>

    @Headers("Accept: application/json")
    @GET("app/rest/buildTypes/id:{id}")
    suspend fun buildConfiguration(@Path("id") buildTypeId: String, @Query("fields") fields: String?): Response<BuildTypeBean>

    @Headers("Accept: application/json")
    @GET("app/rest/buildTypes/id:{id}/buildTags")
    suspend fun buildTypeTags(@Path("id") buildTypeId: String): Response<TagsBean>

    @Headers("Accept: application/json")
    @GET("app/rest/buildTypes/id:{id}/triggers")
    suspend fun buildTypeTriggers(@Path("id") buildTypeId: String): Response<TriggersBean>

    @Headers("Accept: application/json")
    @GET("app/rest/buildTypes/id:{id}/artifact-dependencies")
    suspend fun buildTypeArtifactDependencies(@Path("id") buildTypeId: String): Response<ArtifactDependenciesBean>

    @Headers("Accept: application/json")
    @GET("app/rest/buildTypes/id:{id}/snapshot-dependencies")
    suspend fun buildTypeSnapshotDependencies(@Path("id") buildTypeId: String): Response<SnapshotDependenciesBean>

    @PUT("app/rest/projects/id:{id}/parameters/{name}/value")
    suspend fun setProjectParameter(@Path("id") projectId: String, @Path("name") name: String, @Body value: RequestBody): Response<ResponseBody>

    @DELETE("app/rest/projects/id:{id}/parameters/{name}")
    suspend fun removeProjectParameter(@Path("id") projectId: String, @Path("name") name: String): Response<ResponseBody>

    @PUT("app/rest/buildTypes/id:{id}/parameters/{name}/value")
    suspend fun setBuildTypeParameter(@Path("id") buildTypeId: String, @Path("name") name: String, @Body value: RequestBody): Response<ResponseBody>

    @Headers("Accept: application/json")
    @GET("app/rest/buildTypes/id:{id}/parameters")
    suspend fun getBuildTypeParameters(@Path("id") buildTypeId: String): Response<BuildTypeParametersBean>

    @DELETE("app/rest/buildTypes/id:{id}/parameters/{name}")
    suspend fun removeBuildTypeParameter(@Path("id") projectId: String, @Path("name") name: String): Response<ResponseBody>

    @PUT("app/rest/buildTypes/id:{id}/settings/{name}")
    suspend fun setBuildTypeSettings(@Path("id") buildTypeId: String, @Path("name") name: String, @Body value: RequestBody): Response<ResponseBody>

    @Headers("Accept: application/json")
    @POST("app/rest/buildQueue")
    suspend fun triggerBuild(@Body value: TriggerBuildRequestBean): Response<TriggeredBuildBean>

    @Headers("Accept: application/json")
    @POST("app/rest/builds/id:{id}")
    suspend fun cancelBuild(@Path("id") buildId: String, @Body value: BuildCancelRequestBean): Response<ResponseBody>

    @PUT("app/rest/builds/id:{id}/finish")
    suspend fun finishBuild(@Path("id") buildId: String): Response<ResponseBody>

    @Headers("Content-Type: text/plain")
    @POST("app/rest/builds/id:{id}/log")
    suspend fun log(@Path("id") buildId: String, @Body message: String): Response<ResponseBody>

    @Headers("Accept: application/json")
    @POST("app/rest/buildQueue/id:{id}")
    suspend fun removeQueuedBuild(@Path("id") buildId: String, @Body value: BuildCancelRequestBean): Response<ResponseBody>

    @Headers("Accept: application/json")
    @GET("app/rest/users")
    suspend fun users(): Response<UserListBean>

    @Headers("Accept: application/json")
    @GET("app/rest/users/{userLocator}")
    suspend fun users(@Path("userLocator") userLocator: String): Response<UserBean>

    @Headers("Accept: application/json")
    @GET("app/rest/agents")
    suspend fun agents(): Response<BuildAgentsBean>

    @Headers("Accept: application/json")
    @GET("app/rest/agentPools")
    suspend fun agentPools(): Response<BuildAgentPoolsBean>

    @Headers("Accept: application/json")
    @GET("app/rest/agents/{locator}")
    suspend fun agent(@Path("locator") agentLocator: String? = null): Response<BuildAgentBean>

    @Headers("Accept: application/json")
    @GET("app/rest/agents")
    suspend fun agents(@Query("locator") locator: String, @Query("fields") fields: String): Response<BuildAgentsListBean>

    @Headers("Accept: application/json")
    @GET("app/rest/agentPools/{locator}")
    suspend fun agentPools(@Path("locator") agentLocator: String? = null): Response<BuildAgentPoolBean>

    @Headers("Accept: application/json")
    @GET("app/rest/problemOccurrences")
    suspend fun problemOccurrences(@Query("locator") locator: String, @Query("fields") fields: String): Response<BuildProblemOccurrencesBean>

    @POST("app/rest/projects")
    @Headers("Accept: application/json", "Content-Type: application/xml")
    suspend fun createProject(@Body projectDescriptionXml: RequestBody): Response<ProjectBean>

    @POST("app/rest/vcs-roots")
    @Headers("Accept: application/json", "Content-Type: application/xml")
    suspend fun createVcsRoot(@Body vcsRootXml: RequestBody): Response<VcsRootBean>

    @POST("app/rest/buildTypes")
    @Headers("Accept: application/json", "Content-Type: application/xml")
    suspend fun createBuildType(@Body buildTypeXml: RequestBody): Response<BuildTypeBean>

    @Streaming
    @GET("downloadBuildLog.html")
    suspend fun buildLog(@Query ("buildId") id: String): Response<ResponseBody>

    @Headers("Accept: application/json")
    @GET("app/rest/changes/buildType:{id},version:{version}")
    suspend fun change(@Path("id") buildType: String, @Path("version") version: String): Response<ChangeBean>

    @Headers("Accept: application/json")
    @GET("app/rest/changes/id:{id}")
    suspend fun change(@Path("id") changeId: String): Response<ChangeBean>

    @Headers("Accept: application/json")
    @GET("app/rest/changes/id:{id}?fields=files")
    suspend fun changeFiles(@Path("id") changeId: String): Response<ChangeFilesBean>

    @Headers("Accept: application/json")
    @GET("app/rest/changes/{id}/firstBuilds")
    suspend fun changeFirstBuilds(@Path("id") id: String): Response<BuildListBean>

    @Headers("Accept: application/json")
    @POST("app/rest/agentPools/id:{id}/projects")
    suspend fun assignProjectToAgentPool(@Path("id") id: String, @Body project: ProjectBean): Response<ResponseBody>

    @Headers("Accept: application/json")
    @POST("app/rest/users/{userLocator}/roles/{roleId}/{scope}")
    suspend fun addUserRole(@Path("userLocator") userLocator: String, @Path("roleId") roleId: String, @Path("scope") scope: String): Response<ResponseBody>

    @Headers("Accept: application/json")
    @DELETE("app/rest/users/{userLocator}/roles/{roleId}/{scope}")
    suspend fun deleteUserRole(@Path("userLocator") userLocator: String, @Path("roleId") roleId: String, @Path("scope") scope: String): Response<ResponseBody>

    @Headers("Accept: application/json")
    @GET("app/rest/builds/id:{id}/statistics")
    suspend fun buildStatistics(@Path("id") buildId: String): Response<StatisticsBean>


    @Headers("Accept: application/json")
    @POST("app/rest/builds/{buildLocator}/status")
    suspend fun updateBuildStatus(@Path("buildLocator") buildLocator: String, @Body value: BuildStatusUpdateBean): Response<ResponseBody>

    @Headers("Accept: application/json")
    @GET("app/rest/agents/{locator}/compatibilityPolicy?fields=policy,count,buildTypes(buildType(id))")
    suspend fun agentCompatibilityPolicy(@Path("locator") agentLocator: String): Response<CompatibilityPolicyBean>

    @Headers("Accept: application/json")
    @PUT("app/rest/agents/{locator}/compatibilityPolicy")
    suspend fun updateAgentCompatibilityPolicy(
        @Path("locator") agentLocator: String,
        @Body policy: CompatibilityPolicyBean
    ): Response<ResponseBody>
}

internal fun TeamCityService.errorCatchingBridge() = TeamCityServiceErrorCatchingBridge(this)

internal class TeamCityServiceErrorCatchingBridge(private val service: TeamCityService) {
    private suspend fun <T> runErrorWrappingBridgeCallNullable(provider: suspend () -> Response<T>): T? {
        try {
            val response = provider()
            if (response.isSuccessful) {
                return response.body()
            }

            val errorBodyString = response.errorBody()?.string()
            val errorMessageSuffix = errorBodyString?.let { error -> ", error: $error" } ?: ""
            throw TeamCityConversationException(
                message = "Failed to connect to ${response.raw().request.url}, code ${response.code()}$errorMessageSuffix",
                cause = null,
                httpCode = response.code(),
                responseErrorBody = errorBodyString
            )
        } catch (e: TeamCityRestException) {
            throw e
        } catch (ce: CancellationException) {
            throw ce
        }
        catch (t: Throwable) {
            throw TeamCityRestException("Connect failed: ${t.message}", t)
        }
    }

    private suspend fun <T> runErrorWrappingBridgeCall(provider: suspend () -> Response<T>): T =
        checkNotNull(runErrorWrappingBridgeCallNullable(provider))

    suspend fun root(path: String, encodedParams: Map<String, String>): ResponseBody = runErrorWrappingBridgeCall { service.root(path, encodedParams) }
    suspend fun builds(buildLocator: String, fields: String?): BuildListBean = runErrorWrappingBridgeCall { service.builds(buildLocator, fields) }
    suspend fun queuedBuilds(locator: String?, fields: String?): BuildListBean = runErrorWrappingBridgeCall { service.queuedBuilds(locator, fields) }
    suspend fun build(id: String, fields: String?): BuildBean = runErrorWrappingBridgeCall { service.build(id, fields) }
    suspend fun investigations(investigationLocator: String?): InvestigationListBean = runErrorWrappingBridgeCall { service.investigations(investigationLocator) }
    suspend fun createInvestigations(investigations: InvestigationListBean) = runErrorWrappingBridgeCall { service.createInvestigations(investigations) }
    suspend fun deleteInvestigations(investigationLocator: String) = runErrorWrappingBridgeCallNullable { service.deleteInvestigations(investigationLocator) }
    suspend fun createMutes(mutes: MuteListBean) = runErrorWrappingBridgeCall { service.createMutes(mutes) }
    suspend fun tests(locator: String?, fields: String): TestListBean = runErrorWrappingBridgeCall { service.tests(locator, fields) }
    suspend fun test(id: String, fields: String): TestBean = runErrorWrappingBridgeCall { service.test(id, fields) }
    suspend fun investigation(id: String): InvestigationBean = runErrorWrappingBridgeCall { service.investigation(id) }
    suspend fun mutes(muteLocator: String?): MuteListBean = runErrorWrappingBridgeCall { service.mutes(muteLocator) }
    suspend fun mute(id: String): MuteBean = runErrorWrappingBridgeCall { service.mute(id) }
    suspend fun changes(locator: String, fields: String): ChangesBean = runErrorWrappingBridgeCall { service.changes(locator, fields) }
    suspend fun testOccurrences(locator: String, fields: String?): TestOccurrencesBean = runErrorWrappingBridgeCall { service.testOccurrences(locator, fields) }
    suspend fun testOccurrence(id: String, fields: String?): TestOccurrenceBean = runErrorWrappingBridgeCall { service.testOccurrence(id, fields) }
    suspend fun vcsRoots(locator: String? = null): VcsRootListBean = runErrorWrappingBridgeCall { service.vcsRoots(locator) }
    suspend fun vcsRoot(id: String): VcsRootBean = runErrorWrappingBridgeCall { service.vcsRoot(id) }
    suspend fun addTag(buildId: String, tag: RequestBody): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.addTag(buildId, tag) }
    suspend fun setComment(buildId: String, comment: RequestBody): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.setComment(buildId, comment) }
    suspend fun replaceTags(buildId: String, tags: TagsBean): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.replaceTags(buildId, tags) }
    suspend fun pin(buildId: String, comment: RequestBody): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.pin(buildId, comment) }
    suspend fun unpin(buildId: String, comment: RequestBody): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.unpin(buildId, comment) }
    suspend fun artifactContent(buildId: String, artifactPath: String): ResponseBody = runErrorWrappingBridgeCall { service.artifactContent(buildId, artifactPath) }
    suspend fun artifactChildren(
        buildId: String,
        artifactPath: String,
        locator: String,
        fields: String
    ): ArtifactFileListBean = runErrorWrappingBridgeCall { service.artifactChildren(buildId, artifactPath, locator, fields) }
    suspend fun resultingProperties(buildId: String): ParametersBean = runErrorWrappingBridgeCall { service.resultingProperties(buildId) }
    suspend fun project(id: String): ProjectBean = runErrorWrappingBridgeCall { service.project(id) }
    suspend fun buildConfiguration(buildTypeId: String, fields: String?): BuildTypeBean = runErrorWrappingBridgeCall { service.buildConfiguration(buildTypeId, fields) }
    suspend fun buildTypeTags(buildTypeId: String): TagsBean = runErrorWrappingBridgeCall { service.buildTypeTags(buildTypeId) }
    suspend fun buildTypeTriggers(buildTypeId: String): TriggersBean = runErrorWrappingBridgeCall { service.buildTypeTriggers(buildTypeId) }
    suspend fun buildTypeArtifactDependencies(buildTypeId: String): ArtifactDependenciesBean = runErrorWrappingBridgeCall { service.buildTypeArtifactDependencies(buildTypeId) }
    suspend fun buildTypeSnapshotDependencies(buildTypeId: String): SnapshotDependenciesBean = runErrorWrappingBridgeCall { service.buildTypeSnapshotDependencies(buildTypeId) }
    suspend fun setProjectParameter(
        projectId: String,
        name: String,
        value: RequestBody
    ): ResponseBody? = runErrorWrappingBridgeCallNullable { service.setProjectParameter(projectId, name, value) }
    suspend fun removeProjectParameter(
        projectId: String,
        name: String,
    ): ResponseBody? = runErrorWrappingBridgeCallNullable { service.removeProjectParameter(projectId, name) }

    suspend fun setBuildTypeParameter(
        buildTypeId: String,
        name: String,
        value: RequestBody
    ): ResponseBody? = runErrorWrappingBridgeCallNullable { service.setBuildTypeParameter(buildTypeId, name, value) }

    suspend fun getBuildTypeParameters(buildTypeId: String): BuildTypeParametersBean =
        runErrorWrappingBridgeCall { service.getBuildTypeParameters(buildTypeId) }

    suspend fun removeBuildTypeParameter(
        buildTypeId: String,
        name: String,
    ): ResponseBody? = runErrorWrappingBridgeCallNullable { service.removeBuildTypeParameter(buildTypeId, name) }

    suspend fun setBuildTypeSettings(
        buildTypeId: String,
        name: String,
        value: RequestBody
    ): ResponseBody? = runErrorWrappingBridgeCallNullable { service.setBuildTypeSettings(buildTypeId, name, value) }
    suspend fun triggerBuild(value: TriggerBuildRequestBean): TriggeredBuildBean = runErrorWrappingBridgeCall { service.triggerBuild(value) }
    suspend fun cancelBuild(buildId: String, value: BuildCancelRequestBean): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.cancelBuild(buildId, value) }
    suspend fun finishBuild(buildId: String): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.finishBuild(buildId) }
    suspend fun log(buildId: String, message: String): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.log(buildId, message) }
    suspend fun removeQueuedBuild(buildId: String, value: BuildCancelRequestBean): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.removeQueuedBuild(buildId, value) }
    suspend fun users(): UserListBean = runErrorWrappingBridgeCall { service.users() }
    suspend fun users(userLocator: String): UserBean = runErrorWrappingBridgeCall { service.users(userLocator) }
    suspend fun agents(): BuildAgentsBean = runErrorWrappingBridgeCall { service.agents() }
    suspend fun agents(locator: String, fields: String): BuildAgentsListBean = runErrorWrappingBridgeCall { service.agents(locator, fields) }
    suspend fun agentPools(): BuildAgentPoolsBean = runErrorWrappingBridgeCall { service.agentPools() }
    suspend fun agentPools(agentLocator: String? = null): BuildAgentPoolBean = runErrorWrappingBridgeCall { service.agentPools(agentLocator) }
    suspend fun agent(agentLocator: String? = null): BuildAgentBean = runErrorWrappingBridgeCall { service.agent(agentLocator) }
    suspend fun problemOccurrences(locator: String, fields: String): BuildProblemOccurrencesBean = runErrorWrappingBridgeCall { service.problemOccurrences(locator, fields) }
    suspend fun createProject(projectDescriptionXml: RequestBody): ProjectBean = runErrorWrappingBridgeCall { service.createProject(projectDescriptionXml) }
    suspend fun createVcsRoot(vcsRootXml: RequestBody): VcsRootBean = runErrorWrappingBridgeCall { service.createVcsRoot(vcsRootXml) }
    suspend fun createBuildType(buildTypeXml: RequestBody): BuildTypeBean = runErrorWrappingBridgeCall { service.createBuildType(buildTypeXml) }
    suspend fun buildLog(id: String): ResponseBody = runErrorWrappingBridgeCall { service.buildLog(id) }
    suspend fun change(buildType: String, version: String): ChangeBean = runErrorWrappingBridgeCall { service.change(buildType, version) }
    suspend fun change(changeId: String): ChangeBean = runErrorWrappingBridgeCall { service.change(changeId) }
    suspend fun changeFiles(changeId: String): ChangeFilesBean = runErrorWrappingBridgeCall { service.changeFiles(changeId) }
    suspend fun changeFirstBuilds(id: String): BuildListBean = runErrorWrappingBridgeCall { service.changeFirstBuilds(id) }
    suspend fun assignProjectToAgentPool(poolId: String, project: ProjectBean): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.assignProjectToAgentPool(poolId, project) }
    suspend fun addUserRole(userLocator: String, roleId: String, scope: String): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.addUserRole(userLocator, roleId, scope) }
    suspend fun deleteUserRole(userLocator: String, roleId: String, scope: String): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.deleteUserRole(userLocator, roleId, scope) }
    suspend fun buildStatistics(buildId: String): StatisticsBean = runErrorWrappingBridgeCall { service.buildStatistics(buildId) }
    suspend fun updateBuildStatus(buildLocator: String, value: BuildStatusUpdateBean): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.updateBuildStatus(buildLocator, value) }

    suspend fun agentCompatibilityPolicy(agentLocator: String): CompatibilityPolicyBean =
        runErrorWrappingBridgeCall { service.agentCompatibilityPolicy(agentLocator) }

    suspend fun updateAgentCompatibilityPolicy(agentLocator: String, policy: CompatibilityPolicyBean): ResponseBody? =
        runErrorWrappingBridgeCallNullable { service.updateAgentCompatibilityPolicy(agentLocator, policy) }

}

internal class ProjectsBean {
    var project: List<ProjectBean> = ArrayList()
}

internal class BuildAgentsBean {
    var agent: List<BuildAgentBean> = ArrayList()
}

internal class BuildAgentPoolsBean {
    var agentPool: List<BuildAgentPoolBean> = ArrayList()
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

internal open class IdBean {
    var id: String? = null
}

internal class VcsRootListBean {
    var nextHref: String? = null
    var `vcs-root`: List<VcsRootBean> = ArrayList()
}

internal open class VcsRootBean: IdBean() {
    var name: String? = null

    var properties: NameValuePropertiesBean? = null
}

internal open class VcsRootInstanceBean {
    var `vcs-root-id`: String? = null
    var name: String? = null
}

internal class BuildListBean {
    var nextHref: String? = null
    var build: List<BuildBean> = ArrayList()
}

internal class UserListBean {
    var user: List<UserBean> = ArrayList()
}

internal open class BuildBean: IdBean() {
    var buildTypeId: String? = null
    var canceledInfo: BuildCanceledBean? = null
    var number: String? = null
    var status: BuildStatus? = null
    var state: String? = null
    var personal: Boolean? = null
    var branchName: String? = null
    var defaultBranch: Boolean? = null
    var composite: Boolean? = null

    var statusText: String? = null
    var queuedDate: String? = null
    var startDate: String? = null
    var finishDate: String? = null

    var tags: TagsBean? = null
    var `running-info`: BuildRunningInfoBean? = null
    var revisions: RevisionsBean? = null

    var pinInfo: PinInfoBean? = null

    var triggered: TriggeredBean? = null
    var comment: BuildCommentBean? = null
    var agent: BuildAgentBean? = null

    var properties: ParametersBean? = null
    var resultingProperties: ParametersBean? = null
    var buildType: BuildTypeBean? = null

    var `snapshot-dependencies`: BuildListBean? = null
    var detachedFromAgent: Boolean? = null
    var queuedWaitReasons: QueueWaitReasonsPropertiesBean? = null
    var failedToStart: Boolean? = null
    var history: Boolean? = null

    companion object {
        val fullFieldsFilter: String = buildCustomFieldsFilter(
            fields =  EnumSet.allOf(BuildLocatorSettings.BuildField::class.java),
            wrap = false
        )

        fun buildCustomFieldsFilter(
            fields: Set<BuildLocatorSettings.BuildField>,
            wrap: Boolean
        ): String {
            val allFields = (fields.asSequence().map(::remapField) + "id").distinct()
            return if (wrap) {
                allFields.joinToString(prefix = "nextHref,build(", separator = ",", postfix = ")")
            } else {
                allFields.joinToString(separator = ",")
            }
        }

        private fun remapField(field: BuildLocatorSettings.BuildField): String = when (field) {
            BuildLocatorSettings.BuildField.NAME,
            BuildLocatorSettings.BuildField.PROJECT_ID,
            BuildLocatorSettings.BuildField.PROJECT_NAME -> "buildType(name,projectId,projectName)"
            BuildLocatorSettings.BuildField.BUILD_CONFIGURATION_ID -> "buildTypeId"
            BuildLocatorSettings.BuildField.BUILD_NUMBER -> "number"
            BuildLocatorSettings.BuildField.STATUS -> "status"
            BuildLocatorSettings.BuildField.STATUS_TEXT -> "statusText"
            BuildLocatorSettings.BuildField.STATE -> "state"
            BuildLocatorSettings.BuildField.BRANCH -> "branchName,defaultBranch"
            BuildLocatorSettings.BuildField.IS_PERSONAL -> "personal"
            BuildLocatorSettings.BuildField.CANCELED_INFO -> "canceledInfo(*,user(id,name,username,email))"
            BuildLocatorSettings.BuildField.COMMENT -> "comment(*,user(id,name,username,email))"
            BuildLocatorSettings.BuildField.IS_COMPOSITE -> "composite"
            BuildLocatorSettings.BuildField.QUEUED_DATETIME -> "queuedDate"
            BuildLocatorSettings.BuildField.START_DATETIME -> "startDate"
            BuildLocatorSettings.BuildField.FINISH_DATETIME -> "finishDate"
            BuildLocatorSettings.BuildField.RUNNING_INFO -> "running-info(*)"
            BuildLocatorSettings.BuildField.PARAMETERS -> "properties(*,property(*))"
            BuildLocatorSettings.BuildField.RESULTING_PARAMETERS -> "resultingProperties(*,property(*))"
            BuildLocatorSettings.BuildField.TAGS -> "tags(*,tag(*))"
            BuildLocatorSettings.BuildField.REVISIONS -> "revisions(*,revision(*))"
            BuildLocatorSettings.BuildField.SNAPSHOT_DEPENDENCIES -> "snapshot-dependencies(*,build(id,buildTypeId,number,status,branchName,defaultBranch))"
            BuildLocatorSettings.BuildField.PIN_INFO -> "pinInfo(*,user(id,name,username,email))"
            BuildLocatorSettings.BuildField.TRIGGERED_INFO -> "triggered(*,user(id,name,username,email),build(id,buildTypeId),buildType(name,projectId),properties(*,property(*)))"
            BuildLocatorSettings.BuildField.AGENT -> "agent"
            BuildLocatorSettings.BuildField.IS_DETACHED_FROM_AGENT -> "detachedFromAgent"
            BuildLocatorSettings.BuildField.QUEUED_WAIT_REASONS -> "queuedWaitReasons(*,property(*))"
            BuildLocatorSettings.BuildField.IS_FAILED_TO_START -> "failedToStart"
            BuildLocatorSettings.BuildField.HISTORY -> "history"
        }
    }
}

internal class BuildRunningInfoBean {
    val percentageComplete: Int = 0
    val elapsedSeconds: Long = 0
    val estimatedTotalSeconds: Long = 0
    val outdated: Boolean = false
    val probablyHanging: Boolean = false
}

internal class BuildTypeBean: IdBean() {
    var name: String? = null
    var projectId: String? = null
    var projectName: String? = null
    var paused: Boolean? = null
    var type: String? = null
    var settings: BuildTypeSettingsBean? = null

    companion object {
        const val fields = "id,name,projectId,projectName,paused,type,settings"
    }
}

internal class BuildTypeSettingsBean {
    var property: List<NameValuePropertyBean> = ArrayList()
}

internal class BuildTypeParametersBean {
    var property: List<BuildTypeParameterBean> = ArrayList()
}

internal class BuildTypeParameterBean {
    var name: String? = null
    var value: String? = null
    var inherited: Boolean? = null
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
    var muted: Boolean? = null
    var currentlyMuted: Boolean? = null
    var currentlyInvestigated: Boolean? = null
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
    var agent: BuildAgentBean? = null
    var revisions: RevisionsBean? = null

    var properties: ParametersBean? = null
    var buildType: BuildTypeBean? = null
    var comment: CommentBean? = null
    var `snapshot-dependencies`: BuildListBean? = null

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

internal class ArtifactDependencyBean: IdBean() {
    var type: String? = null
    var disabled: Boolean? = false
    var inherited: Boolean? = false
    var properties: ParametersBean? = ParametersBean()
    var `source-buildType`: BuildTypeBean = BuildTypeBean()
}

internal class ArtifactDependenciesBean {
    var `artifact-dependency`: List<ArtifactDependencyBean>? = ArrayList()
}

internal class SnapshotDependencyBean: IdBean() {
    var disabled: Boolean? = false
    var inherited: Boolean? = false
    var properties: ParametersBean? = ParametersBean()
    var `source-buildType`: BuildTypeBean = BuildTypeBean()
}

internal class SnapshotDependenciesBean {
    var `snapshot-dependency`: List<SnapshotDependencyBean>? = ArrayList()
}

internal class ProjectBean: IdBean() {
    var name: String? = null
    var parentProjectId: String? = null
    var archived: Boolean? = null

    var projects: ProjectsBean? = ProjectsBean()
    var parameters: ParametersBean? = ParametersBean()
    var buildTypes: BuildTypesBean? = BuildTypesBean()
}

internal class BuildAgentBean: IdBean() {
    var typeId: String? = null
    var name: String? = null
    var connected: Boolean? = null
    var enabled: Boolean? = null
    var authorized: Boolean? = null
    var uptodate: Boolean? = null
    var ip: String? = null

    var enabledInfo: EnabledInfoBean? = null
    var authorizedInfo: AuthorizedInfoBean? = null

    var properties: ParametersBean? = null
    var pool: BuildAgentPoolBean? = null
    var build: BuildBean? = null

    companion object {
        const val fields = "agent(name,connected,enabled,authorized,uptodate,ip,id)"
    }
}

internal class BuildAgentsListBean {
    var nextHref: String? = null
    var agent: List<BuildAgentBean> = ArrayList()
}

internal class BuildAgentPoolBean: IdBean() {
    var name: String? = null

    var projects: ProjectsBean? = ProjectsBean()
    var agents: BuildAgentsBean? = BuildAgentsBean()
}

internal class ChangesBean {
    var change: List<ChangeBean>? = ArrayList()
}

internal class ChangeBean: IdBean() {
    var version: String? = null
    var user: UserBean? = null
    var date: String? = null
    val registrationDate: String? = null
    var comment: String? = null
    var username: String? = null
    var vcsRootInstance: VcsRootInstanceBean? = null
}

internal class ChangeFilesBean {
    var files: ChangeFilesChangeBean? = null
}

internal class ChangeFilesChangeBean {
    var count: Int? = null
    var file: List<ChangeFileBean>? = ArrayList()
}

internal class ChangeFileBean {
    var `before-revision`: String? = null
    var `after-revision`: String? = null
    var changeType: String? = null
    var file: String? = null
    var `relative-file`: String? = null
}

internal class UserBean: IdBean() {
    var username: String? = null
    var name: String? = null
    var email: String? = null
    var roles: RolesBean? = null
}

internal class RolesBean {
    var role: List<RoleBean>? = ArrayList()
}

internal class RoleBean {
    var roleId: String? = null
    var scope: String? = null
}

internal class ParametersBean() {
    var property: List<ParameterBean>? = null

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
    var text: String? = null
}

internal class TriggeredBean {
    var user: UserBean? = null
    val build: BuildBean? = null
    var type: String? = null
}

internal class BuildCommentBean {
    var user: UserBean? = null
    var timestamp: String? = null
    var text: String? = null
}

internal class EnabledInfoCommentBean {
    var user: UserBean? = null
    var timestamp: String? = null
    var text: String? = null
}

internal class EnabledInfoBean {
    var comment: EnabledInfoCommentBean? = null
}

internal class AuthorizedInfoCommentBean {
    var user: UserBean? = null
    var timestamp: String? = null
    var text: String? = null
}

internal class AuthorizedInfoBean {
    var comment: AuthorizedInfoCommentBean? = null
}

internal class BuildCanceledBean {
    var user: UserBean? = null
    val timestamp: String? = null
    val text: String? = null
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
    var `vcs-root-instance`: VcsRootInstanceBean? = null
}

internal class NameValuePropertiesBean {
    var property: List<NameValuePropertyBean>? = ArrayList()
}

internal class NameValuePropertyBean {
    var name: String? = null
    var value: String? = null
}

internal class BuildCancelRequestBean {
    var comment: String = ""
    var readdIntoQueue = false
}

internal open class TestOccurrencesBean {
    var nextHref: String? = null
    var testOccurrence: List<TestOccurrenceBean> = ArrayList()
}
internal class TestListBean {
    var test: List<TestBean> = ArrayList()
}
internal open class TestBean: IdBean() {
    var name: String? = null
    val parsedTestName: ParsedTestNameBean? = null

    companion object {
        val fullFieldsFilter: String = buildCustomFieldsFilter(
            fields = EnumSet.allOf(TestLocatorSettings.TestField::class.java),
            wrap = false
        )

        val defaultFields = listOf(TestLocatorSettings.TestField.NAME)

        fun buildCustomFieldsFilter(
            fields: Collection<TestLocatorSettings.TestField>,
            wrap: Boolean
        ): String {
            val allFields = constructFields(fields.asSequence())
            return if (wrap) {
                "test($allFields)"
            } else {
                allFields
            }
        }

        private fun remapField(field: TestLocatorSettings.TestField): GroupedField = when (field) {
            TestLocatorSettings.TestField.NAME -> GroupedField(TestFieldGroup.NONE, "name")
            TestLocatorSettings.TestField.PARSED_METHOD_NAME -> GroupedField(TestFieldGroup.PARSED_TEST_NAME, "testMethodName")
            TestLocatorSettings.TestField.PARSED_NAME_CLASS -> GroupedField(TestFieldGroup.PARSED_TEST_NAME, "testClass")
            TestLocatorSettings.TestField.PARSED_NAME_SUITE -> GroupedField(TestFieldGroup.PARSED_TEST_NAME, "testSuite")
            TestLocatorSettings.TestField.PARSED_SHORT_NAME -> GroupedField(TestFieldGroup.PARSED_TEST_NAME, "testShortName")
            TestLocatorSettings.TestField.PARSED_NAME_WITH_PARAMETERS -> GroupedField(TestFieldGroup.PARSED_TEST_NAME, "testNameWithParameters")
            TestLocatorSettings.TestField.PARSED_NAME_PACKAGE -> GroupedField(TestFieldGroup.PARSED_TEST_NAME, "testPackage")
            TestLocatorSettings.TestField.PARSED_NAME_WITHOUT_PREFIX -> GroupedField(TestFieldGroup.PARSED_TEST_NAME, "testNameWithoutPrefix")
        }

        private fun constructFields(fields: Sequence<TestLocatorSettings.TestField>): String =
            fields.distinct()
                .map { remapField(it) }
                .groupBy { it.group }
                .map { (group, groupFields) ->
                    when (group) {
                        TestFieldGroup.NONE -> groupFields.joinToString(separator = ",") { it.fieldStr }
                        TestFieldGroup.PARSED_TEST_NAME -> "${group.groupName}(${groupFields.joinToString(separator = ",") { it.fieldStr }})"
                    }
                }
                .plus("id")
                .joinToString(separator = ",")

        private enum class TestFieldGroup(val groupName: String) {
            NONE(""),
            PARSED_TEST_NAME("parsedTestName")
        }

        private data class GroupedField(val group: TestFieldGroup, val fieldStr: String)
    }
}

internal open class ParsedTestNameBean {
    var testPackage: String? = null
    var testSuite: String? = null
    var testClass: String? = null
    var testShortName: String? = null
    var testNameWithoutPrefix: String? = null
    var testMethodName: String? = null
    var testNameWithParameters: String? = null
}

internal open class TestOccurrenceBean: IdBean() {
    var name: String? = null
    var status: String? = null
    var ignored: Boolean? = null
    var duration: Long? = null
    var ignoreDetails: String? = null
    var details: String? = null
    val currentlyMuted: Boolean? = null
    val muted: Boolean? = null
    val newFailure: Boolean? = null
    var metadata: MetadataBean? = null

    var build: BuildBean? = null
    var test: TestBean? = null
    var nextFixed: BuildBean? = null
    var firstFailed: BuildBean? = null
    var logAnchor: String? = null

    companion object {
        val fullFieldsFilter: String = buildCustomFieldsFilter(
            testRunFields = EnumSet.allOf(TestRunsLocatorSettings.TestRunField::class.java),
            testFields = EnumSet.allOf(TestLocatorSettings.TestField::class.java),
            wrap = false
        )

        fun buildCustomFieldsFilter(
            testRunFields: Collection<TestRunsLocatorSettings.TestRunField>,
            testFields: Collection<TestLocatorSettings.TestField>,
            wrap: Boolean
        ): String {
            // test(...) fields will be added later, see testFieldsStr
            val resultTestRunFieldsStr = (testRunFields.asSequence() - TestRunsLocatorSettings.TestRunField.TEST_ID)
                .map(::remapField)
                .distinct()

            val testFieldsStr = when {
                testFields.any() -> "test(${TestBean.buildCustomFieldsFilter(testFields, wrap = false)})"
                TestRunsLocatorSettings.TestRunField.TEST_ID in testRunFields -> remapField(TestRunsLocatorSettings.TestRunField.TEST_ID)
                else -> null
            }
            val allFields = (resultTestRunFieldsStr + "id" + testFieldsStr)
                .filterNotNull()
                .distinct()
            return if (wrap) {
                allFields.joinToString(prefix = "nextHref,testOccurrence(", separator = ",", postfix = ")")
            } else {
                allFields.joinToString(separator = ",")
            }
        }

        private fun remapField(field: TestRunsLocatorSettings.TestRunField): String = when (field) {
            TestRunsLocatorSettings.TestRunField.NAME -> "name"
            TestRunsLocatorSettings.TestRunField.STATUS -> "status"
            TestRunsLocatorSettings.TestRunField.DURATION -> "duration"
            TestRunsLocatorSettings.TestRunField.DETAILS -> "ignoreDetails,details"
            TestRunsLocatorSettings.TestRunField.IGNORED -> "ignored"
            TestRunsLocatorSettings.TestRunField.IS_CURRENTLY_MUTED -> "currentlyMuted"
            TestRunsLocatorSettings.TestRunField.IS_MUTED -> "muted"
            TestRunsLocatorSettings.TestRunField.IS_NEW_FAILURE -> "newFailure"
            TestRunsLocatorSettings.TestRunField.BUILD_ID -> "build(id)"
            TestRunsLocatorSettings.TestRunField.FIXED_IN_BUILD_ID -> "nextFixed(id)"
            TestRunsLocatorSettings.TestRunField.FIRST_FAILED_IN_BUILD_ID -> "firstFailed(id)"
            TestRunsLocatorSettings.TestRunField.TEST_ID -> "test(id)"
            TestRunsLocatorSettings.TestRunField.METADATA_VALUES -> "metadata"
            TestRunsLocatorSettings.TestRunField.LOG_ANCHOR -> "logAnchor"
        }
    }
}

internal class MuteListBean {
    var mute: List<MuteBean> = ArrayList()
    var nextHref: String? = null
}


internal open class InvestigationMuteBaseBean(
    val assignment: AssignmentBean? = null,
    val resolution: InvestigationResolutionBean? = null,
    val scope: InvestigationScopeBean? = null,
    val target: InvestigationTargetBean? = null,
): IdBean()

internal class MuteBean(
    assignment: AssignmentBean? = null,
    resolution: InvestigationResolutionBean? = null,
    scope: InvestigationScopeBean? = null,
    target: InvestigationTargetBean? = null,
) : InvestigationMuteBaseBean(assignment, resolution, scope, target) {
    constructor(other: InvestigationMuteBaseBean) : this(other.assignment, other.resolution, other.scope, other.target)
}

internal class InvestigationListBean {
    var nextHref: String? = null
    var investigation: List<InvestigationBean> = ArrayList()
}

internal class InvestigationBean(
    assignment: AssignmentBean? = null,
    resolution: InvestigationResolutionBean? = null,
    scope: InvestigationScopeBean? = null,
    target: InvestigationTargetBean? = null,
    val assignee: UserBean? = null,
    val state: InvestigationState? = null,
) : InvestigationMuteBaseBean(assignment, resolution, scope, target) {
    constructor(base: InvestigationMuteBaseBean, assignee: UserBean?, state: InvestigationState?) : this(
        base.assignment, base.resolution, base.scope, base.target, assignee, state
    )
}

class InvestigationResolutionBean(
    val type: String? = null,
    val time: String? = null,
)

internal class AssignmentBean(
    val user: UserBean? = null,
    val text: String? = null,
    val timestamp: String? = null,
)
internal open class InvestigationTargetBean(
    val tests : TestUnderInvestigationListBean? = null,
    val problems: ProblemUnderInvestigationListBean? = null,
    val anyProblem: Boolean? = null,
)

internal class TestUnderInvestigationListBean {
    val count : Int? = null
    var test : List<TestBean> = ArrayList()

}

internal class ProblemUnderInvestigationListBean {
    val count : Int? = null
    var problem : List<BuildProblemBean> = ArrayList()
}

internal class InvestigationScopeBean(
    val buildTypes : BuildTypesBean? = null,
    val project : ProjectBean? = null,
)

internal class MetadataBean {
    val count: Int? = null
    val typedValues: List<TypedValuesBean>? = null
}

internal class TypedValuesBean {
    val name: String? = null
    val type: String? = null
    val value: String? = null
}

internal class StatisticsBean {
    var property: List<PropertyBean>? = null
}

internal class QueueWaitReasonsPropertiesBean {
    var property: List<PropertyBean>? = null
}

internal class PropertyBean {
    var name: String? = null
    var value: String? = null
}

internal class BuildStatusUpdateBean {
    var status: String? = null
    var comment: String? = null
}

internal class CompatibilityPolicyBean {
    var buildTypes: BuildTypesBean? = BuildTypesBean()
    var policy: String? = null
}
