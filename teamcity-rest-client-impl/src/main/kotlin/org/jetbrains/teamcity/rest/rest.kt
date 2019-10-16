@file:Suppress("RemoveRedundantBackticks")

package org.jetbrains.teamcity.rest

import retrofit.client.Response
import retrofit.http.*
import retrofit.mime.TypedString

internal interface TeamCityService {

    @Streaming
    @Headers("Accept: application/json")
    @GET("/{path}")
    fun root(@Path("path", encode = false) path: String): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/builds")
    fun builds(@Query("locator") buildLocator: String): BuildListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/buildQueue")
    fun queuedBuilds(@Query("locator") locator: String?): BuildListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/builds/id:{id}")
    fun build(@Path("id") id: String): BuildBean

    @Headers("Accept: application/json")
    @GET("/app/rest/investigations")
    fun investigations(@Query("locator") investigationLocator: String?): InvestigationListBean

    @Headers("Accept: application/json")
    @POST("/app/rest/investigations/multiple")
    fun createInvestigations(@Body investigations: InvestigationListBean): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/investigations/id:{id}")
    fun investigation(@Path("id") id: String): InvestigationBean

    @Headers("Accept: application/json")
    @GET("/app/rest/changes")
    fun changes(@Query("locator") locator: String, @Query("fields") fields: String): ChangesBean

    @Headers("Accept: application/json")
    @GET("/app/rest/testOccurrences/")
    fun testOccurrences(@Query("locator") locator: String, @Query("fields") fields: String?): TestOccurrencesBean

    @Headers("Accept: application/json")
    @GET("/app/rest/vcs-roots")
    fun vcsRoots(@Query("locator") locator: String? = null): VcsRootListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/vcs-roots/id:{id}")
    fun vcsRoot(@Path("id") id: String): VcsRootBean

    @POST("/app/rest/builds/id:{id}/tags/")
    fun addTag(@Path("id") buildId: String, @Body tag: TypedString): Response

    @PUT("/app/rest/builds/id:{id}/tags/")
    fun replaceTags(@Path("id") buildId: String, @Body tags: TagsBean): Response

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

    @PUT("/app/rest/buildTypes/id:{id}/settings/{name}")
    fun setBuildTypeSettings(@Path("id") buildTypeId: String, @Path("name") name: String, @Body value: TypedString): Response

    @Headers("Accept: application/json")
    @POST("/app/rest/buildQueue")
    fun triggerBuild(@Body value: TriggerBuildRequestBean): TriggeredBuildBean

    @Headers("Accept: application/json")
    @POST("/app/rest/builds/id:{id}")
    fun cancelBuild(@Path("id") buildId: String, @Body value: BuildCancelRequestBean): Response

    @Headers("Accept: application/json")
    @POST("/app/rest/buildQueue/id:{id}")
    fun removeQueuedBuild(@Path("id") buildId: String, @Body value: BuildCancelRequestBean): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/users")
    fun users(): UserListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/users/{userLocator}")
    fun users(@Path("userLocator") userLocator: String): UserBean

    @Headers("Accept: application/json")
    @POST("/app/rest/users/{userLocator}/roles")
    fun createRole(@Path("userLocator") userLocator: String, @Body role: RoleBean): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/agents")
    fun agents(): BuildAgentsBean

    @Headers("Accept: application/json")
    @GET("/app/rest/agentPools")
    fun agentPools(): BuildAgentPoolsBean

    @Headers("Accept: application/json")
    @GET("/app/rest/agents/{locator}")
    fun agents(@Path("locator") agentLocator: String? = null): BuildAgentBean

    @Headers("Accept: application/json")
    @GET("/app/rest/agents/{locator}/compatibilityPolicy")
    fun agentCompatibilityPolicy(@Path("locator") agentLocator: String): CompatibilityPolicyBean

    @Headers("Accept: application/json")
    @PUT("/app/rest/agents/{locator}/compatibilityPolicy")
    fun updateAgentCompatibilityPolicy(@Path("locator") agentLocator: String, @Body policy: CompatibilityPolicyBean): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/agentPools/{locator}")
    fun agentPools(@Path("locator") agentLocator: String? = null): BuildAgentPoolBean

