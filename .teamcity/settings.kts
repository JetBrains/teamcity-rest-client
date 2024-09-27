import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.Qodana
import jetbrains.buildServer.configs.kotlin.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.buildSteps.qodana
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import jetbrains.buildServer.configs.kotlin.triggers.vcs

version = "2023.05"

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
            text("env.DATA_DIR", "tests/datadir")
            text("env.LOGS_DIR", "tests/logs")

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
        artifactRules = "+:%env.LOGS_DIR% -> server_logs"

        steps {
            script {
                name = "Fix env variables"
                scriptContent = """
                #!/bin/bash
                    
                if [[ "{'${'$'}'}LOGS_DIR" = /* ]]; then
                F_LOGS_DIR=${'$'}LOGS_DIR
                else
                F_LOGS_DIR=${'$'}PWD/${'$'}LOGS_DIR
                fi                            
                
                if [[ "{'${'$'}'}DATA_DIR" = /* ]]; then
                F_DATA_DIR=${'$'}DATA_DIR
                else
                F_DATA_DIR=${'$'}PWD/${'$'}DATA_DIR
                fi
                
                echo "##teamcity[setParameter name='env.LOGS_DIR' value='${'$'}F_LOGS_DIR']"
                echo "##teamcity[setParameter name='env.DATA_DIR' value='${'$'}F_DATA_DIR']"
                """.trimIndent()
            }

            script {
                name = "Deploy test data and clean old test data"
                scriptContent = """
                #!/bin/bash                
                
                rm -rf ${'$'}DATA_DIR 
                mkdir -p ${'$'}DATA_DIR

                rm -rf ${'$'}LOGS_DIR 
                mkdir -p ${'$'}LOGS_DIR
                
                curl -f -L \
                  -H "Authorization: Bearer %space_test_files_token%" \
                  https://packages.jetbrains.team/files/p/teamcity-rest-client/test-files/tests/tc-rest-client-tests-db-1.3.0-20230816.tar.gz | tar xvz -C ${'$'}DATA_DIR
                """.trimIndent()
            }

            script {
                name = "Start server"
                scriptContent = """
                #!/bin/bash 
                docker run -d --name teamcity-server \
                           -v ${'$'}DATA_DIR:/data/teamcity_server/datadir \
                           -v ${'$'}LOGS_DIR:/opt/teamcity/logs \
                           -p 8111:8111 \
                           --privileged \
                           -u 0 \
                           jetbrains/teamcity-server:2023.05.4
                           
                COUNTER=120 # 10 min
                until [ ${'$'}COUNTER -eq 0 ] || [[ ${'$'}(curl --output /dev/null --silent --head --fail http://localhost:8111/login.html) -eq 0 ]]; do
                echo "(${'$'}COUNTER) Waiting for teamcity server startup completion..."
                sleep 5
                let COUNTER-=1
                done
                
                if ${'$'}(curl --output /dev/null --silent --head --fail http://localhost:8111/login.html); then
                echo "Server did not started in 10 minutes, aborting."
                exit 1
                fi                  
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
        id("SecurityCheck")
        name = "Security check with Qodana"

        vcs {
            cleanCheckout = true
            root(DslContext.settingsRoot)
        }

        params {
            param("env.QODANA_TOKEN", "credentialsJSON:74c92ed1-94f9-433a-8576-5e5185a7ad54")
        }

        triggers {
            vcs {
                branchFilter = """
                    +:<default>
                    +:pull/*
                """.trimIndent()
            }

            schedule {
                daily {
                    hour = 8
                }

                /* We want this build to run regardless of the new changes as new vulnerabilities are being discovered all the time*/
                withPendingChangesOnly = false
                enableQueueOptimization = false

                branchFilter = "+:<default>"
            }
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
            qodana {
                linter = jvm {
                    version = Qodana.JVMVersion.LATEST
                }
                cloudToken = "credentialsJSON:74c92ed1-94f9-433a-8576-5e5185a7ad54"
                inspectionProfile = customProfile {
                    path = "qodana.yaml"
                }

                reportAsTests = true

                param("report-as-test-mode", "each-problem-is-test")
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
