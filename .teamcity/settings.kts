import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

version = "2022.10"

project {
    description = "REST API client written in Kotlin"

    buildType {
        id("TC_TeamCityTools_TeamCityRestClient_Build")
        name = "Build"
        triggers {
            vcs {
                branchFilter = "+:<default>"
            }
        }
        requirements {
            exists("docker.version")
            equals("teamcity.agent.jvm.os.name", "Linux")
        }
        params {
            text("username", "admin")
            password("password", "admin")
        }
        vcs {
            cleanCheckout = true
            root(DslContext.settingsRoot)
        }

        steps {
            script {
                name = "Deploy test data"
                scriptContent = """
                #!/bin/bash
                DATA_DIR="${'$'}HOME/DataDirs/web-tests-rest"
                mkdir -p ${'$'}DATA_DIR
                wget -qO- "https://repo.labs.intellij.net/teamcity/org/jetbrains/teamcity/tc-rest-client-tests-db/1.1.0-SNAPSHOT/tc-rest-client-tests-db-1.1.0-20200123.155623-1.tar.gz" | tar xvz -C ${'$'}DATA_DIR
                """.trimIndent()
            }

            script {
                name = "Start server"
                scriptContent = """
                #!/bin/bash
                docker run -d --name teamcity-server -v ${'$'}HOME/DataDirs/web-tests-rest:/data/teamcity_server/datadir -p 8111:8111 jetbrains/teamcity-server:2020.1.5
                until $(curl --output /dev/null --silent --head --fail http://localhost:8111/login.html); do
                echo "waiting for teamcity server startup completion..."
                sleep 5
                done
                """.trimIndent()
            }

            script {
                name = "Create teamcity_connection.properties"
                workingDir = "teamcity-rest-client-impl/src/test"
                scriptContent = """
                mkdir resources
                touch resources/teamcity_connection.properties
                echo "serverUrl=http://localhost:8111" > resources/teamcity_connection.properties
                echo "username=%username%" >> resources/teamcity_connection.properties
                echo "password=%password%" >> resources/teamcity_connection.properties
                """.trimIndent()
            }

            gradle {
                name = "Build"
                tasks = "build"
                useGradleWrapper = true
            }

            script {
                name = "Stop server"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                scriptContent = """
                #!/bin/bash
                docker stop teamcity-server
                docker rm teamcity-server
                """.trimIndent()
            }
        }
    }

    buildType {
        id("TC_TeamCityTools_TeamCityRestClient_Publish")
        name = "Publish to Space"

        steps {
            gradle {
                name = "Publish"
                tasks = "publish"
                useGradleWrapper = true
            }
        }
    }
}