    @Headers("Accept: application/json")
    @POST("/app/rest/agentPools/id:{id}/projects")
    fun assignProjectToAgentPool(@Path("id") id: String, @Body project: ProjectBean): Response

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

    @Streaming
    @GET("/downloadBuildLog.html")
    fun buildLog(@Query("buildId") id: String): Response

    @Headers("Accept: application/json")
    @GET("/app/rest/changes/buildType:{id},version:{version}")
    fun change(@Path("id") buildType: String, @Path("version") version: String): ChangeBean

    @Headers("Accept: application/json")
    @GET("/app/rest/changes/id:{id}")
    fun change(@Path("id") changeId: String): ChangeBean

    @Headers("Accept: application/json")
    @GET("/app/rest/changes/{id}/firstBuilds")
    fun changeFirstBuilds(@Path("id") id: String): BuildListBean

    @Headers("Accept: application/json")
    @GET("/app/rest/mutes")
    fun mutes(@Query("locator") buildLocator: String): MutesBean

    @Headers("Accept: application/json")
    @POST("/app/rest/mutes/multiple")
    fun createMutes(@Body mutes: MutesBean): Response
}

internal class MutesBean {
    var mute: List<MuteBean> = ArrayList()
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

internal open class VcsRootBean : IdBean() {
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

internal open class BuildBean : IdBean() {
    var buildTypeId: String? = null
    var canceledInfo: BuildCanceledBean? = null
    var number: String? = null
    var status: BuildStatus? = null
    var state: String? = null
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

    var properties: ParametersBean? = ParametersBean()
    var buildType: BuildTypeBean? = BuildTypeBean()

    var `snapshot-dependencies`: BuildListBean? = null
}

internal class BuildRunningInfoBean {
    val percentageComplete: Int = 0
    val elapsedSeconds: Long = 0
    val estimatedTotalSeconds: Long = 0
    val outdated: Boolean = false
    val probablyHanging: Boolean = false
}

internal class BuildTypeBean() : IdBean() {
    var name: String? = null
    var projectId: String? = null
    var paused: Boolean? = null
    var settings: BuildTypeSettingsBean? = null

    constructor(buildConfiguration: BuildConfiguration) : this() {
        id = buildConfiguration.id.stringId
        name = buildConfiguration.name
        projectId = buildConfiguration.projectId.stringId
        paused = buildConfiguration.paused
    }
}

internal class BuildTypeSettingsBean {
    var property: List<NameValuePropertyBean> = ArrayList()
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

internal class ArtifactDependencyBean : IdBean() {
    var type: String? = null
    var disabled: Boolean? = false
    var inherited: Boolean? = false
    var properties: ParametersBean? = ParametersBean()
    var `source-buildType`: BuildTypeBean = BuildTypeBean()
}

internal class ArtifactDependenciesBean {
    var `artifact-dependency`: List<ArtifactDependencyBean>? = ArrayList()
}

internal class ProjectBean() : IdBean() {
    var name: String? = null
    var parentProjectId: String? = null
    var archived: Boolean? = null

    var projects: ProjectsBean? = ProjectsBean()
    var parameters: ParametersBean? = ParametersBean()
    var buildTypes: BuildTypesBean? = BuildTypesBean()

    constructor(project: Project) : this() {
        id = project.id.stringId
        name = project.name
        parentProjectId = project.parentProjectId?.stringId
        archived = project.archived
        projects = ProjectsBean().apply {
            this.project = project.childProjects.map(::ProjectBean)
        }
        parameters = ParametersBean(project.parameters.map { ParameterBean(it.name, it.value) })
        buildTypes = BuildTypesBean().apply {
            buildType = project.buildConfigurations.map(::BuildTypeBean)
        }
    }
}

internal class BuildAgentBean : IdBean() {
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
}

internal class CompatibilityPolicyBean {
    var buildTypes: BuildTypesBean? = BuildTypesBean()
    var policy: String? = null
}

internal class BuildAgentPoolBean : IdBean() {
    var name: String? = null

    var projects: ProjectsBean? = ProjectsBean()
    var agents: BuildAgentsBean? = BuildAgentsBean()
}

internal class ChangesBean {
    var change: List<ChangeBean>? = ArrayList()
}

internal class ChangeBean : IdBean() {
    var version: String? = null
    var user: UserBean? = null
    var date: String? = null
    var comment: String? = null
    var username: String? = null
}

internal class UserBean() : IdBean() {
    var username: String? = null
    var name: String? = null
    var email: String? = null
    var roles: RolesBean? = null

