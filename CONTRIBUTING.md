Contributing to teamcity-rest-client
===

The goal of this library is providing convenient access to TeamCity servers from Kotlin and Java code. 
Users of this library don't need to know about TeamCity internals and read documentation of its REST API.

Please maintain the following policies while contributing to the library.

1. Name of API elements must be comprehensible for users. If meaning of a method in REST URL or a property of an object
 isn't clear, we can suggest a better name in the API. E.g. build configurations are called `buildTypes` in the REST API, 
 and we use the more familiar `BuildConfiguration` term in the API. 'Artifact Dependency' object has `source-buildType`
 property in JSON and we use `dependsOnBuildConfiguration` name for the corresponding property in the API.  

2. Use typed interfaces instead of `Map<String, String>` objects to provide access to the main standard properties of API 
objects. This way users of the library won't need to read documentation of REST API to find names of these properties. 
It's ok to use such objects to provide access to user-defined or custom non-standard properties. 

3. Keep binary and source backward compatibility while doing changes.
We suppose that users of the library don't implement interfaces from the API so it's ok to add new functions to it. 
However removing functions and changing their signatures will break compatibility, so mark them as deprecated instead.
Elements marked as deprecated may be removed in the next major version.  