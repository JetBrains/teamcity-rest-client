# teamcity-rest-client [![JetBrains team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![plugin status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityPluginsByJetBrains_TeamCityKubernetesPlugin_Build)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityRestApiClients_KotlinClient_Build&guest=1)

Client for TeamCity REST API written in Kotlin. The code snippet below will download `*.zip` artifacts from the latest successful build with tag `publish` of the specified build configuration to `out` directory.
```kotlin
val docs = BuildConfigurationId("Kotlin_StandardLibraryDocumentation")
val build = TeamCityInstanceFactory.guestAuth("https://teamcity.jetbrains.com").builds()
                            .fromConfiguration(docs)
                            .withTag("publish")
                            .latest()
build!!.downloadArtifacts("*.zip", File("out"))
```

Another snippet will run a build on your own server
```kotlin
val tc = TeamCityInstanceFactory.httpAuth(
        "https://myserver.local", "login", "password")

val buildConfiguration = tc.buildConfiguration(BuildConfigurationId("BuildConfId"))
val build = buildConfiguration.runBuild(
        parameters = mapOf("myparameter1" to "value", "myparameter2" to "value")
)
```
# Published to Space
https://packages.jetbrains.team/maven/p/teamcity-rest-client/teamcity-rest-client


You can add the dependency in your build.gradle file:

```gradle
repositories {
    maven {
        url "https://packages.jetbrains.team/maven/p/teamcity-rest-client/teamcity-rest-client"
    }
}

dependencies {
    compile "org.jetbrains.teamcity:teamcity-rest-client:PACKAGE_VERSION"
}
```

# Contributing

Your contributions are welcome, please read the [CONTRIBUTING.md](CONTRIBUTING.md) for details. 
