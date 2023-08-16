import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.vcs

version = "2022.10"

project {
    description = "REST API client written in Kotlin"

    buildType {
        id("Build")
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
            text("env.BUILD_COUNTER", "%build.counter%")

            text("username", "admin")
            password("password", "admin")

            password("space_test_files_token", "credentialsJSON:e62f0e1a-e534-4184-9680-99edb020abbc")
        }
        vcs {
            cleanCheckout = true
            root(DslContext.settingsRoot)
        }
        features {
            pullRequests {
                provider = github {
                    authType = vcsRoot()
                    filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER_OR_COLLABORATOR
                    ignoreDrafts = true
                }
            }
        }

        steps {
            script {
                name = "Deploy test data"
                scriptContent = """
                #!/bin/bash
                DATA_DIR="${'$'}HOME/DataDirs/web-tests-rest"
                mkdir -p ${'$'}DATA_DIR
                curl -f -L \
                  -H "Authorization: Bearer %space_test_files_token%" \
                  https://packages.jetbrains.team/files/p/teamcity-rest-client/test-files/tests/tc-rest-client-tests-db-1.3.0-20230816.tar.gz | tar xvz -C ${'$'}DATA_DIR                  
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
        id("Publish")
        name = "Publish to Space"

        requirements {
            exists("docker.version")
            equals("teamcity.agent.jvm.os.name", "Linux")
        }

        params {
            text(
                "releaseVersion",
                "SNAPSHOT",
                "Version number to be used when publishing to Space",
                "Version number to be used when publishing to Space",
                ParameterDisplay.PROMPT,
                readOnly = false,
                allowEmpty = false
            )
            text("env.SPACE_USER", "ce15a478-920a-4b37-8137-b73ab65fd926")
            password("env.SPACE_KEY", "credentialsJSON:4cad0710-36a3-4452-bd19-a9fd43c6b1f0")
        }

        vcs {
            cleanCheckout = true
            root(DslContext.settingsRoot)
            branchFilter = "+:<default>"
        }

        steps {
            gradle {
                name = "Publish"
                tasks = "publish"
                gradleParams = "-PforcedVersion=%releaseVersion%"
                useGradleWrapper = true
            }
        }
    }
}
