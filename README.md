# teamcity-rest-client [![Build Status](https://travis-ci.org/JetBrains/teamcity-rest-client.svg?branch=master)](https://travis-ci.org/JetBrains/teamcity-rest-client)

Client for TeamCity REST API written in Kotlin. The code snippet below will download `*.zip` artifacts from the latest successfull build with tag `publish` of the specified build configuration to `out` directory.
```kotlin
val docs = BuildConfigurationId("Kotlin_StandardLibraryDocumentation")
val build = TeamCityInstance.guestAuth("https://teamcity.jetbrains.com").builds()
                            .fromConfiguration(docs)
                            .withTag("publish")
                            .latest()
build!!.downloadArtifacts("*.zip", File("out"))
```
# Published on bintray
https://bintray.com/jetbrains/teamcity-rest-client/teamcity-rest-client/view