    constructor(user: User) : this() {
        id = user.id.stringId
        username = user.username
        name = user.name
        email = user.email
        roles = RolesBean().apply {
            role = user.roles.map(::RoleBean)
        }
    }
}

internal class RolesBean {
    var role: List<RoleBean>? = ArrayList()
}

internal class RoleBean() {
    var roleId: String? = null
    var scope: String? = null

    constructor(role: Role) : this() {
        roleId = role.id.stringId
        scope = role.scope.descriptor
    }
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

internal open class TestBean() {
    var id: String? = null

    constructor(testId: TestId) : this() {
        id = testId.stringId
    }
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

internal class InvestigationListBean {
    var investigation: List<InvestigationBean> = ArrayList()
}

internal class MuteBean : IssueBean {
    @Suppress("unused")
    constructor() : super()

    constructor(mute: Mute) : super(mute) {
        assignment?.user = mute.mutedBy?.let(::UserBean)
    }
}

internal sealed class IssueBean() : IdBean() {
    var assignment: AssignmentBean? = null
    var resolution: InvestigationResolutionBean? = null
    var scope: InvestigationScopeBean? = null
    var target: InvestigationTargetBean? = null

    constructor(issue: Issue) : this() {
        assignment = AssignmentBean().apply {
            text = issue.comment
        }
        resolution = InvestigationResolutionBean(issue.resolveMethod, issue.time)
        scope = InvestigationScopeBean(issue.scope)
        target = InvestigationTargetBean().apply {
            when (issue.targetType) {
                InvestigationTargetType.TEST -> {
                    tests = TestUnderInvestigationListBean().apply {
                        test = issue.testIds?.map(::TestBean) ?: emptyList()
                    }
                }
                InvestigationTargetType.BUILD_PROBLEM -> {
                    problems = ProblemUnderInvestigationListBean().apply {
                        problem = issue.problemIds?.map {
                            BuildProblemBean().apply {
                                id = it.stringId
                            }
                        } ?: emptyList()
                    }
                }
                InvestigationTargetType.BUILD_CONFIGURATION -> anyProblem = true
                else -> error(issue)
            }
        }
    }
}

internal class InvestigationBean : IssueBean {
    var assignee: UserBean? = null
    var state: InvestigationState? = null

    @Suppress("unused")
    constructor() : super()

    constructor(investigation: Investigation) : super(investigation) {
        state = investigation.state
        assignee = UserBean(investigation.assignee)
        assignment?.user = investigation.reporter?.let(::UserBean)
    }
}

class InvestigationResolutionBean() {
    var type: String? = null
    var time: String? = null

    constructor(resolution: InvestigationResolveMethod, time: String?) : this() {
        type = resolution.value
        this.time = time
    }
}

internal class AssignmentBean {
    var user: UserBean? = null
    var text: String? = null
    var timestamp: String? = null
}

internal open class InvestigationTargetBean {
    var tests: TestUnderInvestigationListBean? = null
    var problems: ProblemUnderInvestigationListBean? = null
    var anyProblem: Boolean? = null
}

internal class TestUnderInvestigationListBean {
    val count: Int? = null
    var test: List<TestBean> = ArrayList()

}

internal class ProblemUnderInvestigationListBean {
    val count: Int? = null
    var problem: List<BuildProblemBean> = ArrayList()
}

internal class InvestigationScopeBean() {
    var buildTypes: BuildTypesBean? = null
    var project: ProjectBean? = null

    constructor(scope: InvestigationScope) : this() {
        when (scope) {
            is InvestigationScope.InProject -> {
                project = ProjectBean(scope.project)
            }
            else -> {
                buildTypes = BuildTypesBean().apply {
                    buildType = when (scope) {
                        is InvestigationScope.InBuildConfiguration -> listOf(scope.configuration)
                        is InvestigationScope.InBuildConfigurations -> scope.configuration
                        else -> error(scope::class.java)
                    }.map(::BuildTypeBean)
                }
            }
        }

    }
}