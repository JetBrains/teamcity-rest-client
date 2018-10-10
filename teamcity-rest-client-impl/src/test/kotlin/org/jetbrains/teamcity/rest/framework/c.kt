package org.jetbrains.teamcity.rest.framework

import org.jetbrains.teamcity.rest.BuildConfiguration
import org.jetbrains.teamcity.rest.BuildConfigurationId
import org.jetbrains.teamcity.rest.Project
import org.jetbrains.teamcity.rest.ProjectId
import org.jetbrains.teamcity.rest.TeamCityInstance
import org.jetbrains.teamcity.rest.VcsRoot
import org.jetbrains.teamcity.rest.VcsRootId
import org.jetbrains.teamcity.rest.VcsRootType
import java.util.*
import javax.xml.stream.XMLStreamWriter

class TeamCityContext {
    companion object {
        val instance: TeamCityInstance by lazy {
            // here could a different instance. e.g. create from admin user, not superuser
            TeamCityConfigurer.teamcity
        }

        fun asGitURI(repo: String) = "git://git-server/$repo"
    }

    val instance = TeamCityContext.instance
    val project: Project by lazy {
        val uuid = UUID.randomUUID().toString().replace("-", "")

        val projectPrefix = "P_merge_robot"

        val projectRoot = instance.rootProject().childProjects.firstOrNull { it.name == projectPrefix }
                ?: instance.rootProject().createProject(id = ProjectId(projectPrefix), name = projectPrefix)

        val project = projectRoot.createProject(id = ProjectId("project_$uuid"), name = uuid)
        project
    }

    val vcsRoot: VcsRoot by lazy {
        createVcsRoot("test")
    }

        fun createVcsRoot(repo: String) = project.createVcsRoot(
                id = VcsRootId("vcsRoot_${repo.replace(Regex("[-.]"), "")}_${project.name}"),
                name = repo, type = VcsRootType.GIT, properties = mapOf(
                "agentCleanFilesPolicy" to "ALL_UNTRACKED",
                "agentCleanPolicy" to "ALWAYS",
                "authMethod" to "ANONYMOUS",
                "branch" to "refs/heads/master",
                "ignoreKnownHosts" to "true",
                "submoduleCheckout" to "IGNORE",
                "url" to asGitURI(repo),
                "useAlternates" to "true",
                "usernameStyle" to "FULL",
                "teamcity:branchSpec" to "+:refs/heads/(*)"
        ))

        private fun createBuildType(name: String, builder: XMLStreamWriter.() -> Unit): BuildConfiguration {
            val id = "${project.id}_robot_${name.replace(Regex("[^a-zA-Z0-9]"), "")}"

            val xml = generateBuild(
                    id = BuildConfigurationId(id),
                    name = name,
                    parentProjectId = project.id,
                    parameters = mapOf("teamcity.build.serviceMessages.logOriginal" to "true"),
                    vcsRoots = listOf(vcsRoot)) {
                antStep(builder = builder)
            }

            return project.createBuildConfiguration(xml)
        }

        val payloadAlwaysGreenBuild by lazy {
            createBuildType("Always Green") {
                element("project") {
                    element("echo") {
                        attribute("message", "Always green")
                    }
                }
            }
        }

        private val testName = "my.MyTests.TestMethod"

        val payloadAdditionalTestsGreen by lazy {
            createBuildType("Additional Green Tests") {
                element("project") {
                    element("echo") {
                        attribute("message",
                                ServiceMessage.asString(ServiceMessageTypes.TEST_STARTED, mapOf("name" to testName)) + "\n" +
                                        ServiceMessage.asString(ServiceMessageTypes.TEST_FINISHED, mapOf("name" to testName))
                        )
                    }
                }
            }
        }

        val payloadAdditionalTestsGreen2 by lazy {
            createBuildType("Additional Green Tests (2)") {
                element("project") {
                    element("echo") {
                        attribute("message",
                                ServiceMessage.asString(ServiceMessageTypes.TEST_STARTED, mapOf("name" to testName)) + "\n" +
                                        ServiceMessage.asString(ServiceMessageTypes.TEST_FINISHED, mapOf("name" to testName))
                        )
                    }
                }
            }
        }

