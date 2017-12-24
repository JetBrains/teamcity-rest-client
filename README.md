# teamcity-rest-client [![JetBrains team project](http://jb.gg/badges/team.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0) [![Build Status](https://travis-ci.org/JetBrains/teamcity-rest-client.svg?branch=master)](https://travis-ci.org/JetBrains/teamcity-rest-client) [![Download](https://api.bintray.com/packages/jetbrains/teamcity-rest-client/teamcity-rest-client/images/download.svg)](https://bintray.com/bintray/jcenter?filterByPkgName=teamcity-rest-client)

Client for TeamCity REST API written in Kotlin. The code snippet below will download `*.zip` artifacts from the latest successfull build with tag `publish` of the specified build configuration to `out` directory.
```kotlin
val docs = BuildConfigurationId("Kotlin_StandardLibraryDocumentation")
val build = TeamCityInstance.guestAuth("https://teamcity.jetbrains.com").builds()
                            .fromConfiguration(docs)
                            .withTag("publish")
                            .latest()
build!!.downloadArtifacts("*.zip", File("out"))
```
# Published on jcenter
https://bintray.com/bintray/jcenter?filterByPkgName=teamcity-rest-client


You can add the dependency in your build.gradle file:

```gradle
repositories {
    jcenter()
}

dependencies {
    compile "org.jetbrains.teamcity:teamcity-rest-client:PACKAGE_VERSION"
}
```
