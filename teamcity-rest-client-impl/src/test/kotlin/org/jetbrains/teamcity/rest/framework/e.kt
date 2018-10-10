package org.jetbrains.teamcity.rest.framework

import org.jetbrains.teamcity.rest.BuildConfigurationId
import org.jetbrains.teamcity.rest.ProjectId
import org.jetbrains.teamcity.rest.VcsRoot
import javax.xml.stream.XMLStreamWriter

fun generateBuild(id: BuildConfigurationId, name: String, parentProjectId: ProjectId, parameters: Map<String, String>, vcsRoots: Collection<VcsRoot>, stepsBuilder: XMLStreamWriter.() -> Unit) =
        xml {
            element("buildType") {
                attribute("id", id.stringId)
                attribute("name", name)

                element("project") {
                    attribute("id", parentProjectId.stringId)
                }

                element("parameters") {
                    parameters.forEach {
                        property(it.key, it.value)
                    }
                }

                if (vcsRoots.isNotEmpty()) {
                    element("vcs-root-entries") {
                        vcsRoots.sortedBy { it.name }.forEach { vcsRoot ->
                            element("vcs-root-entry") {
                                element("vcs-root") {
                                    attribute("id", vcsRoot.id.stringId)
                                }
                                if (vcsRoots.size > 1) {
                                    element("checkout-rules", "+:. => ${vcsRoot.name}")
                                }
                            }
                        }
                    }
                }

                element("settings") {
                    property("artifactRules", "%system.teamcity.build.tempDir%/**=>tmp")
                    property("cleanBuild", "true")
                    property("executionTimeoutMin", "3")
                }

                element("steps") {
                    stepsBuilder()
                }
            }
        }

fun XMLStreamWriter.antStep(executeAlways: Boolean = false, builder: XMLStreamWriter.() -> Unit) =
        element("step") {
            attribute("type", "Ant")
            element("properties") {
                if (executeAlways) {
                    property("teamcity.step.mode", "execute_always")
                }

                property("build-file", xml {
                    builder()
                })
                property("use-custom-build-file", "true")
            }
        }

fun XMLStreamWriter.runJarStep(jar: String, args: List<String>, executeAlways: Boolean, systemProperties : Map<String, String>) =
        antStep(executeAlways = executeAlways) {
            element("project") {
                element("java") {
                    attribute("jar", jar)
                    attribute("fork", "true")
                    attribute("failonerror", "true")
                    jvmArg("-Xmx1g")
                    jvmArg("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
                    systemProperty("file.encoding", "UTF-8")
                    systemProperty("java.io.tmpdir", "%system.teamcity.build.tempDir%")
                    systemProperties.forEach { key, value ->
                        systemProperty(key, value)
                    }
                    args.forEach {
                        element("arg") {
                            attribute("value", it)
                        }
                    }
                }
            }
        }

private fun XMLStreamWriter.systemProperty(key: String, value: String) =
        element("sysproperty") {
            attribute("key", key)
            attribute("value", value)
        }

private fun XMLStreamWriter.jvmArg(arg: String) =
        element("jvmarg") {
            attribute("value", arg)
        }