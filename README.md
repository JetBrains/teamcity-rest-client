# teamcity-rest-client
Client for TeamCity REST API written in Kotlin. The code snippet below will download `*.zip` artifacts from the latest successfull build with tag `publish` of the specified build configuration to `out` directory.

```kotlin
val docs = BuildConfigurationId("Kotlin_StandardLibraryDocumentation")
val buildLocator = TeamCityInstance.guestAuth("https://teamcity.jetbrains.com").builds()
                            .fromConfiguration(docs)
                            .withTag("publish") as BuildLocator
val build = buildLocator.latest()
build!!.downloadArtifacts("*.zip", File("out"))
```

# Published on bintray
https://bintray.com/jetbrains/teamcity-rest-client/teamcity-rest-client/view

You can add the dependency in your build.gradle file:

```gradle
repositories {
    jcenter()
    maven { url "http://dl.bintray.com/jetbrains/teamcity-rest-client" }
}

dependencies {
    ext.teamcity_client_version = '0.1.45'

    compile "org.jetbrains.teamcity:teamcity-rest-client:$teamcity_client_version"
}
```