        val payloadAdditionalTestsRed by lazy {
            createBuildType("Additional Red Test") {
                element("project") {
                    element("echo") {
                        attribute("message",
                                ServiceMessage.asString(ServiceMessageTypes.TEST_STARTED, mapOf("name" to testName)) + "\n" +
                                        ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED,
                                                mapOf("name" to testName, "message" to "TEST FAILED MESSAGE", "details" to "none")) + "\n" +
                                        ServiceMessage.asString(ServiceMessageTypes.TEST_FINISHED, mapOf("name" to testName))
                        )
                    }
                }
            }
        }

        val payloadAdditionalTestsSecondAttemptSuccess by lazy {
            createBuildType("Second Attempt Success") {
                element("project") {
                    attribute("default", "build")
                    element("condition") {
                        attribute("property", "failedAttempt")

                        element("equals") {
                            attribute("arg1", "\${additionalTestsRetryNumber}")
                            attribute("arg2", "1")
                        }
                    }

                    element("target") {
                        attribute("name", "failOnSpecificAttempt")
                        attribute("if", "failedAttempt")

                        element("echo") {
                            attribute("message", ServiceMessage.asString(ServiceMessageTypes.BUILD_PORBLEM, mapOf("description" to "Some infrastructural problems")))
                            attribute("if", "failedAttempt")
                        }
                    }

                    element("target") {
                        attribute("name", "build")
                        attribute("depends", "failOnSpecificAttempt")
                        element("echo") {
                            attribute("message", "additionalTestsRetryNumber=\${additionalTestsRetryNumber}")
                        }
                    }
                }
            }
        }

        val throwInfraProblem by lazy {
            createBuildType("Infra Problem") {
                element("project") {
                    element("echo") {
                        attribute("message",
                                ServiceMessage.asString(ServiceMessageTypes.BUILD_PORBLEM, mapOf("description" to "Some infrastructural problems")))
                    }
                }
            }
        }

        val payloadAlwaysRedBuild by lazy {
            createBuildType("Always Red") {
                element("project") {
                    element("echo") {
                        attribute("message",
                                ServiceMessage.asString(ServiceMessageTypes.BUILD_PORBLEM, mapOf("description" to "Always Red")))
                    }
                }
            }
        }

        val payloadAlwaysRedCompileBuild by lazy {
            createBuildType("Compile") {
                element("project") {
                    element("echo") {
                        attribute("message",
                                ServiceMessage.asString(ServiceMessageTypes.BUILD_PORBLEM, mapOf("description" to "Always Red")))
                    }
                }
            }
        }

        val payloadSleepBuild by lazy {
            createBuildType("Sleep Build") {
                element("project") {
                    element("sleep") {
                        attribute("minutes", "1")
                    }
                }
            }
        }

        val payloadSleepBuild10s by lazy {
            createBuildType("Sleep Build 10s") {
                element("project") {
                    element("sleep") {
                        attribute("seconds", "10")
                    }
                }
            }
        }

/*
        internal fun createMergeRobotCommand(vararg commands: String,
                                             vcsRoots: Collection<VcsRoot> = listOf(vcsRoot),
                                             parentProject: Project = project,
                                             parameters: Map<String, String> = commonParameters,
                                             jar: File = BaseTest.mergeRobotTestJar,
                                             executeAlwaysAdditionalCommand: String? = null,
                                             systemProperties : Map<String, String> = emptyMap()): BuildConfiguration {
            val uuid = uuid(4)
            val id = "${parentProject.id}_robot_${commands.joinToString("_").replace(Regex("[^a-zA-Z0-9]"), "")}_$uuid"

            val xml = generateBuild(
                    id = BuildConfigurationId(id),
                    name = commands.joinToString(" ") + uuid,
                    parentProjectId = parentProject.id,
                    parameters = parameters,
                    vcsRoots = vcsRoots
            ) {
                runJarStep(jar.name, commands.toList(), executeAlways = false, systemProperties = systemProperties)
                if (executeAlwaysAdditionalCommand != null) {
                    runJarStep(jar.name, listOf(executeAlwaysAdditionalCommand), executeAlways = true, systemProperties = systemProperties)
                }
            }

            return parentProject.createBuildConfiguration(xml)
        }
*/
    }
}