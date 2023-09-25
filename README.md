# teamcity-rest-client [![JetBrains team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![plugin status](https://teamcity.jetbrains.com/app/rest/builds/buildType:(id:TeamCityRestApiClients_KotlinClient_Build)/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityRestApiClients_KotlinClient_Build&guest=1)

Client for TeamCity REST API written in Kotlin. The code snippet below will download `*.zip` artifacts from the latest successful build with tag `publish` of the specified build configuration to `out` directory using *coroutines non-blocking API*.
```kotlin
val docs = BuildConfigurationId("Kotlin_StandardLibraryDocumentation")
val build = TeamCityInstanceBuilder("https://teamcity.jetbrains.com").withGuestAuth().build()
    .builds()
    .fromConfiguration(docs)
    .withTag("publish")
    .latest() // suspending call

// suspending call
build!!.downloadArtifacts("*.zip", File("out"))
```

Another snippet will run a build on your own server using *coroutines non-blocking API*.
```kotlin
val tc = TeamCityInstanceBuilder("https://myserver.local")
    .withHttpAuth("login", "password")
    .build()

// suspending call
val buildConfiguration = tc.buildConfiguration(BuildConfigurationId("BuildConfId"))
// suspending call
val build = buildConfiguration.runBuild( 
    parameters = mapOf("myparameter1" to "value", "myparameter2" to "value")
)
```

Please note that repeated getter call on entities will return the same value because the result is cached:
```kotlin
val tc = TeamCityInstanceBuilder("https://myserver.local").withGuestAuth().build()
val build = tc.builds().latest()!!

val first = build.getState()  // suspending call
delay(1000000L)
val second = build.getState() // fast call; won't suspend, returns cached value 
assert(first == second)       // the check will always pass, even if the build state has changed
```

*Blocking API* is also available for you.
```kotlin
val docs = BuildConfigurationId("Kotlin_StandardLibraryDocumentation")
val build = TeamCityInstanceBuilder("https://teamcity.jetbrains.com")
    .withGuestAuth()
    .buildBlockingInstance() 
    .builds()
    .fromConfiguration(docs)
    .withTag("publish")
    .latest() // will block execution until response received

// will block execution until response received
build!!.downloadArtifacts("*.zip", File("out"))
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

# Deploy (JetBrains internal)

https://buildserver.labs.intellij.net/buildConfiguration/BuildUtils_TeamCityRestClient_Publish

# Contributing

Your contributions are welcome, please read the [CONTRIBUTING.md](CONTRIBUTING.md) for details. 
