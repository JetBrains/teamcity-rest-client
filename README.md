# teamcity-rest-client
Client for TeamCity REST API written in Kotlin. The code snippet below will download `*.zip` artifacts from the latest successfull build with tag `publish` of the specified build configuration to `out` directory.
```kotlin
val configurationId = BuildConfigurationId("Kotlin_StandardLibraryDocumentation")
val build = TeamCityInstance.guestAuth("https://teamcity.jetbrains.com").builds()
                            .fromConfiguration(configurationId)
                            .withTag("publish")
                            .latest()
build!!.downloadArtifacts("*.zip", File("out"))
```
